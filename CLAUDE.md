# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A server-side Paper 26.3 plugin that ports the Fabric mod [Mishkis/orbital-railgun](https://github.com/Mishkis/orbital-railgun)
(MIT). Hard requirement from the owner: **vanilla clients must work without mods**, and gameplay should stay as close
to the mod as possible. The README (German) lists every deviation from the mod (1:1 / approximated / impossible) –
keep that list in sync when behaviour changes.

Owner preferences: no NMS/server internals unless unavoidable, and then justified and encapsulated in one class;
`paper-plugin.yml` (not `plugin.yml`); Brigadier commands via `LifecycleEvents.COMMANDS`; short classes, comments only
where needed. User-facing text (README, `config.yml` comments) is German; code and code comments are English.

## Commands

```bash
./gradlew build                          # jar in build/libs/OrbitalRailgun-<version>.jar, runs the tests
./gradlew test                           # JUnit tests only
./gradlew build -PpluginVersion=1.2.3    # override the version (used by the release workflow)
./gradlew runServer                      # run-paper test server in run/ (gitignored)
```

- Java 25 is provisioned through the Gradle toolchain (foojay); Gradle itself can run on Java 21. run-paper 3.x
  needs Gradle 9, so use the wrapper, not an older system Gradle.
- Versions are pinned in `gradle.properties` (`paperApiVersion`, `nettyVersion` = the Netty bundled with that Paper).
- The PaperMC repository is listed before Maven Central on purpose: Central returned HTTP 429 through the sandbox proxy.
- `src/test` covers what runs without a server: `PackRequestHandler` (Netty `EmbeddedChannel`), the limits in
  `RailgunConfig.load`, the `Cylinder` geometry and `Displays.update` (proxy display). Anything that touches registries (e.g. `Material.isBlock()`)
  throws without a server; verify gameplay on a real server, see below.

## Architecture

Entry points: `OrbitalRailgunBootstrap` registers the `orbital_railgun:strike` damage type in the registry compose
event (must happen in the bootstrapper, before registries freeze). `OrbitalRailgunPlugin` wires everything and runs a
single 1-tick task: `AimController.tick()` then `StrikeManager.tick()`.

Config is an immutable `RailgunConfig` record loaded from `config.yml`; components receive a `Supplier<RailgunConfig>`.
Running strikes keep the snapshot they started with; `/railgun reload` swaps the snapshot.

Texts are `Component.translatable(key, fallback)` built by `text/Messages`. Fallbacks come from
`src/main/resources/lang/<language>.json`; the same files are copied into the resource pack. New keys go into
`en_us.json` and `de_de.json`.

### Controls (the core design constraint)

A vanilla client swallows attack clicks while it is "using" an item (`Minecraft.handleKeybinds`). So the railgun is a
`HEART_OF_THE_SEA` with **no use action** – never give it a `consumable`/use component. While right click is held the
client repeats the use packet every 4 ticks (`rightClickDelay`); `AimController` treats a right click as held until no
use packet arrived for `aim.release-timeout-ticks`. Left click into the air arrives as Paper's `LEFT_CLICK_AIR`
(26.x `ServerboundPunchPacket`); those events are pre-cancelled, so interaction listeners must not use
`ignoreCancelled = true`. The item is identified by a PDC tag (`RailgunItem.isRailgun`), its look is
`item_model = minecraft:crossbow` + `custom_model_data` string `orbital_railgun`.

Vanilla does not save item cooldowns, so `aim/RailgunCooldown` also stores the end (wall clock) in the player's PDC,
checks it when aiming starts and when firing, and restores the item cooldown on join. While a notice (cooldown,
blocked, strike limit) is fresh, the aim action bar holds back so the notice stays readable.

`aim/RailgunAmmo`: every shot costs `ammo.amount` × `ammo.item` (default one nether star; 0 = free like the mod),
searched like a bow searches arrows (off hand, then slots 0–35), never a railgun, free in creative. It is checked when
aiming starts and when firing, and taken only after `launch()` returned `STARTED`. `RailgunConfig.load` can only reject
unknown names and air, because `Material.isItem()` needs the registries.

### Strike pipeline (`strike/`)

- `Strike` follows the mod's `OrbitalRailgunStrikeManager`: entity snapshot of a 500³ box at launch, pull during the
  last 3/7 of `charge-ticks`, damage on impact, then the cylinder is erased. Chunk tickets are reference counted in
  `ChunkTickets` because Bukkit keeps only one ticket per plugin and chunk.
