package dev.majek.pc.data.object;

import dev.majek.pc.PartyChat;
import dev.majek.pc.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class Bar {

    private static BossBar bar;
    private int taskID;

    public void addPlayer(Player p) {
        bar.addPlayer(p);
    }

    public void removePlayer(Player p) {
        bar.removePlayer(p);
    }

    public void createBar(Integer delay) {
        bar = Bukkit.createBossBar(Chat.applyColorCodes(PartyChat.getDataHandler().getConfigString(PartyChat.getDataHandler()
                .messages, "teleport-bar-text")), BarColor.BLUE, BarStyle.SOLID);
        bar.setVisible(true); cast(delay);
    }

    public void removeBar() {
        bar.setVisible(false);
    }

    public BossBar getBar() {
        return bar;
    }

    public void cast(Integer delay) {
        taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PartyChat.instance, new Runnable() {
            double progress = 1.0; final double time = 1.0 / (delay * 20);
            @Override
            public void run() {
                bar.setProgress(progress);
                progress = progress - time;
                if (progress <= 0.0) {
                    Bukkit.getScheduler().cancelTask(taskID);
                }
            }
        }, 0, 0);
    }

}
