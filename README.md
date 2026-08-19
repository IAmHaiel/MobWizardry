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
   separately in `config/mobwizardry/bosses.json`, keyed by preset name. Raids/hordes of enemy
   wizards are defined in `config/mobwizardry/raids.json`.)
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

Files: `config/mobwizardry/presets.json` (wizard presets), `config/mobwizardry/bosses.json`
(boss behavior + natural-spawn settings) and `config/mobwizardry/raids.json` (raids/hordes). All
are written on first launch and re-read on `/mobwizardry reload`.

### Beginner's guide to the settings

Think of the config as a **list of "wizard job applications"**. Each block is one preset — a set of instructions for turning a creature into a wizard. You can have as many presets as you want; each one has a different tag so it never interferes with the others.

Here is a plain-English explanation of every setting:

- **`requiredTag`** — the magic word that *turns the creature on*. A mob only gets its wizard AI while it carries this tag (you apply the tag with the commands below). Each preset needs a unique tag. The mob type is chosen at summon time — the preset itself is not limited to any creature type.
- **`wizardType`** — how the wizard fights. `ranged` (default) keeps distance and casts from afar; `close` charges in, casts point-blank, keeps a ~5-block standoff, and buffs while engaging. See below.
- **`team`** — *optional* team name. Mobs carrying presets with the same team name can never target, retaliate against, or hurt each other (even through spell splash). Leave it out or empty for a mob with no team. Example: give undead mobs `"team": "undead"` and human mobs `"team": "human"` so undead never fight undead and humans never fight humans, while the two groups still fight each other.
- **`faction`** — *optional* (`enemy` or `friendly`, default `enemy`). **`enemy`** wizards act like hostile mobs: they hunt **friendly-faction wizards**, and a Wizard NPC additionally hunts players, villagers and iron golems. **`friendly`** wizards never attack players or villagers, but they **hunt hostile mobs and enemy-faction wizards** (like a guard), fight back when hurt, and still cast spells. Best used with the Wizard NPC below.
- **`skin`** — *optional* skin name for the Wizard NPC (a `.png` file in `config/mobwizardry/wizard-skins/`, e.g. `"skin": "alex"`). Leave it out to get a random skin per spawned NPC.
- **`speed`** — how fast the mob moves while casting. `1.0` is normal walking speed; bigger = faster.
- **`castInterval`** — the minimum number of ticks between cast attempts (20 ticks = 1 second). Smaller = casts more often.
- **`castIntervalMax`** — *optional* upper bound for the cast interval. If set above `0`, the actual interval is randomized between `castInterval` and `castIntervalMax` each cast (keeps the timing unpredictable). `0` (default) = always exactly `castInterval`. If it is set lower than `castInterval`, it is ignored.
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
- **`attack`** — cast in combat against the target. **For bosses this category is ignored** — a
  boss's attack spells are its `combos` (see [Boss fights](#boss-fights-220)), so boss presets and
  phases only use defense/movement/support/escape.
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

### Example config — full `presets.json`

