# MobWizardry - Mobs using Magics!

MobWizardry attaches Iron's Spellbooks spellcasting AI to existing mobs — fully config-driven. No new mobs are added: any vanilla or modded mob becomes a spellcaster when it carries a configured tag.

- **Target:** Minecraft Forge 1.20.1 (47.4.10)

## How it works

1. Each preset in `config/mobwizardry/presets.json` defines:
   - the entity tag that activates it (`requiredTag`)
   - the wizard type (`wizardType`: `ranged` or `close`), movement speed and cast cadence
   - equipment, attribute overrides and a full mana pool
   - attack / defense / movement / support / escape spell kits
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

File: `config/mobwizardry/presets.json`

### Beginner's guide to the settings

Think of the config as a **list of "wizard job applications"**. Each block is one preset — a set of instructions for turning a creature into a wizard. You can have as many presets as you want; each one has a different tag so it never interferes with the others.

Here is a plain-English explanation of every setting:

- **`requiredTag`** — the magic word that *turns the creature on*. A mob only gets its wizard AI while it carries this tag (you apply the tag with the commands below). Each preset needs a unique tag. The mob type is chosen at summon time — the preset itself is not limited to any creature type.
- **`wizardType`** — how the wizard fights. `ranged` (default) keeps distance and casts from afar; `close` charges in, casts point-blank, keeps a ~5-block standoff, and buffs while engaging. See below.
- **`team`** — *optional* team name. Mobs carrying presets with the same team name can never target, retaliate against, or hurt each other (even through spell splash). Leave it out or empty for a mob with no team. Example: give undead mobs `"team": "undead"` and human mobs `"team": "human"` so undead never fight undead and humans never fight humans, while the two groups still fight each other.
- **`speed`** — how fast the mob moves while casting. `1.0` is normal walking speed; bigger = faster.
- **`castInterval`** — the minimum number of ticks between cast attempts (20 ticks = 1 second). Smaller = casts more often.
- **`movementDistanceOffset`** — how many blocks closer than the spell range the wizard repositions with a movement spell. Default `5.0`: with a 20-block spell range it uses a movement spell when the target is beyond 15 blocks instead of 20. Set `0` for the old behavior, or ignore this and set explicit absolute distances via `movementStartDistance` / `movementFarDistance` (those take precedence when set).
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
- **`attributes`** — the mob's magic stats. Examples: `irons_spellbooks:max_mana` (mana pool size), `irons_spellbooks:mana_regen` (mana per second), `irons_spellbooks:spell_power` (spell damage multiplier). You can also override **vanilla attributes** — they use the `minecraft:generic.*` namespace, e.g. `minecraft:generic.max_health` for max health, `minecraft:generic.armor`, `minecraft:generic.attack_damage`, `minecraft:generic.movement_speed`. After applying the overrides the mob refills to full health, so a boosted `max_health` spawns it at full HP.
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

This is exactly the default config the mod writes on first launch — copy it and change the values to taste.

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

At server start (and on `/mobwizardry reload`) every entry is validated against the real registries:

- unknown spell IDs, item IDs or attribute IDs are logged and removed,
- spell levels are clamped to the spell's max level,
- a warning is logged when a spell's intrinsic cooldown exceeds `castInterval`.

Invalid presets fail loudly in the log instead of silently doing nothing.

## Admin commands

Requires permission level 2. (`help` and `list` are available to everyone.)

| Command | Description |
|---|---|
| `/mobwizardry help` | Shows this list of commands with a short explanation for each. |
| `/mobwizardry summon <preset> <mobType> [pos]` | Spawns a mob of `<mobType>` with the preset applied (tag, equipment, attributes, wizard AI) immediately. The mob type is not restricted by the preset. |
| `/mobwizardry wizardify <preset> [radius] [pos]` | Turns every mob within `radius` (1–64, default 16) of you (or of `pos`) into wizards — tag, equipment, attributes and wizard AI. Non-mob entities in range are skipped and reported. |
| `/mobwizardry unwizardify <preset> [radius] [pos]` | Removes the tag from all wizards in range — their AI deactivates on the next tick. |
| `/mobwizardry reload` | Re-reads and re-validates `presets.json` without restarting. |
| `/mobwizardry list [page]` | Lists loaded presets in a readable, colored format — 5 per page, with clickable previous/next arrows. |

### Examples

```
/mobwizardry help
/mobwizardry summon wizard minecraft:zombie
/mobwizardry summon wizard minecraft:zombie 100 64 100
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
5. Tweak `presets.json` and run `/mobwizardry reload` — no server restart needed. Code changes (if any) require rebuilding the jar and restarting.

## Notes

- `CastSource.MOB` in Iron's Spellbooks does not consume mana or enforce its player cooldown system, so the goal's `castInterval` is the effective cast cadence; per-spell cooldowns are still respected as the source of truth.
- The `wizard` and `wizard_lite` presets in the default config are examples — copy them and change `requiredTag` and spell IDs to taste.
