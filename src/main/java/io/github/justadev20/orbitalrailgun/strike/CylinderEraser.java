package io.github.justadev20.orbitalrailgun.strike;

import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.bukkit.ExplosionResult;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jspecify.annotations.Nullable;

/**
 * The original mod sets every block with x² + z² ≤ r² from the bottom to the top of the world to air in a single
 * tick. This does the same, layer by layer from the top down, in small batches with a time check after each batch,
 * so a single tick never runs far past its deadline even when the code paths are still cold.
 */
final class CylinderEraser {
    private static final int BATCH = 256;

    private final World world;
    private final Location center;
    private final Supplier<@Nullable ? extends Entity> source;
    private final RailgunConfig.Destruction settings;
    private final boolean fireEvents;
    private final int minY;
    private final int radius;
    private final boolean[][] rim;
    private int[] xs;
    private int[] zs;
    private int[] tops;
    private int measured;
    private boolean prepared;

    private int y;
    private int active;
    private int index;

    CylinderEraser(Location center, int radius, Supplier<@Nullable ? extends Entity> source, RailgunConfig.Destruction settings, boolean fireEvents) {
        this.world = center.getWorld();
        this.center = center;
        this.source = source;
        this.settings = settings;
        this.fireEvents = fireEvents;
        this.minY = world.getMinHeight();
        this.radius = radius;
        this.rim = new boolean[radius * 2 + 1][radius * 2 + 1];

        List<int[]> columns = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (inside(dx, dz)) {
                    rim[dx + radius][dz + radius] = !inside(dx + 1, dz) || !inside(dx - 1, dz) || !inside(dx, dz + 1) || !inside(dx, dz - 1);
                    columns.add(new int[] {center.getBlockX() + dx, center.getBlockZ() + dz});
                }
            }
        }
        this.xs = columns.stream().mapToInt(column -> column[0]).toArray();
        this.zs = columns.stream().mapToInt(column -> column[1]).toArray();
        this.tops = new int[xs.length];
    }

    private boolean inside(int dx, int dz) {
        return dx * dx + dz * dz <= radius * radius;
    }

    boolean done() {
        return prepared && y < minY;
    }

    /** Processes up to {@code budget} positions or until {@code deadline} (System#nanoTime) has passed. */
    int step(int budget, long deadline) {
        if (!prepare(deadline)) {
            return 0;
        }
        List<Block> batch = new ArrayList<>(BATCH);
        int processed = 0;
        while (!done() && processed < budget) {
            if (index >= active) {
                y--;
                index = 0;
                advanceActive();
                continue;
            }
            int i = index++;
            processed++;
            Block block = world.getBlockAt(xs[i], y, zs[i]);
            Material type = block.getType();
            if (!type.isAir() && !settings.skips(type)) {
                batch.add(block);
            }
            if (batch.size() >= BATCH || (processed & 1023) == 0) {
                remove(batch);
                batch = new ArrayList<>(BATCH);
                if (System.nanoTime() > deadline) {
                    break;
                }
            }
        }
        remove(batch);
        return processed;
    }

    /** Reads the surface height of every column (spread over ticks) and orders the columns from high to low. */
    private boolean prepare(long deadline) {
        while (!prepared && measured < xs.length) {
            tops[measured] = world.getHighestBlockYAt(xs[measured], zs[measured], HeightMap.WORLD_SURFACE);
            measured++;
            if ((measured & 127) == 0 && System.nanoTime() > deadline) {
                return false;
            }
        }
        if (!prepared) {
            Integer[] order = IntStream.range(0, xs.length).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingInt((Integer i) -> tops[i]).reversed());
            int[] sortedX = new int[xs.length];
            int[] sortedZ = new int[xs.length];
            int[] sortedTop = new int[xs.length];
            for (int i = 0; i < order.length; i++) {
                sortedX[i] = xs[order[i]];
                sortedZ[i] = zs[order[i]];
                sortedTop[i] = tops[order[i]];
            }
            xs = sortedX;
            zs = sortedZ;
            tops = sortedTop;
            y = Math.min(tops.length == 0 ? minY - 1 : tops[0], world.getMaxHeight() - 1);
            advanceActive();
            prepared = true;
        }
        return true;
    }

    private void advanceActive() {
        while (active < tops.length && tops[active] >= y) {
            active++;
        }
    }

    private void remove(List<Block> batch) {
        if (batch.isEmpty()) {
            return;
        }
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (Block block : fireEvents ? protectionFiltered(batch) : batch) {
            int dx = block.getX() - cx + radius;
            int dz = block.getZ() - cz + radius;
            boolean edge = dx < 0 || dz < 0 || dx >= rim.length || dz >= rim.length || rim[dx][dz];
            block.setType(Material.AIR, edge);
        }
    }

    /**
     * Lets protection plugins remove blocks from the batch, exactly like they do for explosions: with the shooter as
     * the exploding entity while they are online, otherwise as a block explosion at the target.
     */
    private List<Block> protectionFiltered(List<Block> batch) {
        Entity entity = source.get();
        if (entity != null && entity.isValid()) {
            EntityExplodeEvent event = new EntityExplodeEvent(entity, center.clone(), batch, 0f, ExplosionResult.DESTROY);
            return event.callEvent() ? event.blockList() : List.of();
        }
        Block origin = center.getBlock();
        BlockExplodeEvent event = new BlockExplodeEvent(origin, origin.getState(), batch, 0f, ExplosionResult.DESTROY);
        return event.callEvent() ? event.blockList() : List.of();
    }
}
