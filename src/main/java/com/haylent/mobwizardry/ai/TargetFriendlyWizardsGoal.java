package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.MobWizardryTeams;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Target goal for enemy-faction wizards: they hunt wizardified mobs carrying the {@code friendly}
 * faction (the opposing side). Same-team targets are still blocked by the team target guard, so
 * different teams fight and same teams never do.
 */
public class TargetFriendlyWizardsGoal extends NearestAttackableTargetGoal<Mob>
{
    public TargetFriendlyWizardsGoal(PathfinderMob mob)
    {
        super(mob, Mob.class, 10, true, false, TargetFriendlyWizardsGoal::isEnemy);
    }

    private static boolean isEnemy(LivingEntity entity)
    {
        return "friendly".equals(MobWizardryTeams.factionOf(entity));
    }
}
