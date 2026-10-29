package io.github.justadev20.orbitalrailgun.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CylinderTest {
    @Test
    void radiusOneIsTheCentreAndItsFourNeighbours() {
        Cylinder cylinder = new Cylinder(1);

        assertEquals(Set.of(List.of(0, 0), List.of(1, 0), List.of(-1, 0), List.of(0, 1), List.of(0, -1)), columns(cylinder));
        assertFalse(cylinder.rim(0, 0));
        assertTrue(cylinder.rim(1, 0) && cylinder.rim(-1, 0) && cylinder.rim(0, 1) && cylinder.rim(0, -1));
    }

    /** Lattice points with x² + z² ≤ r² (Gauss circle problem); r = 24 gives the mod's ~690 000 blocks over 384 layers. */
    @ParameterizedTest
    @CsvSource({"1, 5, 4", "2, 13, 8", "3, 29, 16", "24, 1793, 132", "64, 12853, 360"})
    void columnAndRimCounts(int radius, int columns, int rimColumns) {
        Cylinder cylinder = new Cylinder(radius);

        assertEquals(columns, cylinder.size());
        assertEquals(columns, columns(cylinder).size());
        assertEquals(rimColumns, columns(cylinder).stream().filter(column -> cylinder.rim(column.get(0), column.get(1))).count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 24, 63, 64})
    void containsExactlyTheColumnsInsideTheCircle(int radius) {
        Cylinder cylinder = new Cylinder(radius);
        Set<List<Integer>> expected = new HashSet<>();
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                if (x * x + z * z <= radius * radius) {
                    expected.add(List.of(x, z));
                }
            }
        }
        assertEquals(expected, columns(cylinder));
    }

    /** Erasing a non-rim column without physics never leaves an outside neighbour without a block update. */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 24, 64})
    void exactlyTheColumnsNextToTheOutsideAreRim(int radius) {
        Cylinder cylinder = new Cylinder(radius);
        for (List<Integer> column : columns(cylinder)) {
            int x = column.get(0);
            int z = column.get(1);
            boolean touchesOutside = !cylinder.inside(x + 1, z) || !cylinder.inside(x - 1, z)
                    || !cylinder.inside(x, z + 1) || !cylinder.inside(x, z - 1);
            assertEquals(touchesOutside, cylinder.rim(x, z), "column " + column);
        }
    }

    @Test
    void positionsOutsideCountAsRim() {
        Cylinder cylinder = new Cylinder(24);

        assertTrue(cylinder.rim(25, 0));
        assertTrue(cylinder.rim(17, 17));
        assertTrue(cylinder.rim(-1000, 1000));
        assertFalse(cylinder.rim(16, 16));
    }

    private static Set<List<Integer>> columns(Cylinder cylinder) {
        Set<List<Integer>> columns = new HashSet<>();
        for (int i = 0; i < cylinder.size(); i++) {
            columns.add(List.of(cylinder.dx(i), cylinder.dz(i)));
        }
        return columns;
    }
}
