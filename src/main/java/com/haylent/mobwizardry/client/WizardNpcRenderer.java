package com.haylent.mobwizardry.client;

import com.haylent.mobwizardry.MobWizardryMod;
import com.haylent.mobwizardry.entity.WizardNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders {@link WizardNpc} with a player-shaped {@link PlayerModel} (classic arms) plus armor and
 * held-item layers, using the NPC's chosen skin texture from
 * {@code assets/mobwizardry/textures/entity/wizard/skins/}. Unknown skins fall back to the vanilla
 * Steve texture.
 */
public class WizardNpcRenderer extends HumanoidMobRenderer<WizardNpc, PlayerModel<WizardNpc>>
{
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/steve.png");

    private final Map<String, ResourceLocation> skinCache = new HashMap<>();

    public WizardNpcRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(WizardNpc entity)
    {
        String skin = entity.getSkin();
        if (skin == null || skin.isEmpty())
        {
            return DEFAULT_TEXTURE;
        }
        return skinCache.computeIfAbsent(skin, s -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    MobWizardryMod.MODID, com.haylent.mobwizardry.entity.WizardSkins.SKIN_FOLDER + "/" + s + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent()
                    ? loc : DEFAULT_TEXTURE;
        });
    }
}
