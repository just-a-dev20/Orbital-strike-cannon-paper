package io.github.justadev20.orbitalrailgun.strike;

import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import io.github.justadev20.orbitalrailgun.effect.StrikeEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * One orbital strike, following OrbitalRailgunStrikeManager of the original mod:
 * entities inside a 500³ box are captured at launch, pulled towards the centre during the last 3/7 of the charge
 * time and hit with the strike damage on impact, after which the cylinder is erased.
 */
final class Strike {
    private static final double CAPTURE_HALF_SIZE = 250;
    private static final double ORIGINAL_RADIUS = 24;
    private static final double ORIGINAL_PULL_RING = 20;
    /** Deaths (loot, events) are expensive; a crowd at the impact is spread over a few ticks. */
    private static final long DAMAGE_NANOS_PER_TICK = 5_000_000;

    private final RailgunConfig config;
    private final ChunkTickets tickets;
    private final World world;
    private final Location center;
    private final Vector centerVector;
    private final @Nullable UUID shooter;
    private final int startTick;
    private final int radius;
    private final int chargeTicks;
    private final int pullStartTicks;
    private final List<Entity> entities;
    private final List<int[]> chunks = new ArrayList<>();
    private final List<int[]> ticketed = new ArrayList<>();
    private final StrikeEffects effects;
    private @Nullable CylinderEraser eraser;
    private @Nullable DamageSource damageSource;
    private int damaged;
    private boolean impacted;
    private boolean disposed;

    Strike(ChunkTickets tickets, RailgunConfig config, @Nullable Player shooter, Block target, int startTick) {
        this.config = config;
        this.tickets = tickets;
        this.world = target.getWorld();
        this.center = target.getLocation().add(0.5, 0.5, 0.5);
        this.centerVector = center.toVector();
        this.shooter = shooter != null ? shooter.getUniqueId() : null;
        this.startTick = startTick;
        this.radius = config.strike().radius();
        this.chargeTicks = config.strike().chargeTicks();
        this.pullStartTicks = chargeTicks * 4 / 7;
        this.entities = new ArrayList<>(world.getNearbyEntities(center, CAPTURE_HALF_SIZE, CAPTURE_HALF_SIZE, CAPTURE_HALF_SIZE));
        this.effects = new StrikeEffects(center, radius, chargeTicks, config.effects());
        keepChunksLoaded();
    }

    private void keepChunksLoaded() {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                int[] chunk = {x, z};
                chunks.add(chunk);
                world.getChunkAtAsync(x, z).thenAccept(loaded -> {
                    if (!disposed) {
                        tickets.acquire(world, chunk[0], chunk[1]);
                        ticketed.add(chunk);
                    }
                });
            }
        }
    }

    void tick(int now) {
        int age = now - startTick;
        effects.tick(age);
        if (impacted) {
            damageSome();
        } else if (age >= chargeTicks) {
            impact();
        } else if (config.strike().pull() && age >= pullStartTicks) {
            pull(age);
        }
    }

    private void pull(int age) {
        double progress = (age - pullStartTicks) / (double) (chargeTicks - pullStartTicks);
        double ring = ORIGINAL_PULL_RING * radius / ORIGINAL_RADIUS;
        for (Entity entity : entities) {
            if (!entity.isValid() || entity.getWorld() != world || entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Vector direction = centerVector.clone().subtract(entity.getLocation().toVector());
            double length = direction.length();
            double magnitude = Math.min(1.0 / Math.abs(length - ring) * 4.0 * progress, 5.0);
            if (length < 1.0E-5 || !Double.isFinite(magnitude)) {
                continue;
            }
            entity.setVelocity(entity.getVelocity().add(direction.multiply(magnitude / length)));
        }
    }

    private void impact() {
        impacted = true;
        if (config.damage().enabled()) {
            damageSource = StrikeDamage.source(config.damage().attributeToShooter() ? shooter() : null);
            damageSome();
        } else {
            damaged = entities.size();
        }
        effects.impact();
    }

    private void damageSome() {
        if (damageSource == null || damaged >= entities.size()) {
            return;
        }
        long deadline = System.nanoTime() + DAMAGE_NANOS_PER_TICK;
        double radiusSquared = (double) radius * radius;
        while (damaged < entities.size() && System.nanoTime() < deadline) {
            Entity entity = entities.get(damaged++);
            if (!entity.isValid() || entity.getWorld() != world || entity.getLocation().toVector().distanceSquared(centerVector) > radiusSquared) {
                continue;
            }
            if (entity instanceof LivingEntity living) {
                living.damage(config.damage().amount(), damageSource);
            } else if (entity instanceof Item || entity instanceof ExperienceOrb) {
                entity.remove();
            }
        }
    }

    /**
     * The eraser, from the tick after the impact on and once every chunk of the cylinder is loaded;
     * {@code null} before that or if destruction is disabled.
     */
    @Nullable CylinderEraser eraser(int now) {
        if (!impacted || !config.destruction().enabled() || now - startTick <= chargeTicks) {
            return null;
        }
        if (eraser == null && chunks.stream().allMatch(chunk -> world.isChunkLoaded(chunk[0], chunk[1]))) {
            eraser = new CylinderEraser(center, radius, this::shooter, config.destruction(), config.fireExplodeEvents());
        }
        return eraser;
    }

    /** The shooter if online; looked up each time so a re-login is picked up. */
    private @Nullable Player shooter() {
        return shooter != null ? Bukkit.getPlayer(shooter) : null;
    }

    boolean finished(int now) {
        boolean erased = !config.destruction().enabled() || (eraser != null && eraser.done());
        return impacted && damaged >= entities.size() && erased && effects.done(now - startTick);
    }

    void dispose() {
        disposed = true;
        effects.dispose();
        ticketed.forEach(chunk -> tickets.release(world, chunk[0], chunk[1]));
        ticketed.clear();
    }
}
