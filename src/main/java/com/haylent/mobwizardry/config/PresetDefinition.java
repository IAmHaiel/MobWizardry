package com.haylent.mobwizardry.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PresetDefinition
{
    public String requiredTag = "";
    public double speed = 1.0;
    public int castInterval = 60;
    public Map<String, String> equipment = new HashMap<>();
    public Map<String, Double> attributes = new HashMap<>();
    public float mana = 0.0f;
    public Spells spells = new Spells();

    public static class Spells
    {
        public List<SpellEntry> attack = new ArrayList<>();
        public List<SpellEntry> defense = new ArrayList<>();
        public List<SpellEntry> movement = new ArrayList<>();
        public List<SpellEntry> support = new ArrayList<>();
    }

    public static class SpellEntry
    {
        public String id = "";
        public int level = 1;
    }
}
