package com.haylent.mobwizardry.client;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;

/**
 * Client-side bridge between {@code WizardNpc} and its renderer: exposes the reusable cast
 * animation layer the renderer swaps spell animations into. Kept OUT of the mixin package
 * because Mixin forbids direct references to classes in a defined mixin package.
 */
public interface WizardNpcCastAnimator
{
    ModifierLayer<KeyframeAnimationPlayer> mobwizardry$getCastLayer();
}
