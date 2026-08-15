package com.haylent.mobwizardry.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;

/**
 * A priority-0 target goal that "locks in" the mob's current target so the natural target goals
 * (e.g. a zombie's {@code NearestAttackableTargetGoal<Player>}) cannot steal it mid-fight.
 *
 * <p>While the mob is committed to a live, attackable target within follow range it holds the
 * {@code TARGET} flag, which blocks every lower-priority target goal from starting. It yields the
 * flag briefly while the mob was recently hurt by a different attackable entity, letting the
 * vanilla {@code HurtByTargetGoal} retarget the attacker; once the target switches it re-engages
 * and locks the new target in.
 */
public class StickyTargetGoal extends TargetGoal
{
    /**
     * How recently (in ticks) the mob must have been hurt for the goal to yield to retaliation.
     * Covers {@code HurtByTargetGoal}'s one-tick delay between taking damage and retargeting.
     */
    private static final int YIELD_AFTER_HURT_TICKS = 10;

    private final String requiredTag;

    public StickyTargetGoal(Mob mob, String requiredTag)
    {
        super(mob, false, false);
        this.requiredTag = requiredTag;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        this.targetMob = validTarget();
        return targetMob != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return canUse();
    }

    @Override
    public void start()
    {
        // TargetGoal.start() does not re-assert the target in 1.20.1 - only stop() clears it. The
        // goal that this one replaces clears the mob's target on stop(), so re-assert it here.
        if (targetMob != null)
        {
            mob.setTarget(targetMob);
        }
        super.start();
    }

    /**
     * The committed target, or {@code null} when the goal must stay inactive: the wizard tag is
     * gone, the mob was recently hurt by a different attackable entity (yield to retaliation), or
     * the current target is dead, unattackable or out of follow range.
     */
    private LivingEntity validTarget()
    {
        if (!mob.getTags().contains(requiredTag) || shouldYieldToAttacker())
        {
            return null;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.canAttack(target))
        {
            return null;
        }
        double followRange = getFollowDistance();
        if (mob.distanceToSqr(target) > followRange * followRange)
        {
            return null;
        }
        return target;
    }

    /**
     * True while the mob was recently hurt by a live, attackable entity that is not its current
     * target. Releases the {@code TARGET} flag so the vanilla {@code HurtByTargetGoal} can switch
     * the mob to the attacker.
     */
    private boolean shouldYieldToAttacker()
    {
        LivingEntity attacker = mob.getLastHurtByMob();
        return attacker != null
                && attacker != mob.getTarget()
                && mob.canAttack(attacker)
                && mob.tickCount - mob.getLastHurtByMobTimestamp() <= YIELD_AFTER_HURT_TICKS;
    }
}
