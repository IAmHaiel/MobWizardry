package com.haylent.mobwizardry.ai;

import com.haylent.mobwizardry.config.PresetDefinition;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Applies preset equipment, attribute overrides and initial mana to a mob.
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
    }

    private static void applyEquipment(PathfinderMob mob, PresetDefinition preset)
    {
        for (Map.Entry<String, String> entry : preset.equipment.entrySet())
        {
            EquipmentSlot slot = parseSlot(entry.getKey());
            if (slot == null)
            {
                LOGGER.warn("[MobWizardry] Unknown equipment slot '{}' in preset '{}' - skipped", entry.getKey(), preset.requiredTag);
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getValue());
            if (rl == null)
            {
                continue;
            }
            var item = ForgeRegistries.ITEMS.getValue(rl);
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
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl == null)
            {
                continue;
            }
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(rl);
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
        MagicData magicData = MagicData.getPlayerMagicData((LivingEntity) mob);
        float maxMana = (float) mob.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
        if (preset.mana > 0)
        {
            magicData.setMana(Math.min(preset.mana, maxMana));
        }
        else
        {
            magicData.setMana(Math.max(0.0f, maxMana));
        }
    }

    public static EquipmentSlot parseSlot(String name)
    {
        return switch (name.toLowerCase())
        {
            case "mainhand", "main_hand", "hand" -> EquipmentSlot.MAINHAND;
            case "offhand", "off_hand" -> EquipmentSlot.OFFHAND;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
