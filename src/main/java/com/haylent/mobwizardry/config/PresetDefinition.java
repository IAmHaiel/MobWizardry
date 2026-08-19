package com.haylent.mobwizardry.config;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PresetDefinition
{
    public String requiredTag = "";
    public String wizardType = "ranged";
    public String team = "";
    public String faction = "enemy";
    public String skin = "";
    public double speed = 1.0;
    public int castInterval = 60;
    public int castIntervalMax = 0;
    public double movementStartDistance = 0;
    public double movementFarDistance = 0;
    public double movementDistanceOffset = 5.0;
    public double movementTooCloseDistance = 5.0;
    public double retaliationChance = 0.4;
    public Map<String, String> equipment = new HashMap<>();
    public Map<String, Double> attributes = new HashMap<>();
    public Spells spells = new Spells();
    public Boss boss = new Boss();

    public static EquipmentSlot parseSlot(String name)
    {
        return switch (name.toLowerCase())
        {
            case "mainhand", "main_hand", "hand" -> EquipmentSlot.MAINHAND;
            case "offhand", "off_hand" -> EquipmentSlot.OFFHAND;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    /**
     * Resolves an item id from the registry, or null if it is missing/invalid.
     */
    public static Item resolveItem(String itemId)
    {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    /**
     * Resolves an attribute id from the registry, or null if it is missing/invalid.
     */
    public static Attribute resolveAttribute(String attributeId)
    {
        ResourceLocation rl = ResourceLocation.tryParse(attributeId);
        return rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
    }

    public int effectiveCastIntervalMax()
    {
        return castIntervalMax > castInterval ? castIntervalMax : castInterval * 2;
    }

    /**
     * Resolves a spell id from the registry, or null if it is missing/invalid. Shared by the
     * preset spell entries and the boss combo steps so spell resolution lives in one place.
     */
    public static AbstractSpell resolveSpell(String id)
    {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null)
        {
            return null;
        }
        AbstractSpell spell = SpellRegistry.getSpell(rl);
        if (spell == null || spell == SpellRegistry.none())
        {
            return null;
        }
        return spell;
    }

    public static class Spells
    {
        public List<SpellEntry> attack = new ArrayList<>();
        public List<SpellEntry> defense = new ArrayList<>();
        public List<SpellEntry> movement = new ArrayList<>();
        public List<SpellEntry> support = new ArrayList<>();
        public List<SpellEntry> escape = new ArrayList<>();
    }

    public static class SpellEntry
    {
        public String id = "";
        public int level = 1;
        public boolean emergency = false;

        /**
         * Resolves this entry's spell from the registry, or null if the id is invalid/unknown.
         */
        public AbstractSpell resolveSpell()
        {
            return PresetDefinition.resolveSpell(id);
        }
    }

    /**
     * Boss settings for a preset. When {@code enabled} the mob is bossified on attach: struck by
     * visual lightning, announced in chat, given a colored name tag, and run through the
     * configured health-based {@link BossPhase}s. {@link #spawnSettings} controls how (and
     * whether) this boss is naturally spawned; the day/night spawn weights bias the roll.
     * {@link #combos} replace the boss's attack casting with scripted sequences.
     */
    public static class Boss
    {
        public boolean enabled = false;
        public String name = "";
        public String nameColor = "red";
        public String spawnEntity = "mobwizardry:wizard";
        public double daySpawnWeight = 0;
        public double nightSpawnWeight = 0;
        public SpawnSettings spawnSettings = new SpawnSettings();
        public List<BossPhase> phases = new ArrayList<>();
        public List<Combo> combos = new ArrayList<>();

        /**
         * What every player receives when this boss is defeated (message + per-player commands).
         * Raid bosses use the raid's rewards instead - a boss dying while its raid is still
         * active grants nothing here (avoids double-granting on a raid win).
         */
        public Rewards rewards = new Rewards();

        /**
         * The boss's combo pool at the given phase: the boss-level {@code combos} plus every
         * phase's {@code combos} whose number is at most {@code phaseNumber}. Phases only ever
         * add to the pool (never replace), so entering a phase unlocks its combos on top of the
         * earlier ones.
         */
        public List<Combo> activeCombos(int phaseNumber)
        {
            List<Combo> pool = new ArrayList<>(combos);
            for (BossPhase phase : phases)
            {
                if (phase != null && phase.combos != null && phase.number <= phaseNumber)
                {
                    pool.addAll(phase.combos);
                }
            }
            return pool;
        }

        /**
         * Per-boss natural-spawn controls. Each boss schedules its own spawn attempts, has its
         * own concurrent cap and spawns at its own distance from a player. Also controls the
         * arrival behavior: whether the boss glows on arrival and whether a naturally-spawned
         * boss disappears when the day/night phase flips.
         */
        public static class SpawnSettings
        {
            /** Legacy name for {@code spawnAttemptIntervalSeconds}; migrated on load (2.3.4). */
            @Deprecated
            public int attemptIntervalSeconds = 0;
            public boolean enabled = true;
            public int spawnAttemptIntervalSeconds = 1200;
            public int maxActiveBosses = 3;
            public double minDistanceFromPlayer = 24;
            public double maxDistanceFromPlayer = 48;

            /**
             * The chance (0..1) that a boss actually spawns each time its spawn timer elapses
             * and it is picked. 0 = never, 1 = every attempt; lower = rarer. Effective frequency
             * is roughly {@code spawnAttemptIntervalSeconds / spawnChance}.
             */
            public double spawnChance = 0.5;
            public boolean despawnOnTimeChange = true;
            public int spawnGlowSeconds = 60;

            /**
             * How many extra visual-only lightning bolts (besides the main strike) flash around
             * the boss on arrival, so the sky thunders even in clear weather. 0 disables.
             * Clamped to 0..30.
             */
            public int skyFlashBolts = 4;
        }
    }

    /**
     * One boss phase. The boss enters it once its health ratio drops to
     * {@code healthPercent} or below, at which point the phase's spell kit replaces the boss's
     * current kit, its {@link #effects} are applied (and persist across all later phases), its
     * {@link #combos} are added to the boss's combo pool, and the phase message is broadcast.
     * Phases are sorted by {@code healthPercent} descending at load, so phase 1 (usually 100) is
     * the boss's starting kit.
     */
    public static class BossPhase
    {
        public int number = 1;
        public double healthPercent = 100;
        public String message = "";
        public Spells spells = new Spells();
        public List<PhaseEffect> effects = new ArrayList<>();
        public List<Combo> combos = new ArrayList<>();
    }

    /**
     * A MobEffect applied to the boss when its phase activates. {@code duration} is in ticks;
     * {@code -1} (the default) is infinite, so the effect persists across all later phases.
     */
    public static class PhaseEffect
    {
        public String id = "";
        public int amplifier = 0;
        public int duration = -1;

        /**
         * Resolves this effect's {@link MobEffect} from the registry, or null if the id is
         * invalid/unknown.
         */
        public MobEffect resolveEffect()
        {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null)
            {
                return null;
            }
            return ForgeRegistries.MOB_EFFECTS.getValue(rl);
        }
    }

    /**
     * A scripted boss attack sequence. When a boss with combos fights, it picks one combo at
     * random, casts its steps in list order at their offsets from the combo start, then pauses
     * {@code pauseAfterComboExecution} ticks (0 = the preset's castInterval) before it may pick
     * another random combo. Combos replace the boss's weighted attack casting, and while a combo
     * runs the boss cannot cast any other spell category (defense/movement/support/escape resume
     * after it finishes).
     */
    public static class Combo
    {
        /** Legacy name for {@code pauseAfterComboExecution} (2.3.0); migrated on load. */
        @Deprecated
        public int castInterval = 0;
        /** Legacy name for {@code pauseAfterComboExecution} (2.3.1); migrated on load. */
        @Deprecated
        public int tickBeforeComboExecution = 0;
        public int pauseAfterComboExecution = 0;
        public List<ComboStep> steps = new ArrayList<>();
    }

    /**
     * One combo step: cast {@code spell} at level {@code level}, then wait
     * {@code waitAfterCast} ticks before the next step of the combo fires (20 ticks = 1 second;
     * 0 = cast the next step as soon as the boss is free). The first step casts immediately when
     * the combo starts. {@code category} is informational (attack/defense/support/movement/
     * escape) - every step is cast the same way.
     */
    public static class ComboStep
    {
        /**
         * Legacy name for {@code waitAfterCast} (pre-3.8.1, "ticks after the combo start").
         * Migrated on load with a warning.
         */
        @Deprecated
        public int castAfterTicks = 0;
        public String category = "attack";
        public String spell = "";
        public int level = 1;
        public int waitAfterCast = 0;

        /**
         * Resolves this step's spell from the registry, or null if the id is invalid/unknown.
         */
        public AbstractSpell resolveSpell()
        {
            return PresetDefinition.resolveSpell(spell);
        }
    }

    /**
     * Parses a {@code nameColor} string into a chat {@link Style}, or null when it is invalid.
     * Accepts a named {@link ChatFormatting} color (e.g. {@code red}, {@code dark_red},
     * {@code gold}) or a hex code ({@code #FF5555}).
     */
    public static Style parseNameColor(String nameColor)
    {
        if (nameColor != null)
        {
            ChatFormatting named = ChatFormatting.getByName(nameColor.trim().toLowerCase());
            if (named != null && named.isColor())
            {
                return Style.EMPTY.withColor(named);
            }
            if (nameColor.startsWith("#"))
            {
                TextColor hex = TextColor.parseColor(nameColor.trim());
                if (hex != null)
                {
                    return Style.EMPTY.withColor(hex);
                }
            }
        }
        return null;
    }

    /**
     * The chat style for a boss's {@code nameColor}, falling back to red on invalid input.
     */
    public static Style nameColorStyle(String nameColor)
    {
        Style parsed = parseNameColor(nameColor);
        return parsed != null ? parsed : Style.EMPTY.withColor(ChatFormatting.RED);
    }

    /**
     * The boss's display name as a colored chat component (its {@code nameColor}). Shared by the
     * name tag, boss bar and chat announcements so the boss's name looks the same everywhere.
     */
    public static Component bossNameComponent(Boss boss)
    {
        return Component.literal(boss.name).withStyle(nameColorStyle(boss.nameColor));
    }
}
