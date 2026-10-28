package io.github.justadev20.orbitalrailgun.item;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import java.util.Arrays;
import java.util.function.Supplier;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Keeps the railgun out of vanilla recipes that accept its base item, and unlocks its own recipe. */
public final class CraftingListener implements Listener {
    private final Supplier<RailgunConfig> config;

    public CraftingListener(Supplier<RailgunConfig> config) {
        this.config = config;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix()).anyMatch(RailgunItem::isRailgun)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter
                && Arrays.stream(crafter.getInventory().getContents()).anyMatch(RailgunItem::isRailgun)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RailgunConfig.Recipe recipe = config.get().recipe();
        if (recipe.enabled() && recipe.discoverOnJoin() && event.getPlayer().hasPermission(RailgunKeys.PERMISSION_USE)) {
            event.getPlayer().discoverRecipe(RailgunKeys.RECIPE);
        }
    }
}
