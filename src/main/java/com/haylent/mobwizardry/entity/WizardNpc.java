package com.haylent.mobwizardry.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.level.Level;

/**
 * The player-like Wizard NPC. Spells, presets, teams and factions are applied by
 * {@code WizardAiGoal.attach} when the mob carries a preset tag; this class only provides the
 * baseline goals (retaliation + idle life) and the synced, save-persisted skin.
 */
public class WizardNpc extends PathfinderMob
{
    public static final String SKIN_TAG = "WizardSkin";

    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(WizardNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_CASTING_SPELL =
            SynchedEntityData.defineId(WizardNpc.class, EntityDataSerializers.STRING);

    public WizardNpc(EntityType<? extends WizardNpc> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    protected void registerGoals()
    {
        // Offensive targeting is added at wizardify time from the preset's 'faction' field;
        // baseline here is only retaliation plus idle life so friendly NPCs never initiate.
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public void tick()
    {
        // Declared so the client-side WizardNpcAnimationMixin's @Inject(method = "tick",
        // at = @At("TAIL")) has a concrete target - inherited methods don't get a refmap entry,
        // which made the injection fail and the client crash. Behavior is unchanged.
        super.tick();
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN, "");
        this.entityData.define(DATA_CASTING_SPELL, "");
    }

    public String getSkin()
    {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkin(String skin)
    {
        this.entityData.set(DATA_SKIN, skin == null ? "" : skin);
    }

    /**
     * The id of the spell currently being cast, or {@code ""} when idle. Synced to clients so the
     * renderer can play the matching player casting animation on both sides.
     */
    public String getCastingSpell()
    {
        return this.entityData.get(DATA_CASTING_SPELL);
    }

    public void setCastingSpell(String spellId)
    {
        this.entityData.set(DATA_CASTING_SPELL, spellId == null ? "" : spellId);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(SKIN_TAG, getSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        setSkin(tag.getString(SKIN_TAG));
    }

    @Override
    public boolean isPersistenceRequired()
    {
        return true;
    }
}
