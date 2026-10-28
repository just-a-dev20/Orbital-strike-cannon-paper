package io.github.justadev20.orbitalrailgun.effect;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;

/**
 * The shader's explosion cylinder: four rotated translucent boxes approximate the round column from the bottom
 * of the world into the sky, with a bright core. It widens to the full radius within a few seconds and then
 * thins out over 36 seconds, like the shader fades it out. The displays are created ahead of time, one per tick.
 */
final class ImpactBeam {
    private static final int SHELLS = 4;
    private static final int CORES = 2;
    private static final double SHELL_FACTOR = 1 / 1.3;
    private static final double CORE_FACTOR = 0.3;
    private static final int UPDATE_TICKS = 4;

    private final Location base;
    private final double radius;
    private final float height;
    private final List<BlockDisplay> shells = new ArrayList<>();
    private final List<BlockDisplay> cores = new ArrayList<>();

    ImpactBeam(Location center, double radius) {
        World world = center.getWorld();
        this.base = new Location(world, center.getX(), world.getMinHeight(), center.getZ());
        this.radius = radius;
        this.height = world.getMaxHeight() + 192 - world.getMinHeight();
    }

    /** Spawns the next display; returns false once all exist. */
    boolean prepare() {
        boolean shell = shells.size() < SHELLS;
        if (!shell && cores.size() == CORES) {
            return false;
        }
        BlockDisplay display = shell
                ? Displays.spawn(base, Material.LIGHT_BLUE_STAINED_GLASS, null, 10, true)
                : Displays.spawn(base, Material.WHITE_CONCRETE, Palette.BLUE, 10, true);
        display.setDisplayWidth((float) (radius * 3));
        display.setDisplayHeight(height);
        display.setInterpolationDuration(UPDATE_TICKS);
        (shell ? shells : cores).add(display);
        return true;
    }

    void update(double seconds, int ticks) {
        if (ticks % UPDATE_TICKS != 0) {
            return;
        }
        double beamRadius = StrikeTimeline.beamRadius(seconds, radius);
        float spin = ticks * 0.01f;
        float shell = (float) (beamRadius * SHELL_FACTOR * 2);
        float core = (float) (beamRadius * SHELL_FACTOR * CORE_FACTOR * 2);
        for (int i = 0; i < shells.size(); i++) {
            Displays.update(shells.get(i), Displays.box(0, 0, 0, shell, height, shell, spin + (float) (Math.PI / 8 * i)));
        }
        for (int i = 0; i < cores.size(); i++) {
            Displays.update(cores.get(i), Displays.box(0, 0, 0, core, height, core, -spin + (float) (Math.PI / 4 * i)));
        }
    }

    void remove() {
        shells.forEach(Display::remove);
        cores.forEach(Display::remove);
        shells.clear();
        cores.clear();
    }
}
