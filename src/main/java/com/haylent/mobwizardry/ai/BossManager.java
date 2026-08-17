package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.BossSpawnSettings;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss behavior for boss-enabled presets, driven by {@code WizardAiGoal.attach} (the single
 * choke point every boss join, summon, wizardify and reload passes through):
 * <ul>
 *   <li>first bossification - visual-only lightning strike, a colored name tag, the
 *       {@code NAME has arrived.} chat announcement and activation of the first phase;</li>
 *   <li>later joins / reloads (the {@code mobwizardry_bossified} flag is already set) just
 *       re-apply the name and the current phase's kit - no re-lightning, no re-announce;</li>
 *   <li>each phase (sorted by health percent descending) swaps the boss's spell kit and prints
 *       its message once the boss's health ratio drops to the phase's threshold;</li>
 *   <li>a natural spawner on the {@code _spawnSettings} timer picks a boss preset weighted by its
 *       day/night spawn weights and spawns it near a random player.</li>
 * </ul>
 */
public class BossManager
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String BOSSIFIED_KEY = "mobwizardry_bossified";
    private static final String PHASE_KEY = "mobwizardry_boss_phase";

    /**
     * Bosses currently loaded, keyed by entity UUID so a chunk reload of the same boss re-uses
     * the entry. Pruned by {@link #tickServer} when the boss dies or unloads.
     */
    private static final Map<UUID, ActiveBoss> BOSSES = new HashMap<>();

    private BossManager()
    {
    }

    /**
     * Entry point called from {@code WizardAiGoal.attach} and {@code reapply}. No-ops for
     * presets without an enabled boss block.
     */
    public static void sync(PathfinderMob mob, PresetDefinition preset)
    {
        if (preset == null || preset.boss == null || !preset.boss.enabled)
        {
            return;
        }
        if (isBossified(mob))
        {
            refreshName(mob, preset);
            applyActivePhaseSpells(mob, preset);
            track(mob, preset);
        }
        else
        {
            bossify(mob, preset);
        }
    }

    private static void bossify(PathfinderMob mob, PresetDefinition preset)
    {
        mob.getPersistentData().putBoolean(BOSSIFIED_KEY, true);
        refreshName(mob, preset);
        track(mob, preset);
        if (!preset.boss.phases.isEmpty())
        {
            activatePhase(mob, preset, preset.boss.phases.get(0));
        }
        strikeLightning(mob);
        broadcastArrival(mob, preset);
        LOGGER.info("[MobWizardry] Bossified {} ({}) at {}", preset.boss.name,
                mob.getType().getDescriptionId(), mob.blockPosition());
    }

    private static boolean isBossified(PathfinderMob mob)
    {
        return mob.getPersistentData().getBoolean(BOSSIFIED_KEY);
    }

    private static void track(PathfinderMob mob, PresetDefinition preset)
    {
        BOSSES.put(mob.getUUID(), new ActiveBoss(mob, preset));
    }

    /**
     * Re-applies the boss's configured name tag (in case the config changed on reload).
     */
    private static void refreshName(PathfinderMob mob, PresetDefinition preset)
    {
        Style color = PresetDefinition.nameColorStyle(preset.boss.nameColor);
        mob.setCustomName(Component.literal(preset.boss.name).withStyle(color));
        mob.setCustomNameVisible(true);
    }

    /**
     * Applies the spell kit of the phase recorded as active, restoring a reloaded or re-applied
     * boss to the correct phase without re-broadcasting its message.
     */
    private static void applyActivePhaseSpells(PathfinderMob mob, PresetDefinition preset)
    {
        int activePhase = mob.getPersistentData().getInt(PHASE_KEY);
        for (PresetDefinition.BossPhase phase : preset.boss.phases)
        {
            if (phase.number == activePhase)
            {
                WizardAiGoal goal = WizardAiGoal.find(mob);
                if (goal != null)
                {
                    goal.applyPhaseSpells(phase);
                }
                return;
            }
        }
    }

    /**
     * Per-tick server work for bosses: health-based phase transitions and natural spawning.
     * Called from a {@code ServerTickEvent} (END phase) in {@code MobWizardryMod}.
     */
    public static void tickServer(MinecraftServer server)
    {
        tickPhases(server);
        tickSpawns(server);
    }

    private static void tickPhases(MinecraftServer server)
    {
        if (BOSSES.isEmpty())
        {
            return;
        }
        for (Iterator<Map.Entry<UUID, ActiveBoss>> it = BOSSES.entrySet().iterator(); it.hasNext();)
        {
            ActiveBoss active = it.next().getValue();
            PathfinderMob mob = active.mob();
            if (mob == null || !mob.isAlive() || mob.isRemoved())
            {
                it.remove();
                continue;
            }
            PresetDefinition preset = active.preset();
            int activePhase = mob.getPersistentData().getInt(PHASE_KEY);
            float hpRatio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0f;
            for (PresetDefinition.BossPhase phase : preset.boss.phases)
            {
                if (phase.number > activePhase && hpRatio <= phase.healthPercent / 100.0f)
                {
                    activatePhase(mob, preset, phase);
                }
            }
        }
    }

    private static int nextSpawnTick = 0;

    /**
     * Natural boss spawning on the configured timer ({@code _spawnSettings}). Each attempt picks
     * a random player, builds a weighted pool of boss presets (weight = {@code daySpawnWeight}
     * by day, {@code nightSpawnWeight} by night) and spawns one boss at a safe spot
     * {@code minDistanceFromPlayer..maxDistanceFromPlayer} blocks away. The spawned mob carries
     * the preset tag, so it is bossified through the normal entity-join path.
     */
    private static void tickSpawns(MinecraftServer server)
    {
        BossSpawnSettings settings = BossSpawnSettings.get();
        if (settings == null || !settings.enabled)
        {
            return;
        }
        int tick = server.getTickCount();
        if (tick < nextSpawnTick)
        {
            return;
        }
        nextSpawnTick = tick + Math.max(20, settings.attemptIntervalSeconds * 20);
        if (countActiveBosses() >= settings.maxActiveBosses)
        {
            return;
        }
        ServerPlayer player = pickRandomPlayer(server);
        if (player == null)
        {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean night = level.isNight();
        PresetDefinition preset = weightedPick(level, night);
        if (preset == null)
        {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double minDist = Math.max(8.0, settings.minDistanceFromPlayer);
        double maxDist = Math.max(minDist + 1.0, settings.maxDistanceFromPlayer);
        double dist = minDist + level.random.nextDouble() * (maxDist - minDist);
        Vec3 pos = player.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spawnBoss(level, preset, pos);
    }

    /**
     * Spawns a boss-enabled preset's mob at (a safe spot near) the given position with its tag
     * applied, so the normal entity-join handler bossifies it. Public so it can also be driven
     * directly by tests or future commands.
     */
    public static void spawnBoss(ServerLevel level, PresetDefinition preset, Vec3 pos)
    {
        if (preset.boss == null || !preset.boss.enabled)
        {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(preset.boss.spawnEntity);
        EntityType<?> type = rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null)
        {
            LOGGER.warn("[MobWizardry] Cannot spawn boss '{}': unknown spawnEntity '{}'", preset.boss.name, preset.boss.spawnEntity);
            return;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof PathfinderMob mob))
        {
            LOGGER.warn("[MobWizardry] Cannot spawn boss '{}': spawnEntity '{}' is not a PathfinderMob", preset.boss.name, preset.boss.spawnEntity);
            return;
        }
        Vec3 safe = SpawnHelper.findSafeSpawn(level, pos);
        mob.moveTo(safe.x, safe.y, safe.z);
        mob.addTag(preset.requiredTag);
        level.addFreshEntity(mob);
        LOGGER.info("[MobWizardry] Natural boss spawn: '{}' (tag '{}') at {}", preset.boss.name, preset.requiredTag, safe);
    }

    private static int countActiveBosses()
    {
        int count = 0;
        for (ActiveBoss active : BOSSES.values())
        {
            PathfinderMob mob = active.mob();
            if (mob != null && mob.isAlive() && !mob.isRemoved())
            {
                count++;
            }
        }
        return count;
    }

    private static ServerPlayer pickRandomPlayer(MinecraftServer server)
    {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty())
        {
            return null;
        }
        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    private static PresetDefinition weightedPick(ServerLevel level, boolean night)
    {
        double total = 0;
        List<WeightedPreset> pool = new ArrayList<>();
        for (PresetDefinition preset : PresetManager.getPresets().values())
        {
            if (preset.boss == null || !preset.boss.enabled || !hasSpawnEntity(preset))
            {
                continue;
            }
            double weight = night ? preset.boss.nightSpawnWeight : preset.boss.daySpawnWeight;
            if (weight <= 0)
            {
                continue;
            }
            pool.add(new WeightedPreset(preset, weight));
            total += weight;
        }
        if (pool.isEmpty())
        {
            return null;
        }
        double roll = level.random.nextDouble() * total;
        for (WeightedPreset candidate : pool)
        {
            roll -= candidate.weight;
            if (roll <= 0)
            {
                return candidate.preset;
            }
        }
        return pool.get(pool.size() - 1).preset;
    }

    private static boolean hasSpawnEntity(PresetDefinition preset)
    {
        if (preset.boss.spawnEntity == null || preset.boss.spawnEntity.isBlank())
        {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(preset.boss.spawnEntity);
        return rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl);
    }

    private static void activatePhase(PathfinderMob mob, PresetDefinition preset, PresetDefinition.BossPhase phase)
    {
        mob.getPersistentData().putInt(PHASE_KEY, phase.number);
        WizardAiGoal goal = WizardAiGoal.find(mob);
        if (goal != null)
        {
            goal.applyPhaseSpells(phase);
        }
        broadcastPhaseMessage(mob, preset, phase);
        LOGGER.info("[MobWizardry] Boss '{}' entered phase {} (health <= {}%)",
                preset.boss.name, phase.number, phase.healthPercent);
    }

    private static void strikeLightning(PathfinderMob mob)
    {
        if (!(mob.level() instanceof ServerLevel level))
        {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null)
        {
            return;
        }
        bolt.moveTo(mob.getX(), mob.getY(), mob.getZ());
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private static void broadcastArrival(PathfinderMob mob, PresetDefinition preset)
    {
        Style color = PresetDefinition.nameColorStyle(preset.boss.nameColor);
        Component message = Component.literal(preset.boss.name).withStyle(color)
                .append(Component.literal(" has arrived.").withStyle(ChatFormatting.GOLD));
        broadcast(mob, message);
    }

    private static void broadcastPhaseMessage(PathfinderMob mob, PresetDefinition preset,
                                              PresetDefinition.BossPhase phase)
    {
        if (phase.message == null || phase.message.isBlank())
        {
            return;
        }
        Style color = PresetDefinition.nameColorStyle(preset.boss.nameColor);
        Component message = Component.literal("[" + preset.boss.name + "] ").withStyle(color)
                .append(Component.literal(phase.message).withStyle(ChatFormatting.GOLD));
        broadcast(mob, message);
    }

    private static void broadcast(PathfinderMob mob, Component message)
    {
        if (mob.level() instanceof ServerLevel level && level.getServer() != null)
        {
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    /**
     * A currently-loaded boss: the mob plus the preset it was bossified with (kept so the tick
     * handler can consult the latest phase list without a registry search per tick).
     */
    private record ActiveBoss(PathfinderMob mob, PresetDefinition preset)
    {
    }

    /**
     * A boss preset entered into the natural-spawn weighted pool.
     */
    private record WeightedPreset(PresetDefinition preset, double weight)
    {
    }
}
