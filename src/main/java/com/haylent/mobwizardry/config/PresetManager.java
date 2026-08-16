package com.haylent.mobwizardry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.haylent.mobwizardry.MobWizardryMod;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PresetManager
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, PresetDefinition> PRESETS = new LinkedHashMap<>();
    private static final Map<String, PresetDefinition> PRESETS_VIEW = Collections.unmodifiableMap(PRESETS);

    public static Map<String, PresetDefinition> getPresets()
    {
        return PRESETS_VIEW;
    }

    public static PresetDefinition getPreset(String name)
    {
        return PRESETS.get(name);
    }

    public static void reload()
    {
        PRESETS.clear();
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(MobWizardryMod.MODID).resolve("presets.json");
        Path configDir = configPath.getParent();

        try
        {
            Files.createDirectories(configDir);

            if (!Files.exists(configPath))
            {
                Files.writeString(configPath, defaultPresetsJson());
                LOGGER.warn("[MobWizardry] No presets.json found - wrote default config to {}", configPath);
            }

            try (Reader reader = Files.newBufferedReader(configPath))
            {
                Map<String, PresetDefinition> parsed = GSON.fromJson(reader, new com.google.gson.reflect.TypeToken<Map<String, PresetDefinition>>() {}.getType());
                if (parsed == null)
                {
                    LOGGER.error("[MobWizardry] presets.json is empty or invalid - no presets loaded");
                    return;
                }

                for (Map.Entry<String, PresetDefinition> entry : parsed.entrySet())
                {
                    validatePreset(entry.getKey(), entry.getValue());
                }
            }
        }
        catch (JsonSyntaxException e)
        {
            LOGGER.error("[MobWizardry] Failed to parse presets.json - no presets loaded", e);
        }
        catch (IOException e)
        {
            LOGGER.error("[MobWizardry] Could not read presets.json - no presets loaded", e);
        }
    }

    private static void validatePreset(String name, PresetDefinition preset)
    {
        if (preset.requiredTag == null || preset.requiredTag.isBlank())
        {
            LOGGER.error("[MobWizardry] Preset '{}' has no requiredTag - preset will not activate. Add a tag like \"wizard\".", name);
            return;
        }

        validateWizardType(name, preset);
        validateTeam(name, preset);

        validateSpellList(name, "attack", preset.spells.attack, preset.castInterval);
        validateSpellList(name, "defense", preset.spells.defense, preset.castInterval);
        validateSpellList(name, "movement", preset.spells.movement, preset.castInterval);
        validateSpellList(name, "support", preset.spells.support, preset.castInterval);
        validateSpellList(name, "escape", preset.spells.escape, preset.castInterval);

        validateEquipment(name, preset);
        validateAttributes(name, preset);

        int castMin = preset.castInterval;
        int castMax = preset.effectiveCastIntervalMax();
        if (preset.castIntervalMax > 0 && preset.castIntervalMax < castMin)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has castIntervalMax ({}) smaller than castInterval ({}) - using castInterval*2 ({})", name, preset.castIntervalMax, castMin, castMax);
        }
        if (preset.movementStartDistance > 0 && preset.movementFarDistance > 0 && preset.movementStartDistance >= preset.movementFarDistance)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has movementStartDistance ({}) >= movementFarDistance ({}) - ignoring both, movement triggers will be derived from the spell range", name, preset.movementStartDistance, preset.movementFarDistance);
        }
        if (preset.movementDistanceOffset < 0)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has a negative movementDistanceOffset ({}) - using 0", name, preset.movementDistanceOffset);
            preset.movementDistanceOffset = 0;
        }
        Double maxManaAttr = preset.attributes.get("irons_spellbooks:max_mana");
        String castInfo = ", castRange=" + castMin + "-" + castMax + "t";
        String teamInfo = preset.team != null && !preset.team.isBlank() ? ", team=" + preset.team : "";
        String manaInfo = maxManaAttr != null ? ", max_mana=" + maxManaAttr : "";
        long emergencyHeals = preset.spells.support.stream().filter(e -> e.emergency).count();
        String emergencyInfo = emergencyHeals > 0 ? ", emergencyHeals=" + emergencyHeals : "";
        int escapeCount = preset.spells.escape.size();
        String escapeInfo = escapeCount > 0 ? ", escape=" + escapeCount : "";
        String movementInfo = (preset.movementStartDistance > 0 || preset.movementFarDistance > 0)
                ? ", movement=" + (preset.movementStartDistance > 0 ? preset.movementStartDistance : "range*0.75") + "-" + (preset.movementFarDistance > 0 ? preset.movementFarDistance : "range") : "";
        String movementOffsetInfo = preset.movementDistanceOffset > 0 ? ", movementOffset=" + preset.movementDistanceOffset : "";
        PRESETS.put(name, preset);
        LOGGER.info("[MobWizardry] Loaded preset '{}' (tag={}, type={}{}{}{}{}{}{}{})", name, preset.requiredTag, preset.wizardType, teamInfo, castInfo, movementInfo, movementOffsetInfo, manaInfo, emergencyInfo, escapeInfo);
    }

    private static void validateWizardType(String name, PresetDefinition preset)
    {
        String type = preset.wizardType == null ? "" : preset.wizardType.trim().toLowerCase();
        if (!"ranged".equals(type) && !"close".equals(type))
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has invalid wizard_type '{}' - falling back to 'ranged'", name, preset.wizardType);
            preset.wizardType = "ranged";
        }
        else
        {
            preset.wizardType = type;
        }
    }

    private static void validateTeam(String name, PresetDefinition preset)
    {
        if (preset.team == null)
        {
            preset.team = "";
        }
        else
        {
            preset.team = preset.team.trim();
        }
    }

    private static void validateEquipment(String presetName, PresetDefinition preset)
    {
        preset.equipment.entrySet().removeIf(entry -> {
            if (entry.getValue() == null || entry.getValue().isBlank())
            {
                LOGGER.error("[MobWizardry] Preset '{}' equipment slot '{}' has a blank item id - removed", presetName, entry.getKey());
                return true;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getValue());
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl))
            {
                LOGGER.error("[MobWizardry] Preset '{}' equipment slot '{}' references unknown item '{}' - removed", presetName, entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });
    }

    private static void validateAttributes(String presetName, PresetDefinition preset)
    {
        preset.attributes.entrySet().removeIf(entry -> {
            if (entry.getKey() == null || entry.getKey().isBlank())
            {
                LOGGER.error("[MobWizardry] Preset '{}' has a blank attribute id - removed", presetName);
                return true;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null || !ForgeRegistries.ATTRIBUTES.containsKey(rl))
            {
                LOGGER.error("[MobWizardry] Preset '{}' references unknown attribute '{}' - removed", presetName, entry.getKey());
                return true;
            }
            return false;
        });
    }

    private static void validateSpellList(String presetName, String category, List<PresetDefinition.SpellEntry> entries, int castInterval)
    {
        entries.removeIf(entry -> {
            if (entry.id == null || entry.id.isBlank())
            {
                LOGGER.error("[MobWizardry] Preset '{}' {} spell has a blank id - removed", presetName, category);
                return true;
            }
            AbstractSpell spell = entry.resolveSpell();
            if (spell == null)
            {
                LOGGER.error("[MobWizardry] Preset '{}' {} spell '{}' not found in Iron's Spellbooks registry - removed", presetName, category, entry.id);
                return true;
            }
            if (entry.level < 1 || entry.level > spell.getMaxLevel())
            {
                LOGGER.warn("[MobWizardry] Preset '{}' {} spell '{}' level {} is out of range 1-{} - clamped", presetName, category, entry.id, entry.level, spell.getMaxLevel());
                entry.level = Math.max(1, Math.min(spell.getMaxLevel(), entry.level));
            }
            int cooldownTicks = spell.getSpellCooldown();
            if (cooldownTicks > castInterval)
            {
                LOGGER.warn("[MobWizardry] Preset '{}' {} spell '{}' has an intrinsic cooldown of {} ticks, longer than castInterval {} - spell will be cast less often than intended", presetName, category, entry.id, cooldownTicks, castInterval);
            }
            if (entry.emergency && !"support".equals(category))
            {
                LOGGER.warn("[MobWizardry] Preset '{}' {} spell '{}' has emergency=true, but that flag only applies to support spells - ignored", presetName, category, entry.id);
                entry.emergency = false;
            }
            LOGGER.info("[MobWizardry] Preset '{}' {} spell '{}' (level {}) cooldown={}t{}", presetName, category, entry.id, entry.level, cooldownTicks, entry.emergency ? " emergency" : "");
            return false;
        });
    }

    private static String defaultPresetsJson()
    {
        return """
                {
                  "wizard": {
                    "requiredTag": "wizard",
                    "wizardType": "ranged",
                    "team": "undead",
                    "speed": 1.15,
                    "castInterval": 60,
                    "castIntervalMax": 0,
                    "movementStartDistance": 0,
                    "movementFarDistance": 0,
                    "movementDistanceOffset": 5.0,
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
                    "team": "undead",
                    "speed": 1.1,
                    "castInterval": 80,
                    "castIntervalMax": 0,
                    "movementStartDistance": 0,
                    "movementFarDistance": 0,
                    "movementDistanceOffset": 5.0,
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
                    "team": "undead",
                    "speed": 1.15,
                    "castInterval": 60,
                    "castIntervalMax": 100,
                    "movementStartDistance": 15.0,
                    "movementFarDistance": 20.0,
                    "movementDistanceOffset": 5.0,
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
                    "team": "undead",
                    "speed": 1.2,
                    "castInterval": 50,
                    "castIntervalMax": 0,
                    "movementStartDistance": 0,
                    "movementFarDistance": 0,
                    "movementDistanceOffset": 5.0,
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
                """;
    }
}
