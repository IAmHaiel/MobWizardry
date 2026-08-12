# MobWizardry

MobWizardry attaches Iron's Spellbooks spellcasting AI to existing mobs — fully config-driven. No new mobs are added: any vanilla or modded mob becomes a spellcaster when it carries a configured tag.

- **Target:** Minecraft Forge 1.20.1 (47.4.10)
- **Requires:** Iron's Spells 'n Spellbooks 1.20.1-3.16.2, T.O Magic 'n Extras 6.3.0

## How it works

1. Each preset in `config/mobwizardry/presets.json` defines:
   - which mob types it applies to (`targetMobs`)
   - the entity tag that activates it (`requiredTag`)
   - movement speed and cast cadence
   - equipment, attribute overrides and starting mana
   - attack / defense / movement / support spell kits
2. When a matching mob joins the world carrying the required tag, MobWizardry:
   - equips the configured gear and sets attributes/mana,
   - attaches a real Iron's Spellbooks `WizardAttackGoal` (wrapped behind a live tag check),
   - lets the mob cast its kit under the appropriate conditions.
3. Adding or removing the tag at runtime enables/disables the AI immediately — no restart needed.

## Installation

1. Make sure these are installed on the **server**:
   - Forge 1.20.1 (47.4.10)
   - Iron's Spells 'n Spellbooks 1.20.1-3.16.2
   - T.O Magic 'n Extras 6.3.0
   - (and whatever transitive dependencies your install already carries — e.g. the BielGG Spells Addon if you use it for T.O/cataclysm compatibility)
2. Drop `mobwizardry-1.0.0.jar` into the server's `mods/` folder.
3. Start the server. On first launch the mod writes a default config.

This mod is server-side logic; clients do not need it installed.

## Configuration

File: `config/mobwizardry/presets.json`

```json
{
  "wizard": {
    "targetMobs": ["minecraft:zombie"],
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
        { "id": "traveloptics:halberd_horizon", "level": 4 }
      ],
      "defense": [
        { "id": "irons_spellbooks:slow", "level": 3 }
      ],
      "movement": [
        { "id": "irons_spellbooks:blood_step", "level": 2 }
      ],
      "support": []
    }
  }
}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `targetMobs` | `string[]` | Mob type IDs that may gain wizard AI, e.g. `minecraft:zombie`, `recruits:recruit`. |
| `requiredTag` | `string` | Entity tag that activates the preset. Mobs only get AI while carrying this tag. |
| `speed` | `number` | Movement speed multiplier used by the wizard goal. |
| `castInterval` | `int` | Minimum ticks between spell cast attempts (goal cadence; per-spell cooldowns still apply). |
| `equipment` | `object` | Slot → item ID. Slots: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`. Equipped items never drop. |
| `attributes` | `object` | Attribute ID → base value overrides, e.g. `irons_spellbooks:max_mana`, `mana_regen`, `spell_power`. |
| `mana` | `number` | Starting mana for the mob's MagicData. |
| `spells` | `object` | Spell kits per category. Each entry is `{ "id": "<spell id>", "level": <int> }`. |

Spell categories map to casting conditions handled by Iron's Spellbooks: `attack` (in combat), `defense` (under pressure), `movement` (positioning/out of range), `support` (utility).

### Validation on load

At server start (and on `/mobwizardry reload`) every entry is validated against the real registries:

- unknown mob types, spell IDs, item IDs or attribute IDs are logged and removed,
- spell levels are clamped to the spell's max level,
- a warning is logged when a spell's intrinsic cooldown exceeds `castInterval`,
- a warning is logged when a spell's mana cost exceeds the preset `mana`.

Invalid presets fail loudly in the log instead of silently doing nothing.

## Admin commands

Requires permission level 2.

| Command | Description |
|---|---|
| `/mobwizardry summon <preset> <mobType> [pos]` | Spawns a mob of `<mobType>` with the preset applied (tag, equipment, mana, wizard AI) immediately. |
| `/mobwizardry tag <preset> <targets>` | Adds the preset's required tag to existing entities and fully initializes matching mobs. |
| `/mobwizardry untag <preset> <targets>` | Removes the tag — the wizard AI deactivates on the next tick. |
| `/mobwizardry reload` | Re-reads and re-validates `presets.json` without restarting. |
| `/mobwizardry list` | Lists loaded presets with their spell kits. |

### Examples

```
/mobwizardry summon wizard minecraft:zombie
/mobwizardry summon wizard minecraft:zombie 100 64 100
/mobwizardry tag wizard @e[type=minecraft:zombie]
/mobwizardry untag wizard @e[type=minecraft:zombie]
/mobwizardry reload
/mobwizardry list
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
- The `wizard` preset in the default config is an example — copy it and change `requiredTag`, `targetMobs` and spell IDs to taste.
