package com.haylent.mobwizardry.ai;

/**
 * Per-preset combat behavior for wizard mobs. A preset's {@code wizard_type}
 * selects the strategy; the goal consults it at each decision point instead of
 * branching on a boolean flag, so adding a new type is one new subclass.
 */
public abstract class WizardType
{
    public static final WizardType RANGED = new RangedWizardType();
    public static final WizardType CLOSE = new CloseWizardType();
    protected static final int REPOSITION_WEIGHT = 400;

    /** Whether the wizard may use its escape kit (retreat). Ranged: yes. Close: no. */
    public boolean allowsEscape()
    {
        return true;
    }

    /** Forward strafe amount while in combat range; 0 = use the base goal's movement. */
    public float strafeForward(double distance, double tooCloseDistance)
    {
        return 0.0f;
    }

    /** Whether the support gate opens while engaging (e.g. for pre-fight buffs). */
    public boolean supportOpenWhileEngaging(double distance)
    {
        return false;
    }

    /** Modifies the computed attack weight. */
    public int adjustAttackWeight(int base, double distance, double range)
    {
        return base;
    }

    /** Modifies the computed movement weight. */
    public int adjustMovementWeight(int base, double distance, double range, double tooCloseDistance)
    {
        return base;
    }

    /**
     * The distance inside which the wizard orbits/strafes the target instead of running
     * straight at it. Ranged: the full spell range. Close: point-blank combat range.
     */
    public double orbitRange(double spellRange)
    {
        return spellRange;
    }

    /**
     * The distance below which the wizard backs straight away from the target instead of
     * orbiting (its standoff). Ranged: 0 = never back-pedal. Close: the configured too-close
     * distance (default ~5 blocks).
     */
    public double tooCloseDistance(double tooCloseDistance)
    {
        return 0.0;
    }

    /** Modifies the computed support weight. */
    public int adjustSupportWeight(int base, double distance, boolean recentlyAttacked, float hpRatio)
    {
        return base;
    }

    public static WizardType fromName(String name)
    {
        return name != null && name.equalsIgnoreCase("close") ? CLOSE : RANGED;
    }
}
