package io.github.justadev20.orbitalrailgun.config;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of config.yml; running strikes keep the snapshot they were started with. */
public record RailgunConfig(
        String language,
        Strike strike,
        Damage damage,
        Destruction destruction,
        boolean fireExplodeEvents,
        Effects effects,
        Aim aim,
        Recipe recipe,
        ResourcePack resourcePack) {

    public record Strike(int radius, int chargeTicks, int cooldownTicks, double maxRange, Set<String> allowedWorlds, boolean pull) {
        public boolean allowedIn(World world) {
            return allowedWorlds.isEmpty() || allowedWorlds.contains(world.getName());
        }
    }

    public record Damage(boolean enabled, double amount, boolean attributeToShooter) {
    }

    public record Destruction(boolean enabled, boolean destroyBedrock, Set<Material> skipBlocks, int blocksPerTick, double maxMillisPerTick) {
        public boolean skips(Material material) {
            return skipBlocks.contains(material) || (!destroyBedrock && material == Material.BEDROCK);
        }
    }

    public record Effects(boolean enabled, int viewDistance, double particleMultiplier, boolean darkness, boolean blackout,
                          boolean flash, boolean light, boolean sounds) {
    }

    public record Aim(int releaseTimeoutTicks, double slowdown, boolean actionBar) {
    }

    public record Recipe(boolean enabled, boolean discoverOnJoin) {
    }

    public record ResourcePack(boolean enabled, String url, String publicAddress, String sha1, boolean required, String prompt,
                               boolean aimOverlay) {
        /** Without a URL the plugin serves the pack itself on the game port. */
        public boolean selfHosted() {
            return url.isEmpty();
        }
    }

    public static RailgunConfig load(FileConfiguration file, Logger logger) {
        ConfigurationSection s = section(file, "strike");
        Strike strike = new Strike(
                clamp(s.getInt("radius", 24), 1, 64),
                clamp(s.getInt("charge-ticks", 700), 100, 12000),
                clamp(s.getInt("cooldown-ticks", 2400), 0, 1_728_000),
                clamp(s.getDouble("max-range", 300), 16, 512),
                Set.copyOf(s.getStringList("allowed-worlds")),
                s.getBoolean("pull", true));

        ConfigurationSection d = section(file, "damage");
        Damage damage = new Damage(d.getBoolean("enabled", true), Math.max(0, d.getDouble("amount", 100000)),
                d.getBoolean("attribute-to-shooter", false));

        ConfigurationSection x = section(file, "destruction");
        Destruction destruction = new Destruction(
                x.getBoolean("enabled", true),
                x.getBoolean("destroy-bedrock", true),
                materials(x.getStringList("skip-blocks"), logger),
                clamp(x.getInt("blocks-per-tick", 20000), 256, 1_000_000),
                clamp(x.getDouble("max-millis-per-tick", 10), 1, 40));

        ConfigurationSection e = section(file, "effects");
        Effects effects = new Effects(
                e.getBoolean("enabled", true),
                clamp(e.getInt("view-distance", 256), 32, 512),
                clamp(e.getDouble("particle-multiplier", 1.0), 0, 4),
                e.getBoolean("darkness", true),
                e.getBoolean("blackout", true),
                e.getBoolean("flash", true),
                e.getBoolean("light", true),
                e.getBoolean("sounds", true));

        ConfigurationSection a = section(file, "aim");
        Aim aim = new Aim(clamp(a.getInt("release-timeout-ticks", 8), 5, 40), clamp(a.getDouble("slowdown", 0.8), 0, 1),
                a.getBoolean("action-bar", true));

        ConfigurationSection r = section(file, "recipe");
        Recipe recipe = new Recipe(r.getBoolean("enabled", true), r.getBoolean("discover-on-join", true));

        ConfigurationSection p = section(file, "resource-pack");
        ResourcePack pack = new ResourcePack(
                p.getBoolean("enabled", false),
                p.getString("url", "").trim(),
                p.getString("public-address", "").trim(),
                p.getString("sha1", "").trim(),
                p.getBoolean("required", false),
                p.getString("prompt", ""),
                p.getBoolean("aim-overlay", true));

        return new RailgunConfig(file.getString("language", "en_us"), strike, damage, destruction,
                section(file, "protection").getBoolean("fire-explode-events", true), effects, aim, recipe, pack);
    }

    private static ConfigurationSection section(FileConfiguration file, String path) {
        ConfigurationSection section = file.getConfigurationSection(path);
        return section != null ? section : file.createSection(path);
    }

    private static Set<Material> materials(Iterable<String> names, Logger logger) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        Set<String> unknown = new HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) {
                result.add(material);
            } else {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            logger.warning("Ignoring unknown blocks in destruction.skip-blocks: " + unknown);
        }
        return Set.copyOf(result);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
