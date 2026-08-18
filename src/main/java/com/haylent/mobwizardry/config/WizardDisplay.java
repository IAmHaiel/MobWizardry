package com.haylent.mobwizardry.config;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.List;

/**
 * Wizard name-tag display settings and helpers: the name/team colors come from the
 * {@code _wizardDisplay} block of {@code presets.json}, and the random-name pool comes from
 * {@code config/mobwizardry/names.json}. A wizard's name tag shows its name (a random name from
 * the pool for normal wizards, or the configured boss name for bosses) with a {@code < Team >}
 * line below it; both lines use configurable colors. Vanilla name tags render all lines at one
 * fixed size, so the team line is a dimmer second line rather than a smaller font.
 */
public class WizardDisplay
{
    private static Settings settings = new Settings();

    private WizardDisplay()
    {
    }

    public static void setSettings(Settings newSettings)
    {
        settings = newSettings != null ? newSettings : new Settings();
    }

    public static Settings getSettings()
    {
        return settings;
    }

    /**
     * The style for the name line (first line of the tag).
     */
    public static Style nameStyle()
    {
        return PresetDefinition.nameColorStyle(settings.nameColor);
    }

    /**
     * The style for the {@code < Team >} line.
     */
    public static Style teamStyle()
    {
        return PresetDefinition.nameColorStyle(settings.teamColor);
    }

    /**
     * Replaces the random-name pool (from {@code names.json}). Blank entries are dropped; a null
     * list clears the pool.
     */
    public static void setNames(List<String> names)
    {
        if (names == null)
        {
            settings.names = new ArrayList<>();
            return;
        }
        List<String> cleaned = new ArrayList<>();
        for (String name : names)
        {
            if (name != null && !name.isBlank())
            {
                cleaned.add(name.trim());
            }
        }
        settings.names = cleaned;
    }

    /**
     * The current random-name pool.
     */
    public static List<String> getNames()
    {
        return settings.names == null ? List.of() : settings.names;
    }

    /**
     * A random name from the pool, or null when the pool is empty (no name tag then).
     */
    public static String randomName(RandomSource random)
    {
        List<String> names = getNames();
        if (names.isEmpty())
        {
            return null;
        }
        return names.get(random.nextInt(names.size()));
    }

    /**
     * Gives a (non-boss) wizard a random name tag with its team line, unless it already has a
     * custom name or the name pool is empty.
     */
    public static void applyRandomName(PathfinderMob mob, String team)
    {
        if (mob.getCustomName() != null)
        {
            return;
        }
        String name = randomName(mob.getRandom());
        if (name == null)
        {
            return;
        }
        mob.setCustomName(displayName(Component.literal(name).withStyle(nameStyle()), team));
        mob.setCustomNameVisible(true);
    }

    /**
     * Builds the two-line name tag: {@code name} with a {@code < Team >} line beneath it (team
     * capitalized, e.g. {@code undead} → {@code Undead}). A blank team yields just the name.
     */
    public static Component displayName(Component name, String team)
    {
        Component result = name;
        String teamLine = teamLine(team);
        if (teamLine != null)
        {
            result = name.copy().append(Component.literal("\n" + teamLine).withStyle(teamStyle()));
        }
        return result;
    }

    private static String teamLine(String team)
    {
        if (team == null || team.isBlank())
        {
            return null;
        }
        String trimmed = team.trim();
        String capitalized = Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        return "< " + capitalized + " >";
    }

    /**
     * The {@code _wizardDisplay} settings: name/team colors. The random-name pool lives in
     * {@code config/mobwizardry/names.json} (the {@code names} field here only exists so old
     * configs that still carry it can be read for the migration path).
     */
    public static class Settings
    {
        public String nameColor = "white";
        public String teamColor = "gray";
        public List<String> names = new ArrayList<>();
    }
}
