# MobWizardry - Mobs using Magics!

MobWizardry attaches Iron's Spellbooks spellcasting AI to existing mobs — fully config-driven. No new mobs are added: any vanilla or modded mob becomes a spellcaster when it carries a configured tag.

- **Target:** Minecraft Forge 1.20.1 (47.4.10)

## How it works

1. Each preset in `config/mobwizardry/presets.json` defines:
   - the entity tag that activates it (`requiredTag`)
   - the wizard type (`wizardType`: `ranged` or `close`), movement speed and cast cadence
   - equipment, attribute overrides and a full mana pool
   - attack / defense / movement / support / escape spell kits
   (Boss behavior — name, color, health-based phases, day/night spawn weights — is defined
   separately in `config/mobwizardry/bosses.json`, keyed by preset name.)
2. When a mob joins the world carrying the required tag, MobWizardry:
   - equips the configured gear and sets attributes/mana,
   - attaches a real Iron's Spellbooks `WizardAttackGoal` (wrapped behind a live tag check),
   - lets the mob cast its kit under the appropriate conditions.
3. Adding or removing the tag at runtime enables/disables the AI immediately — no restart needed.

## Installation

1. Make sure the **required** mods are installed on the **server**:

   **Required:**
   - Forge 1.20.1 (47.4.10)
   - Iron's Spells 'n Spellbooks 1.20.1-3.16.2
   - Iron's Spellbooks' own required libraries (installed automatically with it): geckolib, curios, playeranimator, irons_lib

2. Drop `mobwizardry-1.20.1-1.0.2.jar` into the server's `mods/` folder (the same folder all your other mods live in):
   ```
   <server>\mods\mobwizardry-1.20.1-1.0.2.jar
   ```
3. Start the server. On first launch the mod writes a default config.

This mod is server-side logic; clients do not need it installed.

### Optional but supported addons

The following spell addons are **not required**, but when installed their spells can be used in presets — just reference their spell IDs in the config. If an addon is missing, its spells are skipped automatically (logged and removed at load), no crash. Each addon's own extra dependencies are the player's responsibility.

| Addon | Tested version | Notes |
|---|---|---|
| T.O Magic 'n Extras | 6.3.0 | spells, weapons, bosses (its addons also work) |
| BielGG's Spells Addon | 1.3-hotfix | also fixes T.O / Cataclysm compatibility |
| Cataclysm: Spellbooks | 1.2.9 | |
| GTBC's Geomancy Plus | 2.0.0 | needs Mowzie's Mobs + GTBC's SpellLib |
| Hazen 'N Stuff | 1.1.2 (watered-down-edition) | |
| Ice and Fire: Spellbooks | 2.3.2 | needs Ice and Fire: Dragons |
| Legendary Spellbooks | 0.3.2-hotfix | needs Legendary Monsters |
| Magic From The East | 1.0.0b | |
| Somake Spells | 1.0.8 | |
| Wind's Spellbooks | 1.0.3 | |
| Apprentice's Codex | 0.9.6 | |

## Configuration

Files: `config/mobwizardry/presets.json` (wizard presets) and
`config/mobwizardry/bosses.json` (boss behavior + natural-spawn settings). Both are written on
first launch and re-read on `/mobwizardry reload`.

### Beginner's guide to the settings

Think of the config as a **list of "wizard job applications"**. Each block is one preset — a set of instructions for turning a creature into a wizard. You can have as many presets as you want; each one has a different tag so it never interferes with the others.

Here is a plain-English explanation of every setting:

