package com.haylent.mobwizardry.ai;

/**
 * Close-range behavior: charge in, cast point-blank, melee when adjacent and
 * buff while engaging. The per-hook behavior is added incrementally in 1.0.3
 * steps 4-6; initially every hook inherits the identity default from
 * {@link WizardType} (same as ranged).
 */
public class CloseWizardType extends WizardType
{
    @Override
    public String getName()
    {
        return "close";
    }
}
