package io.github.justadev20.orbitalrailgun.strike;

import java.util.Arrays;

/**
 * The columns x² + z² ≤ r² around a centre column, as offsets. Rim columns have a horizontal neighbour outside the
 * cylinder; only they need neighbour updates when they are erased.
 */
final class Cylinder {
    private final int radius;
    private final int[] dx;
    private final int[] dz;
    private final boolean[][] rim;

    Cylinder(int radius) {
        this.radius = radius;
        this.rim = new boolean[radius * 2 + 1][radius * 2 + 1];
        int[] xs = new int[rim.length * rim.length];
        int[] zs = new int[xs.length];
        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (inside(x, z)) {
                    rim[x + radius][z + radius] = !inside(x + 1, z) || !inside(x - 1, z) || !inside(x, z + 1) || !inside(x, z - 1);
                    xs[count] = x;
                    zs[count++] = z;
                }
            }
        }
        this.dx = Arrays.copyOf(xs, count);
        this.dz = Arrays.copyOf(zs, count);
    }

    boolean inside(int x, int z) {
        return x * x + z * z <= radius * radius;
    }

    int size() {
        return dx.length;
    }

    int dx(int column) {
        return dx[column];
    }

    int dz(int column) {
        return dz[column];
    }

    /** Whether the column at this offset is on the rim; positions outside the cylinder count as rim as well. */
    boolean rim(int x, int z) {
        return !inside(x, z) || rim[x + radius][z + radius];
    }
}
