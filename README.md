# MobWizardry

MobWizardry attaches Iron's Spellbooks spellcasting AI to existing mobs — fully config-driven. No new mobs are added: any vanilla or modded mob becomes a spellcaster when it carries a configured tag.

- **Target:** Minecraft Forge 1.20.1 (47.4.10)
- **Download:** `mobwizardry-1.20.1-1.0.1.jar`

## How it works

1. Each preset in `config/mobwizardry/presets.json` defines:
   - the entity tag that activates it (`requiredTag`)
   - movement speed and cast cadence
   - equipment, attribute overrides and starting mana
   - attack / defense / movement / support spell kits
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

2. Drop `mobwizardry-1.20.1-1.0.1.jar` into the server's `mods/` folder (the same folder all your other mods live in):
   ```
   <server>\mods\mobwizardry-1.20.1-1.0.1.jar
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
- **`speed`** — how fast the mob moves while casting. `1.0` is normal walking speed; bigger = faster.
- **`castInterval`** — the minimum number of ticks between cast attempts (20 ticks = 1 second). Smaller = casts more often.
- **`equipment`** — what gear the mob wears. The slot name comes first (`mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`), then the item ID. Equipped items never drop.
- **`attributes`** — the mob's magic stats. Examples: `irons_spellbooks:max_mana` (mana pool size), `irons_spellbooks:mana_regen` (mana per second), `irons_spellbooks:spell_power` (spell damage multiplier).
- **`mana`** — how much mana the mob starts with.
- **`spells`** — its spell kit, split into four categories (see below). Each spell is written as `{ "id": "mod:spell_id", "level": 1 }`.

Spell categories: **`attack`** (cast in combat), **`defense`** (cast under pressure), **`movement`** (cast when positioning / out of range), **`support`** (utility).

### Example config (two presets)

This is exactly the default config the mod writes on first launch — copy it and change the values to taste.

```json
{
  "wizard": {
    "requiredTag": "wizard",
    "speed": 1.15,
    "castInterval": 60,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 100,
      "irons_spellbooks:mana_regen": 3,
      "irons_spellbooks:spell_power": 1.5
    },
    "mana": 100,
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
        { "id": "irons_spellbooks:heal", "level": 1 }
      ]
    }
  },
  "wizard_lite": {
    "requiredTag": "wizard_lite",
    "speed": 1.1,
    "castInterval": 80,
    "equipment": {
      "mainhand": "irons_spellbooks:blood_staff"
    },
    "attributes": {
      "irons_spellbooks:max_mana": 60,
      "irons_spellbooks:mana_regen": 2,
      "irons_spellbooks:spell_power": 1.0
    },
    "mana": 60,
    "spells": {
      "attack": [
        { "id": "irons_spellbooks:magic_arrow", "level": 1 }
      ],
      "defense": [],
      "movement": [],
      "support": []
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
- a warning is logged when a spell's intrinsic cooldown exceeds `castInterval`,
- a warning is logged when a spell's mana cost exceeds the preset `mana`.

Invalid presets fail loudly in the log instead of silently doing nothing.

## Admin commands

Requires permission level 2.

| Command | Description |
|---|---|
| `/mobwizardry summon <preset> <mobType> [pos]` | Spawns a mob of `<mobType>` with the preset applied (tag, equipment, mana, wizard AI) immediately. The mob type is not restricted by the preset. |
| `/mobwizardry tag <preset> <targets>` | Adds the preset's required tag to existing entities and fully initializes matching mobs. |
| `/mobwizardry untag <preset> <targets>` | Removes the tag — the wizard AI deactivates on the next tick. |
| `/mobwizardry wizardify <preset> [radius] [pos]` | Turns every mob within `radius` (1–64, default 16) of you (or of `pos`) into wizards — tag, equipment, mana and wizard AI. Non-mob entities in range are skipped and reported. |
| `/mobwizardry unwizardify <preset> [radius] [pos]` | Removes the tag from all wizards in range — their AI deactivates on the next tick. |
| `/mobwizardry reload` | Re-reads and re-validates `presets.json` without restarting. |
| `/mobwizardry list [page]` | Lists loaded presets in a readable, colored format — 5 per page, with clickable previous/next arrows. |

### Examples

```
/mobwizardry summon wizard minecraft:zombie
/mobwizardry summon wizard minecraft:zombie 100 64 100
/mobwizardry tag wizard @e[type=minecraft:zombie]
/mobwizardry untag wizard @e[type=minecraft:zombie]
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
   - **defense** spells cast under pressure,
   - **movement** spells cast when repositioning / out of range,
   - cooldowns match the spell's own configured values.
5. Tweak `presets.json` and run `/mobwizardry reload` — no server restart needed. Code changes (if any) require rebuilding the jar and restarting.

## Notes

- `CastSource.MOB` in Iron's Spellbooks does not consume mana or enforce its player cooldown system, so the goal's `castInterval` is the effective cast cadence; per-spell cooldowns are still respected as the source of truth.
- The `wizard` and `wizard_lite` presets in the default config are examples — copy them and change `requiredTag` and spell IDs to taste.
