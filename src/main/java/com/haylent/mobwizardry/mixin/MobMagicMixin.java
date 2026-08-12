package com.haylent.mobwizardry.mixin;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.spells.CastingMobAimingData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the Iron's Spellbooks {@link IMagicEntity} interface into all vanilla {@link Mob}s so that
 * {@code WizardAttackGoal} can be attached to any tagged mob. Also replicates the cast-progression tick loop
 * that ISS normally runs in {@code AbstractSpellCastingMob.customServerAiStep()}.
 */
@Mixin(Mob.class)
public abstract class MobMagicMixin implements IMagicEntity
{
    @Unique
    private SpellData mobwizardry$castingSpell;
    @Unique
    private boolean mobwizardry$hasUsedSingleAttack;

    @Override
    public MagicData getMagicData()
    {
        return MagicData.getPlayerMagicData((LivingEntity) (Object) this);
    }

    @Override
    public void setSyncedSpellData(SyncedSpellData data)
    {
        // Client-side only in ISS; no-op on the server.
    }

    @Override
    public boolean isCasting()
    {
        return getMagicData().isCasting();
    }

    @Override
    public void initiateCastSpell(AbstractSpell spell, int level)
    {
        if (spell == SpellRegistry.none())
        {
            this.mobwizardry$castingSpell = null;
            return;
        }
        this.mobwizardry$castingSpell = new SpellData(spell, level);
        LivingEntity self = (LivingEntity) (Object) this;
        MagicData magicData = getMagicData();
        if (!self.level().isClientSide)
        {
            if (!spell.checkPreCastConditions(self.level(), level, self, magicData))
            {
                this.mobwizardry$castingSpell = null;
                return;
            }
            if (spell == SpellRegistry.TELEPORT_SPELL.get() || spell == SpellRegistry.FROST_STEP_SPELL.get())
            {
                setTeleportLocationBehindTarget(10);
            }
            else if (spell == SpellRegistry.BLOOD_STEP_SPELL.get())
            {
                setTeleportLocationBehindTarget(3);
            }
            else if (spell == SpellRegistry.BURNING_DASH_SPELL.get())
            {
                setBurningDashDirectionData();
            }
            else if (spell == SpellRegistry.RAY_OF_SIPHONING_SPELL.get())
            {
                magicData.setAdditionalCastData(new CastingMobAimingData());
            }
            magicData.initiateCast(spell, level, spell.getEffectiveCastTime(level, self), CastSource.MOB, SpellSelectionManager.MAINHAND);
            spell.onServerPreCast(self.level(), level, self, magicData);
        }
    }

    @Override
    public void cancelCast()
    {
        if (isCasting())
        {
            getMagicData().resetCastingState();
        }
        this.mobwizardry$castingSpell = null;
    }

    @Override
    public void castComplete()
    {
        LivingEntity self = (LivingEntity) (Object) this;
        MagicData magicData = getMagicData();
        if (!self.level().isClientSide && this.mobwizardry$castingSpell != null)
        {
            this.mobwizardry$castingSpell.getSpell().onServerCastComplete(
                    self.level(), this.mobwizardry$castingSpell.getLevel(), self, magicData, false);
        }
        else if (self.level().isClientSide)
        {
            magicData.resetCastingState();
        }
        this.mobwizardry$castingSpell = null;
    }

    @Override
    public void notifyDangerousProjectile(Projectile projectile)
    {
    }

    @Override
    public boolean setTeleportLocationBehindTarget(int distance)
    {
        PathfinderMob self = (PathfinderMob) (Object) this;
        LivingEntity target = self.getTarget();
        if (target == null)
        {
            return false;
        }
        Vec3 look = target.getLookAngle().normalize();
        Vec3 pos = target.position().add(look.scale(-distance));
        BlockPos bp = BlockPos.containing(pos);
        if (self.level().getBlockState(bp).isAir() && self.level().getBlockState(bp.above()).isAir())
        {
            self.teleportTo(pos.x, pos.y, pos.z);
            return true;
        }
        return false;
    }

    @Override
    public void setBurningDashDirectionData()
    {
    }

    @Override
    public boolean isDrinkingPotion()
    {
        return false;
    }

    @Override
    public boolean getHasUsedSingleAttack()
    {
        return this.mobwizardry$hasUsedSingleAttack;
    }

    @Override
    public void setHasUsedSingleAttack(boolean hasUsed)
    {
        this.mobwizardry$hasUsedSingleAttack = hasUsed;
    }

    @Override
    public void startDrinkingPotion()
    {
    }

    /**
     * Replicates the cast-progression loop from {@code AbstractSpellCastingMob.customServerAiStep()}
     * so tagged vanilla mobs actually fire their spells.
     */
    @Inject(method = "customServerAiStep()V", at = @At("RETURN"))
    private void mobwizardry$tickCast(CallbackInfo ci)
    {
        if (this.mobwizardry$castingSpell == null)
        {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide)
        {
            return;
        }
        MagicData magicData = getMagicData();
        magicData.handleCastDuration();
        if (magicData.isCasting())
        {
            SpellData sd = this.mobwizardry$castingSpell;
            AbstractSpell spell = sd.getSpell();
            spell.onServerCastTick(self.level(), sd.getLevel(), self, magicData);
            if (magicData.getCastDurationRemaining() <= 0)
            {
                CastType type = spell.getCastType();
                if (type == CastType.LONG || type == CastType.INSTANT)
                {
                    spell.onCast(self.level(), sd.getLevel(), self, CastSource.MOB, magicData);
                }
                castComplete();
            }
            else if (spell.getCastType() == CastType.CONTINUOUS)
            {
                if ((magicData.getCastDurationRemaining() + 1) % 10 == 0)
                {
                    spell.onCast(self.level(), sd.getLevel(), self, CastSource.MOB, magicData);
                }
            }
        }
    }
}
