package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.MobWizardryTeams;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;

/**
 * Target goal for friendly-faction wizards: they hunt vanilla hostile mobs ({@link Monster}) and
 * wizardified mobs carrying the {@code enemy} faction, but never players, villagers or their own
 * side. Same-team targets are still blocked by the team target guard.
 */
public class TargetEnemiesGoal extends NearestAttackableTargetGoal<Mob>
{
    public TargetEnemiesGoal(PathfinderMob mob)
    {
        super(mob, Mob.class, 10, true, false, TargetEnemiesGoal::isEnemy);
    }

    private static boolean isEnemy(LivingEntity entity)
    {
        return entity instanceof Monster || "enemy".equals(MobWizardryTeams.factionOf(entity));
    }
}
