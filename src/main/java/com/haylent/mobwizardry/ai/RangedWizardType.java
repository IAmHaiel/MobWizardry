package com.haylent.mobwizardry.ai;

/**
 * Default behavior: keep distance, cast from afar, flee per the survival rules.
 * Every hook returns the identity / "no change" value, so this type is exactly
 * the pre-1.0.3 behavior.
 */
public class RangedWizardType extends WizardType
{
    @Override
    public String getName()
    {
        return "ranged";
    }
}
