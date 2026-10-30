package io.github.justadev20.orbitalrailgun.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Fully lit, non-persistent block displays used for markers, satellites and beams. */
public final class Displays {
    private Displays() {
    }

    public static BlockDisplay spawn(Location location, Material block, @Nullable Color glow, float viewRange, boolean visibleByDefault) {
        return location.getWorld().spawn(location, BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(visibleByDefault);
            display.setBlock(block.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(viewRange);
            display.setShadowRadius(0);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            display.setTransformation(box(0, 0, 0, 0, 0, 0, 0));
            if (glow != null) {
                display.setGlowing(true);
                display.setGlowColorOverride(glow);
            }
        });
    }

    /**
     * A box of the given size whose bottom centre is at (x, y, z) relative to the entity, rotated around the
     * vertical axis through that point.
     */
    public static Transformation box(float x, float y, float z, float width, float height, float depth, float yaw) {
        Quaternionf rotation = new Quaternionf().rotateY(yaw);
        Vector3f corner = rotation.transform(new Vector3f(-width / 2, 0, -depth / 2)).add(x, y, z);
        return new Transformation(corner, rotation, new Vector3f(width, height, depth), new Quaternionf());
    }

    /** A cube centred on (x, y, z), spun around a tilted axis. */
    public static Transformation spinningCube(float x, float y, float z, float size, float angle) {
        Quaternionf rotation = new Quaternionf().rotateAxis(angle, new Vector3f(1, 1, 0).normalize());
        Vector3f corner = rotation.transform(new Vector3f(-size / 2, -size / 2, -size / 2)).add(x, y, z);
        return new Transformation(corner, rotation, new Vector3f(size), new Quaternionf());
    }

    /**
     * Interpolates to the given transformation. Unchanged transformations are skipped: the delay is always sent,
     * and the client then restarts the last interpolation, so a display updated every tick replays its last change
     * every tick (a hidden marker would flash up at its old size).
     */
    public static void update(Display display, Transformation transformation) {
        if (display.isValid() && !transformation.equals(display.getTransformation())) {
            display.setInterpolationDelay(0);
            display.setTransformation(transformation);
        }
    }
}