- **`requiredTag`** — the magic word that *turns the creature on*. A mob only gets its wizard AI while it carries this tag (you apply the tag with the commands below). Each preset needs a unique tag. The mob type is chosen at summon time — the preset itself is not limited to any creature type.
- **`wizardType`** — how the wizard fights. `ranged` (default) keeps distance and casts from afar; `close` charges in, casts point-blank, keeps a ~5-block standoff, and buffs while engaging. See below.
- **`team`** — *optional* team name. Mobs carrying presets with the same team name can never target, retaliate against, or hurt each other (even through spell splash). Leave it out or empty for a mob with no team. Example: give undead mobs `"team": "undead"` and human mobs `"team": "human"` so undead never fight undead and humans never fight humans, while the two groups still fight each other.
- **`faction`** — *optional* (`enemy` or `friendly`, default `enemy`). **`enemy`** wizards act like hostile mobs (a Wizard NPC hunts players, villagers and iron golems). **`friendly`** wizards never attack players or villagers, but they **hunt hostile mobs and enemy-faction wizards** (like a guard), fight back when hurt, and still cast spells. Best used with the Wizard NPC below.
- **`skin`** — *optional* skin name for the Wizard NPC (a file in the skins folder, e.g. `"skin": "alex"`). Leave it out to get a random skin per spawned NPC.
- **`speed`** — how fast the mob moves while casting. `1.0` is normal walking speed; bigger = faster.
- **`castInterval`** — the minimum number of ticks between cast attempts (20 ticks = 1 second). Smaller = casts more often.
- **`movementDistanceOffset`** — how much *earlier* the wizard uses its movement spell (the teleport/dash spells like `blood_step`) to jump closer to its target. Measured in blocks; it is subtracted from the spell's range. Default `5.0`.
  - Example with spells that reach 20 blocks: old behavior = the wizard only jumps when the target is **20+ blocks** away; with `5.0` it jumps when the target is **15+ blocks** away (5 blocks sooner).
  - A **bigger** number = the wizard repositions sooner (the target can't get as far away before the wizard jumps). `0` = the old "wait until out of range" behavior.
  - It only moves the *trigger point* — it does **not** change how far the teleport spell itself jumps.
  - Negative numbers are ignored (treated as `0`), and the trigger never drops below 2 blocks, so the wizard won't teleport when the target is right next to it.
  - Want exact distances instead of the derived ones? Set `movementStartDistance` / `movementFarDistance` (see below).
The three movement distances work on one line of target distance:

```
0 ── movementTooCloseDistance ── movementStartDistance ── movementFarDistance ── ∞
   (too close: back away)       (normal fight zone)     (far zone)             (too far: jump closer)
```

- **`movementFarDistance`** — target **farther than this** → strong movement-spell desire (the
  wizard casts its movement spell, e.g. `blood_step`, to jump closer). Default: derived from the
  spell range (`range − movementDistanceOffset`).
- **`movementStartDistance`** — target **between** `movementStartDistance` and `movementFarDistance`
  → the desire **starts ramping up** (from none at `start` to strong at `far`). It's the beginning
  of the "far" zone, **not** the "close" zone. Default: derived (`0.75 × (range − movementDistanceOffset)`).
- **`movementTooCloseDistance`** (default `5.0`, also the close-wizard standoff) — the one that
  handles **close**: target **closer than this** → the wizard uses its movement spell to **back
  away / reposition** instead of standing point-blank. Set `0` to disable.
- A target in the normal fight zone (between `movementTooCloseDistance` and
  `movementStartDistance`) produces no distance-driven movement desire — the wizard attacks and
  strafes instead.
- Worked example (spells reach 20 blocks, `movementDistanceOffset: 5` → `start ≈ 11`, `far = 15`):
  target at 4 blocks → back away; at 8 blocks → normal fight; at 13 blocks → ramping desire; at
  16 blocks → strong desire, casts `blood_step` to jump closer.
- Set `movementStartDistance` / `movementFarDistance` explicitly to take full control instead of
  the derived defaults (e.g. `"movementStartDistance": 15.0, "movementFarDistance": 20.0`); those
  override `movementDistanceOffset`.
- **`retaliationChance`** — when a wizard that is already fighting gets hit by someone else (a player, another wizard, a hostile mob), the chance (0–1) that it switches to that attacker instead of staying on its current target. Default `0.4` (40%). An idle wizard with no target always retaliates (100%).
- **`equipment`** — what gear the mob wears. A slot name maps to an item ID; the mob puts the item on and it never drops. All six equipment slots are supported:
  - `mainhand` — the weapon/staff hand (e.g. `irons_spellbooks:blood_staff`)
  - `offhand` — the other hand (e.g. a shield)
  - `head` / `helmet` — e.g. `minecraft:diamond_helmet`
  - `chest` / `chestplate` — e.g. `minecraft:diamond_chestplate`
  - `legs` / `leggings` — e.g. `minecraft:diamond_leggings`
  - `feet` / `boots` — e.g. `minecraft:diamond_boots`

  Example — a fully-geared wizard:
  ```json
  "equipment": {
    "mainhand": "irons_spellbooks:blood_staff",
    "offhand": "minecraft:shield",
    "head": "minecraft:diamond_helmet",
    "chest": "minecraft:diamond_chestplate",
    "legs": "minecraft:diamond_leggings",
    "feet": "minecraft:diamond_boots"
  }
  ```
  (Slot synonyms: `hand`/`main_hand` = mainhand, `off_hand` = offhand, `chestplate` = chest, `leggings` = legs, `helmet` = head, `boots` = feet.)
- **`attributes`** — the mob's magic stats. Examples: `irons_spellbooks:max_mana` (mana pool size), `irons_spellbooks:mana_regen` (mana per second), `irons_spellbooks:spell_power` (spell damage multiplier). You can also override **vanilla attributes** — they use the `minecraft:generic.*` namespace, e.g. `minecraft:generic.max_health` for max health, `minecraft:generic.armor`, `minecraft:generic.attack_damage`, `minecraft:generic.movement_speed`. After applying the overrides the mob refills to full health, so a boosted `max_health` spawns it at full HP. Example: `"attributes": { "irons_spellbooks:max_mana": 100, "minecraft:generic.max_health": 40 }` gives a 40-HP wizard.
- **`spells`** — its spell kit, split into five categories (see below). Each spell is written as `{ "id": "mod:spell_id", "level": 1 }`. A support spell may also set `"emergency": true` — see the support category.

Spell categories:
- **`attack`** — cast in combat against the target.
- **`defense`** — cast only while the caster is actually **being attacked** (recently hurt). Tip: any spell works here — put `irons_spellbooks:shield` for a classic barrier, or put an offensive spell like `irons_spellbooks:fireball` to make the caster retaliate when it gets hit.
- **`movement`** — cast when the target is **far away / out of spell range** to close the gap (e.g. `irons_spellbooks:blood_step`, `irons_spellbooks:teleport`).
- **`support`** — self-aid spells, cast when the caster is hurt or below half health. Good options: `irons_spellbooks:heal`, `irons_spellbooks:greater_heal` (health), `irons_spellbooks:fortify` (armor), `irons_spellbooks:charge` (speed), `irons_spellbooks:heartstop`. Note: there is no "mana regen" spell in Iron's Spells 'n Spellbooks — mana recovery is the `irons_spellbooks:mana_regen` attribute, so give a support caster that attribute as well. Balance: support casts are **chance-gated** (up to ~55% per cast attempt, scaling with missing health) and **cooldown-limited** (at most once every 7 seconds), so a dying caster can't heal-spam itself to immortality. Smart healing: mark a support spell `"emergency": true` (e.g. on `heal`/`greater_heal`) and, when the caster drops below 30% health, support casts will always pick one of those emergency heals instead of randomly wasting the cast on a buff like `fortify`.
- **`escape`** — repositioning spells cast only when the caster is **critically low** (below 30% health) **and** has recently been attacked, to retreat from danger (e.g. `irons_spellbooks:teleport`). Escape shares a 100-tick survival cooldown with emergency heals: after an escape, a heal can't land inside the same window, and a critical heal likewise blocks a follow-up escape — so the two can't chain together. It is chance-gated (~35% per eligible cast attempt) so a wizard doesn't teleport-spam, and it is checked before the weighted category pick, so it always wins over attack/movement while its conditions hold.

### Wizard types

The `wizardType` field chooses how the wizard fights:

- **`ranged`** (default) — keeps distance and casts from afar. Uses the escape kit to retreat
  when critically low and recently attacked (shares a 100-tick survival cooldown with
  emergency heals). This is the classic behavior.
- **`close`** — charges in and stays engaged. It always advances toward the target while
  circling (never backs away), casts its attack kit point-blank, keeps a ~5-block standoff, and
  casts its support buffs (`fortify`, `charge`, attack damage) while engaging even at full
  health. It **ignores the `escape` kit** — it doubles down instead of running. Buffs stay
  chance-gated and cooldown-limited, so a close wizard can't spam them.

Example close preset:
```json
{
  "wizard_close": {
    "requiredTag": "wizard_close",
    "wizardType": "close",
    "speed": 1.2,
    "castInterval": 50,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 80
    },
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_missile", "level": 1 }
      ],
      "support": [
        { "id": "irons_spellbooks:heal", "level": 1, "emergency": true },
        { "id": "irons_spellbooks:fortify", "level": 1 }
      ],
      "escape": []
    }
  }
}
```

### Mana explained

**Mobs don't spend mana to cast.** MobWizardry casts with Iron's Spellbooks' `CastSource.MOB`, which bypasses mana costs and cooldowns entirely — a wizard can keep casting regardless of its mana bar, and there is **no `mana` config field** for presets (it was removed).

The `irons_spellbooks:max_mana` and `irons_spellbooks:mana_regen` attributes are still accepted under `attributes` — they control the mana pool size and regeneration for anything that does read mana, but they never gate casting.

### Example config (two presets)

This is the default config the mod writes on first launch, minus the `wizard_boss` example (its
boss behavior now lives in `bosses.json` — see [Boss fights](#boss-fights-220) for the full
config). Copy it and change the values to taste.

```json
{
  "wizard": {
    "requiredTag": "wizard",
    "wizardType": "ranged",
    "speed": 1.15,
    "castInterval": 60,
    "castIntervalMax": 0,
    "movementStartDistance": 0,
    "movementFarDistance": 0,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff",
      "head": "irons_spellbooks:wandering_magician_helmet",
      "chest": "irons_spellbooks:wandering_magician_chestplate",
      "legs": "irons_spellbooks:wandering_magician_leggings",
      "feet": "irons_spellbooks:wandering_magician_boots"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 100,
      "irons_spellbooks:mana_regen": 3,
      "irons_spellbooks:spell_power": 1.5
    },
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_missile", "level": 1 },
        { "id": "irons_spellbooks:fireball", "level": 1 }
      ],
      "defense": [
        { "id": "irons_spellbooks:shield", "level": 1 }
      ],
      "movement": [
        { "id": "irons_spellbooks:blood_step", "level": 1 }
      ],
      "support": [
        { "id": "irons_spellbooks:heal", "level": 1, "emergency": true }
      ],
      "escape": [
        { "id": "irons_spellbooks:teleport", "level": 1 }
      ]
    }
  },
  "wizard_lite": {
    "requiredTag": "wizard_lite",
    "wizardType": "ranged",
    "speed": 1.1,
    "castInterval": 80,
    "castIntervalMax": 0,
    "movementStartDistance": 0,
    "movementFarDistance": 0,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff",
      "head": "irons_spellbooks:wandering_magician_helmet",
      "chest": "irons_spellbooks:wandering_magician_chestplate",
      "legs": "irons_spellbooks:wandering_magician_leggings",
      "feet": "irons_spellbooks:wandering_magician_boots"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 60,
      "irons_spellbooks:mana_regen": 2,
      "irons_spellbooks:spell_power": 1.0
    },
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_arrow", "level": 1 }
      ],
      "defense": [],
      "movement": [],
      "support": [],
      "escape": []
    }
  },
  "wizard_range": {
    "requiredTag": "wizard_range",
    "wizardType": "ranged",
    "speed": 1.15,
    "castInterval": 60,
    "castIntervalMax": 100,
    "movementStartDistance": 15.0,
    "movementFarDistance": 20.0,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff",
      "head": "irons_spellbooks:wandering_magician_helmet",
      "chest": "irons_spellbooks:wandering_magician_chestplate",
      "legs": "irons_spellbooks:wandering_magician_leggings",
      "feet": "irons_spellbooks:wandering_magician_boots"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 100,
      "irons_spellbooks:mana_regen": 3,
      "irons_spellbooks:spell_power": 1.5
    },
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_missile", "level": 1 },
        { "id": "irons_spellbooks:fireball", "level": 1 }
      ],
      "defense": [
        { "id": "irons_spellbooks:shield", "level": 1 }
      ],
      "movement": [
        { "id": "irons_spellbooks:blood_step", "level": 1 }
      ],
      "support": [
        { "id": "irons_spellbooks:heal", "level": 1, "emergency": true }
      ],
      "escape": [
        { "id": "irons_spellbooks:teleport", "level": 1 }
      ]
    }
  },
  "wizard_close": {
    "requiredTag": "wizard_close",
    "wizardType": "close",
    "speed": 1.2,
    "castInterval": 50,
    "castIntervalMax": 0,
    "movementStartDistance": 0,
    "movementFarDistance": 0,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff",
      "head": "minecraft:iron_helmet",
      "chest": "minecraft:iron_chestplate",
      "legs": "minecraft:iron_leggings",
      "feet": "minecraft:iron_boots"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 80,
      "irons_spellbooks:mana_regen": 3,
      "irons_spellbooks:spell_power": 1.5
    },
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_missile", "level": 1 },
        { "id": "irons_spellbooks:fireball", "level": 1 }
      ],
      "defense": [
        { "id": "irons_spellbooks:shield", "level": 1 }
      ],
      "movement": [
        { "id": "irons_spellbooks:blood_step", "level": 1 }
      ],
      "support": [
        { "id": "irons_spellbooks:heal", "level": 1, "emergency": true },
        { "id": "irons_spellbooks:fortify", "level": 1 },
        { "id": "irons_spellbooks:charge", "level": 1 }
      ],
      "escape": []
    }
  }
}
```

### Using spells from addon mods

Any installed addon's spells can be used just like Iron's Spellbooks spells — the mod looks them up in the same shared spell list at load time. For example, if you have T.O Magic 'n Extras installed:

```json
"spells": {
  "attack": [
    { "id": "traveloptics:halberd_horizon", "level": 1 }
  ]
}
```

If the addon isn't installed, that spell is logged as "not found in Iron's Spellbooks registry - removed" and simply skipped — **no crash, no error screen**.

### Validation on load

At server start (and on `/mobwizardry reload`) every entry in `presets.json` and `bosses.json` is
validated against the real registries:

- unknown spell IDs, item IDs or attribute IDs are logged and removed,
- spell levels are clamped to the spell's max level,
- a warning is logged when a spell's intrinsic cooldown exceeds `castInterval`,
- boss config issues (unknown spawn entity, invalid name color, weights/health percents out of
  range, bad spawn settings like a negative interval, overlapping distances or a negative glow
  seconds, combo steps with unknown spells or invalid categories, phases referencing unknown
  spells) are logged and fixed or disabled,
- a boss key in `bosses.json` with no matching preset, a preset with an inline `boss` block, a
  leftover top-level `_spawnSettings` in `bosses.json`, and a `_spawnSettings` left in
  `presets.json` are all warned about.

Invalid presets fail loudly in the log instead of silently doing nothing.

## The Wizard NPC (2.0.0)

A new entity, **`mobwizardry:wizard`**, that looks like a player and uses the same preset system:

- **Spawning** — the intended way is `/mobwizardry summon <preset> mobwizardry:wizard`. A vanilla
  `/summon mobwizardry:wizard` also works and automatically applies the default `wizard` preset
  (ranged, enemy faction). There's also a spawn egg in the MobWizardry creative tab.
- **Skins** — the NPC renders with a player-shaped model. Skin files are 64x64 PNGs placed in
  `assets/mobwizardry/textures/entity/wizard/skins/` (shipped with the mod or a server-installed
  resource pack). Each spawned NPC picks a random skin; a preset's `"skin": "name"` field forces
  a specific one. Missing files fall back to the vanilla Steve texture.
- **Faction** — the preset's `faction` field decides behavior:
  - `enemy` — hunts players, villagers and iron golems like a hostile mob.
  - `friendly` — never attacks on its own; it only fights back when hurt (and still casts).
- **Teams** — same-team NPCs can never hurt/target each other, so you can build friendly and
  enemy groups that coexist.

## Boss fights (2.2.0)

Any wizard preset can become a **boss** by adding a `boss` entry for it in
`config/mobwizardry/bosses.json` (keyed by the preset's name). When a mob with a boss preset
joins the world (summoned, wizardified, spawned by the natural spawner, or loaded from a save):

- a **lightning bolt** strikes it (visual only — the boss takes no damage from it),
- the chat announces `NAME has arrived.`,
- it wears a **colored name tag** (no boss bar), and
- its first phase becomes active.

The file's shape is:

```json
{
  "bosses": {
    "wizard_boss": {
      "enabled": true,
      "name": "...",
      "spawnSettings": { ... },
      "phases": [ ... ]
    }
  }
}
```

The `bosses` map keys are **preset names** from presets.json — a boss key with no matching preset
is ignored (and warned in the log). A preset that still carries its `boss` block inline in
presets.json keeps working but is warned to migrate; the bosses.json entry wins when both exist.

### The `boss` block

| Field | Meaning |
|---|---|
| `enabled` | set `true` to make this preset a boss. |
| `name` | the boss's display name (used for the name tag and chat). |
| `nameColor` | the name tag / chat color — a Minecraft color name like `red`, `dark_red`, `gold`, `aqua`, or a hex color like `#FF5555`. Default `red`. |
| `spawnEntity` | which entity type the natural spawner uses for this boss (e.g. `mobwizardry:wizard`, `minecraft:zombie`). Commands still let you pick any mob type. |
| `spawnSettings` | this boss's own natural-spawn controls (below) — each boss decides whether and how often it naturally spawns, its own concurrent cap and its own spawn distance. |
| `daySpawnWeight` | how likely this boss is to be naturally spawned during the day. `0` = never by day. |
| `nightSpawnWeight` | how likely this boss is to be naturally spawned at night. `0` = never by night. |
| `phases` | the list of health-based phases (below). |
| `combos` | the list of scripted attack sequences (below) — replaces the boss's normal random attack casting. |

### Phases

A phase has a **number**, a **healthPercent** (the health ratio, as a percentage, at or below
which the boss enters the phase), an optional **message**, and a **spells** kit in the same
five-category format as the preset's own `spells` block. Each phase lists the **full** arsenal
the boss should have from that point on — so phase 2's `attack` list includes phase 1's spells
plus any new ones. When the boss's health drops to a phase's threshold, the kit is swapped in and
`[NAME] message` is broadcast (the name in red).

```json
"phases": [
  {
    "number": 1,
    "healthPercent": 100,
    "message": "So you dare face me?",
    "spells": {
      "attack": [ { "id": "irons_spellbooks:fireball", "level": 1 } ],
      "defense": [], "movement": [], "support": [], "escape": []
    }
  },
  {
    "number": 2,
    "healthPercent": 50,
    "message": "Fool! Now you face my true power!",
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:fireball", "level": 1 },
        { "id": "irons_spellbooks:magic_missile", "level": 1 }
      ],
      "defense": [ { "id": "irons_spellbooks:shield", "level": 1 } ],
      "movement": [ { "id": "irons_spellbooks:blood_step", "level": 1 } ],
      "support": [ { "id": "irons_spellbooks:heal", "level": 1, "emergency": true } ],
      "escape": []
    }
  }
]
```

`healthPercent` is the threshold for *entering* the phase: phase 2 above activates the moment the
boss is at or below 50% health. Phases are sorted by `healthPercent` descending at load, so the
phase with the highest threshold (usually `100`) is the boss's starting kit. A boss with no
`phases` is still a named boss (lightning + name tag + arrival) but never changes kits.

### Combo presets (2.3.0)

A boss with `combos` replaces its **attack** casting with scripted sequences; defense, movement,
support and escape remain the normal wizard behavior (the preset's spells, triggered as usual).
While fighting, the boss randomly picks one combo, casts its steps **in list order** at their
offsets from the combo start, then waits the combo's `castInterval` before starting another
random combo.

```json
"combos": [
  {
    "castInterval": 40,
    "steps": [
      { "category": "attack",  "spell": "irons_spellbooks:magic_missile", "level": 1, "castAfterTicks": 30 },
      { "category": "attack",  "spell": "irons_spellbooks:magic_missile", "level": 1, "castAfterTicks": 10 },
      { "category": "attack",  "spell": "irons_spellbooks:fireball",      "level": 1, "castAfterTicks": 60 },
      { "category": "escape",  "spell": "irons_spellbooks:blood_step",    "level": 1, "castAfterTicks": 120 }
    ]
  }
]
```

| Field | Meaning |
|---|---|
| `castInterval` | ticks between this combo's repetitions (20 ticks = 1 second; `0` = the preset's `castInterval`, so 40-60 ticks ≈ 2-3 seconds). |
| `steps` | the combo, executed top-to-bottom. |
| `step.category` | informational — `attack`/`defense`/`support`/`movement`/`escape`. |
| `step.spell` | the spell to cast (same id format as preset spells). |
| `step.level` | cast level (clamped to the spell's max). |
| `step.castAfterTicks` | how many ticks after the combo started this step casts; if the boss is still finishing an earlier cast, it casts as soon as it is free. |

Steps with unknown spells are skipped at load; a combo with no usable steps ends immediately and
the next combo follows.

### Natural spawning (per-boss `spawnSettings`)

The mod can spawn bosses in the world on a timer. Each boss controls its own natural spawning
through its `spawnSettings` block — there is no global setting:

```json
"spawnSettings": {
  "enabled": true,
  "attemptIntervalSeconds": 300,
  "maxActiveBosses": 3,
  "minDistanceFromPlayer": 24,
  "maxDistanceFromPlayer": 48,
  "despawnOnTimeChange": true,
  "spawnGlowSeconds": 60
}
```

| Field | Meaning |
|---|---|
| `enabled` | `false` = this boss never naturally spawns (summon/wizardify still work). |
| `attemptIntervalSeconds` | minimum seconds between this boss's natural spawn attempts (300 = every 5 minutes). |
| `maxActiveBosses` | how many of *this* boss may be alive at once before it stops rolling. |
| `minDistanceFromPlayer` / `maxDistanceFromPlayer` | this boss spawns at a safe spot between these distances from a random online player. |
| `despawnOnTimeChange` | `true` (default) = a boss that **naturally** spawned disappears when the day/night phase flips (a night-spawned boss vanishes at day, a day-spawned boss vanishes at night). Bosses summoned with `/mobwizardry summon`/`boss` are never affected. |
| `spawnGlowSeconds` | how long the boss glows after arriving so players can see it (default 60; `0` disables the glow). |

Each boss schedules its own spawn attempts: every tick, a boss whose `enabled` is true, whose
day/night weight for the current time is above `0`, whose live count is below its own
`maxActiveBosses` and whose interval has elapsed joins a weighted pool; one winner is spawned
using that boss's own distances. A boss with `daySpawnWeight` and `nightSpawnWeight` both at `0`
(or `spawnSettings.enabled` false) never naturally spawns.

### On arrival

When a boss is bossified (naturally spawned or summoned):

- a **lightning bolt** strikes it (visual only), the chat announces `NAME has arrived.`, and it
  gets its colored name tag and first phase,
- it **immediately targets a random attackable online player** and navigates toward them
  (multiplayer = random among them); with no attackable players it stays idle — after that it
  fights with the exact same wizard AI as any other wizard.

### Example boss preset

The default config ships a complete three-phase example, `wizard_boss` (Aetheron, the Crimson
Archon): the plain `wizard_boss` wizard preset lives in presets.json and its boss behavior in
bosses.json. To fight one: `/mobwizardry boss wizard_boss mobwizardry:wizard`, or let the night
spawner do its job (nightSpawnWeight 20 vs daySpawnWeight 5).

## Admin commands

Requires permission level 2. (`help` and `list` are available to everyone.)

| Command | Description |
|---|---|
| `/mobwizardry help` | Shows this list of commands with a short explanation for each. |
| `/mobwizardry summon <preset> <mobType> [pos]` | Spawns a mob of `<mobType>` with the preset applied (tag, equipment, attributes, wizard AI) immediately. The mob type is not restricted by the preset. |
| `/mobwizardry boss <preset> <mobType> [pos]` | Summons a boss — like `summon`, but the preset must be boss-enabled (a `boss` entry in `bosses.json`). The boss is struck by lightning, named and announced. |
| `/mobwizardry wizardify <preset> [radius] [pos]` | Turns every mob within `radius` (1–64, default 16) of you (or of `pos`) into wizards — tag, equipment, attributes and wizard AI. Non-mob entities in range are skipped and reported. |
| `/mobwizardry unwizardify <preset> [radius] [pos]` | Removes the tag from all wizards in range — their AI deactivates on the next tick. |
| `/mobwizardry reload` | Re-reads and re-validates `presets.json` and `bosses.json` without restarting. |
| `/mobwizardry list [page]` | Lists loaded presets in a readable, colored format — 5 per page, with clickable previous/next arrows. |

### Examples

```
/mobwizardry help
/mobwizardry summon wizard minecraft:zombie
/mobwizardry summon wizard minecraft:zombie 100 64 100
/mobwizardry boss wizard_boss mobwizardry:wizard
/mobwizardry boss wizard_boss mobwizardry:wizard 100 64 100
/mobwizardry wizardify wizard 10
/mobwizardry wizardify wizard 20 100 64 100
/mobwizardry unwizardify wizard 10
/mobwizardry reload
/mobwizardry list
/mobwizardry list 2
```

## Testing your preset in-game

1. Start the server with the mod installed.
2. Run `/mobwizardry list` — confirm your preset shows up (if not, check the log for validation errors).
3. Spawn one: `/mobwizardry summon <preset> minecraft:zombie`
4. Observe the mob:
   - **attack** spells cast while it has a target in range,
   - **defense** spells cast while it is being attacked,
   - **movement** spells cast when the target is far / out of range,
   - **support** spells cast when it is hurt or below half health (or, for `close` wizards, while engaging),
   - **escape** spells cast when it is critically low and recently attacked (`ranged` only),
   - a **`close`** wizard advances, casts point-blank, keeps a ~5-block standoff, and never retreats,
   - cooldowns match the spell's own configured values.
5. For a **boss** preset (a `boss` entry in `bosses.json`):
   - summoning it (`/mobwizardry boss <preset> <mobType>`) strikes lightning, prints `NAME has arrived.` in chat, shows the colored name tag, glows for `spawnGlowSeconds`, and targets a random online player (idle if none),
   - deal damage until it crosses a phase's `healthPercent` — the phase message appears and its spell kit swaps (e.g. phase 2 gains the spells you listed there),
   - with `combos`, its attack spells come only from the randomly-selected combo sequence (steps cast in order at their tick offsets, then a gap before the next combo), while defense/movement/support/escape still trigger like a normal wizard,
   - with its `spawnSettings.enabled` true and a `daySpawnWeight`/`nightSpawnWeight` above 0, it should also appear near players over time (more often at night if the night weight is higher); with `despawnOnTimeChange` true it disappears when the time of day flips.
6. Tweak `presets.json` / `bosses.json` and run `/mobwizardry reload` — no server restart needed. Code changes (if any) require rebuilding the jar and restarting.

## Notes

- `CastSource.MOB` in Iron's Spellbooks does not consume mana or enforce its player cooldown system, so the goal's `castInterval` is the effective cast cadence; per-spell cooldowns are still respected as the source of truth.
- The `wizard` and `wizard_lite` presets in the default config are examples — copy them and change `requiredTag` and spell IDs to taste.
