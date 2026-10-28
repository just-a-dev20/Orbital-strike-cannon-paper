package io.github.justadev20.orbitalrailgun.effect;

import io.github.justadev20.orbitalrailgun.config.RailgunConfig;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Server-side replacement for the strike shader, driven by the age of the strike in ticks. */
public final class StrikeEffects {
    private static final int VIEWER_REFRESH = 10;
    private static final int AFTERMATH_TICKS = (int) (StrikeTimeline.AFTERMATH_SECONDS * 20);

    private final RailgunConfig.Effects settings;
    private final Location center;
    private final int chargeTicks;
    private final double viewDistanceSquared;
    private final ParticleDraw draw;
    private final LockOnMarker lockOn;
    private final ChargeDome dome;
    private final Satellites satellites;
    private final ImpactBeam beam;
    private final Shockwaves shockwaves;
    private final ScreenEffects screen;
    private final StrikeLights lights;
    private final StrikeAudio audio;
    private List<Player> viewers = List.of();

    public StrikeEffects(Location center, int radius, int chargeTicks, RailgunConfig.Effects settings) {
        this.settings = settings;
        this.center = center;
        this.chargeTicks = chargeTicks;
        this.viewDistanceSquared = (double) settings.viewDistance() * settings.viewDistance();
        Terrain terrain = new Terrain(center.getWorld(), center.getBlockY());
        this.draw = new ParticleDraw(center.getWorld(), settings.particleMultiplier());
        this.lockOn = new LockOnMarker(center, radius, terrain);
        this.dome = new ChargeDome(center, radius, terrain);
        this.satellites = new Satellites(center, radius);
        this.beam = new ImpactBeam(center, radius);
        this.shockwaves = new Shockwaves(center, radius, settings.viewDistance(), terrain);
        this.screen = new ScreenEffects(settings, center);
        this.lights = new StrikeLights(center, radius, terrain);
        this.audio = new StrikeAudio(center, settings.viewDistance(), chargeTicks);
    }

    public void tick(int age) {
        if (!settings.enabled()) {
            return;
        }
        if (age % VIEWER_REFRESH == 0) {
            viewers = center.getWorld().getPlayers().stream()
                    .filter(player -> player.getLocation().distanceSquared(center) <= viewDistanceSquared)
                    .toList();
            draw.receivers(viewers);
        }
        if (age < chargeTicks) {
            charge(age, age * StrikeTimeline.IMPACT / chargeTicks);
        } else {
            aftermath(age - chargeTicks);
        }
    }

    private void charge(int age, double shaderTime) {
        if (!satellites.prepare()) {
            beam.prepare();
        }
        if (settings.sounds()) {
            audio.charge(age);
        }
        if (shaderTime < StrikeTimeline.LOCK_ON_END) {
            if (age % 2 == 0) {
                lockOn.draw(draw, shaderTime);
            }
            if (age == (int) Math.round(3.0 * chargeTicks / StrikeTimeline.IMPACT)) {
                screen.blackout(viewers);
            }
            return;
        }
        double localTime = shaderTime - StrikeTimeline.LOCK_ON_END;
        satellites.update(localTime, age);
        if (age % 2 == 0) {
            dome.draw(draw, localTime, age);
        }
        screen.darkness(viewers, age);
        if (settings.light()) {
            lights.show(viewers);
        }
    }

    /** Called once by the strike on the impact tick, after the damage has started. */
    public void impact() {
        if (!settings.enabled()) {
            return;
        }
        satellites.hide();
        screen.impact(viewers);
        shockwaves.burst(draw);
        if (settings.sounds()) {
            audio.impact();
        }
    }

    private void aftermath(int ticks) {
        double seconds = ticks / 20.0;
        satellites.removeOne();
        beam.update(seconds, ticks);
        if (ticks % 2 == 0) {
            shockwaves.draw(draw, seconds);
        }
        screen.wave(viewers, seconds);
        if (settings.sounds()) {
            audio.aftermath(ticks);
        }
        if (ticks == AFTERMATH_TICKS / 2) {
            lights.clear();
        }
    }

    public boolean done(int age) {
        return !settings.enabled() || age >= chargeTicks + AFTERMATH_TICKS;
    }

    public void dispose() {
        satellites.remove();
        beam.remove();
        lights.clear();
    }
}
