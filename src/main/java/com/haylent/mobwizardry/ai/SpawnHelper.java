package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Finds a safe, spawnable position for the summon command: prefers the exact spot, then the
 * surface above it, then scans upward from the requested Y.
 */
public final class SpawnHelper
{
    private SpawnHelper()
    {
    }

    /**
     * Creates a mob of {@code entityTypeId}, tags it with the preset's required tag and adds it
     * to the level at a safe spot near {@code pos} (the join handler attaches the wizard AI).
     * Shared by the boss spawner and the raid system. Returns the spawned mob, or null if the
     * entity type is unknown or not a {@link PathfinderMob}.
     */
    public static PathfinderMob spawnTaggedMob(ServerLevel level, String entityTypeId, PresetDefinition preset, Vec3 pos)
    {
        ResourceLocation rl = ResourceLocation.tryParse(entityTypeId);
        EntityType<?> type = rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null)
        {
            return null;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof PathfinderMob mob))
        {
            return null;
        }
        Vec3 safe = findSafeSpawn(level, pos);
        mob.moveTo(safe.x, safe.y, safe.z);
        mob.addTag(preset.requiredTag);
        level.addFreshEntity(mob);
        return mob;
    }

    public static Vec3 findSafeSpawn(ServerLevel level, Vec3 pos)
    {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        double surfaceY = topSolidY(level, pos.x, pos.z);
        if (isSpawnableAt(level, bp.set(pos.x, pos.y, pos.z)) && pos.y >= surfaceY)
        {
            return pos;
        }
        Vec3 surfacePos = new Vec3(Math.floor(pos.x) + 0.5, surfaceY + 1, Math.floor(pos.z) + 0.5);
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
