package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps an Iron's Spellbooks {@link WizardAttackGoal} behind a dynamic tag check. The inner goal only
 * activates while the mob still carries the preset's required tag, allowing tags to be added/removed
 * freely at runtime via admin commands.
 */
public class WizardAiGoal extends Goal
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PathfinderMob mob;
    private final PresetDefinition preset;
    private final WizardAttackGoal inner;

    public WizardAiGoal(PathfinderMob mob, PresetDefinition preset)
    {
        this.mob = mob;
        this.preset = preset;
        int castIntervalMax = preset.castIntervalMax > preset.castInterval ? preset.castIntervalMax : preset.castInterval * 2;
        MobWizardryAttackGoal goal = new MobWizardryAttackGoal((IMagicEntity) mob, preset.speed, preset.castInterval, castIntervalMax);
        goal.setSpells(
                        resolveSpells(preset.spells.attack),
                        resolveSpells(preset.spells.defense),
                        resolveSpells(preset.spells.movement),
                        resolveSpells(preset.spells.support))
                .setSpellQuality(minQuality(), maxQuality());
        goal.setEmergencyHealSpells(resolveEmergencySpells(preset.spells.support));
        goal.setEscapeSpells(resolveSpells(preset.spells.escape));
        goal.setMovementDistances(preset.movementStartDistance, preset.movementFarDistance);
        this.inner = goal;
        setFlags(inner.getFlags());
    }

    private static List<AbstractSpell> resolveSpells(List<PresetDefinition.SpellEntry> entries)
    {
        List<AbstractSpell> spells = new ArrayList<>();
        for (PresetDefinition.SpellEntry entry : entries)
        {
            ResourceLocation rl = ResourceLocation.tryParse(entry.id);
            if (rl == null)
            {
                continue;
            }
            AbstractSpell spell = SpellRegistry.getSpell(rl);
            if (spell != null && spell != SpellRegistry.none())
            {
                spells.add(spell);
            }
        }
        return spells;
    }

    private static List<AbstractSpell> resolveEmergencySpells(List<PresetDefinition.SpellEntry> entries)
    {
        List<AbstractSpell> spells = new ArrayList<>();
        for (PresetDefinition.SpellEntry entry : entries)
        {
            if (!entry.emergency)
            {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.id);
            if (rl == null)
            {
                continue;
            }
            AbstractSpell spell = SpellRegistry.getSpell(rl);
            if (spell != null && spell != SpellRegistry.none())
            {
                spells.add(spell);
            }
        }
        return spells;
    }

    private float minQuality()
    {
        float quality = 1.0f;
        for (PresetDefinition.SpellEntry entry : allSpellEntries())
        {
            float q = quality(entry);
            if (q < quality)
            {
                quality = q;
            }
        }
        return Math.max(0.0f, quality);
    }

    private float maxQuality()
    {
        float quality = 0.0f;
        for (PresetDefinition.SpellEntry entry : allSpellEntries())
        {
            float q = quality(entry);
            if (q > quality)
            {
                quality = q;
            }
        }
        return Math.min(1.0f, quality);
    }

    private float quality(PresetDefinition.SpellEntry entry)
    {
        ResourceLocation rl = ResourceLocation.tryParse(entry.id);
        if (rl == null)
        {
            return 0.5f;
        }
        AbstractSpell spell = SpellRegistry.getSpell(rl);
        if (spell == null || spell == SpellRegistry.none() || spell.getMaxLevel() <= 0)
        {
            return 0.5f;
        }
        return (float) Math.max(1, entry.level) / spell.getMaxLevel();
    }

    private List<PresetDefinition.SpellEntry> allSpellEntries()
    {
        List<PresetDefinition.SpellEntry> all = new ArrayList<>();
        all.addAll(preset.spells.attack);
        all.addAll(preset.spells.defense);
        all.addAll(preset.spells.movement);
        all.addAll(preset.spells.support);
        return all;
    }

    public String getPresetName()
    {
        return preset.requiredTag;
    }

    @Override
    public boolean canUse()
    {
        return mob.getTags().contains(preset.requiredTag) && inner.canUse();
    }

    @Override
    public boolean canContinueToUse()
    {
        return mob.getTags().contains(preset.requiredTag) && inner.canContinueToUse();
    }

    @Override
    public void start()
    {
        inner.start();
    }

    @Override
    public void stop()
    {
        inner.stop();
    }

    @Override
    public void tick()
    {
        if (!mob.getTags().contains(preset.requiredTag))
        {
            inner.stop();
            return;
        }
        inner.tick();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return inner.requiresUpdateEveryTick();
    }

    @Override
    public boolean isInterruptable()
    {
        return inner.isInterruptable();
    }

    /**
     * Turns a mob into a wizard for the given preset: applies equipment/attributes/mana and
     * attaches the wizard goal. Idempotent - safe to call repeatedly (the goal is only added once).
     */
    public static void attach(PathfinderMob mob, PresetDefinition preset)
    {
        WizardMobInit.apply(mob, preset);
        tryApply(mob, preset);
    }

    /**
     * Applies wizard AI to a mob if it matches a loaded preset and carries the preset's tag.
     * Safe to call from both {@code EntityJoinLevelEvent} and the admin tag command.
     */
    public static boolean tryApply(PathfinderMob mob, PresetDefinition preset)
    {
        if (mob.getTags().contains(preset.requiredTag) && !hasGoal(mob))
        {
            mob.goalSelector.addGoal(1, new WizardAiGoal(mob, preset));
            LOGGER.info("[MobWizardry] Attached wizard AI (tag={}) to {} at {}",
                    preset.requiredTag, mob.getType().getDescriptionId(), mob.blockPosition());
            return true;
        }
        return false;
    }

    private static boolean hasGoal(PathfinderMob mob)
    {
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals())
        {
            if (goal.getGoal() instanceof WizardAiGoal)
            {
                return true;
            }
        }
        return false;
    }
}
