package com.haylent.mobwizardry.config;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
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
    public Map<String, String> equipment = new HashMap<>();
    public Map<String, Double> attributes = new HashMap<>();
    public Spells spells = new Spells();

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
}
