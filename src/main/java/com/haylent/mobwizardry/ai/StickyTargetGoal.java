package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.MobWizardryTeams;
import net.minecraft.util.Mth;
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
    private final double retaliationChance;
    private int decisionTick = -1;
    private boolean yieldDecision;

    public StickyTargetGoal(Mob mob, String requiredTag, double retaliationChance)
    {
        super(mob, false, false);
        this.requiredTag = requiredTag;
        this.retaliationChance = Mth.clamp(retaliationChance, 0.0, 1.0);
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
     * target and is not on its own team. Releases the {@code TARGET} flag so the vanilla
     * {@code HurtByTargetGoal} can switch the mob to the attacker - but only with the configured
     * {@code retaliationChance} when the wizard is already committed to a target (an idle wizard
     * always retaliates). The roll happens once per hurt event, not per tick, so the configured
     * percentage is meaningful.
     */
    private boolean shouldYieldToAttacker()
    {
        LivingEntity attacker = mob.getLastHurtByMob();
        if (attacker == null || attacker == mob.getTarget() || !mob.canAttack(attacker))
        {
            return false;
        }
        if (MobWizardryTeams.areAllies(mob, attacker))
        {
            return false;
        }
        if (mob.tickCount - mob.getLastHurtByMobTimestamp() > YIELD_AFTER_HURT_TICKS)
        {
            return false;
        }
        if (mob.getTarget() == null)
        {
            return true;
        }
        int hurtTick = mob.getLastHurtByMobTimestamp();
        if (hurtTick != this.decisionTick)
        {
            this.decisionTick = hurtTick;
            this.yieldDecision = mob.getRandom().nextDouble() < this.retaliationChance;
        }
        return this.yieldDecision;
    }
}
