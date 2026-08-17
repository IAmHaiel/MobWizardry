package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.entity.WizardNpc;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

/**
 * Applies a preset's {@code faction} to a mob's target goals:
 * <ul>
 *   <li>{@code enemy} (default) — hostile like zombies/pillagers. Every enemy wizard hunts
 *       {@code friendly}-faction wizards (the opposing side); the Wizard NPC additionally gets
 *       player/villager/iron-golem targeting (existing mobs already have their own natural
 *       hostile goals).</li>
 *   <li>{@code friendly} — never attacks players or villagers, but hunts vanilla hostile mobs
 *       and {@code enemy}-faction wizards ({@link TargetEnemiesGoal}) and still retaliates when
 *       hurt (baseline {@code HurtByTargetGoal}).</li>
 * </ul>
 */
public class WizardFaction
{
    private WizardFaction()
    {
    }

    public static void apply(PathfinderMob mob, PresetDefinition preset)
    {
        if ("friendly".equalsIgnoreCase(preset.faction))
        {
            removeDefaultTargetGoals(mob);
            addIfMissing(mob, 3, new TargetEnemiesGoal(mob));
        }
        else
        {
            removeTargetGoal(mob, TargetEnemiesGoal.class);
            addIfMissing(mob, 2, new TargetFriendlyWizardsGoal(mob));
            if (mob instanceof WizardNpc && !hasGenericNearestTargetGoal(mob))
            {
                mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
                mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, AbstractVillager.class, false));
                mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, IronGolem.class, true));
            }
        }
    }

    /**
     * Removes the natural nearest-target goals (player/villager/golem) so a friendly wizard never
     * initiates against players or villagers. {@code TargetEnemiesGoal} is intentionally kept.
     */
    private static void removeDefaultTargetGoals(PathfinderMob mob)
    {
        for (WrappedGoal goal : new ArrayList<>(mob.targetSelector.getAvailableGoals()))
        {
            if (goal.getGoal() instanceof NearestAttackableTargetGoal<?> && !(goal.getGoal() instanceof TargetEnemiesGoal))
            {
                mob.targetSelector.removeGoal(goal.getGoal());
            }
        }
    }

    private static void removeTargetGoal(PathfinderMob mob, Class<?> goalClass)
    {
        for (WrappedGoal goal : new ArrayList<>(mob.targetSelector.getAvailableGoals()))
        {
            if (goalClass.isInstance(goal.getGoal()))
            {
                mob.targetSelector.removeGoal(goal.getGoal());
            }
        }
    }

    private static void addIfMissing(PathfinderMob mob, int priority, Goal goal)
    {
        for (WrappedGoal existing : mob.targetSelector.getAvailableGoals())
        {
            if (existing.getGoal().getClass() == goal.getClass())
            {
                return;
            }
        }
        mob.targetSelector.addGoal(priority, goal);
    }

    /**
     * Whether the mob already has a nearest-target goal that is not one of ours (the Wizard NPC's
     * player/villager/golem batch or a natural mob goal) - used to avoid duplicating the NPC goals
     * on reapply.
     */
    private static boolean hasGenericNearestTargetGoal(PathfinderMob mob)
    {
        for (WrappedGoal goal : mob.targetSelector.getAvailableGoals())
        {
            if (goal.getGoal() instanceof NearestAttackableTargetGoal<?> g
                    && !(g instanceof TargetEnemiesGoal) && !(g instanceof TargetFriendlyWizardsGoal))
            {
                return true;
            }
        }
        return false;
    }
}
