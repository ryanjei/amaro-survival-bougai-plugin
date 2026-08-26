package jp.amaro.survival.paper;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.function.Predicate;

public final class OwnedMobService {
    public static final int MAX_OWNED_MOBS = 80;
    private final NamespacedKey markerKey;
    private final NamespacedKey sourceKey;

    public OwnedMobService(JavaPlugin plugin) {
        markerKey = new NamespacedKey(plugin, "owned_mob");
        sourceKey = new NamespacedKey(plugin, "mob_source");
    }

    public void mark(Entity entity, String source) {
        entity.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(sourceKey, PersistentDataType.STRING, source);
    }

    public boolean isOwned(Entity entity) {
        return entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public int countOwned() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) total += countMarked(world.getEntities(), this::isOwned);
        return total;
    }

    public int remainingCapacity() {
        return Math.max(0, MAX_OWNED_MOBS - countOwned());
    }

    public int allowedSpawnCount(int requested) {
        return allowedSpawnCount(countOwned(), requested);
    }

    static int allowedSpawnCount(int owned, int requested) {
        return Math.min(Math.max(0, requested), Math.max(0, MAX_OWNED_MOBS - owned));
    }

    static <T> int countMarked(Iterable<T> values, Predicate<T> marked) {
        int count = 0;
        for (T value : values) if (marked.test(value)) count++;
        return count;
    }

    public void cleanupAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) if (isOwned(entity)) entity.remove();
        }
    }
}
