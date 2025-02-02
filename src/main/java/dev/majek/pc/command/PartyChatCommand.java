package dev.majek.pc.command;

import dev.majek.pc.PartyChat;
import dev.majek.pc.data.object.Party;
import dev.majek.pc.data.Restrictions;
import dev.majek.pc.data.object.User;
import dev.majek.pc.util.Chat;
import dev.majek.pc.util.TabCompleterBase;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.majek.pc.command.PartyCommand.*;

public class PartyChatCommand implements TabCompleter, CommandExecutor {

    /**
     * PartyChat's main config file.
     */
    public static FileConfiguration mainConfig = PartyChat.getDataHandler().mainConfig;

    /**
     * Refresh the main PartyChat config object used in this class and subclasses.
     */
    public static void reload() {
        PartyChat.getCore().reloadConfig();
        mainConfig = PartyChat.getDataHandler().mainConfig;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String commandString = "/" + command.getName() + " " + String.join(" ", args);
        try {
            if (command.getName().equalsIgnoreCase("partychat")) {

                PartyChat.getDataHandler().logToFile("Sender " + sender.getName()
                        + " executed command " + commandString, "COMMAND");

                // Check if the admins wants to use permissions
                // Ignore this whole section if they have admin perms
                if (PartyChat.getDataHandler().getConfigBoolean(mainConfig, "use-permissions")
                        && !sender.hasPermission("partychat.admin"))
                    if (!sender.hasPermission("partychat.use")) {
                        sendMessage(sender, "no-permission"); return true;
                    }

                // Everything for /pc <edit|spy|reload|bugreport>
                if (sender.hasPermission("partychat.admin") && args.length > 0) {
                    switch (args[0]) {
                        case "reload":
                            PartyChat.getDataHandler().reload();
                            PartyCommand.reload();
                            PartyChatCommand.reload();
                            PartyChat.getCommandHandler().reload();
                            sendMessage(sender, "plugin-reloaded"); return true;
                        case "spy":
                            if (!(sender instanceof Player)) {
                                sendMessage(sender, "console-spy"); return true;
                            }
                            Player player = (Player) sender;
                            User user = PartyChat.getDataHandler().getUser(player);
                            user.flipSpyToggle();
                            sendMessage(player, user.isSpyToggle() ? "spy-enabled" : "spy-disabled");
                            PartyChat.getDataHandler().addToUserMap(user);
                            return true;
                        case "bugreport":
                            sendMessage(sender, "getting-log");
                            StringBuilder contentBuilder = new StringBuilder();
                            contentBuilder.append("Log file for ").append(java.time.LocalDate.now().toString())
                                    .append(" submitted by ").append(sender.getName()).append("\n")
                                    .append("Server Software: ").append(Bukkit.getVersion()).append("\n")
                                    .append("PartyChat Version: ").append(PartyChat.instance.getDescription().getVersion())
                                    .append("\n\n");
                            try (Stream<String> stream = Files.lines(Paths.get(PartyChat.getDataHandler()
                                    .getTodaysLog().toURI()), StandardCharsets.UTF_8)) {
                                stream.forEach(s -> contentBuilder.append(s).append("\n"));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            contentBuilder.append("\nEnd of file.");
                            String toPaste = contentBuilder.toString();
                            final MediaType PLAIN_TEXT_TYPE = MediaType.parse("text/plain; charset=utf-8");
                            final OkHttpClient client = new OkHttpClient();

                            Request request = new Request.Builder()
                                    .url("https://paste.majek.dev/documents")
                                    .post(RequestBody.create(PLAIN_TEXT_TYPE, toPaste))
                                    .build();

                            try (Response response = client.newCall(request).execute()) {
                                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                                JSONParser parser = new JSONParser();
                                JSONObject json = (JSONObject) parser.parse(Objects.requireNonNull(response.body()).string());
                                sendMessageWithReplacement(sender, "bug-report", "%link%",
                                        "https://paste.majek.dev/" + json.get("key"));
                            }
                            return true;
                        case "edit":
                            if (args.length == 1) {
                                sendMessage(sender, "specify-subcommand"); return true;
                            } else if (args.length == 2) {
                                sendMessage(sender, "specify-field"); return true;
                            }
                            PartyCommand partyCommand = PartyChat.getCommandHandler().getCommand(args[1]);
                            if (partyCommand == null) {
                                sendMessage(sender, "invalid-arg");
                                return true;
                            }
                            if (args[2].equalsIgnoreCase("cooldown")) {
                                if (args.length == 3) {
                                    int value = PartyCommand.getSubCommandCooldown(args[1]);
                                    sendMessageWithReplacement(sender, "current-value",
                                            "%value%", String.valueOf(value)); return true;
                                }
                                int cooldown;
                                try  {
                                    cooldown = Integer.parseInt(args[3]);
                                } catch (NumberFormatException ex) {
                                    sendMessage(sender, "invalid-arg"); return true;
                                }
                                partyCommand.setCooldown(cooldown);

                                File file = new File(PartyChat.getCore().getDataFolder(), "commands.yml");
                                String fileContents = "null";
                                StringBuilder newConfig = new StringBuilder();
                                try {
                                    BufferedReader reader = new BufferedReader(new FileReader(file));
                                    boolean found = false;
                                    for (String line : reader.lines().collect(Collectors.toList())) {
                                        if (line.equalsIgnoreCase("  " + args[1] + ":")) {
                                            found = true;
                                            newConfig.append(line).append("\n");
                                            continue;
                                        }
                                        if (found) {
                                            if (line.contains("cooldown")) {
                                                line = "    cooldown: " + cooldown;
                                                found = false;
                                            }
                                        }
                                        newConfig.append(line).append("\n");
                                    }
                                    fileContents = newConfig.toString();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }
                                try {
                                    FileWriter writer = new FileWriter(file);
                                    writer.write(fileContents);
                                    writer.flush();
                                    writer.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                PartyChat.getDataHandler().reload();
                                PartyCommand.reload();
                                PartyChatCommand.reload();
                                sendMessage(sender, "updated-value"); return true;
                            }
                            else if (args[2].equalsIgnoreCase("disabled")) {
                                if  (args.length == 3) {
                                    boolean value = PartyCommand.getSubCommandDisabled(args[1]);
                                    sendMessageWithReplacement(sender, "current-value",
                                            "%value%", String.valueOf(value)); return true;
                                }
                                if (args[3].equalsIgnoreCase("true")
                                        || args[3].equalsIgnoreCase("false")) {
                                    boolean disable = args[3].equalsIgnoreCase("true");
                                    partyCommand.setDisabled(disable);
                                    Bukkit.getConsoleSender().sendMessage("Setting disabled field for " + partyCommand.getName() + " to " + disable);
                                    File file = new File(PartyChat.getCore().getDataFolder(), "commands.yml");
                                    String fileContents = "null";
                                    StringBuilder newConfig = new StringBuilder();
                                    try {
                                        BufferedReader reader = new BufferedReader(new FileReader(file));
                                        boolean found = false;
                                        for (String line : reader.lines().collect(Collectors.toList())) {
                                            if (line.equalsIgnoreCase("  " + args[1] + ":")) {
                                                found = true;
                                                newConfig.append(line).append("\n");
                                                continue;
                                            }
                                            if (found) {
                                                if (line.contains("disabled")) {
                                                    line = "    disabled: " + disable;
                                                    Bukkit.getConsoleSender().sendMessage("Changed");
                                                    found = false;
                                                }
                                            }
                                            newConfig.append(line).append("\n");
                                        }
                                        fileContents = newConfig.toString();
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        FileWriter writer = new FileWriter(file);
                                        writer.write(fileContents);
                                        writer.flush();
                                        writer.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                    PartyChat.getDataHandler().reload();
                                    PartyCommand.reload();
                                    PartyChatCommand.reload();
                                    sendMessage(sender, "updated-value");
                                }  else {
                                    sendMessage(sender, "invalid-arg");
                                }
                                return true;
                            } else {
                                sendMessage(sender, "invalid-arg"); return true;
                            }
                    }
                }

                // Don't allow use from console
                if (!(sender instanceof Player)) {
                    sendMessage(sender, "no-console"); return true;
                }
                Player player = (Player) sender;
                User user = PartyChat.getDataHandler().getUser(player);

                // Check if the player is not in a party
                if (!user.isInParty()) {
                    sendMessage(sender, "not-in-party");
                    return true;
                }

                // Get the party the player is in
                Party party = PartyChat.getPartyHandler().getParty(user);

                // This should never happen, but I want to know if it does
                if (party == null) {
                    PartyChat.error("Error: PC-CMD_1 | The plugin is fine, but please report this error " +
                            "code here: https://discord.gg/CGgvDUz");
                    sendMessage(player, "not-in-party"); return true;
                }

                // Toggle party chat if there are no args
                if (args.length == 0) {
                    user.flipPartyChatToggle();
                    sendMessage(player, user.partyChatToggle() ? "pc-enabled" : "pc-disabled");
                    return true;
                }

                // Check for args to toggle party chat
                if (args[0].equalsIgnoreCase("on")) {
                    user.setPartyChatToggle(true);
                    sendMessage(player, "pc-enabled");
                    return true;
                } else if (args[0].equalsIgnoreCase("off")) {
                    user.setPartyChatToggle(false);
                    sendMessage(player, "pc-disabled");
                    return true;
                }

                // Check if the player is currently muted
                if (Restrictions.isMuted(player)) {
                    sendMessage(player, "muted"); return true;
                }

                // Build the message to send
                StringBuilder message = new StringBuilder();
                for (String arg : args) {
                    message.append(arg).append(" ");
                }
                // This is used so staff don't get the message twice
                List<Player> messageReceived = new ArrayList<>();

                // Log message to console if that's enabled
                if (PartyChat.getDataHandler().getConfigBoolean(mainConfig, "console-log"))
                    sendMessageWithEverything(Bukkit.getConsoleSender(), "spy-format", "%partyName%",
                            Chat.removeColorCodes(party.getName()), "%player%", player.getName(), message.toString());

                // Send message to party members
                party.getMembers().stream().map(User::getPlayer).filter(Objects::nonNull).forEach(member -> {
                    sendMessageWithEverything(member, "message-format", "%partyName%",
                            party.getName(), "%player%", player.getDisplayName(), message.toString());
                    messageReceived.add(member);
                });

                // Send message to server staff
                PartyChat.getDataHandler().getUserMap().values().stream().filter(User::isSpyToggle).map(User::getPlayer)
                        .filter(Objects::nonNull).filter(staff -> !messageReceived.contains(staff))
                        .forEach(staff -> sendMessageWithEverything(staff, "spy-format",
                                "%partyName%", Chat.removeColorCodes(party.getName()), "%player%",
                                player.getName(), message.toString()));

                PartyChat.getDataHandler().addToUserMap(user);
                return true;
            }
        } catch (Exception ex) {
            StringBuilder error = new StringBuilder();
            error.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append('\n');
            for (StackTraceElement ste : ex.getStackTrace())
                error.append("    at ").append(ste.toString()).append('\n');
            String errorString = error.toString();
            if (sender.hasPermission("partychat.admin"))
                sendMessageWithReplacement(sender, "command-error-staff", "%command%", commandString);
            else
                sendMessageWithReplacement(sender, "command-error", "%command%", commandString);
            PartyChat.error("There was an error executing command " + commandString);
            PartyChat.error(errorString);
            ex.printStackTrace();
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (sender.hasPermission("partychat-admin")) {
            if (args.length == 1)
                return TabCompleterBase.filterStartingWith(args[0], Arrays.asList("on", "off",
                        "reload", "edit", "spy", "bugreport"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("edit"))
                return TabCompleterBase.filterStartingWith(args[1], PartyChat.getCommandHandler()
                        .getCommands().stream().map(PartyCommand::getName));
            else if (args.length == 3 && PartyChat.getCommandHandler().getCommands().stream().map(PartyCommand::getName)
                    .collect(Collectors.toList()).contains(args[1].toLowerCase(Locale.ROOT)))
                return TabCompleterBase.filterStartingWith(args[2], Arrays.asList("disabled", "cooldown"));
            else if (args.length == 4 && args[2].equalsIgnoreCase("disabled"))
                return TabCompleterBase.filterStartingWith(args[3], Arrays.asList("true", "false"));
            else if (args.length == 4 && args[2].equalsIgnoreCase("cooldown"))
                return TabCompleterBase.filterStartingWith(args[3], Collections.singletonList("<seconds>"));
            else
                return Collections.emptyList();
        } else {
            if (args.length == 1)
                return TabCompleterBase.filterStartingWith(args[0], Arrays.asList("on", "off"));
            else
                return Collections.emptyList();
        }
    }
}
