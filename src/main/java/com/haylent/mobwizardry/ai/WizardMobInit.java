package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Applies preset equipment, attribute overrides and a full mana pool to a mob.
 * Uses Iron's Spellbooks' MagicData which is auto-attached to all LivingEntities.
 */
public class WizardMobInit
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void apply(PathfinderMob mob, PresetDefinition preset)
    {
        applyEquipment(mob, preset);
        applyAttributes(mob, preset);
        applyMana(mob, preset);
        // If the preset overrode max health (e.g. minecraft:generic.max_health), refill the mob
        // so it spawns at full health instead of at a fraction of the new max.
        mob.setHealth(mob.getMaxHealth());
    }

    /**
     * Removes the equipment that the preset's {@code equipment} block put on the mob.
     * Called when a mob is de-wizardified (tag removed) so the wizard gear disappears.
     */
    public static void stripWizardEquipment(PathfinderMob mob, PresetDefinition preset)
    {
        for (String slotName : preset.equipment.keySet())
        {
            EquipmentSlot slot = PresetDefinition.parseSlot(slotName);
            if (slot != null)
            {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                mob.setDropChance(slot, 0.0f);
            }
        }
    }

    private static void applyEquipment(PathfinderMob mob, PresetDefinition preset)
    {
        for (Map.Entry<String, String> entry : preset.equipment.entrySet())
        {
            EquipmentSlot slot = PresetDefinition.parseSlot(entry.getKey());
            if (slot == null)
            {
                LOGGER.warn("[MobWizardry] Unknown equipment slot '{}' in preset '{}' - skipped", entry.getKey(), preset.requiredTag);
                continue;
            }
            Item item = PresetDefinition.resolveItem(entry.getValue());
            if (item == null)
            {
                LOGGER.warn("[MobWizardry] Equipment item '{}' not found for preset '{}' - skipped", entry.getValue(), preset.requiredTag);
                continue;
            }
            mob.setItemSlot(slot, new ItemStack(item));
            mob.setDropChance(slot, 0.0f);
        }
    }

    private static void applyAttributes(PathfinderMob mob, PresetDefinition preset)
    {
        for (Map.Entry<String, Double> entry : preset.attributes.entrySet())
        {
            Attribute attribute = PresetDefinition.resolveAttribute(entry.getKey());
            if (attribute == null)
            {
                continue;
            }
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance == null)
            {
                LOGGER.warn("[MobWizardry] Attribute '{}' not present on {} - skipped", entry.getKey(), mob.getType().getDescriptionId());
                continue;
            }
            instance.setBaseValue(entry.getValue());
        }
    }

    private static void applyMana(PathfinderMob mob, PresetDefinition preset)
    {
        // Mob casting is free (CastSource.MOB bypasses mana costs), so just keep the mana bar full.
        MagicData magicData = MagicData.getPlayerMagicData((LivingEntity) mob);
        float maxMana = (float) mob.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        magicData.setMana(Math.max(0.0f, maxMana));
    }
}
