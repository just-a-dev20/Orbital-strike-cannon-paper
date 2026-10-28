package io.github.justadev20.orbitalrailgun.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/**
 * Called right before an orbital strike is launched. Cancelling it prevents the strike entirely.
 * Block removal later fires {@link org.bukkit.event.entity.EntityExplodeEvent}s per batch on top of this.
 */
public final class OrbitalStrikeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @Nullable Player shooter;
    private final Location target;
    private final int radius;
    private boolean cancelled;

    public OrbitalStrikeEvent(@Nullable Player shooter, Location target, int radius) {
        this.shooter = shooter;
        this.target = target;
        this.radius = radius;
    }

    /** The player who fired, or {@code null} if the strike was started by a command from the console. */
    public @Nullable Player getShooter() {
        return shooter;
    }

    /** Centre of the targeted block. */
    public Location getTarget() {
        return target.clone();
    }

    public int getRadius() {
        return radius;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
