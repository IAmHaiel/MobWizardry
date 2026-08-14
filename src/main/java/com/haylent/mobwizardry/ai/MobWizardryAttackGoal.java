package com.haylent.mobwizardry.ai;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends Iron's Spellbooks' {@link WizardAttackGoal} to fix the AI's category selection
 * weights for tagged mobs:
 * <ul>
 *   <li>defense only fires while the caster was recently attacked (not just at low health);</li>
 *   <li>movement fires when the target is far / out of spell range;</li>
 *   <li>support fires when hurt or below half health, but is chance-gated and cooldown-limited
 *       so a critical caster can't heal-spam and become unkillable;</li>
 *   <li>in critical health, an {@code emergency}-flagged heal is guaranteed (checked before the
 *       weighted category pick, so attack/movement spells can't crowd it out);</li>
 *   <li>escape and critical support casts share one survival cooldown: a heal can't land inside
 *       an escape's window and a heal itself blocks a follow-up escape for the same duration;</li>
 *   <li>type-specific behavior (approach, melee, buffs, escape eligibility) is delegated to the
 *       preset's {@link WizardType} strategy instead of boolean flags.</li>
 * </ul>
 */
public class MobWizardryAttackGoal extends WizardAttackGoal
{
    private static final int DEFENSE_WINDOW_TICKS = 100;
    private static final int SUPPORT_COOLDOWN_TICKS = 140;
    private static final int SURVIVAL_COOLDOWN_TICKS = 100;
    private static final int MELEE_PAUSE_TICKS = 20;
    private static final float CRITICAL_HP = 0.3f;
    private static final float ESCAPE_HP = 0.5f;
    private static final float ESCAPE_CHANCE = 0.35f;
    private int mobwizardry$lastSupportCastTick = -100000;
    private int mobwizardry$lastSurvivalActionTick = -100000;
    private double mobwizardry$movementStartDistance = 0;
    private double mobwizardry$movementFarDistance = 0;
    private List<AbstractSpell> mobwizardry$emergencyHealSpells = new ArrayList<>();
    private List<AbstractSpell> mobwizardry$escapeSpells = new ArrayList<>();
    private WizardType mobwizardry$wizardType = WizardType.RANGED;

    public MobWizardryAttackGoal(IMagicEntity entity, double speed, int minInterval, int maxInterval)
    {
        super(entity, speed, minInterval, maxInterval);
    }

    public void setWizardType(WizardType wizardType)
    {
        this.mobwizardry$wizardType = wizardType != null ? wizardType : WizardType.RANGED;
    }

    public void setEmergencyHealSpells(List<AbstractSpell> emergencyHealSpells)
    {
        this.mobwizardry$emergencyHealSpells = emergencyHealSpells != null ? emergencyHealSpells : new ArrayList<>();
    }

    public void setEscapeSpells(List<AbstractSpell> escapeSpells)
    {
        this.mobwizardry$escapeSpells = escapeSpells != null ? escapeSpells : new ArrayList<>();
    }

    public void setMovementDistances(double startDistance, double farDistance)
    {
        this.mobwizardry$movementStartDistance = startDistance;
        this.mobwizardry$movementFarDistance = farDistance;
    }

    @Override
    protected AbstractSpell getNextSpellType()
    {
        if (shouldEscape())
        {
            mobwizardry$lastSurvivalActionTick = mob.tickCount;
            return mobwizardry$escapeSpells.get(mob.getRandom().nextInt(mobwizardry$escapeSpells.size()));
        }
        if (isCritical() && survivalCooldownReady() && !mobwizardry$emergencyHealSpells.isEmpty())
        {
            mobwizardry$lastSurvivalActionTick = mob.tickCount;
            mobwizardry$lastSupportCastTick = mob.tickCount;
            return mobwizardry$emergencyHealSpells.get(mob.getRandom().nextInt(mobwizardry$emergencyHealSpells.size()));
        }
        AbstractSpell spell = super.getNextSpellType();
        if (lastSpellCategory == supportSpells)
        {
            mobwizardry$lastSupportCastTick = mob.tickCount;
            if (isCritical())
            {
                mobwizardry$lastSurvivalActionTick = mob.tickCount;
                if (!mobwizardry$emergencyHealSpells.isEmpty()
                        && !mobwizardry$emergencyHealSpells.contains(spell))
                {
                    spell = mobwizardry$emergencyHealSpells.get(mob.getRandom().nextInt(mobwizardry$emergencyHealSpells.size()));
                }
            }
        }
        return spell;
    }

    private boolean shouldEscape()
    {
        if (!mobwizardry$wizardType.allowsEscape() || mobwizardry$escapeSpells.isEmpty() || !survivalCooldownReady())
        {
            return false;
        }
        if (!recentlyAttacked())
        {
            return false;
        }
        float hpRatio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0f;
        if (hpRatio >= ESCAPE_HP)
        {
            return false;
        }
        return mob.getRandom().nextFloat() < ESCAPE_CHANCE;
    }

    private boolean survivalCooldownReady()
    {
        return mob.tickCount - mobwizardry$lastSurvivalActionTick >= SURVIVAL_COOLDOWN_TICKS;
    }

    private boolean isCritical()
    {
        return mob.getMaxHealth() > 0 && mob.getHealth() / mob.getMaxHealth() < CRITICAL_HP;
    }

    @Override
    protected int getAttackWeight()
    {
        int base = super.getAttackWeight();
        if (target == null)
        {
            return base;
        }
        double distance = mob.distanceTo(target);
        double range = Math.sqrt(spellcastingRangeSqr);
        return mobwizardry$wizardType.adjustAttackWeight(base, distance, range);
    }

    @Override
    protected void doMovement(double distanceSqr)
    {
        float forwardOverride = mobwizardry$wizardType.strafeForward();
        if (forwardOverride <= 0)
        {
            super.doMovement(distanceSqr);
            return;
        }
        double speed = (spellCastingMob.isCasting() ? 0.75f : 1.0f) * movementSpeed();
        mob.lookAt(target, 30.0f, 30.0f);
        float strafeMultiplier = getStrafeMultiplier();
        if (distanceSqr < spellcastingRangeSqr && seeTime >= 5)
        {
            mob.getNavigation().stop();
            strafeTime++;
            if (strafeTime > 25 && mob.getRandom().nextDouble() < 0.1)
            {
                strafingClockwise = !strafingClockwise;
                strafeTime = 0;
            }
            float strafeDir = strafingClockwise ? 1.0f : -1.0f;
            mob.getMoveControl().strafe(forwardOverride * strafeMultiplier, (float) speed * strafeDir * strafeMultiplier);
            if (mob.horizontalCollision && mob.getRandom().nextFloat() < 0.1f)
            {
                tryJump();
            }
        }
        else
        {
            mob.getNavigation().moveTo(target, speedModifier);
        }
    }

    @Override
    protected void doSpellAction()
    {
        boolean emergencyDue = isCritical() && survivalCooldownReady() && !mobwizardry$emergencyHealSpells.isEmpty();
        if (!emergencyDue && target != null && mobwizardry$wizardType.wantsMelee(mob.distanceTo(target)))
        {
            mob.doHurtTarget(target);
            spellAttackDelay = MELEE_PAUSE_TICKS;
            return;
        }
        super.doSpellAction();
    }

    @Override
    protected int getDefenseWeight()
    {
        if (!recentlyAttacked())
        {
            return -1000;
        }
        int weight = -20;
        float health = mob.getHealth();
        float maxHealth = mob.getMaxHealth();
        if (maxHealth > 0)
        {
            float hp = health / maxHealth;
            weight += (int) (50.0f * (1.0f - hp * hp * hp));
        }
        weight += 95 * projectileCount;
        if (target != null && target.getMaxHealth() > 0)
        {
            weight += (int) ((1.0f - target.getHealth() / target.getMaxHealth()) * -35.0f);
        }
        return weight + 30;
    }

    @Override
    protected int getMovementWeight()
    {
        if (target == null)
        {
            return 0;
        }
        double distSqr = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        double range = Math.sqrt(spellcastingRangeSqr);
        double start = mobwizardry$movementStartDistance > 0 ? mobwizardry$movementStartDistance : range * 0.75;
        double far = mobwizardry$movementFarDistance > start ? mobwizardry$movementFarDistance : range;
        double distance = Math.sqrt(distSqr);
        int weight = 0;
        if (distance > far)
        {
            weight += 200 + (int) (100.0 * Math.min((distance - far) / far, 2.0));
        }
        else if (distance > start)
        {
            weight += (int) (80.0 * (distance - start) / Math.max(far - start, 1.0));
        }
        if (!hasLineOfSight)
        {
            weight += 80;
        }
        double distRatio = Mth.clamp(distSqr / spellcastingRangeSqr, 0.0, 1.0);
        float hpRatio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0f;
        weight += (int) (400.0f * (1.0f - hpRatio) * (1.0f - hpRatio) * (float) (1.0 - distRatio) * (float) (1.0 - distRatio));
        return weight;
    }

    @Override
    protected int getSupportWeight()
    {
        if (isCritical() && !survivalCooldownReady())
        {
            return -1000;
        }
        float hpRatio = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0f;
        boolean hurt = recentlyAttacked();
        double distance = target == null ? Double.MAX_VALUE : mob.distanceTo(target);
        if (!hurt && hpRatio >= 0.5f && !mobwizardry$wizardType.supportOpenWhileEngaging(distance))
        {
            return -1000;
        }
        if (mob.tickCount - mobwizardry$lastSupportCastTick < SUPPORT_COOLDOWN_TICKS)
        {
            return -1000;
        }
        float chance = Mth.clamp(0.15f + (0.55f - hpRatio) * 0.9f, 0.0f, 0.55f);
        if (mobwizardry$wizardType.supportOpenWhileEngaging(distance))
        {
            chance = Math.max(chance, 0.3f);
        }
        if (mob.getRandom().nextFloat() >= chance)
        {
            return -1000;
        }
        int weight = -15 + (int) Math.min(120, 300.0f * (1.0f - hpRatio));
        if (hurt)
        {
            weight += 60;
        }
        return mobwizardry$wizardType.adjustSupportWeight(weight, distance, hurt, hpRatio);
    }

    private boolean recentlyAttacked()
    {
        return mob.hurtTime > 0
                || (mob.getLastHurtByMob() != null
                && mob.tickCount - mob.getLastHurtByMobTimestamp() <= DEFENSE_WINDOW_TICKS);
    }
}

