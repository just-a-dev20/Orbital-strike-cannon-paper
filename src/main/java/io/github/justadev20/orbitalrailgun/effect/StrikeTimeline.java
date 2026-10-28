package io.github.justadev20.orbitalrailgun.effect;

/**
 * Formulas taken from shaders/program/strike.fsh. They work on "shader seconds": the shader shows a 4 s lock-on,
 * a 32 s charge-up and the beam at 36 s. The strike's charge time is mapped onto these 36 s so the beam coincides
 * with the server-side impact; values are for the original radius of 24 and are scaled by the caller.
 */
final class StrikeTimeline {
    static final double LOCK_ON_END = 4;
    static final double IMPACT = 36;
    static final double EXPANSION = 32;
    static final double AFTERMATH_SECONDS = 36;

    private StrikeTimeline() {
    }

    /** Radius of the main sphere ({@code -scale} in the shader); not visible while negative. */
    static double domeRadius(double localTime) {
        double shrink = Math.pow(clamp(2 * localTime / EXPANSION - 1, 0, 10), 2);
        double scale = 1.2 * (EXPANSION * 25 / (localTime + 0.7) / 32 - 32 * (1 - shrink)) + 2.4;
        return -scale;
    }

    static double satelliteRadius(double localTime) {
        return Math.min(domeRadius(localTime), 3);
    }

    static double satelliteOffset(double localTime) {
        return -Math.pow((localTime - 13) / 1.4, 2) + 60;
    }

    static double satelliteRotation(double localTime) {
        double rotation = Math.min(25 / (localTime - 25) + 1.5, 0);
        return Double.isFinite(rotation) ? rotation % (Math.PI * 2) : 0;
    }

    static double beamBase(double localTime) {
        return clamp(Math.pow(localTime - 16, 3) * 50, 0, 5000);
    }

    static double beamTop(double localTime) {
        return Math.min(Math.pow(localTime - 11, 3) * 50, 5000);
    }

    /** Radius of the explosion cylinder ({@code length(p.xz) + 8 / t - 24}) shrunk by the shader's fade-out. */
    static double beamRadius(double secondsAfterImpact, double radius) {
        double grow = radius - radius / 3 / Math.max(secondsAfterImpact, 0.05);
        double fade = 1 - Math.pow(clamp(secondsAfterImpact / AFTERMATH_SECONDS, 0, 1), 2);
        return Math.max(0, grow * fade);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
