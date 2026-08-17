package com.haylent.mobwizardry.client;

import com.haylent.mobwizardry.MobWizardryMod;
import com.haylent.mobwizardry.entity.WizardNpc;
import com.haylent.mobwizardry.entity.WizardSkins;
import com.haylent.mobwizardry.client.WizardNpcCastAnimator;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Renders {@link WizardNpc} with a player-shaped {@link PlayerModel} (classic arms) plus armor and
 * held-item layers, using the NPC's chosen skin texture from
 * {@code config/mobwizardry/wizard-skins/}. Unknown/missing skins fall back to the vanilla Steve
 * texture. While the NPC casts, it plays the same per-spell player casting animation a player
 * would (via playerAnimator + Iron's Spells animation data).
 */
public class WizardNpcRenderer extends HumanoidMobRenderer<WizardNpc, PlayerModel<WizardNpc>>
{
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/steve.png");

    private final Map<String, ResourceLocation> skinCache = new HashMap<>();
    private String lastCastingSpell = "";

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
    public void render(WizardNpc entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight)
    {
        String castingSpell = entity.getCastingSpell();
        if (!castingSpell.equals(this.lastCastingSpell))
        {
            String previous = this.lastCastingSpell;
            this.lastCastingSpell = castingSpell;
            if (castingSpell.isEmpty())
            {
                if (!previous.isEmpty())
                {
                    playCastAnimation(entity, previous, false);
                }
            }
            else
            {
                playCastAnimation(entity, castingSpell, true);
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * Swaps the NPC's cast layer to the spell's player cast animation (start) or fades it out
     * (finish), mirroring how a player's cast is animated.
     */
    private void playCastAnimation(WizardNpc entity, String spellId, boolean start)
    {
        if (!(entity instanceof WizardNpcCastAnimator animator))
        {
            return;
        }
        ModifierLayer<KeyframeAnimationPlayer> layer = animator.mobwizardry$getCastLayer();
        AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE);
        KeyframeAnimationPlayer player = resolveCastAnimation(spellId, start);
        if (player != null)
        {
            layer.replaceAnimationWithFade(fade, player, false);
        }
        else
        {
            layer.replaceAnimationWithFade(fade, null, false);
        }
    }

    private KeyframeAnimationPlayer resolveCastAnimation(String spellId, boolean start)
    {
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null)
        {
            return null;
        }
        AnimationHolder holder = start ? spell.getCastStartAnimation() : spell.getCastFinishAnimation();
        Optional<ResourceLocation> anim = holder.getForPlayer();
        if (anim.isEmpty())
        {
            return null;
        }
        KeyframeAnimation keyframes = PlayerAnimationRegistry.getAnimation(anim.get());
        return keyframes == null ? null : new KeyframeAnimationPlayer(keyframes);
    }

    @Override
    public ResourceLocation getTextureLocation(WizardNpc entity)
    {
        String skin = entity.getSkin();
        if (skin == null || skin.isEmpty())
        {
            return DEFAULT_TEXTURE;
        }
        return skinCache.computeIfAbsent(skin, this::loadSkinTexture);
    }

    /**
     * Loads a skin's {@code .png} from {@code config/mobwizardry/wizard-skins} into a dynamic
     * texture (registered once per skin), falling back to the vanilla Steve texture when the file
     * is missing.
     */
    private ResourceLocation loadSkinTexture(String skin)
    {
        Path path = WizardSkins.skinDirectory().resolve(skin + ".png");
        if (!Files.exists(path))
        {
            return DEFAULT_TEXTURE;
        }
        try (InputStream in = Files.newInputStream(path))
        {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(image);
            return Minecraft.getInstance().getTextureManager().register("mobwizardry_skin_" + skin, texture);
        }
        catch (IOException e)
        {
            return DEFAULT_TEXTURE;
        }
    }
}
