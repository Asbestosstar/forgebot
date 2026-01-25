package nl.finnt730.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.dv8tion.jda.api.entities.Member;
import nl.finnt730.DatabaseManager;
import nl.finnt730.commands.builtin.EchoCommand;
import nl.finnt730.commands.builtin.FindCommand;
import nl.finnt730.commands.builtin.PasteSiteCommand;
import nl.finnt730.commands.builtin.reserved.AliasCommand;
import nl.finnt730.commands.builtin.reserved.DeleteCommand;
import nl.finnt730.commands.builtin.reserved.DescriptionCommand;
import nl.finnt730.commands.builtin.reserved.RegisterNewCommand;

public class CommandCache {
    private static final Map<String, Command> cache = new HashMap<>();
    public static final String DEFAULT_PREFIX = "!";
    public static final String HOI4_ESP = "\u00BA";//In HOI4 they always use the key next to 1 no matter the layout.
    private static final DatabaseManager dbManager = DatabaseManager.getInstance();

    // Init builtin commands
    static {
        cache.put("register", new RegisterNewCommand());
        cache.put("alias", new AliasCommand());
        cache.put("delete", new DeleteCommand());
        cache.put("pastesite", new PasteSiteCommand());
        cache.put("description", new DescriptionCommand());
        cache.put("find", new FindCommand());
    }
    public static CommandContext getOrDefault(Member user, String rawContent) {
        String actualCommand = null; // String command name, e.g. "register" or "optifine"
        String additionalData = null; // Remainder of message
        if (rawContent.startsWith(DEFAULT_PREFIX)||rawContent.startsWith(HOI4_ESP)) {
            var temp = rawContent.substring(1).split(" ", 2);
            actualCommand = temp[0];
            additionalData = temp.length > 1 ? temp[1] : "";
        }

        if (actualCommand == null) return CommandContext.NONE;
        if (existsInCache(actualCommand)) return new CommandContext(cache.get(actualCommand), additionalData);
        CommandContext result = null;
        
        // Check if it's a real command name
        if (existsIsReal(actualCommand)) {
            Optional<DatabaseManager.CommandData> cmdData = dbManager.getCommand(actualCommand);
            if (cmdData.isPresent()) {
                String data = cmdData.get().data();
                // todo will need adaptation for things that aren't EchoCommands, but atm everything is... so.... :)
                result = new CommandContext(new EchoCommand(data), null);
            }
        }
        
        // Check if it's an alias
        if (result == null) {
            Optional<DatabaseManager.CommandData> cmdData = dbManager.getCommandByAlias(actualCommand);
            if (cmdData.isPresent()) {
                String data = cmdData.get().data();
                // todo same as above
                result = new CommandContext(new EchoCommand(data), null);
            }
        }

        if (result != null) {
            cache.put(actualCommand, result.command());
            return result;
        }
        return CommandContext.NOT_FOUND;
    }


    public static void invalidateOnUpdate(String command) {
        cache.remove(command);
        dbManager.invalidateCache(command);
    }

    public static boolean existsInCache(String command) { 
        return cache.containsKey(command);
    }

    public static boolean existsIsReal(String commandName) {
        return dbManager.commandExists(commandName);
    }

    public static boolean isTakenAlias(String command) {
        return dbManager.isTakenAlias(command);
    }

    public static void addObservedAlias(String name) {
        dbManager.addObservedAlias(name);
    }

    public static Set<String> getAllLoadedNames() {
        return cache.keySet();
    }

    public static Optional<CommandContext> existsAsAlias(String aliasName) {
        Optional<DatabaseManager.CommandData> cmdData = dbManager.getCommandByAlias(aliasName);
        if (cmdData.isPresent()) {
            String data = cmdData.get().data();
            // todo same as above
            return Optional.of(new CommandContext(new EchoCommand(data), null));
        }
        return Optional.empty();
    }
}
