package com.haylent.mobwizardry.ai;

/**
 * Default behavior: keep distance, cast from afar, retreat via the escape kit.
 * Adds a movement-weight demand when the target is closer than the configured too-close
 * distance, so the wizard casts its movement spell (blood_step / teleport) to reposition away
 * instead of standing point-blank and strafing.
 */
public class RangedWizardType extends WizardType
{
    @Override
    public int adjustMovementWeight(int base, double distance, double range, double tooCloseDistance)
    {
        if (distance < tooCloseDistance)
        {
            return Math.max(base, REPOSITION_WEIGHT);
        }
        return base;
    }
}
