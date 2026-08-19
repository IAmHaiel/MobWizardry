package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.Rewards;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Grants configured rewards to the players in a level: each per-player command is executed with
 * {@code %player%} replaced by the player's name, then the reward message (if any) is broadcast.
 */
public final class RewardManager
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private RewardManager()
    {
    }

    /**
     * Grants every configured reward to every player currently in {@code level}. Commands run via
     * the server's command dispatcher with a suppressed feedback source, one per player.
     */
    public static void grantRewards(ServerLevel level, Rewards rewards)
    {
        if (rewards == null || !rewards.hasContent())
        {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null)
        {
            return;
        }
        for (ServerPlayer player : level.players())
        {
            if (player.isRemoved())
            {
                continue;
            }
            String playerName = player.getGameProfile().getName();
            for (String command : rewards.commands)
            {
                if (command == null || command.isBlank())
                {
                    continue;
                }
                String resolved = command.replace("%player%", playerName);
                try
                {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(), resolved);
                }
                catch (Exception e)
                {
                    LOGGER.warn("[MobWizardry] Reward command failed for {}: '{}'", playerName, resolved, e);
                }
            }
        }
        if (rewards.message != null && !rewards.message.isBlank())
        {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(rewards.message).withStyle(ChatFormatting.GOLD), false);
        }
    }
}