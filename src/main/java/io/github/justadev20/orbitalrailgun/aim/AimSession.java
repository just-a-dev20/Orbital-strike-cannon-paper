package io.github.justadev20.orbitalrailgun.aim;

import io.github.justadev20.orbitalrailgun.effect.ParticleDraw;
import io.github.justadev20.orbitalrailgun.effect.Terrain;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.Nullable;

/** State of one player holding right click with the railgun. */
final class AimSession {
    final Player player;
    final EquipmentSlot hand;
    final int startTick;
    final TargetMarker marker;
    final ParticleDraw draw;
    int lastUseTick;
    @Nullable Block target;
    @Nullable Terrain terrain;
    boolean overlayShown;

    AimSession(Player player, EquipmentSlot hand, int now, TargetMarker marker, ParticleDraw draw) {
        this.player = player;
        this.hand = hand;
        this.startTick = now;
        this.lastUseTick = now;
        this.marker = marker;
        this.draw = draw;
    }

    void retarget(@Nullable Block block) {
        if (block == null) {
            target = null;
            return;
        }
        if (target == null || !target.equals(block)) {
            if (terrain == null || target == null || Math.abs(target.getY() - block.getY()) > 2) {
                terrain = new Terrain(block.getWorld(), block.getY());
            }
            target = block;
        }
    }

    /** Seconds since aiming started, the shader's {@code iTime}. */
    double seconds(int now) {
        return (now - startTick) / 20.0;
    }
}
