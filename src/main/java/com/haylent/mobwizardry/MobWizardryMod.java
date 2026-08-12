package com.haylent.mobwizardry;

import com.haylent.mobwizardry.config.PresetManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MobWizardryMod.MODID)
public class MobWizardryMod
{
    public static final String MODID = "mobwizardry";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MobWizardryMod()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("MobWizardry mod loaded on server");
        PresetManager.reload();
    }
}
