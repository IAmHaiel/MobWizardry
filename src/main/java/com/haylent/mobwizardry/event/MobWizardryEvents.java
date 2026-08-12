package com.haylent.mobwizardry.event;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
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
            if (!matchesTargetMob(mob, preset))
            {
                continue;
            }
            WizardAiGoal.tryApply(mob, preset);
        }
    }

    private boolean matchesTargetMob(PathfinderMob mob, PresetDefinition preset)
    {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null)
        {
            return false;
        }
        return preset.targetMobs.contains(mobId.toString());
    }
}
