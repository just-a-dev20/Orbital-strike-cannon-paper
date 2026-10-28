package io.github.justadev20.orbitalrailgun.pack;

import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jspecify.annotations.Nullable;

/**
 * Builds the optional server resource pack (model, texture, translations, aim overlay) from the plugin jar plus
 * anything in {@code pack-overrides/}, and offers it to joining players. Without a configured URL the plugin serves
 * the zip itself on the game port (see {@link GamePortHost}).
 */
public final class ResourcePackService implements Listener {
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("orbital_railgun:pack".getBytes(StandardCharsets.UTF_8));
    private static final long ENTRY_TIME = 1_767_225_600_000L;

    private final File pluginJar;
    private final Path dataFolder;
    private final Logger logger;
    private final Supplier<RailgunConfig> config;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final GamePortHost host;
    private @Nullable String hash;

    public ResourcePackService(File pluginJar, Path dataFolder, Logger logger, Supplier<RailgunConfig> config) {
        this.pluginJar = pluginJar;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.config = config;
        this.host = new GamePortHost(logger);
    }

    /** Rebuilds the zip and, if the content changed, offers it again to everybody online. */
    public void rebuild() {
        RailgunConfig.ResourcePack settings = config.get().resourcePack();
        if (!settings.enabled()) {
            hash = null;
            host.stop();
            return;
        }
        String previous = hash;
        byte[] bytes;
        try {
            bytes = build();
            Path zip = dataFolder.resolve("OrbitalRailgun-pack.zip");
            Files.write(zip, bytes);
            String built = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            hash = settings.selfHosted() || settings.sha1().isEmpty() ? built : settings.sha1().toLowerCase();
            logger.info("Resource pack written to " + zip + " (SHA-1 " + built + ").");
        } catch (IOException | NoSuchAlgorithmException e) {
            hash = null;
            logger.severe("Could not build the resource pack: " + e);
            return;
        }
        if (settings.selfHosted()) {
            if (!host.serve(servedPath(), bytes)) {
                hash = null;
                return;
            }
            logger.info("Serving the resource pack on the game port at " + servedPath() + ".");
        } else {
            host.stop();
        }
        if (previous != null && !previous.equals(hash)) {
            Bukkit.getOnlinePlayers().forEach(this::offer);
        }
    }

    private String servedPath() {
        return "/orbital_railgun/" + hash + ".zip";
    }

    private byte[] build() throws IOException {
        SortedMap<String, byte[]> entries = new TreeMap<>();
        try (JarFile jar = new JarFile(pluginJar)) {
            jar.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
                String name = entry.getName();
                String target = name.startsWith("pack/") ? name.substring("pack/".length())
                        : name.startsWith("lang/") ? "assets/orbital_railgun/" + name : null;
                if (target != null) {
                    try {
                        entries.put(target, jar.getInputStream(entry).readAllBytes());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
        Path overrides = dataFolder.resolve("pack-overrides");
        if (Files.isDirectory(overrides)) {
            try (Stream<Path> files = Files.walk(overrides)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    entries.put(overrides.relativize(file).toString().replace(File.separatorChar, '/'), Files.readAllBytes(file));
                }
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(ENTRY_TIME);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private void offer(Player player) {
        RailgunConfig.ResourcePack settings = config.get().resourcePack();
        if (!settings.enabled() || hash == null) {
            return;
        }
        URI uri = uri(player, settings);
        if (uri == null) {
            logger.warning("Cannot tell " + player.getName() + " where to download the resource pack: unknown server address. "
                    + "Set resource-pack.public-address.");
            return;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, uri, hash);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(settings.required())
                .prompt(settings.prompt().isEmpty() ? null : Component.text(settings.prompt()))
                .replace(false));
    }

    /** The configured URL, or the game port under the address the player connected with. */
    private @Nullable URI uri(Player player, RailgunConfig.ResourcePack settings) {
        try {
            if (!settings.selfHosted()) {
                return URI.create(settings.url());
            }
            String address = settings.publicAddress();
            if (address.isEmpty()) {
                InetSocketAddress virtualHost = player.getVirtualHost();
                if (virtualHost == null) {
                    return null;
                }
                String hostName = virtualHost.getHostString().split("\0")[0];
                address = (hostName.contains(":") ? "[" + hostName + "]" : hostName) + ":" + virtualHost.getPort();
            } else if (!address.matches(".*:\\d+")) {
                address += ":" + Bukkit.getPort();
            }
            return URI.create("http://" + address + servedPath());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void close() {
        host.stop();
    }

    public boolean hasPack(Player player) {
        return loaded.contains(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        offer(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> loaded.remove(event.getPlayer().getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }
}
