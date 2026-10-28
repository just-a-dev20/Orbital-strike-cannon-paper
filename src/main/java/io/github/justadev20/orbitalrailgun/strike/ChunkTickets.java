package io.github.justadev20.orbitalrailgun.strike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Reference-counted plugin chunk tickets; Bukkit keeps only one ticket per plugin and chunk. */
final class ChunkTickets {
    private record ChunkRef(UUID world, int x, int z) {
    }

    private final Plugin plugin;
    private final Map<ChunkRef, Integer> counts = new HashMap<>();

    ChunkTickets(Plugin plugin) {
        this.plugin = plugin;
    }

    void acquire(World world, int x, int z) {
        if (counts.merge(new ChunkRef(world.getUID(), x, z), 1, Integer::sum) == 1) {
            world.addPluginChunkTicket(x, z, plugin);
        }
    }

    void release(World world, int x, int z) {
        ChunkRef ref = new ChunkRef(world.getUID(), x, z);
        if (counts.containsKey(ref) && counts.computeIfPresent(ref, (key, count) -> count > 1 ? count - 1 : null) == null) {
            world.removePluginChunkTicket(x, z, plugin);
        }
    }

    void releaseAll() {
        counts.keySet().forEach(ref -> {
            World world = Bukkit.getWorld(ref.world());
            if (world != null) {
                world.removePluginChunkTicket(ref.x(), ref.z(), plugin);
            }
        });
        counts.clear();
    }
}
