package com.haylent.mobwizardry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.haylent.mobwizardry.MobWizardryMod;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class PresetManager
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, PresetDefinition> PRESETS = new LinkedHashMap<>();

    public static Map<String, PresetDefinition> getPresets()
    {
        return PRESETS;
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

        validateSpellList(name, "attack", preset.spells.attack, preset.castInterval, preset.mana);
        validateSpellList(name, "defense", preset.spells.defense, preset.castInterval, preset.mana);
        validateSpellList(name, "movement", preset.spells.movement, preset.castInterval, preset.mana);
        validateSpellList(name, "support", preset.spells.support, preset.castInterval, preset.mana);

        validateEquipment(name, preset);
        validateAttributes(name, preset);

        Double maxManaAttr = preset.attributes.get("irons_spellbooks:max_mana");
        if (maxManaAttr != null && maxManaAttr > 0 && preset.mana > maxManaAttr)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has starting mana ({}) higher than its max_mana attribute ({}) - starting mana will be capped at max_mana", name, preset.mana, maxManaAttr);
        }

        String manaInfo = preset.mana > 0
                ? "starting mana=" + preset.mana
                : "starting mana=full (mana omitted)";
        if (maxManaAttr != null)
        {
            manaInfo += ", max_mana=" + maxManaAttr;
        }
        PRESETS.put(name, preset);
        LOGGER.info("[MobWizardry] Loaded preset '{}' (tag={}, {})", name, preset.requiredTag, manaInfo);
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
            if (rl == null || !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(rl))
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
            if (rl == null || !net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.containsKey(rl))
            {
                LOGGER.error("[MobWizardry] Preset '{}' references unknown attribute '{}' - removed", presetName, entry.getKey());
                return true;
            }
            return false;
        });
    }

    private static void validateSpellList(String presetName, String category, java.util.List<PresetDefinition.SpellEntry> entries, int castInterval, float presetMana)
    {
        entries.removeIf(entry -> {
            if (entry.id == null || entry.id.isBlank())
            {
                LOGGER.error("[MobWizardry] Preset '{}' {} spell has a blank id - removed", presetName, category);
                return true;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.id);
            if (rl == null)
            {
                LOGGER.error("[MobWizardry] Preset '{}' {} spell '{}' is not a valid resource location - removed", presetName, category, entry.id);
                return true;
            }
            AbstractSpell spell = SpellRegistry.getSpell(rl);
            if (spell == null || spell == SpellRegistry.none())
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
            int manaCost = spell.getManaCost(entry.level);
            if (cooldownTicks > castInterval)
            {
                LOGGER.warn("[MobWizardry] Preset '{}' {} spell '{}' has an intrinsic cooldown of {} ticks, longer than castInterval {} - spell will be cast less often than intended", presetName, category, entry.id, cooldownTicks, castInterval);
            }
            if (presetMana > 0 && manaCost > presetMana)
            {
                LOGGER.warn("[MobWizardry] Preset '{}' {} spell '{}' costs {} mana at level {}, but preset mana is {} - mob may not have enough mana to cast", presetName, category, entry.id, manaCost, entry.level, presetMana);
            }
            LOGGER.info("[MobWizardry] Preset '{}' {} spell '{}' (level {}) cooldown={}t mana={}", presetName, category, entry.id, entry.level, cooldownTicks, manaCost);
            return false;
        });
    }

    private static String defaultPresetsJson()
    {
        return """
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
                """;
    }
}
