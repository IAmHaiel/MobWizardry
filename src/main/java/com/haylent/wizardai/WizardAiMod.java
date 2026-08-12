package com.haylent.wizardai;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(WizardAiMod.MODID)
public class WizardAiMod
{
    public static final String MODID = "wizardai";
    private static final Logger LOGGER = LogUtils.getLogger();

    public WizardAiMod()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("Wizard AI mod loaded on server");
    }
}
