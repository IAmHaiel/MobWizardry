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
 * {@code TARGET} flag, which blocks every lower-priority target goal from starting. Once that
 * target dies (or leaves follow range, or the wizard tag is removed) the goal releases the flag
 * and the natural goals re-scan and acquire the next target.
 */
public class StickyTargetGoal extends TargetGoal
{
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
        if (!mob.getTags().contains(requiredTag))
        {
            this.targetMob = null;
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.canAttack(target))
        {
            this.targetMob = null;
            return false;
        }
        double followRange = getFollowDistance();
        if (mob.distanceToSqr(target) > followRange * followRange)
        {
            this.targetMob = null;
            return false;
        }
        this.targetMob = target;
        return true;
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
}
