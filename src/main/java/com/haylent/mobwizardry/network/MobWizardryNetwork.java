package com.haylent.mobwizardry.network;

import com.haylent.mobwizardry.MobWizardryMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * The mod's packet channel. Registered during common setup so the channel and its message are
 * ready before any connection handshake. One message for now: the server pushes the wizard skin
 * PNGs it loaded from {@code config/mobwizardry/wizard-skins} to every client so the renderer
 * can show them on a dedicated server.
 */
public final class MobWizardryNetwork
{
    public static final String CHANNEL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MobWizardryMod.MODID, "main"),
            () -> CHANNEL_VERSION,
            CHANNEL_VERSION::equals,
            CHANNEL_VERSION::equals);

    private static boolean registered = false;

    private MobWizardryNetwork()
    {
    }

    /**
     * Registers the channel's messages. Called from {@code FMLCommonSetupEvent} (both sides).
     */
    public static void register()
    {
        if (!registered)
        {
            registered = true;
            CHANNEL.registerMessage(0, SyncSkinsPacket.class,
                    SyncSkinsPacket::write,
                    SyncSkinsPacket::new,
                    SyncSkinsPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }
}
