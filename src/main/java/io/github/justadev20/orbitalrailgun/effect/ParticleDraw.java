package io.github.justadev20.orbitalrailgun.effect;

import com.destroystokyo.paper.ParticleBuilder;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Sends particles to an explicit list of receivers, always with the long-distance flag. */
public final class ParticleDraw {
    private final World world;
    private final double multiplier;
    private List<Player> receivers = List.of();

    public ParticleDraw(World world, double multiplier) {
        this.world = world;
        this.multiplier = multiplier;
    }

    public ParticleDraw receivers(List<Player> players) {
        this.receivers = players;
        return this;
    }

    /** Number of points for a shape, scaled by the configured particle multiplier. */
    public int points(double base) {
        return (int) Math.ceil(base * multiplier);
    }

    public void dust(double x, double y, double z, Color color, float size) {
        spawn(new ParticleBuilder(Particle.DUST).data(new Particle.DustOptions(color, size)), x, y, z, 1, 0, 0);
    }

    public void particle(Particle particle, double x, double y, double z, int count, double spread, double speed) {
        spawn(new ParticleBuilder(particle), x, y, z, count, spread, speed);
    }

    /** A ring of dust on the ground; {@code terrain} may be null for a flat ring at {@code y}. */
    public void ring(double cx, double y, double cz, double radius, double phase, int points, Color color, float size, @Nullable Terrain terrain) {
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2 * i / points;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            dust(x, terrain != null ? terrain.surface(x, z) : y, z, color, size);
        }
    }

    private void spawn(ParticleBuilder builder, double x, double y, double z, int count, double spread, double speed) {
        if (receivers.isEmpty() || multiplier <= 0) {
            return;
        }
        builder.location(world, x, y, z).count(count).offset(spread, spread, spread).speed(speed)
                .receivers(receivers).force(true).spawn();
    }
}
