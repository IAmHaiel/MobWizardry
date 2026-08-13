package com.haylent.mobwizardry.event;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
}
