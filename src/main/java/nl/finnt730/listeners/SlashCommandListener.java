package nl.finnt730.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import nl.finnt730.DatabaseManager;
import nl.finnt730.UserDB;
import nl.finnt730.paste.PasteReader;
import nl.finnt730.paste.PasteSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlashCommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger("nl.finnt730.slashcommands");
    private static final Set<String> PASTE_SITES = Set.of("mclogs", "gnomebot", "capaste", "cdpaste", "mmd", "pastesdev");
    private static final Set<String> MCLOGS_INSTANCES = Set.of("mclogs", "gnomebot", "capaste");

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();
        Member member = event.getMember();
        
        if (Set.of("register", "alias", "delete", "description").contains(name)) {
            boolean canInvoke = member.getRoles().stream().anyMatch(role -> role.isHoisted() || nl.finnt730.Global.isManager(role) || member.isOwner());
            if (name.equals("description") && !member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR) && !member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
                event.reply("You don't have permission to modify command descriptions!").setEphemeral(true).queue();
                return;
            }
            if (!canInvoke) {
                event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
                return;
            }
        }

        switch (name) {
            case "exec": handleExec(event); break;
            case "register": handleRegister(event); break;
            case "alias": handleAlias(event); break;
            case "delete": handleDelete(event); break;
            case "description": handleDescription(event); break;
            case "pastesite": handlePasteSite(event); break;
            case "find": handleFind(event); break;
            default:
                event.reply("Unknown command.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        String name = event.getName(); // e.g., "Upload to mclogs"
        String siteId = name.replace("Upload to ", "").toLowerCase();
        if (PASTE_SITES.contains(siteId)) {
            event.deferReply().queue();
            processPasteUpload(event.getHook(), event.getTarget(), siteId);
        }
    }

    private void handleExec(SlashCommandInteractionEvent event) {
        String trickName = event.getOption("trickname", OptionMapping::getAsString);
        String args = event.getOption("args", "", OptionMapping::getAsString);
        Optional<DatabaseManager.CommandData> cmdData = DatabaseManager.getInstance().getCommand(trickName);
        if (cmdData.isEmpty()) cmdData = DatabaseManager.getInstance().getCommandByAlias(trickName);
        if (cmdData.isPresent()) {
            String output = cmdData.get().data();
            if (!args.isEmpty()) output += " " + args;
            event.reply(output).queue();
        } else {
            event.reply("Command not found!").setEphemeral(true).queue();
        }
    }

    private void handleRegister(SlashCommandInteractionEvent event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String contents = event.getOption("contents", OptionMapping::getAsString);
        if (DatabaseManager.getInstance().commandExists(name) || DatabaseManager.getInstance().isTakenAlias(name)) {
            event.reply("Command with name " + name + " already exists!").setEphemeral(true).queue();
            return;
        }
        DatabaseManager.getInstance().createCommand(name, "N/A", contents, new ArrayList<>());
        event.reply("Command `" + name + "` has been registered!").queue();
    }

    private void handleAlias(SlashCommandInteractionEvent event) {
        String commandName = event.getOption("command", OptionMapping::getAsString);
        String aliasesStr = event.getOption("aliases", OptionMapping::getAsString);
        String[] parts = aliasesStr.split("\\s+");
        if (!DatabaseManager.getInstance().commandExists(commandName)) {
            event.reply("Command `" + commandName + "` not found!").setEphemeral(true).queue();
            return;
        }
        var cmdData = DatabaseManager.getInstance().getCommand(commandName);
        if (cmdData.isEmpty()) return;
        List<String> newAliases = new ArrayList<>();
        int added = 0;
        for (String alias : parts) {
            if (!cmdData.get().aliases().contains(alias) && !DatabaseManager.getInstance().isTakenAlias(alias)) {
                newAliases.add(alias);
                added++;
            }
        }
        if (added > 0) {
            DatabaseManager.getInstance().addAliases(commandName, newAliases);
            event.reply("Added " + added + " aliases to command `" + commandName + "`!").queue();
        } else {
            event.reply("No new aliases were added (they may already exist or be taken).").setEphemeral(true).queue();
        }
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        String commandName = event.getOption("name", OptionMapping::getAsString);
        if (DatabaseManager.getInstance().commandExists(commandName)) {
            var cmdData = DatabaseManager.getInstance().getCommand(commandName);
            cmdData.ifPresent(data -> {
                for (String alias : data.aliases()) DatabaseManager.getInstance().invalidateCache(alias);
            });
            DatabaseManager.getInstance().deleteCommand(commandName);
            event.reply("Successfully deleted command `" + commandName + "`!").queue();
        } else {
            event.reply("Command `" + commandName + "` not found!").setEphemeral(true).queue();
        }
    }

    private void handleDescription(SlashCommandInteractionEvent event) {
        String commandName = event.getOption("name", OptionMapping::getAsString);
        String newDesc = event.getOption("description", OptionMapping::getAsString);
        var cmdData = DatabaseManager.getInstance().getCommand(commandName);
        if (cmdData.isEmpty()) {
            event.reply("Command `" + commandName + "` not found!").setEphemeral(true).queue();
            return;
        }
        DatabaseManager.getInstance().updateCommand(commandName, newDesc, cmdData.get().data(), cmdData.get().aliases());
        event.reply("Updated description for `" + commandName + "`!").queue();
    }

    private void handlePasteSite(SlashCommandInteractionEvent event) {
        String site = event.getOption("site", OptionMapping::getAsString).toLowerCase();
        if (!PasteSite.isValidSiteId(site)) {
            event.reply("Invalid paste site! Valid options are: " + PasteSite.getAvailableSites()).setEphemeral(true).queue();
            return;
        }
        UserDB.setPasteSite(event.getUser().getId(), site);
        event.reply("✅ Paste site changed to `" + site + "`").queue();
    }

    private void handleFind(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", OptionMapping::getAsString);
        int page = event.getOption("page", 0, OptionMapping::getAsInt);
        var keys = nl.finnt730.commands.CommandCache.getAllLoadedNames();
        var result = keys.stream().filter(str -> {
            try { return str.matches(target) || str.contains(target); } 
            catch (Exception e) { return str.contains(target); }
        }).sorted().toList();
        int PAGE_SIZE = 7;
        int startIndex = page * PAGE_SIZE;
        StringBuilder builder = new StringBuilder();
        builder.append("Found %d results, showing %d-%d\n".formatted(result.size(), startIndex, startIndex + PAGE_SIZE));
        for (int i = startIndex; (i < startIndex + PAGE_SIZE) && i < result.size(); i++) builder.append(result.get(i)).append("\n");
        event.reply(builder.toString()).queue();
    }

    private void processPasteUpload(InteractionHook hook, Message targetMessage, String siteId) {
        CompletableFuture.runAsync(() -> {
            try {
                PasteSite targetSite = PasteSite.getPure(siteId);
                StringBuilder response = new StringBuilder();
                boolean first = true;
                boolean processedAnything = false;
                
                String content = targetMessage.getContentRaw();
                
                // 1. Process links in the message
                java.util.Set<String> processedUrls = new java.util.HashSet<>();
                
                // Pass 1: Markdown links [label](url) or [label](<url>)
                Pattern mdLinkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(<?(https?://[^>\\s]+)>?\\)");
                Matcher mdMatcher = mdLinkPattern.matcher(content);
                while (mdMatcher.find()) {
                    String label = mdMatcher.group(1).trim();
                    String link = mdMatcher.group(2);
                    if (label.isEmpty()) label = "log";
                    processedUrls.add(link);
                    
                    if (PasteReader.isMcLogsInstance(link) && MCLOGS_INSTANCES.contains(siteId)) {
                        String id = PasteReader.getMcLogsId(link);
                        if (id != null) {
                            String baseUrl = switch (siteId) {
                                case "gnomebot" -> "https://gnomebot.dev/paste/mclogs/";
                                case "capaste" -> "https://kostromdan.dev/paste/mclogs/";
                                default -> "https://mclo.gs/";
                            };
                            if (!first) response.append(" | ");
                            response.append("🔗 [").append(label).append("](<").append(baseUrl).append(id).append(">)");
                            first = false;
                            processedAnything = true;
                        }
                    } else {
                        try {
                            String linkContent = PasteReader.read(link);
                            if (!targetSite.largeEnough(linkContent)) {
                                if (!first) response.append(" | ");
                                response.append(String.format("❌ The link `%s` is too large for `%s`. Consider using a larger paste site like `pastesdev` or `cdpaste`.", label, siteId));
                                first = false;
                                processedAnything = true;
                            } else {
                                try {
                                    String url = targetSite.getResultURL(linkContent);
                                    if (!first) response.append(" | ");
                                    response.append("📄 [").append(label).append("](<").append(url).append(">)");
                                    first = false;
                                    processedAnything = true;
                                } catch (java.io.IOException ex) {
                                    if (!first) response.append(" | ");
                                    response.append(String.format("❌ Failed to upload link content to `%s` (%s).", siteId, ex.getMessage()));
                                    first = false;
                                    processedAnything = true;
                                }
                            }
                        } catch (java.io.IOException e) {
                            if (!first) response.append(" | ");
                            response.append(String.format("❌ Failed to read link `%s` (%s).", label, e.getMessage()));
                            first = false;
                            processedAnything = true;
                        }
                    }
                }
                
                // Pass 2: Bare links
                Matcher bareMatcher = Pattern.compile("https?://\\S+").matcher(content);
                while (bareMatcher.find()) {
                    String link = bareMatcher.group().replaceAll("[)\\]}>.,;!?]+$", "");
                    if (processedUrls.contains(link)) continue;
                    String label = "log";
                    
                    if (PasteReader.isMcLogsInstance(link) && MCLOGS_INSTANCES.contains(siteId)) {
                        String id = PasteReader.getMcLogsId(link);
                        if (id != null) {
                            String baseUrl = switch (siteId) {
                                case "gnomebot" -> "https://gnomebot.dev/paste/mclogs/";
                                case "capaste" -> "https://kostromdan.dev/paste/mclogs/";
                                default -> "https://mclo.gs/";
                            };
                            if (!first) response.append(" | ");
                            response.append("🔗 [").append(label).append("](<").append(baseUrl).append(id).append(">)");
                            first = false;
                            processedAnything = true;
                        }
                    } else {
                        try {
                            String linkContent = PasteReader.read(link);
                            if (!targetSite.largeEnough(linkContent)) {
                                if (!first) response.append(" | ");
                                response.append(String.format("❌ The link `%s` is too large for `%s`. Consider using a larger paste site like `pastesdev` or `cdpaste`.", link, siteId));
                                first = false;
                                processedAnything = true;
                            } else {
                                try {
                                    String url = targetSite.getResultURL(linkContent);
                                    if (!first) response.append(" | ");
                                    response.append("📄 [").append(label).append("](<").append(url).append(">)");
                                    first = false;
                                    processedAnything = true;
                                } catch (java.io.IOException ex) {
                                    if (!first) response.append(" | ");
                                    response.append(String.format("❌ Failed to upload link content to `%s` (%s).", siteId, ex.getMessage()));
                                    first = false;
                                    processedAnything = true;
                                }
                            }
                        } catch (java.io.IOException e) {
                            if (!first) response.append(" | ");
                            response.append(String.format("❌ Failed to read link `%s` (%s).", link, e.getMessage()));
                            first = false;
                            processedAnything = true;
                        }
                    }
                }
                
                // 2. Process attachments
                for (Message.Attachment att : targetMessage.getAttachments()) {
                    try (InputStream is = att.getProxy().download().get()) {
                        String attContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        if (!targetSite.largeEnough(attContent)) {
                            if (!first) response.append(" | ");
                            response.append(String.format("❌ The file `%s` is too large for `%s`. Consider using a larger paste site like `pastesdev` or `cdpaste`.", att.getFileName(), siteId));
                            first = false;
                            processedAnything = true;
                        } else {
                            try {
                                String url = targetSite.getResultURL(attContent);
                                String logName = att.getFileName().replaceFirst("\\.[^.]+$", "");
                                if (!first) response.append(" | ");
                                response.append("📄 [").append(logName).append("](<").append(url).append(">)");
                                first = false;
                                processedAnything = true;
                            } catch (java.io.IOException ex) {
                                if (!first) response.append(" | ");
                                response.append(String.format("❌ Failed to upload `%s` to `%s` (%s).", att.getFileName(), siteId, ex.getMessage()));
                                first = false;
                                processedAnything = true;
                            }
                        }
                    } catch (Exception e) { logger.error("Error reading attachment", e); }
                }
                
                // 3. If no links and no attachments, but there is text, upload the text
                if (!processedAnything && !content.isEmpty() && targetMessage.getAttachments().isEmpty()) {
                    if (!targetSite.largeEnough(content)) {
                        response.append(String.format("❌ The message content is too large for `%s`. Consider using a larger paste site like `pastesdev` or `cdpaste`.", siteId));
                        processedAnything = true;
                    } else {
                        try {
                            String url = targetSite.getResultURL(content);
                            response.append("📄 [message-content](<").append(url).append(">)");
                            processedAnything = true;
                        } catch (java.io.IOException ex) {
                            response.append(String.format("❌ Failed to upload message content to `%s` (%s).", siteId, ex.getMessage()));
                            processedAnything = true;
                        }
                    }
                }
                
                if (!processedAnything) {
                    response.append("❌ Failed to find or upload any valid content from the message.");
                }
                hook.sendMessage(response.toString()).queue();
            } catch (Exception e) {
                logger.error("Error in paste upload", e);
                hook.sendMessage("❌ Error: " + e.getMessage()).queue();
            }
        });
    }
}