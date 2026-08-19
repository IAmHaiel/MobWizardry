package com.haylent.mobwizardry.network;

import com.haylent.mobwizardry.client.WizardSkinTextures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server-to-client: the wizard skins the server loaded from
 * {@code config/mobwizardry/wizard-skins}, as name → PNG bytes. The client registers each as a
 * dynamic texture so wizard NPCs render with the correct skin even on a dedicated server. The
 * handler only touches client code through {@code DistExecutor} (never load client classes on
 * the server).
 */
public class SyncSkinsPacket
{
    private final Map<String, byte[]> skins;

    public SyncSkinsPacket(Map<String, byte[]> skins)
    {
        this.skins = skins != null ? new HashMap<>(skins) : new HashMap<>();
    }

    public SyncSkinsPacket(FriendlyByteBuf buf)
    {
        int count = buf.readVarInt();
        Map<String, byte[]> read = new HashMap<>();
        for (int i = 0; i < count; i++)
        {
            read.put(buf.readUtf(64), buf.readByteArray());
        }
        this.skins = read;
    }

    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(skins.size());
        for (Map.Entry<String, byte[]> entry : skins.entrySet())
        {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeByteArray(entry.getValue());
        }
    }

    /**
     * The skins carried by this packet (name → PNG bytes).
     */
    public Map<String, byte[]> skins()
    {
        return skins;
    }

    public static void handle(SyncSkinsPacket msg, Supplier<NetworkEvent.Context> ctxSupplier)
    {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> WizardSkinTextures.receive(msg.skins)));
        ctx.setPacketHandled(true);
    }
}