This is the full default `presets.json` the mod writes on first launch, with all five presets.
The boss's **behavior** (name, color, phases, combos, spawn settings) lives separately in
`bosses.json` — see the [full example](#example-config--full-bossesjson) below. Copy it and
change the values to taste.

```json
{
  "_wizardDisplay": {
    "nameColor": "white",
    "teamColor": "gray"
  },
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
      "head": "minecraft:iron_helmet",
      "chest": "minecraft:iron_chestplate",
      "legs": "minecraft:iron_leggings",
      "feet": "minecraft:iron_boots"
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
      "head": "minecraft:iron_helmet",
      "chest": "minecraft:iron_chestplate",
      "legs": "minecraft:iron_leggings",
      "feet": "minecraft:iron_boots"
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
      "head": "minecraft:iron_helmet",
      "chest": "minecraft:iron_chestplate",
      "legs": "minecraft:iron_leggings",
      "feet": "minecraft:iron_boots"
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
  },
  "wizard_boss": {
    "requiredTag": "wizard_boss",
    "wizardType": "ranged",
    "team": "undead",
    "faction": "enemy",
    "skin": "steve",
    "speed": 1.2,
    "castInterval": 40,
    "castIntervalMax": 0,
    "movementStartDistance": 0,
    "movementFarDistance": 0,
    "movementDistanceOffset": 5.0,
    "movementTooCloseDistance": 5.0,
    "retaliationChance": 0.6,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff",
      "head": "minecraft:netherite_helmet",
      "chest": "minecraft:netherite_chestplate",
      "legs": "minecraft:netherite_leggings",
      "feet": "minecraft:netherite_boots"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 200,
      "irons_spellbooks:mana_regen": 4,
      "irons_spellbooks:spell_power": 2.5,
      "minecraft:generic.max_health": 200,
      "minecraft:generic.armor": 10,
      "minecraft:generic.knockback_resistance": 0.8
    },
    "spells": {
      "defense": [],
      "movement": [],
      "support": [],
      "escape": []
    }
  }
}
```

### Wizard name tags (`_wizardDisplay` + `names.json`)

Every wizard wears a name tag showing its **name** with its **team** beneath it — e.g.:

```
Vodyaniski
< Undead >
```

Normal wizards get a **random name** from `config/mobwizardry/names.json`; bosses keep their
configured boss name. The team line is the preset's `team` with the first letter capitalized
(`undead` → `Undead`). The name-tag colors are set by the `_wizardDisplay` block at the top of
`presets.json`:

```json
"_wizardDisplay": {
  "nameColor": "white",
  "teamColor": "gray"
}
```

| Field | Meaning |
|---|---|
| `nameColor` | color of the name line (named or hex, e.g. `white`, `gold`, `#FF5555`). Default `white`. |
| `teamColor` | color of the `< Team >` line. Default `gray`. |

**Random names live in their own file** — `config/mobwizardry/names.json` (written with the
default pool on first launch). It is a plain JSON array of strings:

```json
[
  "Vodyaniski", "Alech", "Mordecai", "Seraphine", "Kael",
  "Ilyana", "Draven", "Elysia", "Rowan", "Zephyr"
]
```

Edit it without touching `presets.json`; `/mobwizardry reload` re-reads it. An empty list means
normal wizards get no name tag. (A legacy `_wizardDisplay.names` in an old `presets.json` is
still honored — with a warning — until `names.json` exists.)

A wizard with no team shows just its name. The tag is drawn with the team line at 0.6x scale
under the name, and — like vanilla name tags — renders through terrain unless the wizard is
sneaking.

### Armor on wizard NPCs

Wizard NPCs render equipped **vanilla** armor (leather/chain/iron/gold/diamond/turtle/netherite)
through the vanilla armor layer. Armor items whose material has no vanilla overlay texture (e.g.
Iron's Spells' `wandering_magician` set, which uses its own custom models) still apply their
server-side effects but do **not** appear on the model — the mod warns about these at config
load. Use vanilla armor items for visible gear.

### Peaceful difficulty

Setting the world to Peaceful removes **enemy-faction** wizards exactly like vanilla hostile
mobs. **Friendly** wizards (and bosses) are not removed — they persist.

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
  seconds, combo steps with unknown spells or invalid categories, phases with unknown effects or
  out-of-range amplifiers/durations, phases referencing unknown spells) are logged and fixed or
  disabled, and **attack spells defined on a boss are ignored** (a boss's attack comes from its
  combos),
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
  `config/mobwizardry/wizard-skins/` (created on first launch; each `.png` file's name is a
  skin). Each spawned NPC picks a random skin; a preset's `"skin": "name"` field forces
  a specific one. Missing files fall back to the vanilla Steve texture.
- **Faction** — the preset's `faction` field decides behavior:
  - `enemy` — hunts friendly-faction wizards, players, villagers and iron golems like a hostile mob.
  - `friendly` — never attacks on its own; it only fights back when hurt (and still casts).
- **Teams** — same-team NPCs can never hurt/target each other, so you can build friendly and
  enemy groups that coexist.

## Boss fights (2.2.0)

Any wizard preset can become a **boss** by adding a `boss` entry for it in
`config/mobwizardry/bosses.json` (keyed by the preset's name). When a mob with a boss preset
joins the world (summoned, wizardified, spawned by the natural spawner, or loaded from a save):

- a **lightning bolt** strikes it (visual only — the boss takes no damage from it),
- the chat announces `NAME has arrived.`,
- it wears a **colored name tag**, shows a **red boss bar** (name in its color, health as the
  bar fill) to everyone in its dimension, and
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
which the boss enters the phase — a true ratio of the boss's actual max health, so 50% of a
500-health boss triggers at 250), an optional **message**, a **spells** kit, optional
**effects**, and optional **combos** (see below). **Boss spells only use the defense, movement,
support and escape categories — attack comes entirely from `combos`** (any attack list on a boss
is ignored with a warning).
When the boss's health drops to a phase's threshold, the kit is swapped in, the phase's effects
are applied, its combos are added to the combo pool, and `[NAME] message` is broadcast (the name
in red).

