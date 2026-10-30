package io.github.justadev20.orbitalrailgun.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

class DisplaysTest {
    /** Every delay restarts the client's interpolation, so a display updated with its own transformation must not be touched. */
    @Test
    void updateSkipsUnchangedTransformations() {
        List<String> calls = new ArrayList<>();
        BlockDisplay display = fakeDisplay(calls);

        Displays.update(display, Displays.box(0, 0, 0, 1, 1, 1, 0));
        Displays.update(display, Displays.box(0, 0, 0, 1, 1, 1, 0));
        Displays.update(display, Displays.box(0, 0, 0, 0, 0, 0, 0));
        Displays.update(display, Displays.box(0, 0, 0, 0, 0, 0, 0));

        assertEquals(List.of("setInterpolationDelay", "setTransformation", "setInterpolationDelay", "setTransformation"), calls);
    }

    private static BlockDisplay fakeDisplay(List<String> calls) {
        Transformation[] current = {Displays.box(0, 0, 0, 0, 0, 0, 0)};
        return (BlockDisplay) Proxy.newProxyInstance(DisplaysTest.class.getClassLoader(), new Class<?>[]{BlockDisplay.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> true;
                    case "getTransformation" -> current[0];
                    case "setTransformation" -> {
                        calls.add(method.getName());
                        current[0] = (Transformation) args[0];
                        yield null;
                    }
                    case "setInterpolationDelay" -> {
                        calls.add(method.getName());
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
