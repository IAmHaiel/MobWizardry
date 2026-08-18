package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.haylent.mobwizardry.config.RaidDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The raid / horde system: a configurable sequence of waves of enemy wizards (weighted-random
 * capped per wave) ending with a boss fight. Players win by killing every enemy in every wave and
 * the boss; the raid is lost when all players in the raid level are dead. One active raid at a
 * time, shown on a purple raid bar (wave progress, then the boss's health).
 */
public class RaidManager
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String WIZARD_NPC = "mobwizardry:wizard";

    private static ActiveRaid active;

    private RaidManager()
    {
    }

    public static boolean isRaidActive()
    {
        return active != null;
    }

    /**
     * Starts a configured raid in the given level near the given position. Any active raid is
     * cancelled first. Broadcasts the raid's start message and spawns wave 1.
     */
    public static void startRaid(String raidName, ServerLevel level, Vec3 pos)
    {
        RaidDefinition def = PresetManager.getRaid(raidName);
        if (def == null)
        {
            LOGGER.warn("[MobWizardry] Raid '{}' not found", raidName);
            return;
        }
        if (active != null)
        {
            cancelRaid(active);
        }
        ActiveRaid raid = new ActiveRaid(def, level, pos);
        active = raid;
        broadcast(level, def.startMessage, ChatFormatting.RED);
        playRaidStartEffects(raid);
        LOGGER.info("[MobWizardry] Raid '{}' started in {} at {}", def.name, level.dimension().location(), pos);
        startWave(raid);
    }

    /**
     * Audiovisual feedback when a raid starts: every player in the raid's dimension
     * simultaneously hears the pillager horn and a loud lightning crash, and sees a
     * "YOU ARE INVADED" subtitle.
     */
    private static void playRaidStartEffects(ActiveRaid raid)
    {
        Component subtitle = Component.literal("YOU ARE INVADED")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer player : raid.level.players())
        {
            player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 1.0f);
            player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0f, 1.0f);
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        }
    }

    /**
     * Immediately ends the active raid without broadcasting a result.
     */
    public static void stopRaid()
    {
        if (active == null)
        {
            return;
        }
        LOGGER.info("[MobWizardry] Raid '{}' stopped", active.def.name);
        cancelRaid(active);
    }

    /**
     * Per-tick raid progress: lose check, boss tracking or wave enemy pruning + advancement.
     * Called from a ServerTickEvent (END phase) in {@code MobWizardryMod}.
     */
    public static void tickServer(MinecraftServer server)
    {
        if (active == null)
        {
            return;
        }
        ActiveRaid raid = active;
        ServerLevel level = raid.level;

        // Lose: the raid level has players and all of them are dead.
        List<ServerPlayer> players = level.players();
        if (!players.isEmpty() && players.stream().allMatch(p -> !p.isAlive() || p.isRemoved()))
        {
            endRaid(raid, false);
            return;
        }

        if (raid.bossPhase)
        {
            Entity boss = level.getEntity(raid.bossUuid);
            if (boss == null || !boss.isAlive() || boss.isRemoved())
            {
                endRaid(raid, true);
                return;
            }
            updateBossBar(raid, boss);
            return;
        }

        raid.waveEnemies.removeIf(uuid -> {
            Entity e = level.getEntity(uuid);
            return e == null || !e.isAlive() || e.isRemoved();
        });
        updateWaveBar(raid);
        if (raid.waveEnemies.isEmpty())
        {
            raid.waveIndex++;
            playWaveClearedEffect(raid);
            if (raid.waveIndex >= raid.def.waves.size())
            {
                startBossPhase(raid);
            }
            else
            {
                startWave(raid);
            }
        }
    }

    private static void startWave(ActiveRaid raid)
    {
        RaidDefinition.RaidWave wave = raid.def.waves.get(raid.waveIndex);
        raid.waveEnemies.clear();

        // Weighted-random capped: the wave spawns sum(counts) enemies; each pick is weighted by
        // the group's weight among groups that have not yet reached their count.
        List<GroupRemaining> pool = new ArrayList<>();
        int total = 0;
        for (RaidDefinition.RaidEnemy enemy : wave.enemies)
        {
            PresetDefinition preset = PresetManager.getPreset(enemy.preset);
            if (preset == null)
            {
                continue;
            }
            pool.add(new GroupRemaining(enemy, preset));
            total += enemy.count;
        }
        raid.waveTotal = total;
        Vec3 origin = spawnOrigin(raid);
        // One rally point for the whole wave, so the enemies arrive grouped together instead of
        // scattered around the spawn-distance ring.
        Vec3 rally = raidSpawnPos(raid, origin, raid.def.spawnDistance);
        // Visual thunderstorm over the rally point where this wave's group spawns.
        BossManager.arrivalStorm(raid.level, rally, raid.def.skyFlashBolts, 6);
        int spawned = 0;
        for (int i = 0; i < total; i++)
        {
            GroupRemaining pick = weightedPick(raid.level, pool);
            if (pick == null)
            {
                break;
            }
            pick.remaining--;
            if (spawnEnemy(raid, pick.preset, rally))
            {
                spawned++;
            }
        }
        LOGGER.info("[MobWizardry] Raid '{}' wave {} spawned {} enemies", raid.def.name, wave.number, spawned);
        updateWaveBar(raid);
    }

    private static GroupRemaining weightedPick(ServerLevel level, List<GroupRemaining> pool)
    {
        double totalWeight = 0;
        for (GroupRemaining group : pool)
        {
            if (group.remaining > 0 && group.weight() > 0)
            {
                totalWeight += group.weight();
            }
        }
        if (totalWeight > 0)
        {
            double roll = level.random.nextDouble() * totalWeight;
            for (GroupRemaining group : pool)
            {
                if (group.remaining <= 0 || group.weight() <= 0)
                {
                    continue;
                }
                roll -= group.weight();
                if (roll <= 0)
                {
                    return group;
                }
            }
        }
        for (GroupRemaining group : pool)
        {
            if (group.remaining > 0)
            {
                return group;
            }
        }
        return null;
    }

    private static void spawnEnemy(ActiveRaid raid, PresetDefinition preset, Vec3 rally)
    {
        Vec3 pos = clusterPos(raid, rally, raid.def.groupRadius);
        PathfinderMob mob = SpawnHelper.spawnTaggedMob(raid.level, WIZARD_NPC, preset, pos);
        if (mob == null)
        {
            return false;
        }
        raid.waveEnemies.add(mob.getUUID());
        if (raid.def.waveGlowSeconds > 0)
        {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, raid.def.waveGlowSeconds * 20));
        }
        targetRandomPlayer(raid.level, mob);
        return true;
    }

    /**
     * Celebration of a cleared wave: a lightning storm and thunder sound over the players'
     * position before the next wave (or the boss) begins.
     */
    private static void playWaveClearedEffect(ActiveRaid raid)
    {
        Vec3 pos = spawnOrigin(raid);
        BossManager.arrivalStorm(raid.level, pos, raid.def.skyFlashBolts, 4);
        for (ServerPlayer player : raid.level.players())
        {
            player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0f, 1.0f);
        }
    }

    /**
     * A position within {@code radius} blocks of {@code rally} in a random direction (floored at
     * 1 block so the wave's enemies do not stack exactly on the rally point).
     */
    private static Vec3 clusterPos(ActiveRaid raid, Vec3 rally, double radius)
    {
        double angle = raid.level.random.nextDouble() * Math.PI * 2.0;
        double dist = raid.level.random.nextDouble() * Math.max(1.0, radius);
        return rally.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
    }

    /**
     * A position {@code distance} blocks from {@code origin} in a random direction (with
     * 85-115% jitter), floored at 8 blocks so the raid never spawns on top of the player.
     */
    private static Vec3 raidSpawnPos(ActiveRaid raid, Vec3 origin, double distance)
    {
        double angle = raid.level.random.nextDouble() * Math.PI * 2.0;
        double dist = Math.max(8.0, distance) * (0.85 + raid.level.random.nextDouble() * 0.3);
        return new Vec3(origin.x + Math.cos(angle) * dist, origin.y, origin.z + Math.sin(angle) * dist);
    }

    /**
     * Points the mob at a random attackable online player so a fresh raid enemy/boss goes
     * straight for someone; with no attackable player the mob falls back to its normal AI.
     */
    private static void targetRandomPlayer(ServerLevel level, PathfinderMob mob)
    {
        List<ServerPlayer> attackable = level.players().stream().filter(mob::canAttack).toList();
        if (attackable.isEmpty())
        {
            return;
        }
        mob.setTarget(attackable.get(level.random.nextInt(attackable.size())));
    }

    private static void startBossPhase(ActiveRaid raid)
    {
        raid.bossPhase = true;
        if (raid.def.boss == null || raid.def.boss.isBlank())
        {
            LOGGER.info("[MobWizardry] Raid '{}' has no boss - victory after the last wave", raid.def.name);
            endRaid(raid, true);
            return;
        }
        PresetDefinition bossPreset = PresetManager.getPreset(raid.def.boss);
        if (bossPreset == null || bossPreset.boss == null || !bossPreset.boss.enabled)
        {
            endRaid(raid, true);
            return;
        }
        Vec3 origin = spawnOrigin(raid);
        Vec3 pos = raidSpawnPos(raid, origin, raid.def.bossSpawnDistance);
        PathfinderMob boss = BossManager.spawnBoss(raid.level, bossPreset, pos, false);
        if (boss == null)
        {
            endRaid(raid, true);
            return;
        }
        raid.bossUuid = boss.getUUID();
        LOGGER.info("[MobWizardry] Raid '{}' boss '{}' has arrived", raid.def.name, bossPreset.boss.name);
        updateBossBar(raid, boss);
    }

    private static void endRaid(ActiveRaid raid, boolean victory)
    {
        if (victory)
        {
            broadcast(raid.level, raid.def.victoryMessage, ChatFormatting.GOLD);
            LOGGER.info("[MobWizardry] Raid '{}' ended in victory", raid.def.name);
            playEndSound(raid, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        }
        else
        {
            broadcast(raid.level, raid.def.defeatMessage, ChatFormatting.RED);
            LOGGER.info("[MobWizardry] Raid '{}' ended in defeat", raid.def.name);
            playEndSound(raid, SoundEvents.UI_TOAST_ERROR);
        }
        cancelRaid(raid);
    }

    private static void playEndSound(ActiveRaid raid, Holder<SoundEvent> sound)
    {
        for (ServerPlayer player : raid.level.players())
        {
            player.playNotifySound(sound, SoundSource.MASTER, 2.0f, 1.0f);
        }
    }

    private static void cancelRaid(ActiveRaid raid)
    {
        raid.bar.removeAllPlayers();
        raid.bar.setVisible(false);
        if (active == raid)
        {
            active = null;
        }
    }

    private static void updateWaveBar(ActiveRaid raid)
    {
        ServerBossEvent bar = raid.bar;
        // Full bar that drains to empty as the horde is defeated; the client animates the rise
        // back to 100% when the next wave (or the boss) sets it full again.
        float progress = raid.waveTotal <= 0 ? 1.0f
                : Math.max(0.0f, Math.min(1.0f, (float) raid.waveEnemies.size() / raid.waveTotal));
        bar.setName(Component.literal(raid.def.name + " - Wave " + (raid.waveIndex + 1) + "/" + raid.def.waves.size()));
        bar.setProgress(progress);
        bar.setVisible(true);
        reconcilePlayers(raid);
    }

    private static void updateBossBar(ActiveRaid raid, Entity boss)
    {
        ServerBossEvent bar = raid.bar;
        float progress = 0.0f;
        if (boss instanceof PathfinderMob mob && mob.getMaxHealth() > 0)
        {
            progress = Math.max(0.0f, Math.min(1.0f, mob.getHealth() / mob.getMaxHealth()));
        }
        bar.setName(Component.literal(raid.def.name + " - Boss"));
        bar.setProgress(progress);
        bar.setVisible(true);
        reconcilePlayers(raid);
    }

    private static void reconcilePlayers(ActiveRaid raid)
    {
        for (ServerPlayer player : raid.level.players())
        {
            raid.bar.addPlayer(player);
        }
    }

    private static Vec3 spawnOrigin(ActiveRaid raid)
    {
        List<ServerPlayer> players = raid.level.players();
        if (!players.isEmpty())
        {
            return players.get(raid.level.random.nextInt(players.size())).position();
        }
        return raid.startPos;
    }

    private static void broadcast(ServerLevel level, String message, ChatFormatting color)
    {
        if (message == null || message.isBlank() || level.getServer() == null)
        {
            return;
        }
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message).withStyle(color), false);
    }

    private static class GroupRemaining
    {
        final RaidDefinition.RaidEnemy enemy;
        final PresetDefinition preset;
        int remaining;

        GroupRemaining(RaidDefinition.RaidEnemy enemy, PresetDefinition preset)
        {
            this.enemy = enemy;
            this.preset = preset;
            this.remaining = enemy.count;
        }

        double weight()
        {
            return enemy.weight;
        }
    }

    private static class ActiveRaid
    {
        final RaidDefinition def;
        final ServerLevel level;
        final Vec3 startPos;
        final Set<UUID> waveEnemies = new HashSet<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.empty(),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        int waveIndex = 0;
        int waveTotal = 0;
        boolean bossPhase = false;
        UUID bossUuid = null;

        ActiveRaid(RaidDefinition def, ServerLevel level, Vec3 startPos)
        {
            this.def = def;
            this.level = level;
            this.startPos = startPos;
        }
    }
}
