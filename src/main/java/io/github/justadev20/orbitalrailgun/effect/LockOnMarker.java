package io.github.justadev20.orbitalrailgun.effect;

import org.bukkit.Location;

/**
 * First four shader seconds: a ring with a cross that spins up, an inner ring growing outwards, revealed from the
 * centre at 20 blocks per second.
 */
final class LockOnMarker {
    private final Location center;
    private final double radius;
    private final double scale;
    private final Terrain terrain;

    LockOnMarker(Location center, double radius, Terrain terrain) {
        this.center = center;
        this.radius = radius;
        this.scale = radius / 24;
        this.terrain = terrain;
    }

    void draw(ParticleDraw draw, double time) {
        double reveal = time * 20 * scale;
        double spin = Math.pow(time / 2, 4);
        if (reveal >= radius) {
            draw.ring(center.getX(), 0, center.getZ(), radius, spin, draw.points(radius * 2 * Math.PI / 1.2), Palette.BLUE, 1.6f, terrain);
        }
        double inner = 12 * scale * (time - 1.7);
        if (inner > 0.5 && inner < radius) {
            draw.ring(center.getX(), 0, center.getZ(), inner, -spin, draw.points(inner * 2 * Math.PI / 1.5), Palette.BLUE, 1.2f, terrain);
        }
        int steps = draw.points(Math.min(reveal, radius));
        for (int axis = 0; axis < 2; axis++) {
            double angle = spin + axis * Math.PI / 2;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            for (int i = -steps; i <= steps; i++) {
                double t = steps == 0 ? 0 : Math.min(reveal, radius) * i / steps;
                double x = center.getX() + dx * t;
                double z = center.getZ() + dz * t;
                draw.dust(x, terrain.surface(x, z), z, Palette.BLUE, 1.2f);
            }
        }
    }
}
