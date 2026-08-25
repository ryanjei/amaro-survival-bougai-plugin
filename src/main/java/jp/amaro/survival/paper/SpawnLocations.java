package jp.amaro.survival.paper;

import org.bukkit.*;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.random.RandomGenerator;

final class SpawnLocations {
    private SpawnLocations() {}

    static Optional<Location> around(Location center, int minRadius, int maxRadius, RandomGenerator random) {
        World world = center.getWorld();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block floor = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (floor.getType().isSolid() && feet.isPassable() && head.isPassable() && !feet.isLiquid()) {
                return Optional.of(new Location(world, x + .5, y + 1, z + .5));
            }
        }
        return Optional.empty();
    }
}
