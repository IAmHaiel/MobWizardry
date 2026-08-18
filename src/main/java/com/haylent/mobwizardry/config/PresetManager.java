package com.haylent.mobwizardry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.haylent.mobwizardry.MobWizardryMod;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /**
     * Boss configs parsed from {@code bosses.json}, keyed by the preset name they apply to.
     * Merged into the matching preset's {@code boss} field during {@link #reload()}.
     */
    private static final Map<String, PresetDefinition.Boss> BOSS_CONFIGS = new LinkedHashMap<>();

    /**
     * requiredTag → preset index built at load so the entity-join handler finds the preset for a
     * mob's tag with a map lookup instead of scanning every preset. One preset per tag is
     * expected; a duplicate tag is warned about and the last preset wins.
     */
    private static final Map<String, PresetDefinition> BY_TAG = new LinkedHashMap<>();

    /**
     * Raid definitions parsed from {@code raids.json}, keyed by raid name.
     */
    private static final Map<String, RaidDefinition> RAIDS = new LinkedHashMap<>();
    private static final Map<String, RaidDefinition> RAIDS_VIEW = Collections.unmodifiableMap(RAIDS);

    public static Map<String, PresetDefinition> getPresets()
    {
        return PRESETS_VIEW;
    }

    public static PresetDefinition getPreset(String name)
    {
        return PRESETS.get(name);
    }

    public static PresetDefinition getPresetByTag(String tag)
    {
        return BY_TAG.get(tag);
    }

    public static Map<String, RaidDefinition> getRaids()
    {
        return RAIDS_VIEW;
    }

    public static RaidDefinition getRaid(String name)
    {
        return RAIDS.get(name);
    }

    /**
     * Reloads {@code presets.json} first, then {@code bosses.json} (spawn settings + per-boss
     * definitions, validated against the matching preset), merges each boss config into its
     * preset, and logs the effective presets. {@code /mobwizardry reload} calls this so boss
     * edits take effect without a restart.
     */
    public static void reload()
    {
        PRESETS.clear();
        BOSS_CONFIGS.clear();
        BY_TAG.clear();
        RAIDS.clear();
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(MobWizardryMod.MODID);

        List<String> legacyNames = new ArrayList<>();
        loadPresetsFile(configDir, legacyNames);
        loadNamesFile(configDir, legacyNames);
        loadBossesFile(configDir);

        for (Map.Entry<String, PresetDefinition> entry : PRESETS.entrySet())
        {
            applyBossConfig(entry.getKey(), entry.getValue());
            logLoadedPreset(entry.getKey(), entry.getValue());
        }
        rebuildTagIndex();
        // Load raids after the boss configs are merged so the raid boss check sees the real
        // boss-enabled state.
        loadRaidsFile(configDir);
        for (String bossKey : BOSS_CONFIGS.keySet())
        {
            if (!PRESETS.containsKey(bossKey))
            {
                LOGGER.warn("[MobWizardry] Boss '{}' in bosses.json has no matching preset in presets.json - it is ignored", bossKey);
            }
        }
    }

    /**
     * Builds the requiredTag → preset index. A tag used by more than one preset is warned about
     * and the last preset wins (the mod assumes one preset per tag).
     */
    private static void rebuildTagIndex()
    {
        for (Map.Entry<String, PresetDefinition> entry : PRESETS.entrySet())
        {
            String tag = entry.getValue().requiredTag;
            if (tag == null || tag.isBlank())
            {
                continue;
            }
            if (BY_TAG.put(tag, entry.getValue()) != null)
            {
                LOGGER.warn("[MobWizardry] Preset '{}' reuses the requiredTag '{}' of an earlier preset - only '{}' applies to mobs carrying that tag", entry.getKey(), tag, entry.getKey());
            }
        }
    }

    private static void loadBossesFile(Path configDir)
    {
        Path configPath = configDir.resolve("bosses.json");
        try
        {
            Files.createDirectories(configDir);
            if (!Files.exists(configPath))
            {
                Files.writeString(configPath, defaultBossesJson());
                LOGGER.warn("[MobWizardry] No bosses.json found - wrote default config to {}", configPath);
            }

            try (Reader reader = Files.newBufferedReader(configPath))
            {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (rootElement == null || !rootElement.isJsonObject())
                {
                    LOGGER.error("[MobWizardry] bosses.json is empty or invalid - no boss config loaded");
                    return;
                }
                JsonObject root = rootElement.getAsJsonObject();

                JsonElement spawnSettings = root.get("_spawnSettings");
                if (spawnSettings != null && spawnSettings.isJsonObject())
                {
                    LOGGER.warn("[MobWizardry] Top-level '_spawnSettings' in bosses.json is ignored - it moved into each boss's \"spawnSettings\" block");
                }

                JsonElement bosses = root.get("bosses");
                if (bosses == null || !bosses.isJsonObject())
                {
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : bosses.getAsJsonObject().entrySet())
                {
                    String bossKey = entry.getKey();
                    PresetDefinition.Boss boss = GSON.fromJson(entry.getValue(), PresetDefinition.Boss.class);
                    if (boss == null)
                    {
                        LOGGER.error("[MobWizardry] Boss '{}' in bosses.json could not be parsed - skipped", bossKey);
                        continue;
                    }
                    PresetDefinition preset = PRESETS.get(bossKey);
                    validateBossConfig(bossKey, boss, preset != null ? preset.castInterval : 60);
                    BOSS_CONFIGS.put(bossKey, boss);
                }
            }
        }
        catch (JsonSyntaxException e)
        {
            LOGGER.error("[MobWizardry] Failed to parse bosses.json - no boss config loaded", e);
        }
        catch (IOException e)
        {
            LOGGER.error("[MobWizardry] Could not read bosses.json - no boss config loaded", e);
        }
    }


    private static void loadRaidsFile(Path configDir)
    {
        Path configPath = configDir.resolve("raids.json");
        try
        {
            Files.createDirectories(configDir);
            if (!Files.exists(configPath))
            {
                Files.writeString(configPath, defaultRaidsJson());
                LOGGER.warn("[MobWizardry] No raids.json found - wrote default config to {}", configPath);
            }

            try (Reader reader = Files.newBufferedReader(configPath))
            {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (rootElement == null || !rootElement.isJsonObject())
                {
                    LOGGER.error("[MobWizardry] raids.json is empty or invalid - no raids loaded");
                    return;
                }
                JsonObject root = rootElement.getAsJsonObject();
                JsonElement raids = root.get("raids");
                if (raids == null || !raids.isJsonObject())
                {
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : raids.getAsJsonObject().entrySet())
                {
                    RaidDefinition raid = GSON.fromJson(entry.getValue(), RaidDefinition.class);
                    if (raid == null)
                    {
                        LOGGER.error("[MobWizardry] Raid '{}' in raids.json could not be parsed - skipped", entry.getKey());
                        continue;
                    }
                    validateRaid(entry.getKey(), raid);
                    RAIDS.put(entry.getKey(), raid);
                }
            }
        }
        catch (JsonSyntaxException e)
        {
            LOGGER.error("[MobWizardry] Failed to parse raids.json - no raids loaded", e);
        }
        catch (IOException e)
        {
            LOGGER.error("[MobWizardry] Could not read raids.json - no raids loaded", e);
        }
    }

    private static void validateRaid(String name, RaidDefinition raid)
    {
        if (raid.name == null || raid.name.isBlank())
        {
            raid.name = name;
        }
        if (raid.waves == null)
        {
            raid.waves = new ArrayList<>();
        }
        raid.waves.removeIf(wave -> wave == null);
        if (raid.waves.isEmpty())
        {
            LOGGER.warn("[MobWizardry] Raid '{}' has no waves - starting it will end immediately", name);
        }
        for (RaidDefinition.RaidWave wave : raid.waves)
        {
            if (wave.number < 1)
            {
                wave.number = 1;
            }
            if (wave.enemies == null)
            {
                wave.enemies = new ArrayList<>();
            }
            wave.enemies.removeIf(enemy -> enemy == null);
            if (wave.enemies.isEmpty())
            {
                LOGGER.warn("[MobWizardry] Raid '{}' wave {} has no enemies - the wave is skipped when reached", name, wave.number);
                continue;
            }
            for (RaidDefinition.RaidEnemy enemy : wave.enemies)
            {
                if (enemy.preset == null || enemy.preset.isBlank() || !PRESETS.containsKey(enemy.preset))
                {
                    LOGGER.warn("[MobWizardry] Raid '{}' wave {} references an unknown preset '{}' - the group is skipped", name, wave.number, enemy.preset);
                    continue;
                }
                if (enemy.count < 1)
                {
                    LOGGER.warn("[MobWizardry] Raid '{}' wave {} preset '{}' has count {} below 1 - using 1", name, wave.number, enemy.preset, enemy.count);
                    enemy.count = 1;
                }
                if (enemy.weight < 0)
                {
                    LOGGER.warn("[MobWizardry] Raid '{}' wave {} preset '{}' has a negative weight ({}) - using 0", name, wave.number, enemy.preset, enemy.weight);
                    enemy.weight = 0;
                }
            }
        }
        if (raid.boss != null && !raid.boss.isBlank())
        {
            PresetDefinition bossPreset = PRESETS.get(raid.boss);
            if (bossPreset == null || bossPreset.boss == null || !bossPreset.boss.enabled)
            {
                LOGGER.warn("[MobWizardry] Raid '{}' boss '{}' is not a boss-enabled preset - the raid will end after the last wave", name, raid.boss);
                raid.boss = "";
            }
        }
        LOGGER.info("[MobWizardry] Loaded raid '{}' (name={}, waves={}, boss={})",
                name, raid.name, raid.waves.size(), raid.boss.isBlank() ? "none" : raid.boss);
    }

    private static void loadPresetsFile(Path configDir, List<String> legacyNamesOut)
    {
        Path configPath = configDir.resolve("presets.json");
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
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (rootElement == null || !rootElement.isJsonObject())
                {
                    LOGGER.error("[MobWizardry] presets.json is empty or invalid - no presets loaded");
                    return;
                }
                JsonObject root = rootElement.getAsJsonObject();

                for (Map.Entry<String, JsonElement> entry : root.entrySet())
                {
                    String presetName = entry.getKey();
                    if (presetName.startsWith("_"))
                    {
                        if ("_spawnSettings".equals(presetName))
                        {
                            LOGGER.warn("[MobWizardry] '_spawnSettings' in presets.json is ignored - it moved to config/mobwizardry/bosses.json");
                        }
                        else if ("_wizardDisplay".equals(presetName))
                        {
                            WizardDisplay.Settings parsed = GSON.fromJson(entry.getValue(), WizardDisplay.Settings.class);
                            validateWizardDisplayColors(parsed);
                            WizardDisplay.setSettings(parsed);
                            if (parsed.names != null)
                            {
                                for (String name : parsed.names)
                                {
                                    if (name != null && !name.isBlank() && !legacyNamesOut.contains(name.trim()))
                                    {
                                        legacyNamesOut.add(name.trim());
                                    }
                                }
                            }
                        }
                        continue;
                    }
                    PresetDefinition preset = GSON.fromJson(entry.getValue(), PresetDefinition.class);
                    if (preset == null)
                    {
                        LOGGER.error("[MobWizardry] Preset '{}' could not be parsed - skipped", presetName);
                        continue;
                    }
                    validatePreset(presetName, preset);
                    PRESETS.put(presetName, preset);
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

    /**
     * Overrides the preset's inline boss block with the bosses.json config when one exists (the
     * separate file wins). A preset that still defines its boss inline is honored with a warning
     * to migrate.
     */
    private static void applyBossConfig(String presetName, PresetDefinition preset)
    {
        PresetDefinition.Boss configured = BOSS_CONFIGS.get(presetName);
        if (configured != null)
        {
            preset.boss = configured;
        }
        else if (preset.boss != null && preset.boss.enabled)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' defines its boss inline in presets.json - move it to config/mobwizardry/bosses.json (it still works for now)", presetName);
        }
        if (preset.boss != null && preset.boss.enabled)
        {
            clearBossAttackSpells(presetName, preset);
        }
    }

    /**
     * A boss's attack spells are driven entirely by its combos, so any attack list on the boss
     * preset or its phases is ignored (warned once at load).
     */
    private static void clearBossAttackSpells(String presetName, PresetDefinition preset)
    {
        if (!preset.spells.attack.isEmpty())
        {
            LOGGER.warn("[MobWizardry] Boss preset '{}' defines attack spells - a boss's attack is driven by its combos, the attack spells are ignored", presetName);
            preset.spells.attack.clear();
        }
        for (PresetDefinition.BossPhase phase : preset.boss.phases)
        {
            if (!phase.spells.attack.isEmpty())
            {
                LOGGER.warn("[MobWizardry] Boss preset '{}' phase {} defines attack spells - a boss's attack is driven by its combos, the attack spells are ignored", presetName, phase.number);
                phase.spells.attack.clear();
            }
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
        validateFaction(name, preset);
        validateMovement(name, preset);
        validateRetaliation(name, preset);
        if (preset.boss == null)
        {
            preset.boss = new PresetDefinition.Boss();
        }
        else
        {
            validateBossConfig(name, preset.boss, preset.castInterval);
        }

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
    }

    private static void logLoadedPreset(String name, PresetDefinition preset)
    {
        int castMin = preset.castInterval;
        int castMax = preset.effectiveCastIntervalMax();
        String castInfo = ", castRange=" + castMin + "-" + castMax + "t";
        String teamInfo = preset.team != null && !preset.team.isBlank() ? ", team=" + preset.team : "";
        String factionInfo = preset.faction != null && !preset.faction.isBlank() ? ", faction=" + preset.faction : "";
        String skinInfo = preset.skin != null && !preset.skin.isBlank() ? ", skin=" + preset.skin : "";
        Double maxManaAttr = preset.attributes.get("irons_spellbooks:max_mana");
        String manaInfo = maxManaAttr != null ? ", max_mana=" + maxManaAttr : "";
        Double maxHealthAttr = preset.attributes.get("minecraft:generic.max_health");
        String maxHealthInfo = maxHealthAttr != null ? ", max_health=" + maxHealthAttr : "";
        String retaliationInfo = ", retaliation=" + preset.retaliationChance;
        long emergencyHeals = preset.spells.support.stream().filter(e -> e.emergency).count();
        String emergencyInfo = emergencyHeals > 0 ? ", emergencyHeals=" + emergencyHeals : "";
        int escapeCount = preset.spells.escape.size();
        String escapeInfo = escapeCount > 0 ? ", escape=" + escapeCount : "";
        String movementInfo = (preset.movementStartDistance > 0 || preset.movementFarDistance > 0)
                ? ", movement=" + (preset.movementStartDistance > 0 ? preset.movementStartDistance : "range*0.75") + "-" + (preset.movementFarDistance > 0 ? preset.movementFarDistance : "range") : "";
        String movementOffsetInfo = preset.movementDistanceOffset > 0 ? ", movementOffset=" + preset.movementDistanceOffset : "";
        String movementTooCloseInfo = preset.movementTooCloseDistance > 0 ? ", tooClose=" + preset.movementTooCloseDistance : "";
        String bossInfo = "";
        if (preset.boss != null && preset.boss.enabled)
        {
            bossInfo = ", boss=" + preset.boss.name
                    + ", phases=" + preset.boss.phases.size()
                    + ", dayW=" + preset.boss.daySpawnWeight
                    + ", nightW=" + preset.boss.nightSpawnWeight;
        }
        LOGGER.info("[MobWizardry] Loaded preset '{}' (tag={}, type={}{}{}{}{}{}{}{}{}{}{}{}{}{})", name, preset.requiredTag, preset.wizardType, teamInfo, factionInfo, skinInfo, castInfo, movementInfo, movementOffsetInfo, movementTooCloseInfo, manaInfo, maxHealthInfo, emergencyInfo, escapeInfo, retaliationInfo, bossInfo);
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

    private static void validateFaction(String name, PresetDefinition preset)
    {
        String faction = preset.faction == null ? "" : preset.faction.trim().toLowerCase();
        if (!"friendly".equals(faction) && !"enemy".equals(faction))
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has invalid faction '{}' - falling back to 'enemy'", name, preset.faction);
            preset.faction = "enemy";
        }
        else
        {
            preset.faction = faction;
        }
    }

    private static void validateMovement(String name, PresetDefinition preset)
    {
        if (preset.movementTooCloseDistance < 0)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has a negative movementTooCloseDistance ({}) - using 0", name, preset.movementTooCloseDistance);
            preset.movementTooCloseDistance = 0;
        }
    }

    private static void validateRetaliation(String name, PresetDefinition preset)
    {
        if (preset.retaliationChance < 0 || preset.retaliationChance > 1)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' has retaliationChance ({}) outside 0-1 - clamping", name, preset.retaliationChance);
            preset.retaliationChance = Math.max(0.0, Math.min(1.0, preset.retaliationChance));
        }
    }

    private static void validateBossConfig(String name, PresetDefinition.Boss boss, int castInterval)
    {
        if (!boss.enabled)
        {
            return;
        }
        if (boss.name == null || boss.name.isBlank())
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has a blank name - using the preset name", name);
            boss.name = name;
        }
        if (!isValidNameColor(boss.nameColor))
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has an invalid nameColor '{}' - using 'red'", name, boss.nameColor);
            boss.nameColor = "red";
        }
        if (boss.spawnEntity == null || boss.spawnEntity.isBlank())
        {
            boss.spawnEntity = "mobwizardry:wizard";
        }
        ResourceLocation spawnRl = ResourceLocation.tryParse(boss.spawnEntity.trim());
        if (spawnRl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(spawnRl))
        {
            LOGGER.warn("[MobWizardry] Boss '{}' spawnEntity '{}' is not a known entity - natural spawning is disabled for this boss (commands still work)", name, boss.spawnEntity);
            boss.spawnEntity = "";
        }
        else
        {
            boss.spawnEntity = spawnRl.toString();
        }
        if (boss.daySpawnWeight < 0)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has a negative daySpawnWeight ({}) - using 0", name, boss.daySpawnWeight);
            boss.daySpawnWeight = 0;
        }
        if (boss.nightSpawnWeight < 0)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has a negative nightSpawnWeight ({}) - using 0", name, boss.nightSpawnWeight);
            boss.nightSpawnWeight = 0;
        }
        validateSpawnSettings(name, boss);
        validateCombos(name, boss);
        if (boss.phases == null)
        {
            boss.phases = new ArrayList<>();
        }
        boss.phases.removeIf(phase -> phase == null);
        if (boss.phases.isEmpty())
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has no phases - it will just be a named boss with no phase behavior", name);
            return;
        }
        for (PresetDefinition.BossPhase phase : boss.phases)
        {
            if (phase.number < 1)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' has a phase with number {} (must be >= 1) - using 1", name, phase.number);
                phase.number = 1;
            }
            if (phase.healthPercent < 0 || phase.healthPercent > 100)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' phase {} has healthPercent {} outside 0-100 - clamping", name, phase.number, phase.healthPercent);
                phase.healthPercent = Math.max(0.0, Math.min(100.0, phase.healthPercent));
            }
            if (phase.spells == null)
            {
                phase.spells = new PresetDefinition.Spells();
            }
            validatePhaseEffects(name, phase);
            String phaseLabel = "boss phase " + phase.number;
            validateSpellList(name, phaseLabel + " attack", phase.spells.attack, castInterval);
            validateSpellList(name, phaseLabel + " defense", phase.spells.defense, castInterval);
            validateSpellList(name, phaseLabel + " movement", phase.spells.movement, castInterval);
            validateSpellList(name, phaseLabel + " support", phase.spells.support, castInterval);
            validateSpellList(name, phaseLabel + " escape", phase.spells.escape, castInterval);
        }
        // Highest healthPercent first so phase 1 (usually 100) is the boss's starting kit; ties
        // keep the file order via the original index.
        Map<PresetDefinition.BossPhase, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < boss.phases.size(); i++)
        {
            order.put(boss.phases.get(i), i);
        }
        boss.phases.sort((a, b) -> {
            int cmp = Double.compare(b.healthPercent, a.healthPercent);
            return cmp != 0 ? cmp : Integer.compare(order.get(a), order.get(b));
        });
    }

    private static void validateSpawnSettings(String name, PresetDefinition.Boss boss)
    {
        if (boss.spawnSettings == null)
        {
            boss.spawnSettings = new PresetDefinition.Boss.SpawnSettings();
            return;
        }
        if (boss.spawnSettings.spawnAttemptIntervalSeconds == 300 && boss.spawnSettings.attemptIntervalSeconds != 0)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' uses the old 'attemptIntervalSeconds' field - renamed to 'spawnAttemptIntervalSeconds' (the seconds between natural-spawn attempts)", name);
            boss.spawnSettings.spawnAttemptIntervalSeconds = boss.spawnSettings.attemptIntervalSeconds;
            boss.spawnSettings.attemptIntervalSeconds = 0;
        }
        if (boss.spawnSettings.spawnAttemptIntervalSeconds < 1)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has spawnAttemptIntervalSeconds ({}) below 1 - using 1", name, boss.spawnSettings.spawnAttemptIntervalSeconds);
            boss.spawnSettings.spawnAttemptIntervalSeconds = 1;
        }
        if (boss.spawnSettings.maxActiveBosses < 0)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has a negative maxActiveBosses ({}) - using 0", name, boss.spawnSettings.maxActiveBosses);
            boss.spawnSettings.maxActiveBosses = 0;
        }
        double min = boss.spawnSettings.minDistanceFromPlayer;
        double max = boss.spawnSettings.maxDistanceFromPlayer;
        double minClamped = Math.max(1.0, min);
        double maxClamped = Math.max(minClamped, max);
        if (min != minClamped)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has minDistanceFromPlayer ({}) below 1 - using 1", name, min);
            boss.spawnSettings.minDistanceFromPlayer = minClamped;
        }
        if (max != maxClamped)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has maxDistanceFromPlayer ({}) below minDistanceFromPlayer ({}) - using {}", name, max, minClamped, maxClamped);
            boss.spawnSettings.maxDistanceFromPlayer = maxClamped;
        }
        if (boss.spawnSettings.spawnGlowSeconds < 0)
        {
            LOGGER.warn("[MobWizardry] Boss '{}' has a negative spawnGlowSeconds ({}) - using 0 (no glow)", name, boss.spawnSettings.spawnGlowSeconds);
            boss.spawnSettings.spawnGlowSeconds = 0;
        }
    }

    private static void validateCombos(String name, PresetDefinition.Boss boss)
    {
        if (boss.combos == null)
        {
            boss.combos = new ArrayList<>();
            return;
        }
        boss.combos.removeIf(combo -> combo == null);
        if (boss.combos.isEmpty())
        {
            return;
        }
        for (PresetDefinition.Combo combo : boss.combos)
        {
            if (combo.pauseAfterComboExecution == 0 && combo.tickBeforeComboExecution != 0)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' combo uses the old 'tickBeforeComboExecution' field - renamed to 'pauseAfterComboExecution' (the pause after the combo runs, before the next random combo)", name);
                combo.pauseAfterComboExecution = combo.tickBeforeComboExecution;
                combo.tickBeforeComboExecution = 0;
            }
            if (combo.pauseAfterComboExecution == 0 && combo.castInterval != 0)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' combo uses the old 'castInterval' field - renamed to 'pauseAfterComboExecution' (the pause after the combo runs, before the next random combo)", name);
                combo.pauseAfterComboExecution = combo.castInterval;
                combo.castInterval = 0;
            }
            if (combo.pauseAfterComboExecution < 0)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' has a combo with a negative pauseAfterComboExecution ({}) - using 0 (preset castInterval)", name, combo.pauseAfterComboExecution);
                combo.pauseAfterComboExecution = 0;
            }
            if (combo.steps == null)
            {
                combo.steps = new ArrayList<>();
            }
            combo.steps.removeIf(step -> step == null);
            if (combo.steps.isEmpty())
            {
                LOGGER.warn("[MobWizardry] Boss '{}' has a combo with no steps - it is skipped when selected", name);
                continue;
            }
            for (PresetDefinition.ComboStep step : combo.steps)
            {
                if (!isValidComboCategory(step.category))
                {
                    LOGGER.warn("[MobWizardry] Boss '{}' combo step '{}' has an invalid category '{}' - using 'attack'", name, step.spell, step.category);
                    step.category = "attack";
                }
                if (step.spell == null || step.spell.isBlank() || step.resolveSpell() == null)
                {
                    LOGGER.warn("[MobWizardry] Boss '{}' has a combo step with an unknown spell '{}' - the step is skipped", name, step.spell);
                }
                if (step.level < 1)
                {
                    step.level = 1;
                }
                AbstractSpell resolved = step.resolveSpell();
                if (resolved != null && step.level > resolved.getMaxLevel())
                {
                    LOGGER.warn("[MobWizardry] Boss '{}' combo step '{}' has level {} above the spell's max ({}) - clamping", name, step.spell, step.level, resolved.getMaxLevel());
                    step.level = resolved.getMaxLevel();
                }
                if (step.castAfterTicks < 0)
                {
                    LOGGER.warn("[MobWizardry] Boss '{}' combo step '{}' has a negative castAfterTicks ({}) - using 0", name, step.spell, step.castAfterTicks);
                    step.castAfterTicks = 0;
                }
            }
        }
    }

    private static void validatePhaseEffects(String name, PresetDefinition.BossPhase phase)
    {
        if (phase.effects == null)
        {
            phase.effects = new ArrayList<>();
            return;
        }
        phase.effects.removeIf(effect -> effect == null);
        for (PresetDefinition.PhaseEffect effect : phase.effects)
        {
            if (effect.id == null || effect.id.isBlank() || effect.resolveEffect() == null)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' phase {} has an unknown effect '{}' - the effect is skipped", name, phase.number, effect.id);
                continue;
            }
            if (effect.amplifier < 0)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' phase {} effect '{}' has a negative amplifier ({}) - using 0", name, phase.number, effect.id, effect.amplifier);
                effect.amplifier = 0;
            }
            if (effect.duration < -1)
            {
                LOGGER.warn("[MobWizardry] Boss '{}' phase {} effect '{}' has a duration below -1 ({}) - using -1 (infinite)", name, phase.number, effect.id, effect.duration);
                effect.duration = -1;
            }
        }
    }

    private static boolean isValidComboCategory(String category)
    {
        if (category == null)
        {
            return false;
        }
        return switch (category.trim().toLowerCase())
        {
            case "attack", "defense", "support", "movement", "escape" -> true;
            default -> false;
        };
    }

    private static void validateWizardDisplayColors(WizardDisplay.Settings settings)
    {
        if (settings == null)
        {
            WizardDisplay.setSettings(new WizardDisplay.Settings());
            return;
        }
        if (!isValidNameColor(settings.nameColor))
        {
            LOGGER.warn("[MobWizardry] _wizardDisplay has an invalid nameColor '{}' - using 'white'", settings.nameColor);
            settings.nameColor = "white";
        }
        if (!isValidNameColor(settings.teamColor))
        {
            LOGGER.warn("[MobWizardry] _wizardDisplay has an invalid teamColor '{}' - using 'gray'", settings.teamColor);
            settings.teamColor = "gray";
        }
    }

    /**
     * Loads the random wizard-name pool from {@code names.json} (a JSON array of strings). A
     * legacy {@code _wizardDisplay.names} list in presets.json is kept when names.json is missing
     * (the default file is still written for the user to edit) or ignored with a warning when a
     * valid names.json exists. An empty pool leaves the current pool untouched and is warned
     * about once.
     */
    private static void loadNamesFile(Path configDir, List<String> legacyNames)
    {
        Path configPath = configDir.resolve("names.json");
        try
        {
            Files.createDirectories(configDir);
            if (!Files.exists(configPath))
            {
                Files.writeString(configPath, defaultNamesJson());
                LOGGER.warn("[MobWizardry] No names.json found - wrote default config to {}", configPath);
                if (!legacyNames.isEmpty())
                {
                    WizardDisplay.setNames(legacyNames);
                    LOGGER.warn("[MobWizardry] Your presets.json '_wizardDisplay.names' is kept as the name pool - edit config/mobwizardry/names.json to change it");
                    return;
                }
            }

            List<String> names = readNames(configPath);
            if (names == null)
            {
                return;
            }
            if (names.isEmpty())
            {
                LOGGER.warn("[MobWizardry] names.json has no valid names - summoned wizards (non-boss) will have no name tag");
                return;
            }
            WizardDisplay.setNames(names);
            LOGGER.info("[MobWizardry] Loaded {} wizard names from names.json", names.size());
            if (!legacyNames.isEmpty())
            {
                LOGGER.warn("[MobWizardry] presets.json '_wizardDisplay.names' is ignored - random names now come from config/mobwizardry/names.json");
            }
        }
        catch (IOException e)
        {
            LOGGER.error("[MobWizardry] Could not read names.json - keeping the current name pool", e);
        }
    }

    private static List<String> readNames(Path path)
    {
        try (Reader reader = Files.newBufferedReader(path))
        {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray())
            {
                LOGGER.error("[MobWizardry] names.json is empty or invalid - keeping the current name pool");
                return null;
            }
            List<String> names = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray())
            {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
                {
                    String name = element.getAsString().trim();
                    if (!name.isEmpty())
                    {
                        names.add(name);
                    }
                }
            }
            return names;
        }
        catch (JsonSyntaxException | IOException e)
        {
            LOGGER.error("[MobWizardry] Failed to parse names.json - keeping the current name pool", e);
            return null;
        }
    }

    private static String defaultNamesJson()
    {
        return """
                [
                  "Vodyaniski", "Alech", "Mordecai", "Seraphine", "Kael",
                  "Ilyana", "Draven", "Elysia", "Rowan", "Zephyr"
                ]
                """;
    }

    private static boolean isValidNameColor(String nameColor)
    {
        return PresetDefinition.parseNameColor(nameColor) != null;
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
            warnOnNonVanillaArmor(presetName, entry.getKey(), rl);
            return false;
        });
    }

    /**
     * Warns when an equipped armor item uses a custom armor material: the vanilla armor layer
     * builds {@code textures/models/armor/<material>_layer_1.png} in the minecraft namespace, so
     * such items never render on wizard NPCs (the item's effects still apply). Vanilla materials
     * (leather/chain/iron/gold/diamond/turtle/netherite) have overlay textures and render fine.
     */
    private static void warnOnNonVanillaArmor(String presetName, String slotName, ResourceLocation itemId)
    {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (!(item instanceof ArmorItem armorItem))
        {
            return;
        }
        String material = armorItem.getMaterial().getName();
        boolean vanilla = switch (material)
        {
            case "leather", "chain", "iron", "gold", "diamond", "turtle", "netherite" -> true;
            default -> false;
        };
        if (!vanilla)
        {
            LOGGER.warn("[MobWizardry] Preset '{}' equips '{}' (armor material '{}') in {} - it has no vanilla overlay texture and will not render on wizard NPCs (its effects still apply); use a vanilla armor item for visible gear",
                    presetName, itemId, material, slotName);
        }
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
                  "_wizardDisplay": {
                    "nameColor": "white",
                    "teamColor": "gray"
                  },
                  "wizard": {
                    "requiredTag": "wizard",
                    "wizardType": "ranged",
                    "team": "undead",
                    "faction": "enemy",
                    "speed": 1.15,
                    "castInterval": 60,
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
                    "faction": "enemy",
                    "speed": 1.1,
                    "castInterval": 80,
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
                    "faction": "enemy",
                    "speed": 1.15,
                    "castInterval": 60,
                    "castIntervalMax": 100,
                    "movementStartDistance": 15.0,
                    "movementFarDistance": 20.0,
                    "movementDistanceOffset": 5.0,
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
                    "team": "undead",
                    "faction": "enemy",
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
                """;
    }

    private static String defaultBossesJson()
    {
        return """
                {
                  "bosses": {
                    "wizard_boss": {
                      "enabled": true,
                      "name": "Aetheron, the Crimson Archon",
                      "nameColor": "dark_red",
                      "spawnEntity": "mobwizardry:wizard",
                      "spawnSettings": {
                        "enabled": true,
                        "spawnAttemptIntervalSeconds": 300,
                        "maxActiveBosses": 3,
                        "minDistanceFromPlayer": 24,
                        "maxDistanceFromPlayer": 48,
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
                            "defense": [],
                            "movement": [],
                            "support": [],
                            "escape": []
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
                            "defense": [
                              { "id": "irons_spellbooks:shield", "level": 1 }
                            ],
                            "movement": [
                              { "id": "irons_spellbooks:blood_step", "level": 1 }
                            ],
                            "support": [
                              { "id": "irons_spellbooks:heal", "level": 1, "emergency": true }
                            ],
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
                            "defense": [
                              { "id": "irons_spellbooks:shield", "level": 1 }
                            ],
                            "movement": [
                              { "id": "irons_spellbooks:teleport", "level": 1 },
                              { "id": "irons_spellbooks:blood_step", "level": 1 }
                            ],
                            "support": [
                              { "id": "irons_spellbooks:heal", "level": 2, "emergency": true },
                              { "id": "irons_spellbooks:fortify", "level": 1 }
                            ],
                            "escape": [
                              { "id": "irons_spellbooks:teleport", "level": 1 }
                            ]
                          }
                        }
                      ],
                      "combos": [
                        {
                          "pauseAfterComboExecution": 40,
                          "steps": [
                            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "castAfterTicks": 10 },
                            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "castAfterTicks": 25 },
                            { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 1, "castAfterTicks": 50 },
                            { "category": "movement", "spell": "irons_spellbooks:blood_step", "level": 1, "castAfterTicks": 80 }
                          ]
                        },
                        {
                          "pauseAfterComboExecution": 60,
                          "steps": [
                            { "category": "attack", "spell": "irons_spellbooks:fireball", "level": 1, "castAfterTicks": 20 },
                            { "category": "attack", "spell": "irons_spellbooks:magic_missile", "level": 1, "castAfterTicks": 40 },
                            { "category": "escape", "spell": "irons_spellbooks:blood_step", "level": 1, "castAfterTicks": 120 }
                          ]
                        }
                      ]
                    }
                  }
                }
                """;
    }

    private static String defaultRaidsJson()
    {
        return """
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
                      "boss": "wizard_boss"
                    }
                  }
                }
                """;
    }
}
