package io.github.justadev20.orbitalrailgun.pack;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import io.netty.channel.Channel;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

/**
 * Serves the resource pack over the Minecraft port, so neither a web server nor an extra port is needed.
 *
 * <p>Paper offers no public API to accept other protocols on the game port. This class is the plugin's only use
 * of server internals: it registers a {@code io.papermc.paper.network.ChannelInitializeListener} (the hook Paper
 * provides for plugins that need to see new connections) through reflection, which adds a
 * {@link PackRequestHandler} in front of every new connection. If the hook is missing in a future Paper version
 * the pack is simply not served and the plugin logs why.
 */
final class GamePortHost {
    private static final String HOLDER = "io.papermc.paper.network.ChannelInitializeListenerHolder";
    private static final String LISTENER = "io.papermc.paper.network.ChannelInitializeListener";
    private static final Key KEY = Key.key(RailgunKeys.NAMESPACE, "pack_host");
    private static final String HANDLER_NAME = "orbital_railgun_pack";

    record ServedPack(String path, byte[] bytes) {
    }

    private final Logger logger;
    private volatile @Nullable ServedPack pack;
    private boolean installed;

    GamePortHost(Logger logger) {
        this.logger = logger;
    }

    /** Starts serving {@code bytes} at {@code path}; returns false if the hook is unavailable. */
    boolean serve(String path, byte[] bytes) {
        pack = new ServedPack(path, bytes);
        if (!installed) {
            installed = install();
        }
        return installed;
    }

    void stop() {
        pack = null;
        if (installed) {
            try {
                Class.forName(HOLDER).getMethod("removeListener", Key.class).invoke(null, KEY);
            } catch (ReflectiveOperationException | LinkageError e) {
                logger.warning("Could not unregister the resource pack host: " + e);
            }
            installed = false;
        }
    }

    private boolean install() {
        try {
            Class<?> holder = Class.forName(HOLDER);
            Class<?> listenerType = Class.forName(LISTENER);
            Object listener = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {listenerType},
                    (proxy, method, args) -> handle(proxy, method, args));
            holder.getMethod("removeListener", Key.class).invoke(null, KEY);
            holder.getMethod("addListener", Key.class, listenerType).invoke(null, KEY, listener);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            logger.severe("Cannot serve the resource pack on the game port (" + e + "). Set resource-pack.url instead.");
            return false;
        }
    }

    private @Nullable Object handle(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "afterInitChannel" -> {
                ((Channel) args[0]).pipeline().addFirst(HANDLER_NAME, new PackRequestHandler(() -> pack));
                yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "OrbitalRailgun resource pack host";
            default -> null;
        };
    }
}
