package com.haylent.mobwizardry;

import com.haylent.mobwizardry.ai.BossManager;
import com.haylent.mobwizardry.ai.RaidManager;
import com.haylent.mobwizardry.command.MobWizardryCommands;
import com.haylent.mobwizardry.config.PresetManager;
import com.haylent.mobwizardry.entity.WizardSkins;
import com.haylent.mobwizardry.event.MobWizardryEvents;
import com.haylent.mobwizardry.registration.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MobWizardryMod.MODID)
public class MobWizardryMod
{
    public static final String MODID = "mobwizardry";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MobWizardryMod()
    {
        ModEntities.ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModEntities.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModEntities.CREATIVE_TABS.register(FMLJavaModLoadingContext.get().getModEventBus());

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new MobWizardryEvents());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event)
    {
        MobWizardryCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("MobWizardry mod loaded on server");
        WizardSkins.createSkinDirectory();
        PresetManager.reload();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            BossManager.tickServer(event.getServer());
            RaidManager.tickServer(event.getServer());
        }
    }
}
