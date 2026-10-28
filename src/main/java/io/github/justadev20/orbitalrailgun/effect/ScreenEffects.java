package io.github.justadev20.orbitalrailgun.effect;

import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Stand-ins for the shader's full-screen effects: the fade to black before the charge-up (blindness), the dark
 * scene during the charge-up (darkness) and the white flash of the shock wave (flash particle in front of the eyes).
 */
final class ScreenEffects {
    private static final int DARKNESS_REFRESH = 40;

    private final RailgunConfig.Effects settings;
    private final Location center;
    private final Set<UUID> darkened = new HashSet<>();
    private final Set<UUID> flashed = new HashSet<>();

    ScreenEffects(RailgunConfig.Effects settings, Location center) {
        this.settings = settings;
        this.center = center;
    }

    void blackout(List<Player> viewers) {
        if (settings.blackout()) {
            viewers.forEach(player -> player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, false, false)));
        }
    }

    void darkness(List<Player> viewers, int age) {
        if (settings.darkness() && age % DARKNESS_REFRESH == 0) {
            for (Player player : viewers) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DARKNESS_REFRESH + 20, 0, true, false, false));
                darkened.add(player.getUniqueId());
            }
        }
    }

    void impact(List<Player> viewers) {
        for (Player player : viewers) {
            if (darkened.contains(player.getUniqueId())) {
                player.removePotionEffect(PotionEffectType.DARKNESS);
            }
            flash(player);
        }
    }

    /** Flashes every viewer once when the 80 blocks per second wave passes them. */
    void wave(List<Player> viewers, double seconds) {
        double wave = Shockwaves.FLASH_WAVE_SPEED * (seconds - Shockwaves.FLASH_WAVE_DELAY);
        for (Player player : viewers) {
            Location location = player.getLocation();
            double dx = location.getX() - center.getX();
            double dz = location.getZ() - center.getZ();
            if (wave * wave >= dx * dx + dz * dz && flashed.add(player.getUniqueId())) {
                flash(player);
            }
        }
    }

    private void flash(Player player) {
        if (settings.flash()) {
            Location eye = player.getEyeLocation();
            Location front = eye.add(eye.getDirection().multiply(1.5));
            player.spawnParticle(Particle.FLASH, front, 1, 0, 0, 0, 0, Palette.WHITE, true);
        }
    }
}
