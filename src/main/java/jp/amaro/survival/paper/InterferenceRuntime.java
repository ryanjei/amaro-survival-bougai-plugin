package jp.amaro.survival.paper;

import jp.amaro.survival.domain.InterferenceType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.logging.Logger;

public final class InterferenceRuntime {
    private final OwnedMobService ownership; private final BaseRaidRuntime raid; private final RandomGenerator random; private final Logger logger;
    public InterferenceRuntime(OwnedMobService ownership, BaseRaidRuntime raid, RandomGenerator random, Logger logger) {
        this.ownership = ownership; this.raid = raid; this.random = random; this.logger = logger;
    }

    public void apply(InterferenceType type) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (type == InterferenceType.BASE_RAID && raid.active()) {
            Bukkit.broadcast(Component.text("[妨害] 拠点襲撃はすでに進行中です。"));
            logger.info("重複した拠点襲撃要求を安全に無視しました。");
            return;
        }
        announce(type, players);
        switch (type) {
            case DARKNESS -> effect(players, PotionEffectType.DARKNESS, 8 * 20, 0);
            case LEVITATION -> effect(players, PotionEffectType.LEVITATION, 3 * 20, 0);
            case HUNGER -> effect(players, PotionEffectType.HUNGER, 12 * 20, 0);
            case GLOWING -> effect(players, PotionEffectType.GLOWING, 15 * 20, 0);
            case KNOCKBACK -> knockback(players);
            case ZOMBIE_SWARM -> swarm(players, List.of(EntityType.ZOMBIE), 3, false);
            case SKELETON_SWARM -> swarm(players, List.of(EntityType.SKELETON), 3, false);
            case CREEPER_ALERT -> swarm(players, List.of(EntityType.CREEPER), 1, false);
            case MIXED_MOB_SWARM -> swarm(players, List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER), 4, false);
            case ENHANCED_MOB_SWARM -> swarm(players, List.of(EntityType.ZOMBIE, EntityType.SKELETON), 2, true);
            case BASE_RAID -> raid.start();
        }
        logger.info("妨害発動: " + type.name());
    }

    private void announce(InterferenceType type, Collection<? extends Player> players) {
        boolean large = type == InterferenceType.BASE_RAID;
        Component title = Component.text(large ? "大規模妨害発生！" : "視聴者妨害発生！");
        Component subtitle = Component.text(large ? "拠点が襲撃されています！" : type.displayName());
        Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500));
        for (Player player : players) player.showTitle(Title.title(title, subtitle, times));
        Bukkit.broadcast(Component.text("[妨害] " + (large ? "初期拠点への大規模襲撃" : "視聴者コメントにより「" + type.displayName() + "」") + "が発生しました！"));
    }

    private static void effect(Collection<? extends Player> players, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect effect = new PotionEffect(type, ticks, amplifier, false, true, true);
        players.forEach(player -> player.addPotionEffect(effect));
    }

    private void knockback(Collection<? extends Player> players) {
        for (Player player : players) {
            double angle = random.nextDouble() * Math.PI * 2;
            player.setVelocity(player.getVelocity().add(new Vector(Math.cos(angle) * .65, .35, Math.sin(angle) * .65)));
        }
    }

    private void swarm(Collection<? extends Player> players, List<EntityType> types, int count, boolean enhanced) {
        for (Player player : players) for (int i = 0; i < count; i++) {
            Optional<org.bukkit.Location> location = SpawnLocations.around(player.getLocation(), 4, 9, random);
            if (location.isPresent()) {
                Entity entity = player.getWorld().spawnEntity(location.get(), types.get(random.nextInt(types.size())));
                ownership.mark(entity, "interference");
                if (enhanced && entity instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 90 * 20, 0));
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 90 * 20, 0));
                }
            }
        }
    }
}
