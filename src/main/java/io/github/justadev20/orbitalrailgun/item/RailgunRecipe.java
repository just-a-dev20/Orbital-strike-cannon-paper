package io.github.justadev20.orbitalrailgun.item;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/** The shaped recipe from data/orbital_railgun/recipes/orbital_railgun.json. */
public final class RailgunRecipe {

    private RailgunRecipe() {
    }

    public static void register(ItemStack result) {
        ShapedRecipe recipe = new ShapedRecipe(RailgunKeys.RECIPE, result);
        recipe.shape("GNE", "SBN", "LR ");
        recipe.setIngredient('G', Material.GLASS_PANE);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('S', Material.NETHER_STAR);
        recipe.setIngredient('B', Material.NETHERITE_BLOCK);
        recipe.setIngredient('L', Material.LAPIS_LAZULI);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setCategory(CraftingBookCategory.EQUIPMENT);
        Bukkit.addRecipe(recipe);
    }

    public static void unregister() {
        Bukkit.removeRecipe(RailgunKeys.RECIPE);
    }
}
