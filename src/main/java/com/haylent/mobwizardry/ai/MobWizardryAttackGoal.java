package com.haylent.mobwizardry.ai;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;

/**
 * Extends Iron's Spellbooks' {@link WizardAttackGoal} to fix the AI's category selection
 * weights for tagged mobs. Defense spells only fire while the caster has actually been
 * attacked recently, instead of whenever its health is low.
 */
public class MobWizardryAttackGoal extends WizardAttackGoal
{
    private static final int DEFENSE_WINDOW_TICKS = 100;

    public MobWizardryAttackGoal(IMagicEntity entity, double speed, int minInterval, int maxInterval)
    {
        super(entity, speed, minInterval, maxInterval);
    }

    @Override
    protected int getDefenseWeight()
    {
        if (!recentlyAttacked())
        {
            return -1000;
        }
        int weight = -20;
        float health = mob.getHealth();
        float maxHealth = mob.getMaxHealth();
        if (maxHealth > 0)
        {
            float hp = health / maxHealth;
            weight += (int) (50.0f * (1.0f - hp * hp * hp));
        }
        weight += 95 * projectileCount;
        if (target != null && target.getMaxHealth() > 0)
        {
            weight += (int) ((1.0f - target.getHealth() / target.getMaxHealth()) * -35.0f);
        }
        return weight + 30;
    }

    private boolean recentlyAttacked()
    {
        return mob.hurtTime > 0
                || (mob.getLastHurtByMob() != null
                && mob.tickCount - mob.getLastHurtByMobTimestamp() <= DEFENSE_WINDOW_TICKS);
    }
}
