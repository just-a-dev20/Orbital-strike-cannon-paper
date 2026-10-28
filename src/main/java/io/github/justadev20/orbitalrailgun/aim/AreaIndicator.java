package io.github.justadev20.orbitalrailgun.aim;

import io.github.justadev20.orbitalrailgun.effect.Palette;
import io.github.justadev20.orbitalrailgun.effect.ParticleDraw;
import io.github.justadev20.orbitalrailgun.effect.Terrain;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * The blue area-of-effect overlay of gui.fsh projected onto the ground: the strike radius, a cross with arms from
 * 1.5 to 5.5 blocks and a circle of 2.5 blocks, all rotating at 0.2 rad/s.
 */
final class AreaIndicator {
    private static final double SPIN = 0.2;

    private AreaIndicator() {
    }

    static void draw(ParticleDraw draw, Location eye, Block target, Terrain terrain, double radius, double seconds) {
        double cx = target.getX() + 0.5;
        double cz = target.getZ() + 0.5;
        double distance = eye.distance(target.getLocation().add(0.5, 0.5, 0.5));
        float size = (float) Math.max(0.8, Math.min(4, distance / 24));
        double angle = SPIN * seconds;
        draw.ring(cx, 0, cz, radius, angle, draw.points(radius * 2 * Math.PI / 1.2), Palette.BLUE, size, terrain);
        draw.ring(cx, 0, cz, 2.5, -angle, draw.points(14), Palette.BLUE, size, terrain);
        for (int arm = 0; arm < 4; arm++) {
            double a = angle + arm * Math.PI / 2;
            for (double t = 1.5; t <= 5.5; t += 0.5) {
                double x = cx + Math.cos(a) * t;
                double z = cz + Math.sin(a) * t;
                draw.dust(x, terrain.surface(x, z), z, Palette.BLUE, size);
            }
        }
    }
}
