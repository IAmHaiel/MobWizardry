package com.haylent.mobwizardry.mixin;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;

/**
 * Client-side bridge between {@code WizardNpc} and its renderer: exposes the reusable cast
 * animation layer the renderer swaps spell animations into.
 */
public interface WizardNpcCastAnimator
{
    ModifierLayer<KeyframeAnimationPlayer> mobwizardry$getCastLayer();
}
