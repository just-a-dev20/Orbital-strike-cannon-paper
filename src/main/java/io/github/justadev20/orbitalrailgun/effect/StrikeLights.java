package io.github.justadev20.orbitalrailgun.effect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;

/**
 * Glow around the strike: light blocks that exist only on the viewers' clients (block changes are never applied
 * to the world), placed in free air just outside the cylinder and above the target.
 */
final class StrikeLights {
    private static final int POINTS = 16;

    private final World world;
    private final List<Location> positions = new ArrayList<>();
    private final Set<UUID> sent = new HashSet<>();
    private final BlockData light;

    StrikeLights(Location center, double radius, Terrain terrain) {
        this.world = center.getWorld();
        this.light = Material.LIGHT.createBlockData(data -> ((Light) data).setLevel(15));
        for (int i = 0; i < POINTS; i++) {
            double angle = Math.PI * 2 * i / POINTS;
            double x = center.getX() + Math.cos(angle) * (radius + 2);
            double z = center.getZ() + Math.sin(angle) * (radius + 2);
            addIfAir(new Location(world, x, terrain.surface(x, z) + 1, z));
        }
        addIfAir(center.clone().add(0, 4, 0));
    }

    private void addIfAir(Location location) {
        if (world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4) && location.getBlock().getType().isAir()) {
            positions.add(location.toBlockLocation());
        }
    }

    void show(List<Player> viewers) {
        for (Player player : viewers) {
            if (sent.add(player.getUniqueId())) {
                positions.forEach(position -> player.sendBlockChange(position, light));
            }
        }
    }

    void clear() {
        for (UUID id : sent) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld() == world) {
                positions.forEach(position -> player.sendBlockChange(position, position.getBlock().getBlockData()));
            }
        }
        sent.clear();
    }
}
