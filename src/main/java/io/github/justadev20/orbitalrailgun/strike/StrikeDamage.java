package io.github.justadev20.orbitalrailgun.strike;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import io.github.justadev20.orbitalrailgun.text.Messages;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.function.Supplier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jspecify.annotations.Nullable;

/** The custom "orbital_railgun:strike" damage type and its death message. */
public final class StrikeDamage implements Listener {
    private final Supplier<Messages> messages;

    public StrikeDamage(Supplier<Messages> messages) {
        this.messages = messages;
    }

    static DamageSource source(@Nullable Entity attacker) {
        DamageType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getOrThrow(RailgunKeys.DAMAGE_TYPE);
        DamageSource.Builder builder = DamageSource.builder(type);
        if (attacker != null) {
            builder.withCausingEntity(attacker).withDirectEntity(attacker);
        }
        return builder.build();
    }

    /** Vanilla would send the bare key "death.attack.strike"; this adds a fallback for clients without the pack. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        DamageSource source = event.getDamageSource();
        if (!source.getDamageType().key().equals(RailgunKeys.DAMAGE_TYPE)) {
            return;
        }
        Entity attacker = source.getCausingEntity();
        event.deathMessage(attacker == null
                ? messages.get().tr("death.attack.strike", event.getPlayer().displayName())
                : messages.get().tr("death.attack.strike.player", event.getPlayer().displayName(), attacker.name()));
    }
}
