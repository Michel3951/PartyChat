package dev.majek.pc.command.party;

import dev.majek.pc.PartyChat;
import dev.majek.pc.command.PartyCommand;
import dev.majek.pc.data.object.Bar;
import dev.majek.pc.data.object.Party;
import dev.majek.pc.data.object.User;
import dev.majek.pc.api.PartyJoinEvent;
import dev.majek.pc.util.Pair;
import dev.majek.pc.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

public class PartyAccept extends PartyCommand {

    public PartyAccept() {
        super(
                "accept", getSubCommandUsage("accept"), getSubCommandDescription("accept"),
                false, getSubCommandDisabled("accept"), getSubCommandCooldown("accept"),
                getSubCommandAliases("accept")
        );
    }

    @Override
    public boolean execute(Player player, String[] args, boolean leftServer) {

        User user = PartyChat.getDataHandler().getUser(player);

        // Player is not in a party
        if (!user.isInParty()) {

            // Check for pending invitations
            Party party = null;
            for (Party check : PartyChat.getPartyHandler().getPartyMap().values())
                for (Pair<Player, Player> players : check.getPendingInvitations())
                    if (players.getFirst() == player) {
                        party = check; break;
                    }

            // Player has no pending invitations
            if (party == null) {
                sendMessage(player, "no-invites"); return false;
            }

            // Run PartyJoinEvent
            PartyJoinEvent event = new PartyJoinEvent(player, party);
            PartyChat.getCore().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled())
                return false;

            // Send messages
            for (User memberUser : party.getMembers()) {
                Player member = memberUser.getPlayer();
                if (member == null)
                    continue;
                sendMessageWithReplacement(member, "player-join", "%player%", player.getDisplayName());
            }
            sendMessageWithReplacement(player, "you-join", "%partyName%", party.getName());

            // Put the player in the party
            party.removePendingInvitation(player);
            user.setInParty(true);
            user.setPartyID(party.getId());
            party.addMember(user);

            // Update the database if persistent parties is enabled
            if (PartyChat.getDataHandler().persistentParties)
                PartyChat.getPartyHandler().saveParty(party);

            return true;
        } else { // Player is in a party
            Party party = user.getParty();

            // This should never happen, but I want to know if it does
            if (party == null) {
                PartyChat.error("Error: PC-ACPT_1 | The plugin is fine, but please report this error " +
                        "code here: https://discord.gg/CGgvDUz");
                sendMessage(player, "error"); return false;
            }

            // Check if the player has a pending summon request
            if (party.getPendingSummons().contains(player)) {
                // Create teleport bar
                Bar bar = new Bar();
                int teleportDelay = PartyChat.getDataHandler().getConfigInt(mainConfig, "summon-teleport-time");
                bar.createBar(teleportDelay); bar.addPlayer(player);

                // Get the party leader
                Player leader = Bukkit.getPlayer(party.getLeader());
                if (leader == null) {
                    sendMessage(player, "leader-offline"); return false;
                }

                // Get ready to teleport, send messages
                if (party.getSize() <= 5)
                    sendMessageWithReplacement(leader, "teleport-accepted",
                            "%player%", player.getDisplayName());
                sendMessage(player, "teleport-prepare");
                party.removePendingSummons(player); user.setNoMove(true);

                // Delay the teleport.
                Bukkit.getScheduler().scheduleSyncDelayedTask(PartyChat.getCore(), () -> {
                    // Make sure the player didn't move
                    if (user.isNoMove()) {
                        // Make sure the location is safe
                        Location safe = Utils.findSafe(leader.getLocation(),
                                leader.getLocation().getBlockY()-5, 256);
                        if (safe == null)
                            sendMessage(player, "teleport-unsafe");
                        // Teleport player
                        else {
                            player.teleport(safe);
                            sendMessage(player, "teleported");
                        }
                        bar.removePlayer(player);
                        user.setNoMove(false);
                    }
                }, teleportDelay * 20L);

                return true;
            }

            // Check if the player is a leader accepting a join request
            else if (party.getPendingJoinRequests().size() > 0) {

                // Only leaders can accept join requests
                if (!user.isLeader()) {
                    sendMessage(player, "in-party"); return false;
                }

                Player toAccept;
                // Check if the leader doesn't specify a player to accept
                if (args.length == 1) {
                    if (party.getPendingJoinRequests().size() == 1) {
                        toAccept = party.getPendingJoinRequests().get(0);
                    } else {
                        sendMessage(player, "specify-player"); return false;
                    }
                } else {
                    toAccept = Bukkit.getPlayer(args[1]);
                    if (!party.getPendingJoinRequests().contains(toAccept) || toAccept == null) {
                        sendMessage(player, "no-request"); return false;
                    }
                }

                User newUser = PartyChat.getDataHandler().getUser(toAccept);

                // Run PartyJoinEvent
                PartyJoinEvent event = new PartyJoinEvent(toAccept, party);
                PartyChat.getCore().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled())
                    return false;

                // Send messages
                sendMessageWithReplacement(toAccept, "you-join", "%partyName%", party.getName());
                party.getMembers().stream().map(User::getPlayer).filter(Objects::nonNull).forEach(member ->
                        sendMessageWithReplacement(member, "player-join", "%player%", toAccept.getDisplayName()));

                // Put the player in the party
                party.removePendingJoinRequest(toAccept);
                newUser.setInParty(true);
                newUser.setPartyID(party.getId());
                party.addMember(newUser);

                // Update the database if persistent parties is enabled
                if (PartyChat.getDataHandler().persistentParties)
                    PartyChat.getPartyHandler().saveParty(party);

                return true;
            } else {
                sendMessage(player, "no-usage"); return false;
            }
        }
    }
}
