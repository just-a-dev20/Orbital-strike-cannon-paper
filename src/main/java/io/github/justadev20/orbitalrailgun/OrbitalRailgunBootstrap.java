package io.github.justadev20.orbitalrailgun;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.event.RegistryEvents;
import org.bukkit.damage.DamageScaling;

/** Registers the mod's damage type (data/orbital_railgun/damage_type/strike.json) before the registries freeze. */
@SuppressWarnings("UnstableApiUsage")
public final class OrbitalRailgunBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DAMAGE_TYPE.compose().newHandler(event ->
                event.registry().register(TypedKey.create(RegistryKey.DAMAGE_TYPE, RailgunKeys.DAMAGE_TYPE), builder -> builder
                        .messageId("strike")
                        .exhaustion(0.1f)
                        .damageScaling(DamageScaling.NEVER))));
    }
}
