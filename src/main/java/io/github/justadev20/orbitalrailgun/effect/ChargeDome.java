package io.github.justadev20.orbitalrailgun.effect;

import org.bukkit.Color;
import org.bukkit.Location;

/**
 * The shader's main sphere during the charge-up: drawn as an equator and three latitude rings whose colour
 * fades from blue to black and back, plus shock rings travelling outwards every two seconds.
 */
final class ChargeDome {
    private static final double[] LATITUDES = {Math.toRadians(30), Math.toRadians(55), Math.toRadians(75)};
    private static final int PULSE_TICKS = 40;
    private static final int PULSE_LENGTH = 20;

    private final Location center;
    private final double scale;
    private final Terrain terrain;

    ChargeDome(Location center, double radius, Terrain terrain) {
        this.center = center;
        this.scale = radius / 24;
        this.terrain = terrain;
    }

    void draw(ParticleDraw draw, double localTime, int age) {
        double radius = StrikeTimeline.domeRadius(localTime) * scale;
        if (radius < 1) {
            return;
        }
        double phase = age * 0.02;
        Color shell = Palette.darken(Palette.BLUE, 0.75 * Math.abs(Math.sin(Math.PI * localTime / StrikeTimeline.EXPANSION)));
        draw.ring(center.getX(), center.getY(), center.getZ(), radius, phase, draw.points(Math.min(radius * 2 * Math.PI / 1.4, 180)),
                Palette.BLUE, 2.5f, null);
        for (double latitude : LATITUDES) {
            double ringRadius = radius * Math.cos(latitude);
            draw.ring(center.getX(), center.getY() + radius * Math.sin(latitude), center.getZ(), ringRadius, -phase,
                    draw.points(Math.min(ringRadius * 2 * Math.PI / 3, 90)), shell, 3f, null);
        }
        int pulseAge = age % PULSE_TICKS;
        if (pulseAge < PULSE_LENGTH) {
            double pulse = radius + 18 * scale * pulseAge / PULSE_LENGTH;
            draw.ring(center.getX(), 0, center.getZ(), pulse, phase, draw.points(Math.min(pulse * 2 * Math.PI / 2.5, 160)),
                    Palette.mix(Palette.BLUE, Palette.WHITE, 0.5), 1.4f, terrain);
        }
    }
}
