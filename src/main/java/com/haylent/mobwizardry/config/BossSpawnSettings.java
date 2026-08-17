package com.haylent.mobwizardry.config;

/**
 * Global natural boss-spawning settings, read from the {@code _spawnSettings} block of
 * {@code presets.json}. Parsed during {@link PresetManager#reload()}; the current values are
 * kept in a static holder so {@code BossManager} can read them every server tick without
 * re-reading the config file.
 */
public class BossSpawnSettings
{
    private static BossSpawnSettings INSTANCE = new BossSpawnSettings();

    public boolean enabled = true;
    public int attemptIntervalSeconds = 300;
    public int maxActiveBosses = 3;
    public double minDistanceFromPlayer = 24;
    public double maxDistanceFromPlayer = 48;

    public static BossSpawnSettings get()
    {
        return INSTANCE;
    }

    public static void set(BossSpawnSettings settings)
    {
        INSTANCE = settings != null ? settings : new BossSpawnSettings();
    }
}
