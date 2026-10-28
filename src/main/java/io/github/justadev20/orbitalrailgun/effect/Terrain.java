package io.github.justadev20.orbitalrailgun.effect;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.HeightMap;
import org.bukkit.World;

/**
 * Surface heights near a reference height, used to project rings onto the ground like the shader's decals.
 * Columns far above (roofs, mountains over a cave target) or in unloaded chunks use the reference height.
 */
public final class Terrain {
    private static final int MAX_DIFFERENCE = 12;

    private final World world;
    private final int referenceY;
    private final Map<Long, Double> cache = new HashMap<>();

    public Terrain(World world, int referenceY) {
        this.world = world;
        this.referenceY = referenceY;
    }

    public double surface(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return cache.computeIfAbsent(((long) bx << 32) ^ (bz & 0xffffffffL), key -> {
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                return referenceY + 1.1;
            }
            int height = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            return (Math.abs(height - referenceY) <= MAX_DIFFERENCE ? height : referenceY) + 1.1;
        });
    }
}
