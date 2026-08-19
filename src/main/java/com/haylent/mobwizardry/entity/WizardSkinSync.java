package com.haylent.mobwizardry.entity;

import com.haylent.mobwizardry.network.MobWizardryNetwork;
import com.haylent.mobwizardry.network.SyncSkinsPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads the server's wizard skin PNGs from {@code config/mobwizardry/wizard-skins} into memory
 * and pushes them to clients. The server picks a skin name per NPC and syncs the name, but the
 * PNG itself lives only in the server's config folder - so on a dedicated server clients could
 * never render it. This class is the server-side half: it reads the files, and a
 * {@link SyncSkinsPacket} carries the bytes to every client when they join (or on reload).
 */
public final class WizardSkinSync
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<String, byte[]> serverSkins = Map.of();

    private WizardSkinSync()
    {
    }

    /**
     * Re-reads every {@code .png} from the wizard-skins folder into memory so the skins can be
     * sent to clients. Called at mod load, server start and {@code /mobwizardry reload}.
     */
    public static synchronized void refresh()
    {
        Map<String, byte[]> loaded = new HashMap<>();
        Path dir = WizardSkins.skinDirectory();
        if (Files.isDirectory(dir))
        {
            try (Stream<Path> files = Files.list(dir))
            {
                files.filter(path -> path.getFileName().toString().endsWith(".png"))
                        .forEach(path ->
                        {
                            try
                            {
                                loaded.put(stripPng(path.getFileName().toString()), Files.readAllBytes(path));
                            }
                            catch (IOException e)
                            {
                                LOGGER.warn("[MobWizardry] Could not read wizard skin {}", path, e);
                            }
                        });
            }
            catch (IOException e)
            {
                LOGGER.warn("[MobWizardry] Could not list wizard skins in {}", dir, e);
            }
        }
        serverSkins = Map.copyOf(loaded);
        if (!serverSkins.isEmpty())
        {
            LOGGER.info("[MobWizardry] Loaded {} wizard skin(s) to send to clients: {}", serverSkins.size(), serverSkins.keySet());
        }
    }

    /**
     * The currently loaded server skins (name → PNG bytes), used by the packet and tests.
     */
    public static Map<String, byte[]> skins()
    {
        return serverSkins;
    }

    /**
     * Sends the loaded skins to one player (called when they join the server).
     */
    public static void sendToPlayer(ServerPlayer player)
    {
        if (serverSkins.isEmpty())
        {
            return;
        }
        MobWizardryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncSkinsPacket(serverSkins));
    }

    /**
     * Sends the loaded skins to every online player (called on {@code /mobwizardry reload}).
     */
    public static void sendToAll(MinecraftServer server)
    {
        if (server == null || serverSkins.isEmpty())
        {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            sendToPlayer(player);
        }
    }

    private static String stripPng(String name)
    {
        return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
    }
}