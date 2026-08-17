package com.haylent.mobwizardry.config;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
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
    }

    /**
     * Boss settings for a preset. When {@code enabled} the mob is bossified on attach: struck by
     * visual lightning, announced in chat, given a colored name tag, and run through the
     * configured health-based {@link BossPhase}s. The day/night spawn weights feed the natural
     * boss spawner (see {@code _spawnSettings}).
     */
    public static class Boss
    {
        public boolean enabled = false;
        public String name = "";
        public String nameColor = "red";
        public String spawnEntity = "mobwizardry:wizard";
        public double daySpawnWeight = 0;
        public double nightSpawnWeight = 0;
        public List<BossPhase> phases = new ArrayList<>();
    }

    /**
     * One boss phase. The boss enters it once its health ratio drops to
     * {@code healthPercent} or below, at which point the phase's spell kit replaces the boss's
     * current kit and the phase message is broadcast. Phases are sorted by {@code healthPercent}
     * descending at load, so phase 1 (usually 100) is the boss's starting kit.
     */
    public static class BossPhase
    {
        public int number = 1;
        public double healthPercent = 100;
        public String message = "";
        public Spells spells = new Spells();
    }

    /**
     * Parses a preset's {@code boss.nameColor} into a chat {@link Style}. Accepts a named
     * {@link ChatFormatting} color (e.g. {@code red}, {@code dark_red}, {@code gold}) or a hex
     * code ({@code #FF5555}); anything else falls back to red.
     */
    public static Style nameColorStyle(String nameColor)
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
        return Style.EMPTY.withColor(ChatFormatting.RED);
    }
}
