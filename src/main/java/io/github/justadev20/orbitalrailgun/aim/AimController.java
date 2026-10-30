package io.github.justadev20.orbitalrailgun.aim;

import io.github.justadev20.orbitalrailgun.RailgunKeys;
import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import io.github.justadev20.orbitalrailgun.effect.ParticleDraw;
import io.github.justadev20.orbitalrailgun.item.RailgunItem;
import io.github.justadev20.orbitalrailgun.pack.ResourcePackService;
import io.github.justadev20.orbitalrailgun.strike.StrikeManager;
import io.github.justadev20.orbitalrailgun.text.Messages;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.jspecify.annotations.Nullable;

/**
 * "Hold right click to aim, left click to fire" for vanilla clients.
 *
 * <p>A vanilla client swallows attack clicks while it is using an item, which is why the mod needed a mixin. The
 * railgun therefore has no use action: while right click is held the client repeats the use packet every 4 ticks
 * (each one fires a {@link PlayerInteractEvent}), and attack clicks still reach the server. Aiming ends when no use
 * packet arrived for {@code aim.release-timeout-ticks}.
 */
public final class AimController implements Listener {
    private static final Component OVERLAY = Component.text("").font(RailgunKeys.OVERLAY_FONT).shadowColor(ShadowColor.none());
    private static final Title.Times OVERLAY_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(150));
    private static final int NOTICE_INTERVAL = 20;
    /** The target display waits this long after a notice, so the notice can be read. */
    private static final int NOTICE_HOLD = 40;

    private final Plugin plugin;
    private final Supplier<RailgunConfig> config;
    private final Supplier<Messages> messages;
    private final StrikeManager strikes;
    private final ResourcePackService pack;
    private final Map<UUID, AimSession> sessions = new HashMap<>();
    private final Map<UUID, Integer> lastNotice = new HashMap<>();

    public AimController(Plugin plugin, Supplier<RailgunConfig> config, Supplier<Messages> messages, StrikeManager strikes,
                         ResourcePackService pack) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.strikes = strikes;
        this.pack = pack;
    }

    @SuppressWarnings("deprecation") // isInteractable() is approximate, which is enough to leave chests and doors usable
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        AimSession session = sessions.get(player.getUniqueId());
        Action action = event.getAction();
        if (action.isLeftClick()) {
            if (session != null) {
                event.setCancelled(true);
                fire(session);
            }
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (!action.isRightClick() || hand == null || !RailgunItem.isRailgun(event.getItem())) {
            return;
        }
        if (session != null) {
            if (session.hand == hand) {
                session.lastUseTick = Bukkit.getCurrentTick();
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        Block clicked = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        start(player, hand);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        AimSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            if (session.hand == event.getHand()) {
                session.lastUseTick = Bukkit.getCurrentTick();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        AimSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            fire(session);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RailgunCooldown.restore(event.getPlayer(), config.get().strike().cooldownTicks());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        lastNotice.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            stop(event.getPlayer());
        }
    }

    private void start(Player player, EquipmentSlot hand) {
        RailgunConfig settings = config.get();
        ItemStack item = player.getInventory().getItem(hand);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!player.hasPermission(RailgunKeys.PERMISSION_USE)) {
            notice(player, messages.get().tr("orbital_railgun.no_permission"));
            return;
        }
        if (!settings.strike().allowedIn(player.getWorld())) {
            notice(player, messages.get().tr("orbital_railgun.world_disabled"));
            return;
        }
        if (coolingDown(player, item, settings) || missingAmmo(player, settings)) {
            return;
        }
        ParticleDraw draw = new ParticleDraw(player.getWorld(), Math.max(settings.effects().particleMultiplier(), 0.25))
                .receivers(List.of(player));
        AimSession session = new AimSession(player, hand, Bukkit.getCurrentTick(), new TargetMarker(plugin, player), draw);
        sessions.put(player.getUniqueId(), session);
        slowdown(player, settings.aim().slowdown());
        player.playSound(player, Sound.ITEM_SPYGLASS_USE, SoundCategory.PLAYERS, 1f, 0.8f);
        player.playSound(player, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.4f, 1.6f);
        update(session, settings, session.startTick);
    }

    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        RailgunConfig settings = config.get();
        int now = Bukkit.getCurrentTick();
        for (AimSession session : List.copyOf(sessions.values())) {
            if (stillAiming(session, settings, now)) {
                update(session, settings, now);
            } else {
                stop(session);
            }
        }
    }

    private boolean stillAiming(AimSession session, RailgunConfig settings, int now) {
        Player player = session.player;
        return player.isOnline()
                && now - session.lastUseTick <= settings.aim().releaseTimeoutTicks()
                && player.getGameMode() != GameMode.SPECTATOR
                && settings.strike().allowedIn(player.getWorld())
                && RailgunItem.isRailgun(player.getInventory().getItem(session.hand));
    }

    private void update(AimSession session, RailgunConfig settings, int now) {
        Player player = session.player;
        session.retarget(target(player, settings.strike().maxRange()));
        session.marker.show(session.target);
        int age = now - session.startTick;
        if (session.target != null && session.terrain != null && age % 2 == 0) {
            AreaIndicator.draw(session.draw, player.getEyeLocation(), session.target, session.terrain, settings.strike().radius(), session.seconds(now));
        }
        if (settings.aim().actionBar() && age % 5 == 0 && !noticeShowing(player, now)) {
            player.sendActionBar(targetText(player, session.target));
        }
        if (settings.resourcePack().aimOverlay() && pack.hasPack(player) && age % 10 == 0) {
            player.showTitle(Title.title(OVERLAY, Component.empty(), OVERLAY_TIMES));
            session.overlayShown = true;
        }
    }

    private Component targetText(Player player, @Nullable Block target) {
        if (target == null) {
            return messages.get().tr("orbital_railgun.aim.no_target");
        }
        long distance = Math.round(player.getEyeLocation().distance(target.getLocation().add(0.5, 0.5, 0.5)));
        return messages.get().tr("orbital_railgun.aim.target", target.getX(), target.getY(), target.getZ(), distance);
    }

    private void fire(AimSession session) {
        Player player = session.player;
        RailgunConfig settings = config.get();
        ItemStack item = player.getInventory().getItem(session.hand);
        if (coolingDown(player, item, settings)) {
            return;
        }
        if (missingAmmo(player, settings)) {
            player.playSound(player, Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1f, 1.2f);
            return;
        }
        Block target = target(player, settings.strike().maxRange());
        if (target == null) {
            player.playSound(player, Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1f, 1.2f);
            return;
        }
        StrikeManager.Launch launch = strikes.launch(player, target);
        if (launch != StrikeManager.Launch.STARTED) {
            notice(player, messages.get().tr(launch == StrikeManager.Launch.LIMIT_REACHED
                    ? "orbital_railgun.strike_limit" : "orbital_railgun.strike_blocked"));
            return;
        }
        RailgunAmmo.consume(player, settings.ammo());
        if (settings.strike().cooldownTicks() > 0) {
            RailgunCooldown.start(player, item, settings.strike().cooldownTicks());
        }
        stop(session);
    }

    private boolean coolingDown(Player player, ItemStack item, RailgunConfig settings) {
        int cooldown = RailgunCooldown.remaining(player, item, settings.strike().cooldownTicks());
        if (cooldown > 0) {
            notice(player, messages.get().tr("orbital_railgun.cooldown", (cooldown + 19) / 20));
        }
        return cooldown > 0;
    }

    private boolean missingAmmo(Player player, RailgunConfig settings) {
        RailgunConfig.Ammo ammo = settings.ammo();
        if (RailgunAmmo.has(player, ammo)) {
            return false;
        }
        notice(player, messages.get().tr("orbital_railgun.ammo.missing", ammo.amount(), Component.translatable(ammo.item())));
        return true;
    }

    /** The same ray as the mod's client: 300 blocks, block outlines, fluids ignored. */
    private static @Nullable Block target(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, false);
        return hit != null ? hit.getHitBlock() : null;
    }

    private void slowdown(Player player, double amount) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(RailgunKeys.AIM_MODIFIER);
        if (amount > 0) {
            speed.addTransientModifier(new AttributeModifier(RailgunKeys.AIM_MODIFIER, -amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void notice(Player player, Component message) {
        int now = Bukkit.getCurrentTick();
        Integer last = lastNotice.get(player.getUniqueId());
        if (last == null || now - last >= NOTICE_INTERVAL) {
            lastNotice.put(player.getUniqueId(), now);
            player.sendActionBar(message);
        }
    }

    private boolean noticeShowing(Player player, int now) {
        Integer last = lastNotice.get(player.getUniqueId());
        return last != null && now - last < NOTICE_HOLD;
    }

    private void stop(Player player) {
        AimSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            stop(session);
        }
    }

    private void stop(AimSession session) {
        sessions.remove(session.player.getUniqueId());
        session.marker.remove();
        slowdown(session.player, 0);
        if (session.player.isOnline()) {
            if (session.overlayShown) {
                session.player.clearTitle();
            }
            session.player.sendActionBar(Component.empty());
            session.player.playSound(session.player, Sound.ITEM_SPYGLASS_STOP_USING, SoundCategory.PLAYERS, 1f, 0.8f);
        }
    }

    public void stopAll() {
        List.copyOf(sessions.values()).forEach(this::stop);
    }
}
