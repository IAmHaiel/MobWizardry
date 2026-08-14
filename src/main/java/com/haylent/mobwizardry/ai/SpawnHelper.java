package com.haylent.mobwizardry.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Finds a safe, spawnable position for the summon command: prefers the exact spot, then the
 * surface above it, then scans upward from the requested Y.
 */
public final class SpawnHelper
{
    private SpawnHelper()
    {
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
