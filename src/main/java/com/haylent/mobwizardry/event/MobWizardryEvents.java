package com.haylent.mobwizardry.event;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.config.MobWizardryTeams;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.haylent.mobwizardry.entity.WizardNpc;
import com.haylent.mobwizardry.entity.WizardSkinSync;
import com.haylent.mobwizardry.entity.WizardSkins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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

        boolean matched = false;
        for (String tag : mob.getTags())
        {
            PresetDefinition preset = PresetManager.getPresetByTag(tag);
            if (preset != null)
            {
                WizardAiGoal.attach(mob, preset);
                matched = true;
            }
        }
        if (mob instanceof WizardNpc npc)
        {
            // A wizard NPC summoned outside MobWizardry (e.g. vanilla /summon) has no preset tag:
            // default it to the ranged 'wizard' preset so it is still a functional wizard.
            if (!matched)
            {
                PresetDefinition fallback = PresetManager.getPreset("wizard");
                if (fallback != null)
                {
                    npc.addTag(fallback.requiredTag);
                    WizardAiGoal.attach(npc, fallback);
                }
            }
            WizardSkins.ensureSkin(npc);
        }
    }

    /**
     * Allied mobs (same team, or enemy-faction wizards vs hostile monsters) never become each
     * other's target - this is the single choke point every target change (natural goals,
     * retaliation, sticky re-assert) passes through.
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
        if (proposed == null || !MobWizardryTeams.areAllies(mob, proposed))
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
     * Allied mobs cannot hurt each other at all - melee and spell/projectile hits (owner from
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
        if (MobWizardryTeams.areAllies(attacker, event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    /**
     * When a player joins the server, the server pushes the wizard skins it loaded (if any) so
     * the client can render wizard NPCs with the correct skins.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            WizardSkinSync.sendToPlayer(player);
        }
    }
}
