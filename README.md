# Orbital Railgun für Paper

Ein reines Server-Plugin für **Paper 26.3**, das die Fabric-Mod
[Orbital Railgun von Mishkis](https://github.com/Mishkis/orbital-railgun) nachbaut.
Spieler brauchen **keine Mods**: Vanilla-Clients funktionieren vollständig. Ein optionales
Server-Resource-Pack liefert das originale 3D-Modell und die Übersetzungen nach.

> **Hinweis zur Lichtempfindlichkeit:** Wie das Original arbeitet das Plugin mit Lichtblitzen,
> Abdunkeln und Schwarzblenden. Alles davon lässt sich in der `config.yml` abschalten.

## Bedienung

1. Railgun herstellen (Rezept wie im Original) oder per `/railgun give <Spieler>` geben.
2. **Rechtsklick halten**, um zu zielen. Das Ziel wird rot markiert, und der Wirkungsradius
   erscheint als blauer Ring mit Fadenkreuz auf dem Boden. Die Aktionsleiste zeigt Koordinaten und
   Entfernung (bis 300 Blöcke).
3. **Linksklick**, während Rechtsklick gehalten wird, feuert.
4. Nach 35 Sekunden schlägt der Strahl ein. Alles im Zylinder mit Radius 24 verschwindet, vom
   Weltboden bis zum Himmel, Grundgestein eingeschlossen. Die Abklingzeit beträgt 120 Sekunden.

Rezept (geformt):

```
Glasscheibe   Netheritbarren  Smaragd
Netherstern   Netheritblock   Netheritbarren
Lapislazuli   Redstone        –
```

## Installation

Voraussetzungen: Paper 26.3 und Java 25.

1. `OrbitalRailgun-<version>.jar` nach `plugins/` kopieren.
2. Server starten. `plugins/OrbitalRailgun/config.yml` wird angelegt.
3. Optional das Resource-Pack einrichten (siehe unten).

Bauen aus dem Quellcode:

```bash
./gradlew build
```

Das Jar liegt danach unter `build/libs/`. Gradle lädt die Java-25-Toolchain bei Bedarf automatisch.

## Resource-Pack (optional)

Ohne Pack sieht die Railgun aus wie eine Armbrust mit violettem Namen, und die Texte erscheinen in
der unter `language` eingestellten Sprache. Mit Pack bekommt sie das originale 3D-Modell, die
15 Übersetzungen der Mod und einen grünen Visier-Rahmen beim Zielen.

1. In `config.yml` `resource-pack.enabled: true` setzen und den Server neu starten (oder
   `/railgun reload`). Das Plugin erzeugt `plugins/OrbitalRailgun/OrbitalRailgun-pack.zip` und
   schreibt den SHA-1-Hash ins Log.
2. Die Zip-Datei auf einen Webserver hochladen, der sie direkt ausliefert. Das Plugin hostet
   absichtlich nicht selbst, damit kein zusätzlicher Port nötig ist.
3. Die URL bei `resource-pack.url` eintragen. Ab jetzt bekommt jeder Spieler das Pack beim Betreten
   angeboten.

Eigene Inhalte, zum Beispiel Sounds, lassen sich über `plugins/OrbitalRailgun/pack-overrides/`
hinzufügen. Alles in diesem Ordner landet mit demselben Pfad im Pack und überschreibt vorhandene
Dateien, etwa `assets/orbital_railgun/sounds.json` und `assets/orbital_railgun/sounds/*.ogg`.
Die Mod selbst enthält keine Sounds; das Plugin nutzt Vanilla-Sounds.

Technisch überschreibt das Pack `assets/minecraft/items/crossbow.json` mit einer Auswahl nach
`custom_model_data`. Normale Armbrüste behalten ihr Vanilla-Modell, und Spieler ohne Pack sehen
nie eine fehlende Textur.

## Konfiguration

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `language` | `en_us` | Sprache der Texte für Spieler ohne Pack (`de_de`, `fr_fr`, … – alle 15 Sprachen der Mod) |
| `strike.radius` | `24` | Radius von Zerstörung und Schaden (1–64) |
| `strike.charge-ticks` | `700` | Zeit vom Schuss bis zum Einschlag (35 s) |
| `strike.cooldown-ticks` | `2400` | Abklingzeit (120 s), gilt für alle Railguns eines Spielers |
| `strike.max-range` | `300` | Zielentfernung in Blöcken |
| `strike.allowed-worlds` | `[]` | erlaubte Welten; leer bedeutet alle |
| `strike.pull` | `true` | Sog zum Zentrum in den letzten 15 s |
| `damage.enabled` / `damage.amount` | `true` / `100000` | Schaden beim Einschlag (Kugel mit dem Strike-Radius) |
| `damage.attribute-to-shooter` | `false` | `true`: Der Schütze gilt als Angreifer, PvP-Schutz greift |
| `destruction.enabled` | `true` | Blöcke entfernen |
| `destruction.destroy-bedrock` | `true` | auch Grundgestein entfernen |
| `destruction.skip-blocks` | `[]` | Blöcke, die nie entfernt werden |
| `destruction.blocks-per-tick` | `20000` | höchstens so viele Positionen pro Tick (für alle Strikes zusammen) |
| `destruction.max-millis-per-tick` | `10` | Zeitbudget pro Tick; der Abbau pausiert, bis der nächste Tick beginnt |
| `protection.fire-explode-events` | `true` | Schutz-Plugins über Explosions-Events einbinden (siehe unten) |
| `effects.*` | an | Partikel, Displays, Darkness, Schwarzblende, Blitz, Licht und Sounds einzeln schaltbar; `view-distance` legt fest, bis wohin man den Angriff sieht |
| `aim.release-timeout-ticks` | `8` | so lange ohne Rechtsklick-Paket gilt die Taste als losgelassen |
| `aim.slowdown` | `0.8` | Verlangsamung beim Zielen; erzeugt auch einen leichten Zoom (FOV) |
| `recipe.enabled` / `recipe.discover-on-join` | `true` | Rezept registrieren und im Rezeptbuch freischalten |
| `resource-pack.*` | aus | siehe oben; `required: true` trennt Spieler, die das Pack ablehnen |

Änderungen übernimmt `/railgun reload`. Strikes, die schon laufen, behalten ihre alten Einstellungen.

## Befehle und Rechte

| Befehl | Recht | |
|---|---|---|
| `/railgun give <Spieler>` | `orbitalrailgun.give` (OP) | Railgun geben |
| `/railgun reload` | `orbitalrailgun.admin` (OP) | Konfiguration und Pack neu laden |
| `/railgun strike <x y z>` | `orbitalrailgun.admin` | Strike ohne Railgun auslösen (zum Testen) |
| `/railgun stop` | `orbitalrailgun.admin` | alle laufenden Strikes sofort abbrechen |
| – | `orbitalrailgun.use` (alle) | Railgun benutzen |

`orbitalrailgun.admin` schließt `orbitalrailgun.give` ein.

## Schutz-Plugins

- Vor jedem Schuss feuert das Plugin ein abbrechbares `OrbitalStrikeEvent`
  (`io.github.justadev20.orbitalrailgun.api`). Andere Plugins können damit Strikes verbieten.
- Der Abbau läuft in Paketen von höchstens 256 Blöcken. Vor jedem Paket kommt ein
  `EntityExplodeEvent` mit dem Schützen als Quelle. Ist der Schütze offline oder kam der Strike
  von der Konsole, ist es ein `BlockExplodeEvent`. Schutz-Plugins entfernen geschützte Blöcke aus
  der Liste, so wie sie es auch bei TNT tun. CoreProtect und ähnliche Plugins protokollieren den
  Abbau als Explosion.
- Getestet mit **WorldGuard 7.0.20**: Das Flag `build` schützt vor Strikes von Spielern, die keine
  Mitglieder sind. `other-explosion deny` schützt vor allen Strikes, auch vor Konsolen-Strikes und
  solchen, deren Schütze offline gegangen ist.
- Plugins, die Explosionsschäden später wiederherstellen, wirken auch auf den Strike.

## Abweichungen von der Mod

**1:1 übernommen:**
- Rezept und Item: Name, EPIC-Seltenheit, Stapelgröße 1. Die Abklingzeit erscheint im
  Inventar, wie bei Vanilla-Items.
- Zielstrahl: 300 Blöcke, Blockumrisse, Flüssigkeiten werden ignoriert. Trifft der Strahl nichts,
  wird nicht gefeuert.
- Ablauf: 35 s Aufladung. Entity-Snapshot einer 500³-Box beim Schuss. Sog-Formel ab Sekunde 20.
  100 000 Schaden mit dem eigenen Schadenstyp `orbital_railgun:strike` und dessen Todesnachricht.
- Zerstörungsform: Zylinder x² + z² ≤ r² über die gesamte Welthöhe, Grundgestein eingeschlossen,
  ohne Drops. Am Rand laufen Nachbar-Updates, Wasser fließt also nach.
- Mehrere Strikes gleichzeitig sind möglich. Die Abklingzeit gilt für alle Railguns gemeinsam.

**Angenähert:**
- **Steuerung:** Ein Vanilla-Client verschluckt Linksklicks, solange er ein Item „benutzt“. Die
  Railgun hat deshalb keine Benutzen-Aktion. Den gehaltenen Rechtsklick erkennt der Server am
  Use-Paket, das der Client beim Halten alle 4 Ticks wiederholt. Das Loslassen merkt er etwa
  0,3 s verspätet. Liegt in der Nebenhand ein benutzbares Item (Schild, Essen), kann das die
  Erkennung stören.
- **Zoom:** Statt des Fernrohr-Zooms (×0,1) mit geglätteter Maus gibt es eine Verlangsamung, die
  das Sichtfeld auf etwa ×0,6 verengt. Es gibt keine Bogen-Pose und das HUD bleibt sichtbar.
- **Ziel-UI:** Statt des Shaders markiert ein rot leuchtendes `BlockDisplay` den Zielblock,
  sichtbar nur für den Schützen. Partikel zeichnen Ring und Kreuz aufs Gelände. Mit Pack kommt
  ein grüner Visier-Rahmen dazu; ohne Pack fehlen Rahmen, Scanlines und das rotierende Fadenkreuz.
- **Strike-Shader:** Partikelringe stellen die schrumpfende Kuppel dar. Sechs leuchtende
  Seelaternen-Displays sind die Satelliten, sie fliegen nach der Original-Formel hinaus und zurück.
  Dünne Display-Strahlen schießen in den Himmel. Der Hauptstrahl besteht aus rotierenden
  Glas-Displays. Dazu kommen Schockwellen-Ringe, Darkness und Blindness für Abdunkeln und
  Schwarzblende, Flash-Partikel für den Lichtblitz und clientseitige Lichtblöcke.
- **Zerstörung über mehrere Ticks:** Das Original entfernt rund 690 000 Blöcke in einem einzigen
  Tick. Das Plugin trägt von oben nach unten ab, innerhalb von Block- und Zeitbudget. Das Loch
  entsteht dadurch in etwa 2 s (r = 24) bzw. 8 s (r = 64) statt schlagartig. Container-Inhalte
  verschwinden direkt; im Original fallen sie als Items ins Void.
- **Schaden an Nicht-Lebewesen:** Item- und Erfahrungs-Entities werden entfernt (im Original
  zerstört sie der Schaden). Boote, Loren und Endkristalle werden nicht beschädigt, fallen aber
  ohnehin ins Loch.
- **Sichtweite:** Partikel sind bis `effects.view-distance` sichtbar, höchstens 512 Blöcke.
  Displays nur innerhalb der Entity-Tracking-Reichweite des Servers (`spigot.yml`). Der Shader
  reichte bis 2500 Blöcke. Zielen geht nur auf geladene Chunks, also abhängig von der
  `view-distance` des Servers.
- **Sounds:** Die Mod hat keine. Das Plugin untermalt den Ablauf mit Vanilla-Sounds.
- **Texte ohne Pack:** Spieler ohne Pack sehen die Sprache aus `language`; mit Pack übersetzt der
  Client in seine eigene Sprache.

**Nicht möglich ohne Client-Mod:**
- Chromatische Aberration, HUD ausblenden, echter Fernrohr-Zoom bei gleichzeitigem Linksklick und
  Post-Processing-Shader allgemein.
- Eintrag im Kreativ-Inventar (dafür gibt es `/railgun give`).

**Bewusst geändert:**
- Das Original vertraut dem Client: Das Schuss-Paket enthält eine beliebige Blockposition, der
  Server prüft weder Abklingzeit noch Item. Hier entscheidet der Server per eigenem Raytrace.
- Die Chunks des Zylinders bleiben vom Schuss bis zum Ende des Abbaus geladen.
- Im Original sind nur Entities betroffen, die beim Schuss in der Box waren; Spieler, die später
  dazukommen, sehen dort nichts. Hier sieht jeder in Reichweite die Effekte.

## Leistung

Gemessen auf einem lokalen Paper-26.3-Server (Build 227, Java 25, 4 Kerne):

| Szenario | längster Tick | Ø in der Abbauphase | Dauer des Abbaus |
|---|---|---|---|
| r = 24, Standard | 18,9 ms | 1–4 ms | ≈ 2 s |
| r = 64 (Maximum) | 24,8 ms | 11,7 ms | ≈ 8 s |
| Sog mit 148 Entities in der Box | 40 ms | – | – |

Die TPS blieben in allen Fällen bei 20,0. Die Sog-Mechanik des Originals beschleunigt jedes Entity
in der 500³-Box. Auf sehr belebten Servern kostet das Entity-Physik; mit `strike.pull: false`
lässt sie sich abschalten. Das Abbau-Tempo regeln `destruction.blocks-per-tick` und
`destruction.max-millis-per-tick`.

## Entwicklung und Tests

- `./gradlew runServer` startet einen Paper-26.3-Testserver in `run/` (über run-paper).
- `tools/test_bot.py` ist ein minimaler Offline-Client für das 26.3-Protokoll (777). Er sendet
  genau die Pakete eines Vanilla-Clients: Use-Paket alle 4 Ticks beim Halten, Punch-Paket bzw.
  `START_DESTROY_BLOCK` beim Linksklick. Damit sind Zielen, Feuern, Loslassen, Abklingzeit, Schaden
  und Todesnachricht automatisiert geprüft. Vorausgesetzt ist `online-mode=false` und
  `network-compression-threshold=-1`.
- `tools/convert_geckolib_model.py` erzeugt das Vanilla-Item-Modell aus dem GeckoLib-Modell der
  Mod. Die Umrechnung entspricht GeckoLibs Renderer: X gespiegelt, Rotationen um X und Y negiert,
  Box-UVs wie in Blockbench.
- `tools/pack-validator/ValidatePack.java` lädt das erzeugte Pack mit dem echten 26.3-Client-Code,
  ohne Grafikkarte. Geprüft werden `pack.mcmeta`, Item-Definition, Modell und Schrift.

## Lizenz und Credits

MIT, siehe [LICENSE](LICENSE). Spielidee, 3D-Modell, Textur, Icon und Übersetzungen stammen aus
[Orbital Railgun](https://github.com/Mishkis/orbital-railgun) von **Mishkis** (MIT). Die Formeln
der Effekte sind aus den Shadern der Mod abgeleitet.
