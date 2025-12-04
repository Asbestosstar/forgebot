package nl.finnt730.commands.builtin.reserved;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import nl.finnt730.DatabaseManager;
import nl.finnt730.commands.CommandCache;

import java.util.ArrayList;
import java.util.List;

public final class AliasCommand extends ReservedCommand {
    private static final String USAGE_HINT = "Usage: !alias <existing_command> <new_alias1> <new_alias2>...";
    public AliasCommand() {}

    @Override
    public void handle(MessageReceivedEvent event, Member invoker, String commandContents) {
        var parts = commandContents.split(" ");
        if(parts.length < 2) {
            event.getChannel().sendMessage(USAGE_HINT).queue();
            return;
        }
        String existingCommandName = parts[0];
        if (!CommandCache.existsIsReal(existingCommandName)) {
            event.getChannel().sendMessage("Command " + existingCommandName + " not found!").queue();
            return;
        }
        int added = 0;
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            var commandData = dbManager.getCommand(existingCommandName);
            if (commandData.isEmpty()) {
                event.getChannel().sendMessage("Command " + existingCommandName + " not found!").queue();
                return;
            }
            
            List<String> newAliases = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String alias = parts[i];
                if (commandData.get().aliases().contains(alias) || CommandCache.isTakenAlias(alias)) continue;
                newAliases.add(alias);
                added++;
            }
            
            if (added > 0) {
                dbManager.addAliases(existingCommandName, newAliases);
                event.getChannel().sendMessage("Added " + added + " aliases to command " + existingCommandName + "!").queue();
            } else {
                event.getChannel().sendMessage("No new aliases were added (they may already exist or be taken).").queue();
            }
        } catch (Exception ex) {
            event.getChannel().sendMessage("Something went wrong while trying to add aliases. Try again.").queue();
            ex.printStackTrace();
        }
    }
}
