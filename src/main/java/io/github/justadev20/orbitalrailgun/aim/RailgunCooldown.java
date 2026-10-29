package io.github.justadev20.orbitalrailgun.aim;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The railgun cooldown. Vanilla keeps item cooldowns in memory only, so a re-login or a restart would end them;
 * the end time is therefore also stored in the player's PDC (wall clock, so it keeps running while the player is
 * offline or the server is down).
 */
final class RailgunCooldown {
    private static final long MILLIS_PER_TICK = 50;

    private RailgunCooldown() {
    }

    /** Remaining ticks: the item cooldown or the stored end time, whichever lasts longer. */
    static int remaining(Player player, ItemStack item, int cooldownTicks) {
        return Math.max(player.getCooldown(item), Math.min(stored(player), cooldownTicks));
    }

    static void start(Player player, ItemStack item, int ticks) {
        player.setCooldown(item, ticks);
        player.getPersistentDataContainer().set(RailgunKeys.COOLDOWN_END, PersistentDataType.LONG,
                System.currentTimeMillis() + ticks * MILLIS_PER_TICK);
    }

    /** Shows a stored cooldown on the item again after a re-login or a restart. */
    static void restore(Player player, int cooldownTicks) {
        int remaining = Math.min(stored(player), cooldownTicks);
        if (remaining <= 0) {
            player.getPersistentDataContainer().remove(RailgunKeys.COOLDOWN_END);
        } else if (player.getCooldown(RailgunKeys.COOLDOWN_GROUP) < remaining) {
            player.setCooldown(RailgunKeys.COOLDOWN_GROUP, remaining);
        }
    }

    private static int stored(Player player) {
        Long end = player.getPersistentDataContainer().get(RailgunKeys.COOLDOWN_END, PersistentDataType.LONG);
        if (end == null) {
            return 0;
        }
        long millis = end - System.currentTimeMillis();
        return millis <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (millis + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK);
    }
}
