package com.haylent.mobwizardry.config;

import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PresetDefinition
{
    public String requiredTag = "";
    public double speed = 1.0;
    public int castInterval = 60;
    public int castIntervalMax = 0;
    public double movementStartDistance = 0;
    public double movementFarDistance = 0;
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
    }
}
