package com.haylent.mobwizardry.config;

import net.minecraft.world.entity.Entity;

/**
 * Team and faction membership for wizardified mobs, stored on the entity's persistent NBT so it
 * survives saves. Mobs that share a non-blank team cannot target, retaliate against, or hurt each
 * other; mobs with different or no teams keep vanilla behavior. The faction mirrors the preset's
 * {@code faction} field so other wizards can identify enemies.
 */
public class MobWizardryTeams
{
    private static final String TEAM_KEY = "mobwizardry_team";
    private static final String FACTION_KEY = "mobwizardry_faction";

    private MobWizardryTeams()
    {
    }

    /**
     * Assigns a team to an entity. A blank team removes the membership.
     */
    public static void setTeam(Entity entity, String team)
    {
        if (team == null || team.isBlank())
        {
            entity.getPersistentData().remove(TEAM_KEY);
        }
        else
        {
            entity.getPersistentData().putString(TEAM_KEY, team.trim());
        }
    }

    /**
     * The entity's configured team, or an empty string when it has none.
     */
    public static String teamOf(Entity entity)
    {
        return entity.getPersistentData().getString(TEAM_KEY);
    }

    /**
     * True when the two entities share a non-blank team.
     */
    public static boolean sameTeam(Entity a, Entity b)
    {
        String team = teamOf(a);
        return !team.isEmpty() && team.equals(teamOf(b));
    }

    /**
     * Assigns a faction to an entity. A blank faction removes the membership.
     */
    public static void setFaction(Entity entity, String faction)
    {
        if (faction == null || faction.isBlank())
        {
            entity.getPersistentData().remove(FACTION_KEY);
        }
        else
        {
            entity.getPersistentData().putString(FACTION_KEY, faction.trim());
        }
    }

    /**
     * The entity's configured faction, or an empty string when it has none.
     */
    public static String factionOf(Entity entity)
    {
        return entity.getPersistentData().getString(FACTION_KEY);
    }
}
