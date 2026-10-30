package io.github.justadev20.orbitalrailgun.aim;

import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import io.github.justadev20.orbitalrailgun.item.RailgunItem;
import java.util.stream.IntStream;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

/**
 * The item a shot costs. Like arrows for a bow it is taken from the off hand first, then from the hotbar and the main
 * inventory; creative players need none. A railgun never counts, even if its base item is configured as ammo.
 */
final class RailgunAmmo {
    /** Off hand (40), then hotbar and main inventory (0–35). */
    private static final int[] SLOTS = IntStream.concat(IntStream.of(40), IntStream.range(0, 36)).toArray();

    private RailgunAmmo() {
    }

    static boolean has(Player player, RailgunConfig.Ammo ammo) {
        if (free(player, ammo)) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (int slot : SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack, ammo.item())) {
                count += stack.getAmount();
            }
        }
        return count >= ammo.amount();
    }

    static void consume(Player player, RailgunConfig.Ammo ammo) {
        if (free(player, ammo)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int left = ammo.amount();
        for (int slot : SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (left > 0 && matches(stack, ammo.item())) {
                int taken = Math.min(left, stack.getAmount());
                inventory.setItem(slot, taken == stack.getAmount() ? null : stack.subtract(taken));
                left -= taken;
            }
        }
    }

    private static boolean free(Player player, RailgunConfig.Ammo ammo) {
        return !ammo.required() || player.getGameMode() == GameMode.CREATIVE;
    }

    private static boolean matches(@Nullable ItemStack stack, Material item) {
        return stack != null && stack.getType() == item && !RailgunItem.isRailgun(stack);
    }
}
