package com.haylent.mobwizardry.event;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.config.MobWizardryTeams;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MobWizardryEvents
{
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide())
        {
            return;
        }
        if (!(event.getEntity() instanceof PathfinderMob mob))
        {
            return;
        }

        for (PresetDefinition preset : PresetManager.getPresets().values())
        {
            if (!mob.getTags().contains(preset.requiredTag))
            {
                continue;
            }
            WizardAiGoal.attach(mob, preset);
        }
    }

    /**
     * Same-team mobs never become each other's target - this is the single choke point every
     * target change (natural goals, retaliation, sticky re-assert) passes through.
     */
    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event)
    {
        if (event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.MOB_TARGET)
        {
            return;
        }
        if (event.getEntity().level().isClientSide())
        {
            return;
        }
        // Teams are only ever assigned to wizardified Mobs, so nothing else can share one.
        if (!(event.getEntity() instanceof Mob mob))
        {
            return;
        }
        LivingEntity proposed = event.getNewTarget();
        if (proposed == null || !MobWizardryTeams.sameTeam(mob, proposed))
        {
            return;
        }
        event.setCanceled(true);
        if (mob.getTarget() == proposed)
        {
            mob.setTarget(null);
        }
    }

    /**
     * Same-team mobs cannot hurt each other at all - melee and spell/projectile hits (owner from
     * {@code DamageSource.getEntity()}) are canceled before damage is applied.
     */
    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event)
    {
        if (event.getEntity().level().isClientSide())
        {
            return;
        }
        // Teams are only ever assigned to wizardified Mobs, so nothing else can share one.
        if (!(event.getEntity() instanceof Mob))
        {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker))
        {
            return;
        }
        if (MobWizardryTeams.sameTeam(attacker, event.getEntity()))
        {
            event.setCanceled(true);
        }
    }
}
