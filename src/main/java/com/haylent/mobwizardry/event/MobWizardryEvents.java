package com.haylent.mobwizardry.event;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.ai.WizardMobInit;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

public class MobWizardryEvents
{
    private static final Logger LOGGER = LogUtils.getLogger();

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
            WizardMobInit.apply(mob, preset);
            WizardAiGoal.tryApply(mob, preset);
        }
    }
}
