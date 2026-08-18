package com.haylent.mobwizardry.config;

import java.util.ArrayList;
import java.util.List;

/**
 * A configurable raid / horde of enemy wizards, read from {@code raids.json}. A raid runs a
 * sequence of waves (each a weighted mix of wizard presets) and ends with a boss fight using the
 * existing boss system. Players win by killing every enemy in every wave and the boss; the raid
 * is lost when all players die.
 */
public class RaidDefinition
{
    public String name = "";
    public String startMessage = "";
    public String victoryMessage = "";
    public String defeatMessage = "";
    public List<RaidWave> waves = new ArrayList<>();
    public String boss = "";

    /**
     * How far (in blocks) from a random player's position wave enemies spawn, so the player gets
     * a moment to prepare. Values below 8 are clamped to 8.
     */
    public double spawnDistance = 32.0;

    /**
     * How far (in blocks) from a random player's position the final boss spawns. Values below 8
     * are clamped to 8.
     */
    public double bossSpawnDistance = 48.0;

    /**
     * How tightly a wave's enemies cluster around their single rally point (blocks). The rally
     * point sits {@code spawnDistance} from a random player, so a whole wave arrives in one
     * group instead of being scattered around the ring. Values are clamped to 1..16.
     */
    public double groupRadius = 4.0;

    /**
     * One wave of the raid. Its enemies are spawned weighted-random-capped: the wave spawns
     * {@code sum(counts)} mobs, each pick weighted by the enemy's {@code weight} among presets
     * that have not yet reached their {@code count}.
     */
    public static class RaidWave
    {
        public int number = 1;
        public List<RaidEnemy> enemies = new ArrayList<>();
    }

    /**
     * One enemy group in a wave: {@code count} mobs (at most) of the given wizard preset, drawn
     * with the given {@code weight}. Raid enemies are {@code mobwizardry:wizard} NPCs carrying
     * the preset's tag.
     */
    public static class RaidEnemy
    {
        public String preset = "";
        public int count = 1;
        public double weight = 1.0;
    }
}
