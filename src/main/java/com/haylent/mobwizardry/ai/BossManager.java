package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.haylent.mobwizardry.config.WizardDisplay;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private static final String SPAWN_PHASE_KEY = "mobwizardry_spawn_phase";

    /**
     * Bosses currently loaded, keyed by entity UUID so a chunk reload of the same boss re-uses
     * the entry. Pruned by {@link #tickServer} when the boss dies or unloads.
     */
    private static final Map<UUID, ActiveBoss> BOSSES = new HashMap<>();

    /**
     * The boss bar shown for each bossified mob, keyed by entity UUID. Created on track, updated
     * every tick with the boss's health ratio, removed when the boss dies or despawns.
     */
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();

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
        boostFollowRange(mob);
        targetArrivalPlayer(mob, preset);
        applySpawnGlow(mob, preset);
        if (!preset.boss.phases.isEmpty())
        {
            activatePhase(mob, preset, preset.boss.phases.get(0));
        }
        strikeLightning(mob, preset);
        broadcastArrival(mob, preset);
        LOGGER.info("[MobWizardry] Bossified {} ({}) at {}", preset.boss.name,
                mob.getType().getDescriptionId(), mob.blockPosition());
    }

    /**
     * Lets the boss chase its target from the natural-spawn distance (bosses spawn 24-48 blocks
     * away) instead of a mob type's default follow range.
     */
    private static void boostFollowRange(PathfinderMob mob)
    {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null)
        {
            followRange.setBaseValue(64.0);
        }
    }

    /**
     * On arrival the boss immediately targets a random attackable online player (multiplayer =
     * random among them) so it navigates toward them; with no attackable players it stays idle
     * and behaves exactly like a normal wizard (its own target goals handle everything).
     */
    private static void targetArrivalPlayer(PathfinderMob mob, PresetDefinition preset)
    {
        if (!(mob.level() instanceof ServerLevel level))
        {
            return;
        }
        List<ServerPlayer> attackable = level.players().stream()
                .filter(mob::canAttack)
                .toList();
        if (attackable.isEmpty())
        {
            return;
        }
        ServerPlayer pick = attackable.get(level.random.nextInt(attackable.size()));
        mob.setTarget(pick);
        LOGGER.info("[MobWizardry] Boss '{}' arrived and targets player {}",
                preset.boss.name, pick.getName().getString());
    }

    /**
     * Makes the freshly-arrived boss glow so players can see it, for the configured seconds
     * (0 disables).
     */
    private static void applySpawnGlow(PathfinderMob mob, PresetDefinition preset)
    {
        int seconds = preset.boss.spawnSettings.spawnGlowSeconds;
        if (seconds > 0)
        {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, seconds * 20));
        }
    }

    private static boolean isBossified(PathfinderMob mob)
    {
        return mob.getPersistentData().getBoolean(BOSSIFIED_KEY);
    }

    private static void track(PathfinderMob mob, PresetDefinition preset)
    {
        BOSSES.put(mob.getUUID(), new ActiveBoss(mob, preset));
        updateBossBar(mob, preset);
    }

    /**
     * Creates or updates the boss's boss bar: name in the preset's name color, progress = the
     * current health ratio, visible to every player in the boss's dimension (addPlayer is
     * idempotent). Called on track and every tick while the boss is alive.
     */
    private static void updateBossBar(PathfinderMob mob, PresetDefinition preset)
    {
        ServerBossEvent bar = BOSS_BARS.computeIfAbsent(mob.getUUID(),
                id -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(PresetDefinition.bossNameComponent(preset.boss));
        float progress = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 0.0f;
        bar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));
        bar.setVisible(true);
        if (mob.level() instanceof ServerLevel level)
        {
            for (ServerPlayer player : level.players())
            {
                bar.addPlayer(player);
            }
        }
    }

    private static void removeBossBar(PathfinderMob mob)
    {
        ServerBossEvent bar = BOSS_BARS.remove(mob.getUUID());
        if (bar != null)
        {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
    }

    /**
     * Re-applies the boss's configured name tag (in case the config changed on reload), with the
     * boss's team line beneath it.
     */
    private static void refreshName(PathfinderMob mob, PresetDefinition preset)
    {
        mob.setCustomName(WizardDisplay.displayName(PresetDefinition.bossNameComponent(preset.boss), preset.team));
        mob.setCustomNameVisible(true);
    }

    /**
     * Applies the spell kit (and phase effects) of the phase recorded as active, restoring a
     * reloaded or re-applied boss to the correct phase without re-broadcasting its message.
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
                applyPhaseEffects(mob, phase);
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
        tickPhases();
        tickSpawns(server);
        tickSkyStorms(server);
    }

    private static final List<SkyStorm> SKY_STORMS = new ArrayList<>();

    private record SkyStorm(ServerLevel level, Vec3 center, int remaining, int intervalTicks, int nextSpawnTick)
    {
    }

    /**
     * Starts a short thunderstorm of visual-only lightning bolts around {@code center}: one bolt
     * every {@code intervalTicks} ticks until {@code boltCount} have been struck. Each bolt
     * flashes the sky and plays thunder on nearby clients regardless of the world's weather.
     * A count of 0 or less does nothing.
     */
    public static void arrivalStorm(ServerLevel level, Vec3 center, int boltCount, int intervalTicks)
    {
        if (boltCount <= 0 || intervalTicks < 1)
        {
            return;
        }
        SKY_STORMS.add(new SkyStorm(level, center, boltCount, intervalTicks,
                level.getServer().getTickCount() + 1));
    }

    private static void tickSkyStorms(MinecraftServer server)
    {
        if (SKY_STORMS.isEmpty())
        {
            return;
        }
        int tick = server.getTickCount();
        for (Iterator<SkyStorm> it = SKY_STORMS.iterator(); it.hasNext();)
        {
            SkyStorm storm = it.next();
            if (storm.level().isClientSide() || storm.level().getServer() == null)
            {
                it.remove();
                continue;
            }
            if (tick < storm.nextSpawnTick())
            {
                continue;
            }
            strikeStormBolt(storm.level(), storm.center());
            if (storm.remaining() <= 1)
            {
                it.remove();
            }
            else
            {
                it.set(new SkyStorm(storm.level(), storm.center(), storm.remaining() - 1,
                        storm.intervalTicks(), tick + storm.intervalTicks()));
            }
        }
    }

    private static void strikeStormBolt(ServerLevel level, Vec3 center)
    {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double dist = 6.0 + level.random.nextDouble() * 8.0;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null)
        {
            return;
        }
        bolt.moveTo(center.x + Math.cos(angle) * dist, center.y, center.z + Math.sin(angle) * dist);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private static void tickPhases()
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
                removeBossBar(mob);
                it.remove();
                continue;
            }
            PresetDefinition preset = active.preset();
            updateBossBar(mob, preset);
            if (shouldDespawnOnTimeChange(active, mob))
            {
                LOGGER.info("[MobWizardry] Boss '{}' despawned - the day/night phase changed since it naturally spawned", active.preset().boss.name);
                mob.discard();
                removeBossBar(mob);
                it.remove();
                continue;
            }
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

    /**
     * Next natural-spawn attempt tick per boss preset (keyed by preset name). A boss's timer is
     * armed on first sight (one interval after that) and reset to {@code tick + interval} each
     * time the boss wins a roll, so every boss schedules its own spawn frequency.
     */
    private static final Map<String, Integer> NEXT_SPAWN_TICK = new HashMap<>();

    /**
     * Natural boss spawning driven by each boss's own {@code spawnSettings} (per-preset):
     * bosses whose timer has elapsed, whose live count is below their own cap and whose
     * day/night weight is above 0 enter a weighted pool; one is picked and spawned at that
     * boss's own {@code minDistanceFromPlayer..maxDistanceFromPlayer} from a random player. The
     * spawned mob carries the preset tag, so it is bossified through the normal entity-join path.
     */
    private static void tickSpawns(MinecraftServer server)
    {
        NEXT_SPAWN_TICK.keySet().removeIf(key -> {
            PresetDefinition preset = PresetManager.getPreset(key);
            return preset == null || preset.boss == null || !preset.boss.enabled;
        });
        ServerPlayer player = pickRandomPlayer(server);
        if (player == null)
        {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean night = level.isNight();
        int tick = server.getTickCount();

        double total = 0;
        List<WeightedPreset> pool = new ArrayList<>();
        for (Map.Entry<String, PresetDefinition> entry : PresetManager.getPresets().entrySet())
        {
            if (isSpawnEligible(entry.getValue(), entry.getKey(), level, night, tick))
            {
                double weight = night ? entry.getValue().boss.nightSpawnWeight : entry.getValue().boss.daySpawnWeight;
                pool.add(new WeightedPreset(entry.getKey(), entry.getValue(), weight));
                total += weight;
            }
        }
        if (pool.isEmpty())
        {
            return;
        }

        double roll = level.random.nextDouble() * total;
        WeightedPreset picked = pool.get(pool.size() - 1);
        for (WeightedPreset candidate : pool)
        {
            roll -= candidate.weight;
            if (roll <= 0)
            {
                picked = candidate;
                break;
            }
        }
        PresetDefinition preset = picked.preset();
        PresetDefinition.Boss.SpawnSettings spawn = preset.boss.spawnSettings;
        // Rarity roll: only spawnChance of the time does an eligible attempt actually spawn;
        // a failed roll reschedules the next attempt without spawning anything.
        if (spawn.spawnChance < 1.0 && level.random.nextDouble() >= spawn.spawnChance)
        {
            NEXT_SPAWN_TICK.put(picked.key(), tick + Math.max(20, spawn.spawnAttemptIntervalSeconds * 20));
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double minDist = Math.max(8.0, spawn.minDistanceFromPlayer);
        double maxDist = Math.max(minDist + 1.0, spawn.maxDistanceFromPlayer);
        double dist = minDist + level.random.nextDouble() * (maxDist - minDist);
        Vec3 pos = player.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spawnBoss(level, preset, pos);
        NEXT_SPAWN_TICK.put(picked.key(), tick + Math.max(20, spawn.spawnAttemptIntervalSeconds * 20));
    }

    /**
     * Whether this boss may be picked for a natural spawn right now: boss enabled, per-boss
     * spawn settings enabled, a known spawn entity, a positive day/night weight for the current
     * time, below its own live cap, and its own spawn timer elapsed (first sight arms the timer
     * one interval out instead of spawning immediately).
     */
    private static boolean isSpawnEligible(PresetDefinition preset, String key, ServerLevel level, boolean night, int tick)
    {
        PresetDefinition.Boss boss = preset.boss;
        if (boss == null || !boss.enabled)
        {
            return false;
        }
        PresetDefinition.Boss.SpawnSettings spawn = boss.spawnSettings;
        if (spawn == null || !spawn.enabled || spawn.spawnChance <= 0)
        {
            return false;
        }
        if (!hasSpawnEntity(boss))
        {
            return false;
        }
        if (night ? boss.nightSpawnWeight <= 0 : boss.daySpawnWeight <= 0)
        {
            return false;
        }
        if (countActiveBossesOf(preset) >= spawn.maxActiveBosses)
        {
            return false;
        }
        Integer next = NEXT_SPAWN_TICK.get(key);
        if (next == null)
        {
            NEXT_SPAWN_TICK.put(key, tick + Math.max(20, spawn.spawnAttemptIntervalSeconds * 20));
            return false;
        }
        return tick >= next;
    }

    /**
     * Spawns a boss-enabled preset's mob at (a safe spot near) the given position with its tag
     * applied, so the normal entity-join handler bossifies it. Returns the spawned boss (or null
     * if it could not be spawned) so callers like the raid system can track it. The boss is
     * stamped as naturally spawned (day/night despawn applies).
     */
    public static PathfinderMob spawnBoss(ServerLevel level, PresetDefinition preset, Vec3 pos)
    {
        return spawnBoss(level, preset, pos, true);
    }

    /**
     * As {@link #spawnBoss(ServerLevel, PresetDefinition, Vec3)}, but with control over whether
     * the boss is stamped as naturally spawned (and therefore despawns when the day/night phase
     * flips). Raid bosses pass {@code false} so they persist for the whole fight.
     */
    public static PathfinderMob spawnBoss(ServerLevel level, PresetDefinition preset, Vec3 pos, boolean naturalSpawn)
    {
        if (preset.boss == null || !preset.boss.enabled)
        {
            return null;
        }
        PathfinderMob mob = SpawnHelper.spawnTaggedMob(level, preset.boss.spawnEntity, preset, pos);
        if (mob == null)
        {
            LOGGER.warn("[MobWizardry] Cannot spawn boss '{}': unknown spawnEntity '{}'", preset.boss.name, preset.boss.spawnEntity);
            return null;
        }
        if (naturalSpawn)
        {
            // Stamp the day/night phase this boss naturally spawned in, so it despawns when the
            // time flips (see shouldDespawnOnTimeChange). Command-summoned bosses have no stamp
            // and are unaffected.
            mob.getPersistentData().putString(SPAWN_PHASE_KEY, level.isNight() ? "night" : "day");
        }
        LOGGER.info("[MobWizardry] Natural boss spawn: '{}' (tag '{}') at {}", preset.boss.name, preset.requiredTag, mob.blockPosition());
        return mob;
    }

    private static int countActiveBossesOf(PresetDefinition preset)
    {
        int count = 0;
        for (ActiveBoss active : BOSSES.values())
        {
            PathfinderMob mob = active.mob();
            if (mob != null && mob.isAlive() && !mob.isRemoved() && active.preset() == preset)
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

    private static boolean hasSpawnEntity(PresetDefinition.Boss boss)
    {
        if (boss.spawnEntity == null || boss.spawnEntity.isBlank())
        {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(boss.spawnEntity);
        return rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl);
    }

    /**
     * True when a naturally-spawned boss (stamped with the day/night phase it spawned in) must
     * vanish because the current phase no longer matches it and the boss's spawn settings enable
     * the behavior.
     */
    private static boolean shouldDespawnOnTimeChange(ActiveBoss active, PathfinderMob mob)
    {
        PresetDefinition.Boss.SpawnSettings spawn = active.preset().boss.spawnSettings;
        if (spawn == null || !spawn.despawnOnTimeChange)
        {
            return false;
        }
        String spawnedPhase = mob.getPersistentData().getString(SPAWN_PHASE_KEY);
        if (spawnedPhase.isEmpty())
        {
            return false;
        }
        boolean nowNight = mob.level().isNight();
        return ("day".equals(spawnedPhase) && nowNight) || ("night".equals(spawnedPhase) && !nowNight);
    }

    private static void activatePhase(PathfinderMob mob, PresetDefinition preset, PresetDefinition.BossPhase phase)
    {
        mob.getPersistentData().putInt(PHASE_KEY, phase.number);
        WizardAiGoal goal = WizardAiGoal.find(mob);
        if (goal != null)
        {
            goal.applyPhaseSpells(phase);
        }
        applyPhaseEffects(mob, phase);
        broadcastPhaseMessage(mob, preset, phase);
        LOGGER.info("[MobWizardry] Boss '{}' entered phase {} (health <= {}%)",
                preset.boss.name, phase.number, phase.healthPercent);
    }

    /**
     * Applies the phase's effects to the boss. {@code duration} of -1 is infinite, so an
     * effect applied in an earlier phase persists across all later phases (effects accumulate).
     */
    private static void applyPhaseEffects(PathfinderMob mob, PresetDefinition.BossPhase phase)
    {
        for (PresetDefinition.PhaseEffect effect : phase.effects)
        {
            MobEffect type = effect.resolveEffect();
            if (type == null)
            {
                continue;
            }
            mob.addEffect(new MobEffectInstance(type, effect.duration, Math.max(0, effect.amplifier), false, false));
        }
    }

    private static void strikeLightning(PathfinderMob mob, PresetDefinition preset)
    {
        if (!(mob.level() instanceof ServerLevel level) || level.getServer() == null)
        {
            return;
        }
        // Defer to the next server tick so the boss is fully added and tracked before the bolt
        // spawns (its spawn packet then reaches every nearby client), and strike at head level so
        // the bolt is visible even in caves instead of being buried at the mob's feet.
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
            if (!mob.isAlive() || mob.isRemoved())
            {
                return;
            }
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null)
            {
                return;
            }
            bolt.moveTo(mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }));
        // A brief thunderstorm of extra visual bolts around the boss so the sky flashes.
        int stormBolts = preset.boss.spawnSettings.skyFlashBolts;
        if (stormBolts > 0)
        {
            arrivalStorm(level, mob.position(), stormBolts, 6);
        }
    }

    private static void broadcastArrival(PathfinderMob mob, PresetDefinition preset)
    {
        Component message = PresetDefinition.bossNameComponent(preset.boss)
                .copy()
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
        Component message = Component.literal("[").withStyle(color)
                .append(PresetDefinition.bossNameComponent(preset.boss))
                .append(Component.literal("] ").withStyle(color))
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
     * A boss preset entered into the natural-spawn weighted pool, carrying its preset-name key
     * so the winner's spawn timer can be reset.
     */
    private record WeightedPreset(String key, PresetDefinition preset, double weight)
    {
    }
}
