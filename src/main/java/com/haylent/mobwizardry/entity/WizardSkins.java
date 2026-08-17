package com.haylent.mobwizardry.entity;

import com.haylent.mobwizardry.MobWizardryMod;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Chooses a skin for a {@link WizardNpc} from the config folder
 * {@code config/mobwizardry/wizard-skins/} (each skin is a {@code .png} file there). The server
 * enumerates that folder (so there is no separate config list to maintain) and stores the picked
 * name in the NPC's synced skin data; the renderer loads the matching file from the same folder.
 * Skins must be present on both the server (for picking) and every client (for rendering); a
 * missing file falls back to the vanilla Steve texture.
 */
public class WizardSkins
{
    private WizardSkins()
    {
    }

    /**
     * The folder skins are loaded from: {@code config/mobwizardry/wizard-skins}.
     */
    public static Path skinDirectory()
    {
        return FMLPaths.CONFIGDIR.get().resolve(MobWizardryMod.MODID).resolve("wizard-skins");
    }

    /**
     * Creates the skin folder so players know where to drop skin files.
     */
    public static void createSkinDirectory()
    {
        try
        {
            Files.createDirectories(skinDirectory());
        }
        catch (IOException e)
        {
            LogUtils.getLogger().error("[MobWizardry] Could not create the wizard skins folder", e);
        }
    }

    /**
     * Picks a random skin name if the NPC does not have one yet. Uses {@code default} as a last
     * resort; the renderer falls back to the vanilla Steve texture if even that is missing.
     */
    public static void ensureSkin(WizardNpc npc)
    {
        if (!npc.getSkin().isEmpty())
        {
            return;
        }
        MinecraftServer server = npc.level().getServer();
        if (server == null)
        {
            return;
        }
        List<String> names = new ArrayList<>();
        Path dir = skinDirectory();
        if (Files.isDirectory(dir))
        {
            try (Stream<Path> files = Files.list(dir))
            {
                files.filter(path -> path.getFileName().toString().endsWith(".png"))
                        .forEach(path -> {
                            String name = stripPngExtension(path.getFileName().toString());
                            if (!name.isEmpty())
                            {
                                names.add(name);
                            }
                        });
            }
            catch (IOException e)
            {
                LogUtils.getLogger().warn("[MobWizardry] Could not list wizard skins in {}", dir, e);
            }
        }
        if (names.isEmpty())
        {
            names.add("default");
        }
        npc.setSkin(names.get(npc.getRandom().nextInt(names.size())));
    }

    private static String stripPngExtension(String fileName)
    {
        return fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
