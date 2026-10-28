package io.github.justadev20.orbitalrailgun.aim;

import io.github.justadev20.orbitalrailgun.effect.Displays;
import io.github.justadev20.orbitalrailgun.effect.Palette;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The red highlight of the targeted block, visible only to the shooter. Targets can be up to 300 blocks away,
 * far beyond the entity tracking range, so the marker is placed on the line of sight at most {@link #NEAR} blocks
 * from the eye and scaled down by the same factor: from the shooter's eye it covers exactly the target block.
 */
final class TargetMarker {
    private static final double NEAR = 16;
    private static final float INFLATE = 1.04f;

    private final Plugin plugin;
    private final Player player;
    private @Nullable BlockDisplay display;

    TargetMarker(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    void show(@Nullable Block target) {
        if (target == null) {
            if (display != null) {
                Displays.update(display, scaled(0));
            }
            return;
        }
        Location eye = player.getEyeLocation();
        Vector toTarget = target.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5)).subtract(eye.toVector());
        double factor = Math.min(1, NEAR / Math.max(toTarget.length(), 0.01));
        Location position = eye.clone().add(toTarget.multiply(factor));
        if (display == null || !display.isValid()) {
            display = Displays.spawn(position, Material.RED_STAINED_GLASS, Palette.RED, 2, false);
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            player.showEntity(plugin, display);
        } else {
            display.teleport(position);
        }
        Displays.update(display, scaled((float) (INFLATE * factor)));
    }

    private static Transformation scaled(float size) {
        return new Transformation(new Vector3f(-size / 2), new Quaternionf(), new Vector3f(size), new Quaternionf());
    }

    void remove() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }
}
