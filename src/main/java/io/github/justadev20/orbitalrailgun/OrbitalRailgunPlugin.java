package io.github.justadev20.orbitalrailgun;

import io.github.justadev20.orbitalrailgun.aim.AimController;
import io.github.justadev20.orbitalrailgun.command.RailgunCommand;
import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import io.github.justadev20.orbitalrailgun.item.CraftingListener;
import io.github.justadev20.orbitalrailgun.item.RailgunItem;
import io.github.justadev20.orbitalrailgun.item.RailgunRecipe;
import io.github.justadev20.orbitalrailgun.pack.ResourcePackService;
import io.github.justadev20.orbitalrailgun.strike.StrikeDamage;
import io.github.justadev20.orbitalrailgun.strike.StrikeManager;
import io.github.justadev20.orbitalrailgun.text.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server-side port of the Orbital Railgun Fabric mod by Mishkis (https://github.com/Mishkis/orbital-railgun, MIT).
 */
public final class OrbitalRailgunPlugin extends JavaPlugin {
    private RailgunConfig settings;
    private Messages messages;
    private StrikeManager strikes;
    private AimController aim;
    private ResourcePackService pack;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        strikes = new StrikeManager(this, this::settings);
        pack = new ResourcePackService(getFile(), getDataFolder().toPath(), getLogger(), this::settings);
        aim = new AimController(this, this::settings, this::messages, strikes, pack);
        pack.rebuild();
        registerRecipe();

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(aim, this);
        plugins.registerEvents(pack, this);
        plugins.registerEvents(new CraftingListener(this::settings), this);
        plugins.registerEvents(new StrikeDamage(this::messages), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(new RailgunCommand(this).build(), "Orbital Railgun", List.of("orbitalrailgun")));

        getServer().getScheduler().runTaskTimer(this, () -> {
            aim.tick();
            strikes.tick();
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (aim != null) {
            aim.stopAll();
        }
        if (strikes != null) {
            strikes.stopAll();
        }
        if (pack != null) {
            pack.close();
        }
        RailgunRecipe.unregister();
    }

    public void reload() {
        reloadConfig();
        aim.stopAll();
        loadSettings();
        registerRecipe();
        pack.rebuild();
    }

    private void loadSettings() {
        settings = RailgunConfig.load(getConfig(), getLogger());
        messages = Messages.load(settings.language(), getLogger());
    }

    private void registerRecipe() {
        RailgunRecipe.unregister();
        if (settings.recipe().enabled()) {
            RailgunRecipe.register(createRailgun());
        }
    }

    public ItemStack createRailgun() {
        return RailgunItem.create(messages, settings.strike().cooldownTicks());
    }

    public RailgunConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public StrikeManager strikes() {
        return strikes;
    }
}
