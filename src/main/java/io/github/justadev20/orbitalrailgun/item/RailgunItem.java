package io.github.justadev20.orbitalrailgun.item;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import io.github.justadev20.orbitalrailgun.text.Messages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * The railgun is a heart of the sea: it has no use action of its own, so a vanilla client never enters the
 * "using item" state and keeps sending left clicks while right click is held (see AimController).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RailgunItem {
    private static final Material BASE = Material.HEART_OF_THE_SEA;

    private RailgunItem() {
    }

    public static ItemStack create(Messages messages, int cooldownTicks) {
        ItemStack stack = ItemStack.of(BASE);
        stack.setData(DataComponentTypes.ITEM_NAME, messages.tr("item.orbital_railgun.orbital_railgun"));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                loreLine(messages.tr("item.orbital_railgun.orbital_railgun.lore.aim")),
                loreLine(messages.tr("item.orbital_railgun.orbital_railgun.lore.fire")))));
        stack.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setData(DataComponentTypes.ITEM_MODEL, RailgunKeys.FALLBACK_MODEL);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(RailgunKeys.MODEL_DATA).build());
        stack.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(Math.max(cooldownTicks, 1) / 20f)
                .cooldownGroup(RailgunKeys.COOLDOWN_GROUP).build());
        stack.editPersistentDataContainer(pdc -> pdc.set(RailgunKeys.ITEM_TAG, PersistentDataType.BOOLEAN, true));
        return stack;
    }

    public static boolean isRailgun(@Nullable ItemStack stack) {
        return stack != null && stack.getType() == BASE && stack.getPersistentDataContainer().has(RailgunKeys.ITEM_TAG);
    }

    private static Component loreLine(Component text) {
        return text.color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
