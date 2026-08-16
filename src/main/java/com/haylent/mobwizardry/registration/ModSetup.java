package com.haylent.mobwizardry.registration;

import com.haylent.mobwizardry.MobWizardryMod;
import com.haylent.mobwizardry.entity.WizardNpc;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * MOD-bus setup: registers the Wizard NPC's attributes.
 */
@Mod.EventBusSubscriber(modid = MobWizardryMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModSetup
{
    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.WIZARD_NPC.get(), WizardNpc.createAttributes().build());
    }
}
