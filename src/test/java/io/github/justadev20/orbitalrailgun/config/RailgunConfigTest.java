package io.github.justadev20.orbitalrailgun.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RailgunConfigTest {
    private final List<String> warnings = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private RailgunConfig load(Map<String, Object> values) {
        YamlConfiguration yaml = new YamlConfiguration();
        values.forEach(yaml::set);
        return RailgunConfig.load(yaml, logger);
    }

    @Test
    void missingKeysUseTheModDefaults() {
        RailgunConfig config = load(Map.of());

        assertEquals("en_us", config.language());
        assertEquals(new RailgunConfig.Strike(24, 700, 2400, 300, Set.of(), true, 3), config.strike());
        assertEquals(new RailgunConfig.Damage(true, 100000, false), config.damage());
        assertEquals(new RailgunConfig.Destruction(true, true, Set.of(), 20000, 10), config.destruction());
        assertTrue(config.fireExplodeEvents());
        assertEquals(new RailgunConfig.Effects(true, 256, 1.0, true, true, true, true, true), config.effects());
        assertEquals(new RailgunConfig.Aim(8, 0.8, true), config.aim());
        assertEquals(new RailgunConfig.Recipe(true, true), config.recipe());
        assertEquals(new RailgunConfig.ResourcePack(false, "", "", "", false, "", true), config.resourcePack());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void bundledConfigMatchesTheDefaults() throws Exception {
        YamlConfiguration bundled;
        try (InputStream in = RailgunConfig.class.getResourceAsStream("/config.yml")) {
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        RailgunConfig fromFile = RailgunConfig.load(bundled, logger);
        RailgunConfig defaults = load(Map.of());

        assertEquals(defaults.language(), fromFile.language());
        assertEquals(defaults.strike(), fromFile.strike());
        assertEquals(defaults.damage(), fromFile.damage());
        assertEquals(defaults.destruction(), fromFile.destruction());
        assertEquals(defaults.fireExplodeEvents(), fromFile.fireExplodeEvents());
        assertEquals(defaults.effects(), fromFile.effects());
        assertEquals(defaults.aim(), fromFile.aim());
        assertEquals(defaults.recipe(), fromFile.recipe());
        RailgunConfig.ResourcePack pack = fromFile.resourcePack();
        assertEquals(defaults.resourcePack(), new RailgunConfig.ResourcePack(pack.enabled(), pack.url(), pack.publicAddress(),
                pack.sha1(), pack.required(), "", pack.aimOverlay()));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void valuesBelowTheLimitsAreRaised() {
        RailgunConfig config = load(Map.ofEntries(
                Map.entry("strike.radius", 0),
                Map.entry("strike.charge-ticks", 1),
                Map.entry("strike.cooldown-ticks", -20),
                Map.entry("strike.max-range", 1.5),
                Map.entry("strike.max-concurrent", -1),
                Map.entry("damage.amount", -5),
                Map.entry("destruction.blocks-per-tick", 1),
                Map.entry("destruction.max-millis-per-tick", 0),
                Map.entry("effects.view-distance", 0),
                Map.entry("effects.particle-multiplier", -1),
                Map.entry("aim.release-timeout-ticks", 0),
                Map.entry("aim.slowdown", -0.5)));

        assertEquals(1, config.strike().radius());
        assertEquals(100, config.strike().chargeTicks());
        assertEquals(0, config.strike().cooldownTicks());
        assertEquals(16, config.strike().maxRange());
        assertEquals(0, config.strike().maxConcurrent());
        assertEquals(0, config.damage().amount());
        assertEquals(256, config.destruction().blocksPerTick());
        assertEquals(1, config.destruction().maxMillisPerTick());
        assertEquals(32, config.effects().viewDistance());
        assertEquals(0, config.effects().particleMultiplier());
        assertEquals(5, config.aim().releaseTimeoutTicks());
        assertEquals(0, config.aim().slowdown());
    }

    @Test
    void valuesAboveTheLimitsAreLowered() {
        RailgunConfig config = load(Map.ofEntries(
                Map.entry("strike.radius", 65),
                Map.entry("strike.charge-ticks", 12001),
                Map.entry("strike.cooldown-ticks", 2_000_000),
                Map.entry("strike.max-range", 1000),
                Map.entry("strike.max-concurrent", 1001),
                Map.entry("destruction.blocks-per-tick", 2_000_000),
                Map.entry("destruction.max-millis-per-tick", 50),
                Map.entry("effects.view-distance", 1000),
                Map.entry("effects.particle-multiplier", 5),
                Map.entry("aim.release-timeout-ticks", 41),
                Map.entry("aim.slowdown", 1.5)));

        assertEquals(64, config.strike().radius());
        assertEquals(12000, config.strike().chargeTicks());
        assertEquals(1_728_000, config.strike().cooldownTicks());
        assertEquals(512, config.strike().maxRange());
        assertEquals(1000, config.strike().maxConcurrent());
        assertEquals(1_000_000, config.destruction().blocksPerTick());
        assertEquals(40, config.destruction().maxMillisPerTick());
        assertEquals(512, config.effects().viewDistance());
        assertEquals(4, config.effects().particleMultiplier());
        assertEquals(40, config.aim().releaseTimeoutTicks());
        assertEquals(1, config.aim().slowdown());
    }

    @Test
    void valuesAtTheLimitsAreKept() {
        RailgunConfig config = load(Map.of("strike.radius", 64, "strike.charge-ticks", 100, "strike.cooldown-ticks", 0,
                "strike.max-concurrent", 0, "aim.release-timeout-ticks", 5));

        assertEquals(64, config.strike().radius());
        assertEquals(100, config.strike().chargeTicks());
        assertEquals(0, config.strike().cooldownTicks());
        assertEquals(0, config.strike().maxConcurrent());
        assertEquals(5, config.aim().releaseTimeoutTicks());
    }

    /** Telling blocks from items needs the server's registries, so only unknown names are checked here. */
    @Test
    void unknownSkipBlocksAreIgnoredWithAWarning() {
        RailgunConfig config = load(Map.of("destruction.skip-blocks", List.of("NOPE", "minecraft:not_a_block")));

        assertEquals(Set.of(), config.destruction().skipBlocks());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("NOPE") && warnings.getFirst().contains("minecraft:not_a_block"), warnings.getFirst());
    }

    @Test
    void bedrockIsSkippedOnlyWhenItMustNotBeDestroyed() {
        assertFalse(load(Map.of()).destruction().skips(Material.BEDROCK));
        assertTrue(load(Map.of("destruction.destroy-bedrock", false)).destruction().skips(Material.BEDROCK));
        assertFalse(load(Map.of("destruction.destroy-bedrock", false)).destruction().skips(Material.STONE));
    }

    @Test
    void emptyWorldListAllowsEveryWorld() {
        assertTrue(load(Map.of()).strike().allowedIn(world("world_nether")));

        RailgunConfig.Strike strike = load(Map.of("strike.allowed-worlds", List.of("world"))).strike();
        assertTrue(strike.allowedIn(world("world")));
        assertFalse(strike.allowedIn(world("world_nether")));
    }

    @Test
    void resourcePackAddressesAreTrimmed() {
        RailgunConfig.ResourcePack pack = load(Map.of("resource-pack.url", "  https://example.org/pack.zip ",
                "resource-pack.public-address", " mc.example.org:25565 ")).resourcePack();

        assertEquals("https://example.org/pack.zip", pack.url());
        assertEquals("mc.example.org:25565", pack.publicAddress());
        assertFalse(pack.selfHosted());
        assertTrue(load(Map.of("resource-pack.url", "   ")).resourcePack().selfHosted());
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "World " + name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
