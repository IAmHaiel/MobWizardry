package com.haylent.mobwizardry.client;

import com.haylent.mobwizardry.MobWizardryMod;
import com.haylent.mobwizardry.registration.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only MOD-bus registration for the Wizard NPC renderer.
 */
@Mod.EventBusSubscriber(modid = MobWizardryMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MobWizardryClient
{
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.WIZARD_NPC.get(), WizardNpcRenderer::new);
    }
}
