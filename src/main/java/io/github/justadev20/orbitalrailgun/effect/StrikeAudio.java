package io.github.justadev20.orbitalrailgun.effect;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;

/**
 * The mod ships no sounds, so the strike is scored with vanilla sounds. Volume above 1 only widens the range in
 * which a sound is heard (16 blocks per unit), so it is derived from the view distance.
 */
final class StrikeAudio {
    private final Location center;
    private final float volume;
    private final int chargeTicks;

    StrikeAudio(Location center, int viewDistance, int chargeTicks) {
        this.center = center;
        this.volume = viewDistance / 16f;
        this.chargeTicks = chargeTicks;
    }

    void charge(int age) {
        if (age == 0) {
            play(Sound.BLOCK_BEACON_ACTIVATE, 0.5f);
            play(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f);
        }
        if (age == at(3)) {
            play(Sound.BLOCK_BEACON_DEACTIVATE, 0.6f);
        }
        if (age == at(StrikeTimeline.LOCK_ON_END)) {
            play(Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f);
            play(Sound.BLOCK_BEACON_POWER_SELECT, 0.7f);
        }
        if (age > at(StrikeTimeline.LOCK_ON_END) && age % 40 == 0) {
            play(Sound.BLOCK_BEACON_AMBIENT, 0.5f + 1.5f * age / chargeTicks);
        }
        if (age == at(StrikeTimeline.LOCK_ON_END + 11)) {
            play(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f);
            play(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f);
        }
        if (age == at(StrikeTimeline.LOCK_ON_END + 16)) {
            play(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 0.6f);
        }
        if (age == chargeTicks * 4 / 7) {
            play(Sound.BLOCK_PORTAL_TRIGGER, 0.6f);
        }
        if (age == chargeTicks - 34) {
            play(Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f);
        }
    }

    void impact() {
        play(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f);
        play(Sound.ENTITY_GENERIC_EXPLODE, 0.5f);
        play(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f);
        play(Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.7f);
    }

    void aftermath(int ticksAfterImpact) {
        if (ticksAfterImpact == 60) {
            play(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f);
        }
        if (ticksAfterImpact == 600) {
            play(Sound.BLOCK_BEACON_DEACTIVATE, 0.5f);
        }
    }

    private int at(double shaderSeconds) {
        return (int) Math.round(shaderSeconds * chargeTicks / StrikeTimeline.IMPACT);
    }

    private void play(Sound sound, float pitch) {
        center.getWorld().playSound(center, sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
