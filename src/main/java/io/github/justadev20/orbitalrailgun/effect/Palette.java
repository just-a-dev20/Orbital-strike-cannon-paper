package io.github.justadev20.orbitalrailgun.effect;

import org.bukkit.Color;

/** Colours of the original shaders. */
public final class Palette {
    /** {@code vec3(0.62, 0.93, 0.93)} */
    public static final Color BLUE = Color.fromRGB(158, 237, 237);
    /** The target highlight; the shader's red is over-exposed, so a saturated red reads closest in game. */
    public static final Color RED = Color.fromRGB(255, 72, 72);
    public static final Color WHITE = Color.fromRGB(255, 255, 255);

    private Palette() {
    }

    static Color darken(Color color, double amount) {
        double keep = 1 - StrikeTimeline.clamp(amount, 0, 1);
        return Color.fromRGB((int) (color.getRed() * keep), (int) (color.getGreen() * keep), (int) (color.getBlue() * keep));
    }

    static Color mix(Color a, Color b, double t) {
        double k = StrikeTimeline.clamp(t, 0, 1);
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }
}
