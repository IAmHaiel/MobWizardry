package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.MobWizardryTeams;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.haylent.mobwizardry.entity.WizardNpc;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps an Iron's Spellbooks {@link WizardAttackGoal} behind a dynamic tag check. The inner goal only
 * activates while the mob still carries the preset's required tag, so a wizardified mob stops acting
 * as a wizard the moment its tag is removed.
 */
public class WizardAiGoal extends Goal
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PathfinderMob mob;
    private final PresetDefinition preset;
    private final MobWizardryAttackGoal inner;

    public WizardAiGoal(PathfinderMob mob, PresetDefinition preset)
    {
        this.mob = mob;
        this.preset = preset;
        PhaseKit kit = resolveKit(preset.spells);

        MobWizardryAttackGoal goal = new MobWizardryAttackGoal((IMagicEntity) mob, preset.speed, preset.castInterval, preset.effectiveCastIntervalMax());
        goal.setSpells(kit.attack, kit.defense, kit.movement, kit.support)
                .setSpellQuality(kit.minQuality, kit.maxQuality);
        goal.setWizardType(WizardType.fromName(preset.wizardType));
        goal.setEmergencyHealSpells(kit.emergencyHeals);
        goal.setEscapeSpells(kit.escape);
        goal.setMovementDistances(preset.movementStartDistance, preset.movementFarDistance, preset.movementDistanceOffset, preset.movementTooCloseDistance);
        this.inner = goal;
        setFlags(inner.getFlags());
    }

    /**
     * Resolves a preset (or boss-phase) spell kit once into the lists the goal consumes, plus
     * the emergency-heal list and the min/max spell quality derived from the entries.
     */
    private static PhaseKit resolveKit(PresetDefinition.Spells spells)
    {
        float[] qualityRange = {1.0f, 0.0f};
        List<AbstractSpell> emergencyHeals = new ArrayList<>();
        List<AbstractSpell> attack = resolveSpells(spells.attack, null, qualityRange);
        List<AbstractSpell> defense = resolveSpells(spells.defense, null, qualityRange);
        List<AbstractSpell> movement = resolveSpells(spells.movement, null, qualityRange);
        List<AbstractSpell> support = resolveSpells(spells.support, emergencyHeals, qualityRange);
        List<AbstractSpell> escape = resolveSpells(spells.escape, null, null);
        return new PhaseKit(attack, defense, movement, support, escape, emergencyHeals,
                Math.max(0.0f, qualityRange[0]), Math.min(1.0f, qualityRange[1]));
    }

    /**
     * Resolves every entry once, collecting the resolved spells (optionally into an
     * emergency-heal list) and tracking the min/max spell quality for the goal.
     */
    private static List<AbstractSpell> resolveSpells(List<PresetDefinition.SpellEntry> entries,
                                                     List<AbstractSpell> emergencyOut,
                                                     float[] qualityRange)
    {
        List<AbstractSpell> spells = new ArrayList<>();
        for (PresetDefinition.SpellEntry entry : entries)
        {
            AbstractSpell spell = entry.resolveSpell();
            if (spell == null)
            {
                continue;
            }
            spells.add(spell);
            if (entry.emergency && emergencyOut != null)
            {
                emergencyOut.add(spell);
            }
            if (qualityRange != null)
            {
                float q = spell.getMaxLevel() <= 0 ? 0.5f : (float) Math.max(1, entry.level) / spell.getMaxLevel();
                if (q < qualityRange[0])
                {
                    qualityRange[0] = q;
                }
                if (q > qualityRange[1])
                {
                    qualityRange[1] = q;
                }
            }
        }
        return spells;
    }

    /**
     * Swaps the wizard's spell kit to the given boss phase's kit (attack/defense/movement/
     * support/escape plus emergency heals and spell quality), so a boss gains its phase spells
     * when the phase activates. The base goal's category lists are cleared and refilled in
     * place, so an in-flight cast is unaffected.
     */
    public void applyPhaseSpells(PresetDefinition.BossPhase phase)
    {
        PhaseKit kit = resolveKit(phase.spells);
        inner.setSpells(kit.attack, kit.defense, kit.movement, kit.support)
                .setSpellQuality(kit.minQuality, kit.maxQuality);
        inner.setEmergencyHealSpells(kit.emergencyHeals);
        inner.setEscapeSpells(kit.escape);
        LOGGER.info("[MobWizardry] Boss phase {} kit applied to {} at {} (attack={}, defense={}, movement={}, support={}, escape={})",
                phase.number, mob.getType().getDescriptionId(), mob.blockPosition(),
                kit.attack.size(), kit.defense.size(), kit.movement.size(), kit.support.size(), kit.escape.size());
    }

    /**
     * The wizard AI goal currently attached to the mob, or null when the mob has none.
     */
    public static WizardAiGoal find(PathfinderMob mob)
    {
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals())
        {
            if (goal.getGoal() instanceof WizardAiGoal wizard)
            {
                return wizard;
            }
        }
        return null;
    }

    /**
     * A fully-resolved spell kit ready to hand to the inner goal.
     */
    private record PhaseKit(List<AbstractSpell> attack, List<AbstractSpell> defense,
                            List<AbstractSpell> movement, List<AbstractSpell> support,
                            List<AbstractSpell> escape, List<AbstractSpell> emergencyHeals,
                            float minQuality, float maxQuality)
    {
    }

    @Override
    public boolean canUse()
    {
        return mob.getTags().contains(preset.requiredTag) && inner.canUse();
    }

    @Override
    public boolean canContinueToUse()
    {
        return mob.getTags().contains(preset.requiredTag) && inner.canContinueToUse();
    }

    @Override
    public void start()
    {
        inner.start();
    }

    @Override
    public void stop()
    {
        inner.stop();
    }

    @Override
    public void tick()
    {
        if (!mob.getTags().contains(preset.requiredTag))
        {
            inner.stop();
            return;
        }
        inner.tick();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return inner.requiresUpdateEveryTick();
    }

    @Override
    public boolean isInterruptable()
    {
        return inner.isInterruptable();
    }

    /**
     * Turns a mob into a wizard for the given preset: applies equipment/attributes/mana and
     * attaches the wizard goal. Idempotent - safe to call repeatedly (the goal is only added once).
     */
    public static void attach(PathfinderMob mob, PresetDefinition preset)
    {
        WizardMobInit.apply(mob, preset);
        tryApply(mob, preset);
        if (mob instanceof WizardNpc npc && preset.skin != null && !preset.skin.isBlank())
        {
            npc.setSkin(preset.skin.trim());
        }
        BossManager.sync(mob, preset);
    }

    /**
     * Applies wizard AI to a mob if it carries the preset's tag and doesn't already have a wizard goal.
     * Safe to call from {@code EntityJoinLevelEvent} (new tagged mobs) and the wizardify/summon commands.
     */
    public static boolean tryApply(PathfinderMob mob, PresetDefinition preset)
    {
        if (mob.getTags().contains(preset.requiredTag) && !hasGoal(mob))
        {
            WizardFaction.apply(mob, preset);
            mob.goalSelector.addGoal(1, new WizardAiGoal(mob, preset));
            mob.targetSelector.addGoal(0, new StickyTargetGoal(mob, preset.requiredTag, preset.retaliationChance));
            MobWizardryTeams.setTeam(mob, preset.team);
            MobWizardryTeams.setFaction(mob, preset.faction);
            LOGGER.info("[MobWizardry] Attached wizard AI (tag={}) to {} at {}",
                    preset.requiredTag, mob.getType().getDescriptionId(), mob.blockPosition());
            return true;
        }
        return false;
    }

    private static boolean hasGoal(PathfinderMob mob)
    {
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals())
        {
            if (goal.getGoal() instanceof WizardAiGoal)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-applies the (reloaded) presets to every wizardified mob in all loaded dimensions. Used
     * by {@code /mobwizardry reload} so config edits take effect on existing wizards, not just
     * newly summoned/wizardified ones.
     *
     * @return the number of mobs re-applied
     */
    public static int reapplyAll(MinecraftServer server)
    {
        int reapplied = 0;
        for (ServerLevel level : server.getAllLevels())
        {
            for (Entity entity : level.getAllEntities())
            {
                if (!(entity instanceof PathfinderMob mob))
                {
                    continue;
                }
                for (PresetDefinition preset : PresetManager.getPresets().values())
                {
                    if (mob.getTags().contains(preset.requiredTag))
                    {
                        reapply(mob, preset);
                        reapplied++;
                    }
                }
            }
        }
        return reapplied;
    }

    /**
     * Replaces an existing wizard's setup with the given preset: removes the old wizard goals,
     * re-applies team, equipment, attributes and mana, then re-attaches fresh goals.
     */
    private static void reapply(PathfinderMob mob, PresetDefinition preset)
    {
        for (WrappedGoal goal : new ArrayList<>(mob.goalSelector.getAvailableGoals()))
        {
            if (goal.getGoal() instanceof WizardAiGoal)
            {
                mob.goalSelector.removeGoal(goal.getGoal());
            }
        }
        for (WrappedGoal goal : new ArrayList<>(mob.targetSelector.getAvailableGoals()))
        {
            if (goal.getGoal() instanceof StickyTargetGoal)
            {
                mob.targetSelector.removeGoal(goal.getGoal());
            }
        }
        MobWizardryTeams.setTeam(mob, preset.team);
        MobWizardryTeams.setFaction(mob, preset.faction);
        WizardFaction.apply(mob, preset);
        WizardMobInit.stripWizardEquipment(mob, preset);
        WizardMobInit.apply(mob, preset);
        mob.goalSelector.addGoal(1, new WizardAiGoal(mob, preset));
        mob.targetSelector.addGoal(0, new StickyTargetGoal(mob, preset.requiredTag, preset.retaliationChance));
        if (mob instanceof WizardNpc npc && preset.skin != null && !preset.skin.isBlank())
        {
            npc.setSkin(preset.skin.trim());
        }
        BossManager.sync(mob, preset);
        LOGGER.info("[MobWizardry] Re-applied preset '{}' to {} at {}",
                preset.requiredTag, mob.getType().getDescriptionId(), mob.blockPosition());
    }
}
