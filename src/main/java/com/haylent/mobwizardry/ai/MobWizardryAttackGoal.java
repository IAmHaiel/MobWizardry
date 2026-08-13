package com.haylent.mobwizardry.ai;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.util.Mth;

/**
 * Extends Iron's Spellbooks' {@link WizardAttackGoal} to fix the AI's category selection
 * weights for tagged mobs:
 * <ul>
 *   <li>defense only fires while the caster was recently attacked (not just at low health);</li>
 *   <li>movement fires when the target is far / out of spell range;</li>
 *   <li>support fires when hurt, below half health, or low on mana.</li>
 * </ul>
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

    @Override
    protected int getMovementWeight()
    {
        if (target == null)
        {
            return 0;
        }
        double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        double range = Math.sqrt(spellcastingRangeSqr);
        double distance = Math.sqrt(distSqr);
        int weight = 0;
        if (distance > range)
        {
            weight += 200 + (int) (100.0 * Math.min((distance - range) / range, 2.0));
        }
        else if (distance > range * 0.75)
        {
            weight += (int) (80.0 * (distance - range * 0.75) / (range * 0.25));
        }
        if (!hasLineOfSight)
        {
            weight += 80;
        }
        double distRatio = Mth.clamp(distSqr / spellcastingRangeSqr, 0.0, 1.0);
        float hpRatio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0f;
        weight += (int) (400.0f * (1.0f - hpRatio) * (1.0f - hpRatio) * (float) (1.0 - distRatio) * (float) (1.0 - distRatio));
        return weight;
    }

    @Override
    protected int getSupportWeight()
    {
        float hpRatio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0f;
        boolean hurt = recentlyAttacked();
        if (!hurt && hpRatio >= 0.5f)
        {
            return -1000;
        }
        int weight = -15 + (int) (300.0f * (1.0f - hpRatio));
        if (hurt)
        {
            weight += 60;
        }
        return weight;
    }

    private boolean recentlyAttacked()
    {
        return mob.hurtTime > 0
                || (mob.getLastHurtByMob() != null
                && mob.tickCount - mob.getLastHurtByMobTimestamp() <= DEFENSE_WINDOW_TICKS);
    }
}

