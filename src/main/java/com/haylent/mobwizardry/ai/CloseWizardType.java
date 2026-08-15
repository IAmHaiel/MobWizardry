package com.haylent.mobwizardry.ai;

/**
 * Close-range behavior: charge in, cast point-blank, keep a ~5-block standoff and
 * buff while engaging. Does not melee - it is purely a spellcaster.
 */
public class CloseWizardType extends WizardType
{
    private static final double POINT_BLANK_DISTANCE = 10.0;
    private static final double ENGAGE_DISTANCE = 12.0;
    private static final double REPOSITION_TOO_CLOSE = 5.0;
    private static final int REPOSITION_WEIGHT = 400;
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
    public float strafeForward(double distance)
    {
        // Keep a ~5-block standoff: back away while orbiting when the target is inside the
        // band, gently close in when beyond it, so the wizard never charges into melee range.
        if (distance < REPOSITION_TOO_CLOSE)
        {
            return -0.3f;
        }
        return 0.15f;
    }

    @Override
    public double orbitRange(double spellRange)
    {
        return Math.min(spellRange, POINT_BLANK_DISTANCE);
    }

    @Override
    public double tooCloseDistance(double spellRange)
    {
        return REPOSITION_TOO_CLOSE;
    }

    @Override
    public int adjustMovementWeight(int base, double distance, double range)
    {
        // Far: cast a movement spell to close the gap. Too close: hop back a bit.
        if (distance > orbitRange(range) || distance < REPOSITION_TOO_CLOSE)
        {
            return Math.max(base, REPOSITION_WEIGHT);
        }
        return base;
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
