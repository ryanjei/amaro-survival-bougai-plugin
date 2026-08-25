package jp.amaro.survival.paper;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public final class PlayerLifecycleListener implements Listener {
    private final BossBar gaugeBar; private final BaseRaidRuntime raid;
    public PlayerLifecycleListener(BossBar gaugeBar, BaseRaidRuntime raid) { this.gaugeBar = gaugeBar; this.raid = raid; }
    @EventHandler public void join(PlayerJoinEvent event) { Player player = event.getPlayer(); player.showBossBar(gaugeBar); raid.addViewer(player); }
    @EventHandler public void quit(PlayerQuitEvent event) { Player player = event.getPlayer(); player.hideBossBar(gaugeBar); raid.removeViewer(player); }
}
