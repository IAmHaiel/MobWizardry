package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.PathfinderMob;
import org.slf4j.Logger;

import java.util.List;

/**
 * Scripted boss attack sequences. When a boss with combos is in combat, this executor picks one
 * combo at random once its interval has elapsed, casts the combo's steps in list order at their
 * offsets from the combo start, then waits the combo's {@code castInterval} (or the preset's)
 * before picking another combo.
 *
 * <p>Only attack casting is affected: defense/movement/support/escape still flow through the
 * normal goal logic, and both sides respect {@code isCasting()} so a combo step waits for any
 * cast already in flight. The goal's {@code getAttackWeight()} returns -1000 while an executor
 * is attached, so the weighted pick never casts a random attack spell.
 */
public class BossComboExecutor
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PathfinderMob mob;
    private final String bossName;
    private final List<PresetDefinition.Combo> combos;
    private final int defaultInterval;

    private int currentComboIndex = -1;
    private int comboStartTick = 0;
    private int nextStepIndex = 0;
    private int nextComboTick = 0;

    public BossComboExecutor(PathfinderMob mob, String bossName, List<PresetDefinition.Combo> combos, int defaultInterval)
    {
        this.mob = mob;
        this.bossName = bossName;
        this.combos = combos;
        this.defaultInterval = Math.max(1, defaultInterval);
    }

    /**
     * Runs the boss's combo attack logic. Called from the goal's {@code tick()} while the goal
     * is active (the boss has a live target). Picks a random combo when the interval has
     * elapsed, then fires the combo's steps in order at their offsets, at most one cast per tick.
     */
    public void tick(MobWizardryAttackGoal goal)
    {
        if (goal.isCastingSpell())
        {
            return;
        }
        if (currentComboIndex >= 0)
        {
            advanceCombo(goal);
        }
        else if (mob.tickCount >= nextComboTick)
        {
            startRandomCombo();
        }
    }

    private void advanceCombo(MobWizardryAttackGoal goal)
    {
        PresetDefinition.Combo combo = combos.get(currentComboIndex);
        int elapsed = mob.tickCount - comboStartTick;
        if (nextStepIndex < combo.steps.size() && elapsed >= combo.steps.get(nextStepIndex).castAfterTicks)
        {
            PresetDefinition.ComboStep step = combo.steps.get(nextStepIndex);
            nextStepIndex++;
            AbstractSpell spell = step.resolveSpell();
            if (spell == null)
            {
                return;
            }
            int level = Math.max(1, Math.min(step.level, spell.getMaxLevel()));
            goal.castComboSpell(spell, level);
            LOGGER.info("[MobWizardry] Boss '{}' combo step cast {} (level {}) at combo tick {}",
                    bossName, spell.getSpellName(), level, elapsed);
        }
        if (nextStepIndex >= combo.steps.size())
        {
            int interval = combo.tickBeforeComboExecution > 0 ? combo.tickBeforeComboExecution : defaultInterval;
            nextComboTick = mob.tickCount + Math.max(1, interval);
            currentComboIndex = -1;
            LOGGER.info("[MobWizardry] Boss '{}' finished a combo, next combo in {} ticks", bossName, interval);
        }
    }

    /**
     * Whether a combo is currently running (between "starts combo" and the final step's cast).
     * While true, the boss's other spell categories are suspended.
     */
    public boolean isComboActive()
    {
        return currentComboIndex >= 0;
    }

    private void startRandomCombo()
    {
        currentComboIndex = mob.getRandom().nextInt(combos.size());
        comboStartTick = mob.tickCount;
        nextStepIndex = 0;
        LOGGER.info("[MobWizardry] Boss '{}' starts combo #{}", bossName, currentComboIndex + 1);
    }
}