```json
"phases": [
  {
    "number": 1,
    "healthPercent": 100,
    "message": "So you dare face me?",
    "spells": {
      "defense": [], "movement": [], "support": [], "escape": []
    }
  },
  {
    "number": 2,
    "healthPercent": 50,
    "message": "Fool! Now you face my true power!",
    "effects": [
      { "id": "minecraft:resistance", "amplifier": 1, "duration": -1 }
    ],
    "combos": [
      {
        "pauseAfterComboExecution": 50,
        "steps": [
          { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
          { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 2, "waitAfterCast": 80 }
        ]
      }
    ],
    "spells": {
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

**Phase effects (2.3.3):** each phase can apply MobEffects when it activates. `id` is a vanilla
effect id like `minecraft:resistance`, `minecraft:speed` or `minecraft:strength`;
`amplifier` is the effect level minus one (0 = level I, 1 = level II); `duration` is in ticks,
with **`-1` = infinite (the default)** — so an effect granted in phase 2 **persists through all
later phases** (they accumulate: a phase 3 boss keeps phase 2's resistance *and* gains its own
speed). Unknown effect ids are skipped with a warning; effects are re-applied on `/mobwizardry
reload` so config changes take effect.

**Phase combos (3.8.0):** a phase can define its own `combos[]` (same structure as the boss-level
list). Entering a phase **adds** its combos to the boss's combo pool — the pool at any moment is
the boss-level `combos` plus every phase's combos up to the current one, and it never shrinks. So
a boss with combos 1 + 2 from phase 1 that defines combo 3 in phase 2 can pick among combos 1, 2
**and** 3 from phase 2 on. Like the boss-level list, phase combos validate the same way (unknown
spells skipped, levels clamped) and are re-applied on `/mobwizardry reload`.

### Combo presets (2.3.0)

Think of a combo as one **prepared attack routine**: a boss's **attack spells ARE its combos** —
bosses don't define `attack` spells at all (see Phases above). While fighting, it **randomly
picks one combo**, casts the steps **in order** — each step casts, then **waits** its
`waitAfterCast` (e.g. 2 seconds) before the next step fires — then **pauses**, and after the
pause it **randomly picks another combo** (the next pick is completely independent, so it may
even be the same combo again). While a combo is actually running the boss casts **only** the
combo's steps; its normal defense/movement/support/escape spells stay silent until the combo
finishes, then behave like any other wizard until the next combo starts.

The pick is always from the **current pool**: the boss-level `combos` plus every phase's combos
up to the one the boss is in (3.8.0). Phases only ever **add** to the pool — phase 2's combos
join phase 1's instead of replacing them, so later phases keep the earlier routines too.

```json
"combos": [
  {
    "pauseAfterComboExecution": 40,
    "steps": [
      { "category": "attack",  "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
      { "category": "attack",  "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
      { "category": "attack",  "spell": "irons_spellbooks:fireball",      "level": 1, "waitAfterCast": 80 },
      { "category": "escape",  "spell": "irons_spellbooks:blood_step",    "level": 1, "waitAfterCast": 40 }
    ]
  }
]
```

| Field | Meaning |
|---|---|
| `pauseAfterComboExecution` | How long the boss **catches its breath** after this combo finishes before it may pick the next random combo. Counted in **ticks** — Minecraft runs 20 ticks per second, so `40` = 2 seconds. Smaller = the boss chains combos faster; bigger = longer pauses between combos. Set it to `0` to let the boss use its preset's normal cast interval (about 2-3 seconds). If several combos have different values, each combo's own value is used for the pause that follows it. (Old names for this field were `castInterval` and `tickBeforeComboExecution`; they still work, just renamed.) |
| `steps` | the combo, executed top-to-bottom. |
| `step.category` | informational — `attack`/`defense`/`support`/`movement`/`escape`. |
| `step.spell` | the spell to cast (same id format as preset spells). |
| `step.level` | cast level (clamped to the spell's max). |
| `step.waitAfterCast` | how long the boss waits **after casting this step** before the next step fires — e.g. `40` = 2 seconds, `80` = 4 seconds. The first step casts right away when the combo starts; the last step's wait is part of the combo, and only then does `pauseAfterComboExecution` apply. `0` = cast the next step as soon as the boss is free. (Old name: `castAfterTicks` — it counted from the combo start; it still loads and is converted to a wait, but the timing has changed, so check old configs.) |

Steps with unknown spells are skipped at load; a combo with no usable steps ends immediately and
the next combo follows.

### Natural spawning (per-boss `spawnSettings`)

The mod can spawn bosses in the world on a timer. Each boss controls its own natural spawning
through its `spawnSettings` block — there is no global setting:

```json
"spawnSettings": {
  "enabled": true,
  "spawnAttemptIntervalSeconds": 1200,
  "maxActiveBosses": 3,
  "minDistanceFromPlayer": 24,
  "maxDistanceFromPlayer": 48,
  "spawnChance": 0.5,
  "despawnOnTimeChange": true,
  "spawnGlowSeconds": 60,
  "skyFlashBolts": 4
}
```

| Field | Meaning |
|---|---|
| `enabled` | `false` = this boss never naturally spawns (summon/wizardify still work). |
| `spawnAttemptIntervalSeconds` | the **time between natural-spawn attempts** for this boss (1200 = one attempt every 20 minutes). The chance itself is the day/night weighted roll — this is just how often the roll happens. (Old name: `attemptIntervalSeconds`, still works.) |
| `maxActiveBosses` | how many of *this* boss may be alive at once before it stops rolling. |
| `minDistanceFromPlayer` / `maxDistanceFromPlayer` | this boss spawns at a safe spot between these distances from a random online player. |
| `spawnChance` | the chance (0–1) that an eligible attempt actually spawns the boss: `1` = every attempt, `0.5` = on average every other attempt, `0.1` = rare, `0` = never. Effective frequency is roughly `spawnAttemptIntervalSeconds / spawnChance` (e.g. 1200 / 0.5 = a boss roughly every 40 minutes). |
| `skyFlashBolts` | how many extra visual-only lightning bolts (besides the main strike) flash around the boss when it arrives, so the sky thunders even in clear weather. `0` disables. Default `4`, clamped 0–30. |
| `despawnOnTimeChange` | `true` (default) = a boss that **naturally** spawned disappears when the day/night phase flips (a night-spawned boss vanishes at day, a day-spawned boss vanishes at night). Bosses summoned with `/mobwizardry summon`/`boss` are never affected. |
| `spawnGlowSeconds` | how long the boss glows after arriving so players can see it (default 60; `0` disables the glow). |

Each boss schedules its own spawn attempts: every tick, a boss whose `enabled` is true, whose
day/night weight for the current time is above `0`, whose live count is below its own
`maxActiveBosses` and whose interval has elapsed joins a weighted pool; one winner is rolled
against its `spawnChance` and spawned (a failed roll reschedules the next attempt) using that
boss's own distances. A boss with `daySpawnWeight` and `nightSpawnWeight` both at `0`,
`spawnChance` at `0`, or `spawnSettings.enabled` false never naturally spawns.

### On arrival

When a boss is bossified (naturally spawned or summoned):

- a **lightning bolt** strikes it (visual only), the chat announces `NAME has arrived.`, and it
  gets its colored name tag and first phase,
- it **immediately targets a random attackable online player** and navigates toward them
  (multiplayer = random among them); with no attackable players it stays idle — after that it
  fights with the exact same wizard AI as any other wizard.

### Example config — full `bosses.json`

The default config ships a complete three-phase example, `wizard_boss` (Aetheron, the Crimson
Archon): the plain `wizard_boss` wizard preset lives in presets.json (see the [full presets.json
example](#example-config--full-presetsjson)) and its boss behavior below. This is the full
default `bosses.json` the mod writes on first launch. To fight one:
`/mobwizardry boss wizard_boss mobwizardry:wizard`, or let the night spawner do its job
(nightSpawnWeight 20 vs daySpawnWeight 5).

```json
{
  "bosses": {
    "wizard_boss": {
      "enabled": true,
      "name": "Aetheron, the Crimson Archon",
      "nameColor": "dark_red",
      "spawnEntity": "mobwizardry:wizard",
      "spawnSettings": {
        "enabled": true,
        "spawnAttemptIntervalSeconds": 1200,
        "maxActiveBosses": 3,
        "minDistanceFromPlayer": 24,
        "maxDistanceFromPlayer": 48,
        "spawnChance": 0.5,
        "despawnOnTimeChange": true,
        "spawnGlowSeconds": 60
      },
      "daySpawnWeight": 5,
      "nightSpawnWeight": 20,
      "phases": [
        {
          "number": 1,
          "healthPercent": 100,
          "message": "So you dare face me?",
          "spells": {
            "defense": [], "movement": [], "support": [], "escape": []
          }
        },
        {
          "number": 2,
          "healthPercent": 50,
          "message": "Fool! Now you face my true power!",
          "effects": [
            { "id": "minecraft:resistance", "amplifier": 1, "duration": -1 }
          ],
          "spells": {
            "defense": [ { "id": "irons_spellbooks:shield", "level": 1 } ],
            "movement": [ { "id": "irons_spellbooks:blood_step", "level": 1 } ],
            "support": [ { "id": "irons_spellbooks:heal", "level": 1, "emergency": true } ],
            "escape": []
          }
        },
        {
          "number": 3,
          "healthPercent": 25,
          "message": "This is not over! The archon's fury knows no end!",
          "effects": [
            { "id": "minecraft:speed", "amplifier": 1, "duration": -1 }
          ],
          "spells": {
            "defense": [ { "id": "irons_spellbooks:shield", "level": 1 } ],
            "movement": [
              { "id": "irons_spellbooks:teleport", "level": 1 },
              { "id": "irons_spellbooks:blood_step", "level": 1 }
            ],
            "support": [
              { "id": "irons_spellbooks:heal", "level": 2, "emergency": true },
              { "id": "irons_spellbooks:fortify", "level": 1 }
            ],
            "escape": [ { "id": "irons_spellbooks:teleport", "level": 1 } ]
          }
        }
      ],
      "combos": [
        {
          "pauseAfterComboExecution": 40,
          "steps": [
            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
            { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 1, "waitAfterCast": 80 },
            { "category": "movement", "spell": "irons_spellbooks:blood_step", "level": 1, "waitAfterCast": 40 }
          ]
        },
        {
          "pauseAfterComboExecution": 60,
          "steps": [
            { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 1, "waitAfterCast": 40 },
            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 80 },
            { "category": "escape", "spell": "irons_spellbooks:blood_step", "level": 1, "waitAfterCast": 40 }
          ]
        }
      ]
    }
  }
}
```

## Full config reference (every field)

This index lists **every** field accepted by the two config files so nothing is missed. Each row
points to the section that explains it in detail.

### `presets.json`

| Field | Type | Meaning |
|---|---|---|
| `_wizardDisplay` | object | name-tag display settings (see [Wizard name tags](#wizard-name-tags-_wizarddisplay-and-namesjson)) — `nameColor`, `teamColor` (the random-name pool lives in `names.json`) |
| `requiredTag` | string | the tag that activates this preset on a mob |
| `wizardType` | string | `ranged` or `close` (see [Wizard types](#wizard-types)) |
| `team` | string | same-team wizards never fight each other (optional) |
| `faction` | string | `enemy` or `friendly` (optional) |
| `skin` | string | Wizard NPC skin name (optional) |
| `speed` | number | movement speed while casting |
| `castInterval` | int | minimum ticks between cast attempts (20 = 1 s) |
| `castIntervalMax` | int | optional random upper bound for the cast interval (`0` = fixed) |
| `movementStartDistance` | number | start of the "far" movement zone (`0` = derived) |
| `movementFarDistance` | number | beyond this → strong movement-spell desire (`0` = derived) |
| `movementDistanceOffset` | number | how much earlier the movement spell triggers |
| `movementTooCloseDistance` | number | closer than this → back away / reposition |
| `retaliationChance` | number | 0-1 chance to switch to a new attacker |
| `equipment` | object | slot → item id (all six slots) |
| `attributes` | object | attribute id → value (Iron's Spellbooks + `minecraft:generic.*`) |
| `spells` | object | the spell kit (five categories below) |
| `spells.attack` | list | attack spells (**ignored for bosses** — combos are their attack) |
| `spells.defense` | list | cast while being attacked |
| `spells.movement` | list | cast to close the distance |
| `spells.support` | list | self-aid spells (hurt / below half health) |
| `spells.escape` | list | retreat spells (critically low + recently attacked) |
| spell entry `id` | string | the spell id (e.g. `irons_spellbooks:fireball`) |
| spell entry `level` | int | cast level |
| spell entry `emergency` | bool | support-only: always pick this heal below 30% health |

All of the above are explained in the [Beginner's guide](#beginners-guide-to-the-settings);
spell categories in the [spells](#beginners-guide-to-the-settings) bullet.

### `bosses.json`

| Field | Type | Meaning |
|---|---|---|
| `bosses` | object | map of **preset name** → boss definition |
| `enabled` | bool | make this preset a boss |
| `name` | string | the boss's display name |
| `nameColor` | string | name tag / chat color (named or hex, e.g. `red`, `#FF5555`) |
| `spawnEntity` | string | entity type the natural spawner uses |
| `spawnSettings.enabled` | bool | allow this boss to naturally spawn |
| `spawnSettings.spawnAttemptIntervalSeconds` | int | seconds between natural-spawn attempts |
| `spawnSettings.maxActiveBosses` | int | how many of this boss may be alive at once |
| `spawnSettings.minDistanceFromPlayer` | number | min spawn distance from a player |
| `spawnSettings.maxDistanceFromPlayer` | number | max spawn distance from a player |
| `spawnSettings.spawnChance` | number | chance (0–1) an eligible attempt actually spawns (`0` = never, `1` = every attempt) |
| `spawnSettings.despawnOnTimeChange` | bool | naturally-spawned boss vanishes when day/night flips |
| `spawnSettings.spawnGlowSeconds` | int | arrival glow duration (0 = off) |
| `spawnSettings.skyFlashBolts` | int | extra visual-only lightning bolts flashing around the boss on arrival (0 = off, default 4) |
| `daySpawnWeight` | number | natural-spawn weight during the day (`0` = never by day) |
| `nightSpawnWeight` | number | natural-spawn weight at night (`0` = never by night) |
| `phases` | list | the health-based phases |
| phase `number` | int | phase number |
| phase `healthPercent` | number | entry threshold, percent of the boss's actual max health |
| phase `message` | string | broadcast when the phase is entered |
| phase `effects` | list | MobEffects applied when the phase is entered |
| effect `id` | string | the effect id (e.g. `minecraft:resistance`) |
| effect `amplifier` | int | effect level minus one (0 = level I) |
| effect `duration` | int | ticks; `-1` = infinite (persists across all phases) |
| phase `combos` | list | combos **added** to the combo pool when the phase is entered (same structure as `combos`; they join, never replace) |
| phase `spells` | object | defense / movement / support / escape (bosses have no attack) |
| `combos` | list | the scripted attack sequences (a boss's attack) |
| combo `pauseAfterComboExecution` | int | pause after this combo before the next random pick |
| combo `steps` | list | the combo, executed top-to-bottom |
| step `category` | string | `attack`/`defense`/`support`/`movement`/`escape` |
| step `spell` | string | the spell id |
| step `level` | int | cast level |
| step `waitAfterCast` | int | ticks the boss waits after casting this step before the next step fires (20 = 1 second; 0 = no wait). Legacy `castAfterTicks` converts to this with a warning |

Details: [The boss block](#the-boss-block), [Phases](#phases),
[Combo presets](#combo-presets-230), [Natural spawning](#natural-spawning-per-boss-spawnsettings),
[On arrival](#on-arrival).

### `raids.json`

| Field | Type | Meaning |
|---|---|---|
| `raids` | object | map of **raid name** → raid definition |
| `name` | string | the raid's display name (shown on the raid bar) |
| `startMessage` | string | chat message when the raid starts (empty = none) |
| `victoryMessage` | string | chat message when the players win (empty = none) |
| `defeatMessage` | string | chat message when the enemy wins / all players die (empty = none) |
| `waves` | list | the enemy waves, run in order |
| wave `number` | int | wave number |
| wave `enemies` | list | the enemy groups of this wave |
| enemy `preset` | string | the wizard preset to spawn (e.g. `wizard`) |
| enemy `count` | int | how many of this preset the wave contains (max) |
| enemy `weight` | number | relative chance this preset is picked per spawn roll — affects spawn order only; the final count is always `count` |
| `boss` | string | the boss-enabled preset used for the final wave (empty = none) |
| `spawnDistance` | number | how far (in blocks) from a random player's position wave enemies spawn, so you get a moment to prepare. Clamped to ≥ 8. Default `32`. |
| `bossSpawnDistance` | number | how far (in blocks) from a random player's position the final boss spawns. Clamped to ≥ 8. Default `48`. |
| `groupRadius` | number | how tightly a wave's enemies cluster around one rally point. Clamped to 1–16. Default `4`. |
| `skyFlashBolts` | int | visual-only lightning bolts flashing around the wave rally point at each wave's spawn (0 = off, default 4) |
| `waveGlowSeconds` | int | how many seconds each wave's enemies glow after spawning (0 = off, default 20) |

Details and semantics (including "Why is there a weight?"): [Raid / horde](#raid--horde-300).

### Example — every `presets.json` field (one preset)

One preset that uses **every** supported field, so each field's name, type and value format is
visible at a glance:

```json
{
  "archmage": {
    "requiredTag": "archmage",
    "wizardType": "ranged",
    "team": "magic_users",
    "faction": "enemy",
    "skin": "alex",
    "speed": 1.25,
    "castInterval": 40,
    "castIntervalMax": 80,
    "movementStartDistance": 12.0,
    "movementFarDistance": 20.0,
    "movementDistanceOffset": 5.0,
    "movementTooCloseDistance": 4.0,
    "retaliationChance": 0.6,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff",
      "offhand": "minecraft:shield",
      "head": "minecraft:netherite_helmet",
      "chest": "minecraft:netherite_chestplate",
      "legs": "minecraft:netherite_leggings",
      "feet": "minecraft:netherite_boots"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 150,
      "irons_spellbooks:mana_regen": 3,
      "irons_spellbooks:spell_power": 2.0,
      "minecraft:generic.max_health": 40,
      "minecraft:generic.armor": 8
    },
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_missile", "level": 2 },
        { "id": "irons_spellbooks:fireball", "level": 1 }
      ],
      "defense": [ { "id": "irons_spellbooks:shield", "level": 1 } ],
      "movement": [ { "id": "irons_spellbooks:blood_step", "level": 1 } ],
      "support": [
        { "id": "irons_spellbooks:heal", "level": 1, "emergency": true },
        { "id": "irons_spellbooks:fortify", "level": 1 }
      ],
      "escape": [ { "id": "irons_spellbooks:teleport", "level": 1 } ]
    }
  }
}
```

### Example — every `bosses.json` field (one boss)

One boss definition that uses **every** supported field (`archmage` matches the preset above, so
this turns that wizard into a boss):

```json
{
  "bosses": {
    "archmage": {
      "enabled": true,
      "name": "Archmage Malador",
      "nameColor": "dark_red",
      "spawnEntity": "mobwizardry:wizard",
      "spawnSettings": {
        "enabled": true,
        "spawnAttemptIntervalSeconds": 1200,
        "maxActiveBosses": 2,
        "minDistanceFromPlayer": 24,
        "maxDistanceFromPlayer": 48,
        "despawnOnTimeChange": true,
        "spawnGlowSeconds": 60
      },
      "daySpawnWeight": 10,
      "nightSpawnWeight": 25,
      "phases": [
        {
          "number": 1,
          "healthPercent": 100,
          "message": "You dare challenge the Archmage?",
          "effects": [],
          "spells": {
            "defense": [], "movement": [], "support": [], "escape": []
          }
        },
        {
          "number": 2,
          "healthPercent": 50,
          "message": "Fool! Feel my true power!",
          "effects": [
            { "id": "minecraft:resistance", "amplifier": 1, "duration": -1 },
            { "id": "minecraft:strength", "amplifier": 1, "duration": -1 }
          ],
          "combos": [
            {
              "pauseAfterComboExecution": 50,
              "steps": [
                { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
                { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 2, "waitAfterCast": 80 }
              ]
            }
          ],
          "spells": {
            "defense": [ { "id": "irons_spellbooks:shield", "level": 1 } ],
            "movement": [ { "id": "irons_spellbooks:blood_step", "level": 1 } ],
            "support": [ { "id": "irons_spellbooks:heal", "level": 1, "emergency": true } ],
            "escape": []
          }
        },
        {
          "number": 3,
          "healthPercent": 25,
          "message": "The archon's fury knows no end!",
          "effects": [
            { "id": "minecraft:speed", "amplifier": 2, "duration": -1 }
          ],
          "spells": {
            "defense": [ { "id": "irons_spellbooks:shield", "level": 1 } ],
            "movement": [ { "id": "irons_spellbooks:teleport", "level": 1 } ],
            "support": [ { "id": "irons_spellbooks:fortify", "level": 1 } ],
            "escape": [ { "id": "irons_spellbooks:teleport", "level": 1 } ]
          }
        }
      ],
      "combos": [
        {
          "pauseAfterComboExecution": 40,
          "steps": [
            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "waitAfterCast": 40 },
            { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 1, "waitAfterCast": 80 },
            { "category": "escape", "spell": "irons_spellbooks:blood_step", "level": 1, "waitAfterCast": 40 }
          ]
        },
        {
          "pauseAfterComboExecution": 60,
          "steps": [
            { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 1, "waitAfterCast": 40 },
            { "category": "movement", "spell": "irons_spellbooks:blood_step", "level": 1, "waitAfterCast": 80 }
          ]
        }
      ]
    }
  }
}
```

## Raid / horde (3.0.0)

A **raid** is a configurable horde of enemy wizards that fights the players in waves and ends
with a **boss fight** (the existing boss system). Players win by killing **every enemy in every
wave and the boss**; the raid is lost when **all players in the raid's dimension are dead** —
and when the raid defeats the players, it **kills them literally**: every player still alive in
the raid's dimension is slain by the raid itself (death message "was defeated by the raid",
totems of undying cannot save you). Whenever a raid ends — victory, defeat, or a manual stop —
every **surviving raid mob vanishes**: the wave wizards and the boss are removed from the world
with no drops and no death animation, as if they never existed, so nothing is left behind when
the players respawn. Raids are defined in `config/mobwizardry/raids.json`
and run with `/mobwizardry raid start <raid>`.

While a raid runs, everyone in its dimension sees a **purple raid bar**:
- during a wave it shows `Raid Name — Wave N/M` with the fill = how many of that wave's enemies
  you've killed,
- during the boss phase it switches to `Raid Name — Boss` with the boss's health.

### `raids.json` example — full file

```json
{
  "raids": {
    "wizard_horde": {
      "name": "The Wizard Horde",
      "startMessage": "The Wizard Horde has arrived!",
      "victoryMessage": "The Wizard Horde has been driven back!",
      "defeatMessage": "The Wizard Horde has overrun the realm!",
      "waves": [
        {
          "number": 1,
          "enemies": [
            { "preset": "wizard",       "count": 4, "weight": 1 },
            { "preset": "wizard_close", "count": 2, "weight": 1 }
          ]
        },
        {
          "number": 2,
          "enemies": [
            { "preset": "wizard_range", "count": 6, "weight": 2 }
          ]
        }
      ],
      "boss": "wizard_boss",
      "spawnDistance": 32.0,
      "bossSpawnDistance": 48.0,
      "groupRadius": 4.0,
      "skyFlashBolts": 4,
      "waveGlowSeconds": 20
    }
  }
}
```

| Field | What it does |
|---|---|
| `name` | The raid's display name, shown on the raid bar (and in `/mobwizardry raid list`). Falls back to the raid's key if empty. |
| `startMessage` | Chat message broadcast to everyone when the raid starts (e.g. `"The Wizard Horde has arrived!"`). Empty = no message. |
| `victoryMessage` | Chat message when the players win (all waves and the boss defeated). Empty = silent. |
| `defeatMessage` | Chat message when the enemy wins (all players in the raid's dimension die). Empty = silent. |
| `waves` | The enemy waves, run in order. Once all of a wave's enemies are dead, the next wave — or the boss — begins. |
| `waves[].number` | The wave's number (1, 2, 3...). Shown on the raid bar as `Wave N/M`. |
| `waves[].enemies` | The enemy groups of this wave. A wave spawns **`sum(counts)`** enemies in total. |
| `enemy.preset` | Which wizard preset to spawn (e.g. `wizard`, `wizard_close`). Must exist in presets.json. Enemies are `mobwizardry:wizard` NPCs carrying the preset's tag (they never naturally despawn). |
| `enemy.count` | How many mobs of this preset the wave may contain (the cap). The wave's total = the sum of all `count`s. |
| `enemy.weight` | How likely this preset is picked for each spawn roll. It only affects the **spawn order** (higher = that preset's enemies arrive sooner) — the final numbers are always exactly each preset's `count`. See "Why is there a weight?" below. |
| `boss` | The **boss-enabled preset** used for the final wave (e.g. `wizard_boss`). If empty — or not a boss-enabled preset — the raid ends with a player victory right after the last wave. |
| `spawnDistance` | Blocks from a random player's position where wave enemies spawn (85-115% jitter, floored at 8), so you get a moment to prepare. Default `32`. |
| `bossSpawnDistance` | Blocks from a random player's position where the final boss spawns. Default `48`. |
| `groupRadius` | Each wave picks ONE rally point `spawnDistance` away and spawns all of its enemies within this many blocks of it, so the wave arrives grouped together instead of scattered around the ring. Default `4`. |
| `skyFlashBolts` | Visual-only lightning bolts that flash around the wave rally point when a wave spawns (`0` = off, default `4`). Every raid enemy and the boss also spawn **targeting a random attackable player** (falling back to their normal AI when no player is attackable). |
| `waveGlowSeconds` | How long each wave's enemies glow after spawning so you can spot the horde (default `20`; `0` = off). |

Every raid enemy and the boss spawn **targeting a random attackable player** (falling back to
their normal AI when no player is attackable).

**Why is there a `weight`?** `count` always fills **exactly** — a wave always spawns
`sum(counts)` enemies with each preset at its `count` (4 `wizard` + 2 `wizard_close` in the
example above). `weight` only decides the **spawn order**: for each of the wave's `sum(counts)`
spawn slots the raid rolls a preset, weighted by `weight`, among the presets that haven't reached
their `count` yet. A higher weight makes that preset's enemies **arrive earlier / swarm in more
densely**, but it never changes the final numbers. Equal weights (1 and 1) = a fair, mixed order;
`wizard: weight 3` vs `wizard_close: weight 1` = wizard's 4 enemies tend to come first. All-zero
weights just spawn each preset's `count` in order.

### Raid start effects (3.1.0)

When a raid starts, every player in its dimension simultaneously hears the **pillager horn** and
a **loud lightning crash**, and a red **"YOU ARE INVADED"** subtitle appears.

### Commands

- `/mobwizardry raid start <raid> [pos]` (permission 2) — start a configured raid (near you, or
  the `[pos]`).
- `/mobwizardry raid stop` — end the active raid (no result message).
- `/mobwizardry raid list` — list configured raids.

Only **one raid** can be active at a time; starting a new one cancels the current raid.

## Admin commands

Requires permission level 2. (`help` and `list` are available to everyone.)

| Command | Description |
|---|---|
| `/mobwizardry help` | Shows this list of commands with a short explanation for each. |
| `/mobwizardry summon <preset> <mobType> [pos]` | Spawns a mob of `<mobType>` with the preset applied (tag, equipment, attributes, wizard AI) immediately. The mob type is not restricted by the preset. |
| `/mobwizardry boss <preset> <mobType> [pos]` | Summons a boss — like `summon`, but the preset must be boss-enabled (a `boss` entry in `bosses.json`). The boss is struck by lightning, named and announced. |
| `/mobwizardry raid start <raid> [pos]` | Starts a configured raid from `raids.json` — waves of enemy wizards ending in a boss fight, shown on a purple raid bar. |
| `/mobwizardry raid stop` | Ends the active raid. |
| `/mobwizardry raid list` | Lists configured raids (waves, boss). |
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
/mobwizardry raid start wizard_horde
/mobwizardry raid stop
/mobwizardry raid list
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
   - the mob **renders its armor** (vanilla armor items appear on the model; custom-material armor like Iron's Spells' `wandering_magician` gear does not — a load warning is logged for it),
   - its **name tag** shows the random name with the `< Team >` line at 0.6x scale beneath it, visible through walls unless it is sneaking.
5. For **Peaceful** behavior: `/difficulty peaceful` — every **enemy-faction** wizard disappears
   within a couple of seconds, while **friendly** wizards (faction `friendly`) stay put. `/difficulty easy` brings them back on the next summon.
6. For a **boss** preset (a `boss` entry in `bosses.json`):
   - summoning it (`/mobwizardry boss <preset> <mobType>`) strikes lightning, prints `NAME has arrived.` in chat, shows the colored name tag and a red boss bar, glows for `spawnGlowSeconds`, and targets a random online player (idle if none),
   - deal damage until it crosses a phase's `healthPercent` — the phase message appears, its spell kit swaps (e.g. phase 2 gains the spells you listed there), its phase `effects` are applied (and persist into later phases), and the boss bar fill drops,
   - with `combos`, its attack spells come only from the randomly-selected combo sequence (steps cast in order, each waiting its `waitAfterCast` before the next fires); while a combo runs it casts nothing else, and after it finishes defense/movement/support/escape trigger like a normal wizard until the next combo — and when it enters a phase whose `combos` are defined, those join the pool (phase 2's combos are pickable alongside phase 1's from then on),
   - with its `spawnSettings.enabled` true and a `daySpawnWeight`/`nightSpawnWeight` above 0, it should also appear near players over time (more often at night if the night weight is higher); with `despawnOnTimeChange` true it disappears when the time of day flips.
7. For a **raid** (a `raids` entry in `raids.json`):
   - `/mobwizardry raid list` shows it, `/mobwizardry raid start <raid>` starts it — the start message appears, the pillager horn + lightning crash play and the `YOU ARE INVADED` subtitle shows, and the purple raid bar shows `Raid Name — Wave 1/M` at 100%,
   - each wave spawns grouped at its rally point (`spawnDistance` away, within `groupRadius`) under a lightning storm, its enemies glow for `waveGlowSeconds`, and they head straight for a random attackable player,
   - the raid bar **drains from 100% toward 0%** as you defeat the wave's enemies; when a wave is cleared a lightning storm + thunder plays and the bar **animates back up to 100%** for the next wave,
   - kill every enemy in a wave — the bar fills and the next wave (or the boss) spawns,
   - after the last wave the configured boss appears roughly `bossSpawnDistance` blocks away — lightning, name, its own boss bar — and targets a random player; killing it ends the raid with the victory message and a victory chime,
   - if all players die the raid ends in defeat: every player still alive in the raid's dimension is killed by the raid (they see "was defeated by the raid"), then the defeat message and a failure sound play — and when the raid ends (win, lose, or `/mobwizardry raid stop`) every **surviving raid mob vanishes** from the world (wave wizards and the boss — no drops, no death animation, as if they never existed).
8. Tweak `presets.json` / `bosses.json` / `raids.json` / `names.json` and run `/mobwizardry reload` — no server restart needed. Code changes (if any) require rebuilding the jar and restarting.

## Notes

- `CastSource.MOB` in Iron's Spellbooks does not consume mana or enforce its player cooldown system, so the goal's `castInterval` is the effective cast cadence; per-spell cooldowns are still respected as the source of truth.
- The `wizard` and `wizard_lite` presets in the default config are examples — copy them and change `requiredTag` and spell IDs to taste.
