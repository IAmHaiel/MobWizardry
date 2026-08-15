package com.haylent.mobwizardry.ai;

/**
 * Default behavior: keep distance, cast from afar, retreat via the escape kit.
 * Adds a movement-weight demand when the target is too close, so the wizard casts
 * its movement spell (blood_step / teleport) to reposition away instead of standing
 * point-blank and strafing.
 */
public class RangedWizardType extends WizardType
{
    private static final double TOO_CLOSE_RATIO = 0.4;
    private static final int REPOSITION_WEIGHT = 400;

    @Override
    public String getName()
    {
        return "ranged";
    }

    @Override
    public int adjustMovementWeight(int base, double distance, double range)
    {
        if (distance < range * TOO_CLOSE_RATIO)
        {
            return Math.max(base, REPOSITION_WEIGHT);
        }
        return base;
    }
}
