package io.github.justadev20.orbitalrailgun;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;

/** Identifiers shared between the plugin, the bootstrapper and the resource pack. */
public final class RailgunKeys {
    public static final String NAMESPACE = "orbital_railgun";

    public static final Key DAMAGE_TYPE = Key.key(NAMESPACE, "strike");
    public static final Key COOLDOWN_GROUP = Key.key(NAMESPACE, "orbital_railgun");
    public static final Key OVERLAY_FONT = Key.key(NAMESPACE, "overlay");
    public static final Key FALLBACK_MODEL = Key.key("minecraft", "crossbow");
    public static final String MODEL_DATA = "orbital_railgun";

    public static final NamespacedKey ITEM_TAG = new NamespacedKey(NAMESPACE, "railgun");
    public static final NamespacedKey RECIPE = new NamespacedKey(NAMESPACE, "orbital_railgun");
    public static final NamespacedKey AIM_MODIFIER = new NamespacedKey(NAMESPACE, "aim");

    public static final String PERMISSION_USE = "orbitalrailgun.use";
    public static final String PERMISSION_GIVE = "orbitalrailgun.give";
    public static final String PERMISSION_ADMIN = "orbitalrailgun.admin";

    private RailgunKeys() {
    }
}
