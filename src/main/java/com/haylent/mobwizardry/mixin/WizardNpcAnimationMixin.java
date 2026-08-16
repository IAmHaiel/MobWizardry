package com.haylent.mobwizardry.mixin;

import com.haylent.mobwizardry.entity.WizardNpc;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side mixin that registers the Wizard NPC as a playerAnimator animation player (mirroring
 * what playerAnimator does for players), so its existing {@code HumanoidModel} mixins animate the
 * NPC's {@code PlayerModel}. Also hosts the reusable cast-animation layer that the renderer swaps
 * the per-spell player casting animation into. Client-only (declared in the mixins config's
 * {@code client} section).
 */
@Mixin(WizardNpc.class)
public abstract class WizardNpcAnimationMixin implements IAnimatedPlayer, WizardNpcCastAnimator
{
    private static final int CAST_LAYER_PRIORITY = 42;

    @Unique
    private final Map<ResourceLocation, IAnimation> mobwizardry$modAnimationData = new HashMap<>();
    @Unique
    private final AnimationStack mobwizardry$animationStack = new AnimationStack();
    @Unique
    private final AnimationApplier mobwizardry$animationApplier = new AnimationApplier(this.mobwizardry$animationStack);
    @Unique
    private final ModifierLayer<KeyframeAnimationPlayer> mobwizardry$castLayer = new ModifierLayer<>();
    @Unique
    private boolean mobwizardry$castLayerAdded;

    @Override
    public AnimationStack getAnimationStack()
    {
        return this.mobwizardry$animationStack;
    }

    @Override
    public AnimationApplier playerAnimator_getAnimation()
    {
        return this.mobwizardry$animationApplier;
    }

    @Override
    public IAnimation playerAnimator_getAnimation(ResourceLocation id)
    {
        return this.mobwizardry$modAnimationData.get(id);
    }

    @Override
    public IAnimation playerAnimator_setAnimation(ResourceLocation id, IAnimation animation)
    {
        return animation == null
                ? this.mobwizardry$modAnimationData.remove(id)
                : this.mobwizardry$modAnimationData.put(id, animation);
    }

    @Override
    public ModifierLayer<KeyframeAnimationPlayer> mobwizardry$getCastLayer()
    {
        if (!this.mobwizardry$castLayerAdded)
        {
            this.mobwizardry$castLayerAdded = true;
            this.mobwizardry$animationStack.addAnimLayer(CAST_LAYER_PRIORITY, this.mobwizardry$castLayer);
        }
        return this.mobwizardry$castLayer;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mobwizardry$tickAnimation(CallbackInfo ci)
    {
        this.mobwizardry$animationStack.tick();
    }
}
