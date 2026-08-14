package com.haylent.mobwizardry.ai;

/**
 * Close-range behavior: charge in, cast point-blank, melee when adjacent and
 * buff while engaging.
 */
public class CloseWizardType extends WizardType
{
    private static final double MELEE_REACH = 2.5;
    private static final double MELEE_CHANCE = 0.5;
    private static final double POINT_BLANK_DISTANCE = 10.0;
    private static final double ENGAGE_DISTANCE = 12.0;
    private static final int POINT_BLANK_ATTACK_BOOST = 80;

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

    @Override
    public boolean wantsMelee(double distance)
    {
        return distance <= MELEE_REACH && Math.random() < MELEE_CHANCE;
    }

    @Override
    public boolean supportOpenWhileEngaging(double distance)
    {
        return distance <= ENGAGE_DISTANCE;
    }

    @Override
    public int adjustSupportWeight(int base, double distance, boolean recentlyAttacked, float hpRatio)
    {
        if (distance <= ENGAGE_DISTANCE)
        {
            return Math.max(base, 130);
        }
        return base;
    }

    @Override
    public int adjustAttackWeight(int base, double distance, double range)
    {
        if (base <= 0)
        {
            return base;
        }
        if (distance <= POINT_BLANK_DISTANCE)
        {
            return base + POINT_BLANK_ATTACK_BOOST;
        }
        return base;
    }
}
