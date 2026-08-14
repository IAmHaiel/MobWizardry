package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Applies preset equipment, attribute overrides and a full mana pool to a mob.
 * Uses Iron's Spellbooks' MagicData which is auto-attached to all LivingEntities.
 * Also provides safe-spawn lookup for the summon command.
 */
public class WizardMobInit
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void apply(PathfinderMob mob, PresetDefinition preset)
    {
        applyEquipment(mob, preset);
        applyAttributes(mob, preset);
        applyMana(mob, preset);
    }

    /**
     * Removes the equipment that the preset's {@code equipment} block put on the mob.
     * Called when a mob is de-wizardified (tag removed) so the wizard gear disappears.
     */
    public static void stripWizardEquipment(PathfinderMob mob, PresetDefinition preset)
    {
        for (String slotName : preset.equipment.keySet())
        {
            EquipmentSlot slot = PresetDefinition.parseSlot(slotName);
            if (slot != null)
            {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                mob.setDropChance(slot, 0.0f);
            }
        }
    }

    private static void applyEquipment(PathfinderMob mob, PresetDefinition preset)
    {
        for (Map.Entry<String, String> entry : preset.equipment.entrySet())
        {
            EquipmentSlot slot = PresetDefinition.parseSlot(entry.getKey());
            if (slot == null)
            {
                LOGGER.warn("[MobWizardry] Unknown equipment slot '{}' in preset '{}' - skipped", entry.getKey(), preset.requiredTag);
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getValue());
            if (rl == null)
            {
                continue;
            }
            var item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null)
            {
                LOGGER.warn("[MobWizardry] Equipment item '{}' not found for preset '{}' - skipped", entry.getValue(), preset.requiredTag);
                continue;
            }
            mob.setItemSlot(slot, new ItemStack(item));
            mob.setDropChance(slot, 0.0f);
        }
    }

    private static void applyAttributes(PathfinderMob mob, PresetDefinition preset)
    {
        for (Map.Entry<String, Double> entry : preset.attributes.entrySet())
        {
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null)
            {
                continue;
            }
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(rl);
            if (attribute == null)
            {
                continue;
            }
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance == null)
            {
                LOGGER.warn("[MobWizardry] Attribute '{}' not present on {} - skipped", entry.getKey(), mob.getType().getDescriptionId());
                continue;
            }
            instance.setBaseValue(entry.getValue());
        }
    }

    private static void applyMana(PathfinderMob mob, PresetDefinition preset)
    {
        // Mob casting is free (CastSource.MOB bypasses mana costs), so just keep the mana bar full.
        MagicData magicData = MagicData.getPlayerMagicData((LivingEntity) mob);
        float maxMana = (float) mob.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
        magicData.setMana(Math.max(0.0f, maxMana));
    }

    public static Vec3 findSafeSpawn(ServerLevel level, Vec3 pos)
    {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        if (isSpawnableAt(level, bp.set(pos.x, pos.y, pos.z)) && pos.y >= topSolidY(level, pos.x, pos.z))
        {
            return pos;
        }
        Vec3 surfacePos = new Vec3(Math.floor(pos.x) + 0.5, topSolidY(level, pos.x, pos.z) + 1, Math.floor(pos.z) + 0.5);
        if (isSpawnableAt(level, bp.set(surfacePos.x, surfacePos.y, surfacePos.z)))
        {
            return surfacePos;
        }
        int maxY = level.getMaxBuildHeight() - 1;
        int startY = Math.max(level.getMinBuildHeight() + 1, (int) Math.floor(pos.y) + 1);
        for (int y = startY; y <= maxY; y++)
        {
            bp.set(pos.x, y, pos.z);
            if (isSpawnableAt(level, bp))
            {
                return new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            }
        }
        return pos;
    }

    private static int topSolidY(ServerLevel level, double x, double z)
    {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--)
        {
            bp.set(x, y, z);
            BlockState state = level.getBlockState(bp);
            if (!state.getCollisionShape(level, bp).isEmpty() || !state.getFluidState().isEmpty())
            {
                return y;
            }
        }
        return level.getMinBuildHeight() - 1;
    }

    private static boolean isSpawnableAt(ServerLevel level, BlockPos pos)
    {
        if (pos.getY() < level.getMinBuildHeight() + 1 || pos.getY() > level.getMaxBuildHeight() - 1)
        {
            return false;
        }
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return !at.isSuffocating(level, pos) && !above.isSuffocating(level, pos.above());
    }
}
