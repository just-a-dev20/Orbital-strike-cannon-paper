package io.github.justadev20.orbitalrailgun.effect;

import org.bukkit.Location;
import org.bukkit.Particle;

/**
 * The shader's aftermath lighting: rings running outwards at 50 and 25 blocks per second, and from 2.5 s on a
 * bright wave at 80 blocks per second.
 */
final class Shockwaves {
    static final double FLASH_WAVE_SPEED = 80;
    static final double FLASH_WAVE_DELAY = 2.5;

    private final Location center;
    private final double radius;
    private final double maxRadius;
    private final Terrain terrain;

    Shockwaves(Location center, double radius, double maxRadius, Terrain terrain) {
        this.center = center;
        this.radius = radius;
        this.maxRadius = maxRadius;
        this.terrain = terrain;
    }

    void burst(ParticleDraw draw) {
        draw.particle(Particle.EXPLOSION_EMITTER, center.getX(), center.getY() + 1, center.getZ(), 1, 0, 0);
        for (int i = 0; i < draw.points(10); i++) {
            double angle = Math.PI * 2 * i / 10;
            double x = center.getX() + Math.cos(angle) * radius * 0.7;
            double z = center.getZ() + Math.sin(angle) * radius * 0.7;
            draw.particle(Particle.EXPLOSION_EMITTER, x, terrain.surface(x, z), z, 1, 0, 0);
        }
        for (double y = center.getY(); y < center.getY() + 96; y += 6) {
            draw.particle(Particle.SONIC_BOOM, center.getX(), y, center.getZ(), 1, 0, 0);
        }
    }

    void draw(ParticleDraw draw, double seconds) {
        ring(draw, 50 * seconds, Particle.END_ROD);
        ring(draw, 25 * seconds, Particle.CLOUD);
        if (seconds >= FLASH_WAVE_DELAY) {
            double r = FLASH_WAVE_SPEED * (seconds - FLASH_WAVE_DELAY);
            if (r > radius && r < maxRadius) {
                draw.ring(center.getX(), 0, center.getZ(), r, 0, draw.points(Math.min(r * 2 * Math.PI / 3, 220)), Palette.WHITE, 3f, terrain);
            }
        }
    }

    private void ring(ParticleDraw draw, double r, Particle particle) {
        if (r <= radius || r >= maxRadius) {
            return;
        }
        int points = draw.points(Math.min(r * 2 * Math.PI / 4, 200));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double x = center.getX() + Math.cos(angle) * r;
            double z = center.getZ() + Math.sin(angle) * r;
            draw.particle(particle, x, terrain.surface(x, z), z, 1, 0, 0);
        }
    }
}
