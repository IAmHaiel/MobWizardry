package com.haylent.mobwizardry.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side registry of the wizard skins received from the server (see
 * {@code SyncSkinsPacket}). Each PNG is registered as a dynamic texture keyed by skin name, and
 * {@code WizardNpcRenderer} uses it first (so a dedicated server's custom skins render), falling
 * back to the local config folder (LAN / single-player) and then to Steve.
 */
public final class WizardSkinTextures
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private WizardSkinTextures()
    {
    }

    /**
     * Registers (or refreshes) the skins pushed by the server. The {@code DynamicTexture} takes
     * ownership of its {@code NativeImage}, so the image is not closed here.
     */
    public static void receive(Map<String, byte[]> skins)
    {
        for (Map.Entry<String, byte[]> entry : skins.entrySet())
        {
            try
            {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(entry.getValue()));
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation rl = Minecraft.getInstance().getTextureManager()
                        .register("mobwizardry_skin_" + entry.getKey(), texture);
                TEXTURES.put(entry.getKey(), rl);
            }
            catch (IOException e)
            {
                LOGGER.warn("[MobWizardry] Could not register wizard skin '{}' from the server", entry.getKey(), e);
            }
        }
    }

    /**
     * The client texture for a server-provided skin name, or null if not (yet) received.
     */
    public static ResourceLocation get(String skin)
    {
        return TEXTURES.get(skin);
    }
}