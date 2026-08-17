package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.PathfinderMob;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boss behavior for boss-enabled presets, driven by {@code WizardAiGoal.attach} (the single
 * choke point every boss join, summon, wizardify and reload passes through):
 * <ul>
 *   <li>first bossification - visual-only lightning strike, a colored name tag, the
 *       {@code NAME has arrived.} chat announcement and activation of the first phase;</li>
 *   <li>later joins / reloads (the {@code mobwizardry_bossified} flag is already set) just
 *       re-apply the name and the current phase's kit - no re-lightning, no re-announce;</li>
 *   <li>each phase (sorted by health percent descending) swaps the boss's spell kit and prints
 *       its message once the boss's health ratio drops to the phase's threshold.</li>
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
}
