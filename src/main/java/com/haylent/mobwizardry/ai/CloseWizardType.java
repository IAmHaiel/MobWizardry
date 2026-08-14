package com.haylent.mobwizardry.ai;

/**
 * Close-range behavior: charge in, cast point-blank, melee when adjacent and
 * buff while engaging.
 */
public class CloseWizardType extends WizardType
{
    @Override
    public String getName()
    {
        return "close";
    }

    @Override
    public boolean allowsEscape()
    {
        return false;
    }

    @Override
    public float strafeForward()
    {
        return 0.6f;
    }
}
