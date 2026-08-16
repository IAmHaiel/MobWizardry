package com.haylent.mobwizardry.entity;

import com.haylent.mobwizardry.MobWizardryMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses a skin for a {@link WizardNpc} from the resource-pack folder
 * {@code assets/mobwizardry/textures/entity/wizard/skins/}. The server enumerates that folder
 * (so there is no separate config list to maintain) and stores the picked name in the NPC's
 * synced skin data, which the renderer turns into a texture. Skins must be visible to the server
 * (shipped with the mod or in a server-installed resource pack) so every client agrees.
 */
public class WizardSkins
{
    public static final String SKIN_FOLDER = "textures/entity/wizard/skins";

    private WizardSkins()
    {
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
        server.getResourceManager()
                .listResources(SKIN_FOLDER, loc -> loc.getNamespace().equals(MobWizardryMod.MODID)
                        && loc.getPath().endsWith(".png"))
                .keySet()
                .forEach(loc -> {
                    String path = loc.getPath();
                    String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
                    if (!name.isEmpty())
                    {
                        names.add(name);
                    }
                });
        if (names.isEmpty())
        {
            names.add("default");
        }
        npc.setSkin(names.get(npc.getRandom().nextInt(names.size())));
    }
}