- `CylinderEraser` removes x²+z² ≤ r² over the whole world height top-down in 256-block batches. Each batch first
  fires an `EntityExplodeEvent` (shooter as source) or `BlockExplodeEvent` (console/offline shooter) so protection
  plugins (tested with WorldGuard: `build`, `other-explosion`) can filter it. Only rim columns use physics.
- `StrikeManager` shares `destruction.blocks-per-tick` and `max-millis-per-tick` across all strikes. `launch()` also
  enforces `strike.max-concurrent` (for `/railgun strike` too) before firing the `OrbitalStrikeEvent`.
- The column/rim geometry lives in `Cylinder` (pure, unit-tested); `CylinderEraser` only does world access.
- Performance lesson: use **hard deadlines checked inside the tick**, not budgets adjusted after the fact. Cold code
  paths produced 100–200 ms ticks. The same applies to damage (time-sliced) and display entities (spawned/removed one
  per tick via `prepare()`/`removeOne()`).

### Effects (`effect/`)

`StrikeEffects` replaces the mod's shaders with particles, `BlockDisplay`s, client-only light blocks, potion effects
and vanilla sounds. `StrikeTimeline` holds formulas ported from the mod's `strike.fsh` in "shader seconds" (the charge
time is mapped onto the shader's 36 s so the beam coincides with the impact). Displays sit at the strike centre and
are moved via their transformation so they stay in ticketed chunks; billboarded displays cannot be offset that way,
which is why orbs are spinning `BlockDisplay`s. `aim/TargetMarker` places the shooter-only marker on the line of
sight at most 16 blocks away and scales it down (targets can be 300 blocks away, beyond entity tracking range).
Display updates go through `Displays.update`, which skips unchanged transformations: the interpolation delay is always
sent (`force`), and the client then restarts its last interpolation, so per-tick updates with the same value replay
the last change every tick (the hidden target marker flashed up at its old size).

### Resource pack (`pack/`, `src/main/resources/pack`)

Built at runtime from the jar's `pack/` and `lang/` plus `plugins/OrbitalRailgun/pack-overrides/`, as a deterministic
zip. It overrides `assets/minecraft/items/crossbow.json` with a `custom_model_data` select whose fallback is a copy of
the vanilla 26.3 definition, so players without the pack see a normal crossbow. The item model is generated by
`tools/convert_geckolib_model.py` from the mod's GeckoLib model (mirrors GeckoLib's renderer math).

Without `resource-pack.url`, `GamePortHost` serves the zip on the Minecraft port: it registers Paper's internal
`ChannelInitializeListenerHolder` via reflection (the plugin's only use of internals) and `PackRequestHandler`
answers connections that start with `GET `/`HEAD`, passing everything else to Minecraft.

## Testing on a server

`./gradlew runServer` with `run/server.properties`: `online-mode=false`, `network-compression-threshold=-1`,
`pause-when-empty-seconds=-1`, whitelist off. Useful console commands: `railgun strike <x y z>` (no player needed),
`railgun stop`, `mspt`, `forceload add ...` (the plugin releases its tickets after a strike, so chunks unload).

- `tools/test_bot.py <host> <port> <name> <script>` is an offline protocol-777 client that sends exactly the vanilla
  packets for the controls (`hold`, `punch`, `hold_punch`, `hold_dig`, `look`, `wait`). Packet ids are
  version-specific and come from the vanilla data generator (`java -DbundlerMainClass=net.minecraft.data.Main -jar
  server.jar --reports`, then `reports/packets.json`). A bot that died must respawn, and standing in the air gets it
  kicked for flying.
- `tools/pack-validator/ValidatePack.java` parses the built pack with the real client code (needs the 26.3 client jar,
  icu4j and the server libraries on the classpath; it calls the item-model bootstraps individually because
  `ClientBootstrap` also initialises Vulkan).
- When moving to a new Minecraft version, update together: `paperApiVersion`, `nettyVersion`, `minecraftVersion`,
  `pack.mcmeta` format, the vanilla fallback in `items/crossbow.json`, and the packet ids in `test_bot.py`. Paper's
  and the client's behaviour can be checked with `javap` against the unobfuscated 26.x jars.

## CI and releases

`.github/workflows/build.yml` builds (and tests) on every push; `pull_request` runs only for forks, because branches of
this repo already build on push. A concurrency group cancels superseded builds except on the default branch. The
artifact upload is `continue-on-error` because the account's artifact quota was full. Dependabot
(`.github/dependabot.yml`) updates actions and Gradle dependencies weekly; `paper-api` and Netty are ignored because
they move with the Minecraft version. Releases: run the workflow manually with a `version` input (creates tag +
release via `gh`), or push a `v*` tag. Pushing tags from a Claude Code cloud session is rejected (HTTP 403); use the
manual run instead.
