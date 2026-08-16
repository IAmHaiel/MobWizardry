package com.haylent.mobwizardry.registration;

import com.haylent.mobwizardry.MobWizardryMod;
import com.haylent.mobwizardry.entity.WizardNpc;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred registries for the 2.0.0 player-like Wizard NPC entity, its spawn egg and creative tab.
 */
public class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MobWizardryMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MobWizardryMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MobWizardryMod.MODID);

    public static final RegistryObject<EntityType<WizardNpc>> WIZARD_NPC = ENTITY_TYPES.register("wizard",
            () -> EntityType.Builder.of(WizardNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .setTrackingRange(64)
                    .setUpdateInterval(3)
                    .build(MobWizardryMod.MODID + ":wizard"));

    public static final RegistryObject<Item> WIZARD_SPAWN_EGG = ITEMS.register("wizard_spawn_egg",
            () -> new ForgeSpawnEggItem(WIZARD_NPC, 0x6B3F8B, 0x3F8B8B, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("mobwizardry",
            () -> CreativeModeTab.builder()
                    .icon(() -> WIZARD_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(WIZARD_SPAWN_EGG.get()))
                    .build());
}
