package io.github.justadev20.orbitalrailgun.strike;

import io.github.justadev20.orbitalrailgun.api.OrbitalStrikeEvent;
import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Runs all active strikes. Block removal of all strikes shares one budget per tick: at most
 * {@code destruction.blocks-per-tick} positions and {@code destruction.max-millis-per-tick} milliseconds.
 */
public final class StrikeManager {
    private final Plugin plugin;
    private final Supplier<RailgunConfig> config;
    private final ChunkTickets tickets;
    private final List<Strike> strikes = new ArrayList<>();

    public StrikeManager(Plugin plugin, Supplier<RailgunConfig> config) {
        this.plugin = plugin;
        this.config = config;
        this.tickets = new ChunkTickets(plugin);
    }

    public enum Launch { STARTED, CANCELLED, LIMIT_REACHED }

    /**
     * Starts a strike on {@code target} unless {@code strike.max-concurrent} strikes are already running or a plugin
     * cancels the {@link OrbitalStrikeEvent}.
     */
    public Launch launch(@Nullable Player shooter, Block target) {
        RailgunConfig current = config.get();
        int limit = current.strike().maxConcurrent();
        if (limit > 0 && strikes.size() >= limit) {
            return Launch.LIMIT_REACHED;
        }
        OrbitalStrikeEvent event = new OrbitalStrikeEvent(shooter, target.getLocation().add(0.5, 0.5, 0.5), current.strike().radius());
        if (!event.callEvent()) {
            return Launch.CANCELLED;
        }
        strikes.add(new Strike(tickets, current, shooter, target, Bukkit.getCurrentTick()));
        plugin.getLogger().info("Orbital strike " + (shooter != null ? "fired by " + shooter.getName() : "called in")
                + " at " + target.getWorld().getName() + " " + target.getX() + " " + target.getY() + " " + target.getZ());
        return Launch.STARTED;
    }

    public void tick() {
        if (strikes.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        List<CylinderEraser> erasers = new ArrayList<>();
        for (Strike strike : strikes) {
            strike.tick(now);
            CylinderEraser eraser = strike.eraser(now);
            if (eraser != null && !eraser.done()) {
                erasers.add(eraser);
            }
        }
        erase(erasers, now);
        strikes.removeIf(strike -> {
            if (strike.finished(now)) {
                strike.dispose();
                return true;
            }
            return false;
        });
    }

    private void erase(List<CylinderEraser> erasers, int now) {
        if (erasers.isEmpty()) {
            return;
        }
        RailgunConfig.Destruction settings = config.get().destruction();
        long deadline = System.nanoTime() + (long) (settings.maxMillisPerTick() * 1_000_000);
        int share = Math.max(1, settings.blocksPerTick() / erasers.size());
        Collections.rotate(erasers, now % erasers.size());
        for (CylinderEraser eraser : erasers) {
            eraser.step(share, deadline);
        }
    }

    public int stopAll() {
        int count = strikes.size();
        strikes.forEach(Strike::dispose);
        strikes.clear();
        tickets.releaseAll();
        return count;
    }
}
