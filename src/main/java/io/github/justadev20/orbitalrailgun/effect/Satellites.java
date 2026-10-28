package io.github.justadev20.orbitalrailgun.effect;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;

/**
 * The six outer spheres of the shader that fly out to 60 blocks and back while spinning faster and faster, and
 * the thin beams that rise from them into the sky. All displays sit at the centre and are moved by their
 * transformation, so they stay in the chunks kept loaded by the strike. They are created and removed one per tick.
 */
final class Satellites {
    private static final int COUNT = 6;
    private static final float BEAM_WIDTH = 0.4f;

    private final Location center;
    private final double scale;
    private final float beamCap;
    private final List<BlockDisplay> orbs = new ArrayList<>();
    private final List<BlockDisplay> beams = new ArrayList<>();

    Satellites(Location center, double radius) {
        this.center = center;
        this.scale = radius / 24;
        this.beamCap = (float) (center.getWorld().getMaxHeight() + 128 - center.getY());
    }

    /** Spawns the next display; returns false once all exist. */
    boolean prepare() {
        if (beams.size() == COUNT) {
            return false;
        }
        boolean orb = orbs.size() == beams.size();
        BlockDisplay display = Displays.spawn(center, orb ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE, Palette.BLUE, 8, true);
        display.setDisplayWidth((float) (2 * (64 * scale + 8)));
        display.setDisplayHeight(beamCap);
        (orb ? orbs : beams).add(display);
        return true;
    }

    void update(double localTime, int age) {
        double size = StrikeTimeline.satelliteRadius(localTime) * 2;
        double offset = StrikeTimeline.satelliteOffset(localTime) * scale;
        double rotation = StrikeTimeline.satelliteRotation(localTime);
        double base = StrikeTimeline.beamBase(localTime);
        double top = Math.min(StrikeTimeline.beamTop(localTime), beamCap);
        boolean visible = size > 0 && offset > 0;
        for (int i = 0; i < beams.size(); i++) {
            double angle = Math.PI * 2 * i / COUNT - rotation;
            float x = (float) (Math.cos(angle) * offset);
            float z = (float) (Math.sin(angle) * offset);
            Displays.update(orbs.get(i), Displays.spinningCube(x, 0, z, visible ? (float) size : 0, age * 0.15f + i));
            float length = visible && top > base ? (float) (top - base) : 0;
            Displays.update(beams.get(i), Displays.box(x, (float) base, z, BEAM_WIDTH, length, BEAM_WIDTH, 0));
        }
    }

    void hide() {
        orbs.forEach(orb -> Displays.update(orb, Displays.spinningCube(0, 0, 0, 0, 0)));
        beams.forEach(beam -> Displays.update(beam, Displays.box(0, 0, 0, 0, 0, 0, 0)));
    }

    /** Removes the next display; returns false once none are left. */
    boolean removeOne() {
        List<BlockDisplay> from = !beams.isEmpty() ? beams : orbs;
        if (from.isEmpty()) {
            return false;
        }
        from.removeLast().remove();
        return true;
    }

    void remove() {
        orbs.forEach(Display::remove);
        beams.forEach(Display::remove);
        orbs.clear();
        beams.clear();
    }
}
