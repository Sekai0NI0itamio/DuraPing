# DuraPing

A tiny, polished, **client-side** Minecraft Fabric mod that warns you in chat
when your tools, weapons, or armor are about to break.

- Warns at configurable durability thresholds (defaults: **10** and **3** remaining).
- Counts down the final configurable critical range (default: **3…2…1**).
- Monitors the **main hand, offhand, helmet, chestplate, leggings, and boots** —
  each slot tracked independently, each slot toggleable.
- One notification per threshold event — no chat spam, no repeats every tick.
- Everything configurable via **client-side `/duraping` commands** and a
  persistent JSON config file.

No server mod, no packets, no server setup. Install it on your client and it
works in singleplayer and on any server.

> Status: source-complete. There is **no official production release** on
> Modrinth, CurseForge, or GitHub Releases. The only sanctioned build is the
> `duraping-jar` artifact attached to a green GitHub Actions run (see
> [Downloading the CI build](#downloading-the-ci-build)).

## Requirements

- Minecraft Java Edition **1.21.1**
- Fabric Loader **≥ 0.16.14**
- Fabric API for 1.21.1 (tested pin: `0.102.0+1.21.1`)
- Java **21**

## Installation

1. Install Fabric Loader for Minecraft 1.21.1 (see <https://fabricmc.net/use/>).
2. Put a matching Fabric API 1.21.1 JAR in your `.minecraft/mods/` folder.
3. Download the DuraPing build (see below) and put `duraping-1.0.0.jar`
   (not the `-sources.jar`) into `.minecraft/mods/`.
4. Launch the Fabric 1.21.1 profile and join a world.

## What it looks like

With a diamond pickaxe at 10 durability remaining:

```text
[DuraPing] Diamond Pickaxe: 10 durability remaining!
```

At 3, 2, 1 remaining (critical color):

```text
[DuraPing] Diamond Pickaxe: 3 durability remaining!
[DuraPing] Diamond Pickaxe: 2 durability remaining!
[DuraPing] Diamond Pickaxe: 1 durability remaining!
```

Ordinary warnings use the warning color (default yellow); critical/countdown
warnings use the critical color (default red).

## Default thresholds and countdown behavior

- Ordinary thresholds: **10** and **3** (configurable, multiple allowed).
- Critical countdown: **3** — once an item is at 3 remaining, every further
  point warns: 3, then 2, then 1 (configurable 1–1000).
- Thresholds are detected by **crossing**, not equality: if one action takes an
  item from 11 to 8 remaining, the crossed threshold of 10 still warns.
- If an ordinary threshold and the countdown cover the same value (e.g.
  threshold 3 with countdown 3), you get **exactly one** critical message.
- If one action crosses several values at once, you get one message per crossed
  value, highest first, exactly once.
- Reaching 0 reports a single critical "broken" message, never negative numbers.
- Repairing an item re-arms its thresholds silently, so the next wear-down
  warns again. Swapping or moving items never warns by itself.

## Commands (all client-side)

The root command is `/duraping`. It works everywhere — singleplayer and
multiplayer — because it never touches the server. Run `/duraping` alone for
help plus a settings summary.

### Status and general control

| Command            | What it does                                              |
|--------------------|-----------------------------------------------------------|
| `/duraping`        | Help + current settings summary.                          |
| `/duraping status` | Monitoring on/off, thresholds, countdown, slots, messages.|
| `/duraping on`     | Enable monitoring.                                        |
| `/duraping off`    | Disable monitoring (clears pending warning state).        |
| `/duraping reload` | Reload `config/duraping.json` from disk and validate it.  |
| `/duraping reset`  | Restore all defaults (and save).                          |

### Thresholds

```text
/duraping threshold add 10
/duraping threshold remove 10
/duraping threshold list
/duraping threshold clear
/duraping countdown 3
```

- Multiple ordinary thresholds are supported, kept sorted and duplicate-free.
- Values must be 0–1000000; the countdown must be 1–1000. Out-of-range input
  is rejected by the command itself with a friendly message.
- `threshold clear` removes ordinary thresholds but keeps the critical
  countdown intact.

### Equipment slots

```text
/duraping slot                  # show all six slots
/duraping slot mainhand          # show one slot
/duraping slot mainhand on
/duraping slot helmet off
```

Slot names: `mainhand`, `offhand`, `helmet`, `chestplate`, `leggings`, `boots`.
Disabling a slot clears its tracking state immediately.

### Message configuration

```text
/duraping message                                  # show message settings
/duraping message prefix [DuraPing]                # set the prefix text
/duraping message showprefix on                    # show or hide the prefix
/duraping message itemname off                     # include the item name or not
/duraping message warncolor YELLOW                 # ordinary-warning color
/duraping message critcolor RED                    # critical/countdown color
```

Colors accept any `net.minecraft.util.Formatting` color name
(`BLACK`, `DARK_BLUE`, `GOLD`, `AQUA`, `RED`, `YELLOW`, `WHITE`, …).
Unknown names are rejected with a hint.

Every change applies immediately **and** is saved to the config file.

## Configuration file

Location: `.minecraft/config/duraping.json` (created with defaults on first run).

```json
{
  "enabled": true,
  "thresholds": [3, 10],
  "countdownThreshold": 3,
  "slots": {
    "mainhand": true,
    "offhand": true,
    "helmet": true,
    "chestplate": true,
    "leggings": true,
    "boots": true
  },
  "messagePrefix": "[DuraPing]",
  "showPrefix": true,
  "includeItemName": true,
  "warningColor": "YELLOW",
  "criticalColor": "RED"
}
```

Editing by hand: stop Minecraft (or run `/duraping reload` afterwards).
Missing fields fall back to defaults; invalid values are replaced by defaults;
a malformed file is preserved as `duraping.json.invalid` (never silently
overwritten) and the mod keeps running on defaults. Saves are atomic
(temp file + move), so an interrupted write cannot corrupt the file.

## Anti-spam and item tracking

- Each of the six slots has its own tracking state: last remaining value,
  item fingerprint, and which thresholds/countdown values already warned.
- Warnings fire only when durability **decreases across** a threshold or
  countdown value — never every tick, never for standing still.
- Items are fingerprinted by item id + max durability + display name + count
  (damage itself is excluded, otherwise every hit would look like a new item).
- Equipping, swapping, or moving an item only sets a new baseline — no warning
  for the move itself, and no history carried to another slot.
- Repairs re-arm thresholds silently.

## Known limitations

- Two completely identical items (same type, name, count) sharing one slot are
  treated as the same item — Minecraft offers no stable identity for that case.
  Equipping an already-low item therefore warns on its *next* durability loss,
  not on equip.
- A single action crossing several values emits one message per crossed value
  (bounded, once each) rather than a single coalesced message.
- Creative and spectator modes are skipped entirely (no warnings).
- Unbreakable/undamageable items and empty slots are ignored.
- Modded damageable items work wherever they expose standard durability
  (`isDamageable`/`getMaxDamage`/`getDamage`); anything exotic is ignored
  instead of crashing.
- No GUI config screen, no HUD, no sounds — chat warnings only, by design.

## Developing

- Toolchain: Minecraft 1.21.1, Yarn `1.21.1+build.3`, Loader `0.16.14`,
  Fabric API `0.102.0+1.21.1`, Loom `1.7.4`, Gradle `8.10.2`, Java 21.
- **All builds and tests run exclusively in GitHub Actions.** Never run
  `./gradlew` locally; edit files, commit, push, and read the CI logs.
- Pure logic (`logic/`, config validation, message wording) has JUnit 5 tests
  under `src/test/java/com/duraping/`. Minecraft-dependent behavior is
  intentionally not integration-tested in CI (no game launch); that limitation
  is honest, not a gap to paper over.
- Version-specific code is isolated in `monitor/` (slot polling),
  `notify/ChatNotifier` (chat delivery), `command/` (client commands), and
  `client/` (event wiring); thresholds, state, wording, and config are plain
  Java with no Minecraft imports, which keeps future porting manageable.

## Downloading the CI build

1. Open the repository's **Actions** tab → the latest green `build` run.
2. Download the **`duraping-jar`** artifact (`duraping-1.0.0.jar`).
3. Download **`duraping-test-reports`** on the same run for the JUnit reports.

No release has been published; anything outside these CI artifacts is not an
official build.

## License

MIT — see [LICENSE](LICENSE).
