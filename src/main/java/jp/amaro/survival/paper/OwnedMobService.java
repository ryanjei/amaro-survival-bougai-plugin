package jp.amaro.survival.paper;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class OwnedMobService {
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
        return Bukkit.getWorlds().stream().mapToInt(world -> (int) world.getEntities().stream().filter(this::isOwned).count()).sum();
    }

    public void cleanupAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) if (isOwned(entity)) entity.remove();
        }
    }
}
