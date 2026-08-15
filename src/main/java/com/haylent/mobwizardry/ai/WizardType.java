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

    public abstract String getName();

    /** Whether the wizard may use its escape kit (retreat). Ranged: yes. Close: no. */
    public boolean allowsEscape()
    {
        return true;
    }

    /** Forward strafe amount while in combat range; 0 = use the base goal's movement. */
    public float strafeForward()
    {
        return 0.0f;
    }

    /** Whether an adjacent target should be melee'd instead of casting this window. */
    public boolean wantsMelee(double distance)
    {
        return false;
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
    public int adjustMovementWeight(int base, double distance, double range)
    {
        return base;
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
