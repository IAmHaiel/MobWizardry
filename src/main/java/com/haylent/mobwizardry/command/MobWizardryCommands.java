package com.haylent.mobwizardry.command;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.ai.WizardMobInit;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MobWizardryCommands
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType UNKNOWN_PRESET = new SimpleCommandExceptionType(Component.literal("Unknown MobWizardry preset. Use /mobwizardry list to see available presets."));
    private static final SimpleCommandExceptionType UNKNOWN_MOB = new SimpleCommandExceptionType(Component.literal("Unknown mob type."));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("mobwizardry")
                .then(Commands.literal("summon")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .then(Commands.argument("mobType", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "preset"), net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "mobType").toString(), ctx.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "preset"), net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "mobType").toString(), Vec3Argument.getVec3(ctx, "pos")))))))
                .then(Commands.literal("tag")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> tag(ctx, StringArgumentType.getString(ctx, "preset"), EntityArgument.getEntities(ctx, "targets"))))))
                .then(Commands.literal("untag")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> untag(ctx, StringArgumentType.getString(ctx, "preset"), EntityArgument.getEntities(ctx, "targets"))))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(MobWizardryCommands::reload))
                .then(Commands.literal("list")
                        .executes(MobWizardryCommands::list))
        );
    }

    private static int summon(CommandContext<CommandSourceStack> ctx, String presetName, String mobTypeId, Vec3 pos) throws CommandSyntaxException
    {
        PresetDefinition preset = PresetManager.getPreset(presetName);
        if (preset == null)
        {
            throw UNKNOWN_PRESET.create();
        }
        ResourceLocation rl = ResourceLocation.tryParse(mobTypeId);
        EntityType<?> type = rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null)
        {
            throw UNKNOWN_MOB.create();
        }

        ServerLevel level = ctx.getSource().getLevel();
        Entity entity = type.create(level);
        if (entity == null)
        {
            throw UNKNOWN_MOB.create();
        }

        entity.moveTo(pos.x, pos.y, pos.z, ctx.getSource().getRotation().y, 0);
        entity.addTag(preset.requiredTag);
        level.addFreshEntity(entity);

        if (entity instanceof PathfinderMob mob)
        {
            WizardMobInit.apply(mob, preset);
            WizardAiGoal.tryApply(mob, preset);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Summoned " + mobTypeId + " with preset '" + presetName + "' (tag: " + preset.requiredTag + ") at " + pos), true);
        LOGGER.info("[MobWizardry] /mobwizardry summon {} {} at {} by {}", presetName, mobTypeId, pos, ctx.getSource().getTextName());
        return 1;
    }

    private static int tag(CommandContext<CommandSourceStack> ctx, String presetName, Collection<? extends Entity> entities) throws CommandSyntaxException
    {
        PresetDefinition preset = PresetManager.getPreset(presetName);
        if (preset == null)
        {
            throw UNKNOWN_PRESET.create();
        }
        int applied = 0;
        int mobs = 0;
        for (Entity entity : entities)
        {
            entity.addTag(preset.requiredTag);
            if (entity instanceof PathfinderMob mob)
            {
                mobs++;
                WizardMobInit.apply(mob, preset);
                if (WizardAiGoal.tryApply(mob, preset))
                {
                    applied++;
                }
            }
        }
        final int tagged = mobs;
        final int appliedFinal = applied;
        ctx.getSource().sendSuccess(() -> Component.literal("Tagged " + tagged + " entity(ies) with '" + preset.requiredTag + "', applied wizard AI to " + appliedFinal), true);
        return applied;
    }

    private static int untag(CommandContext<CommandSourceStack> ctx, String presetName, Collection<? extends Entity> entities) throws CommandSyntaxException
    {
        PresetDefinition preset = PresetManager.getPreset(presetName);
        if (preset == null)
        {
            throw UNKNOWN_PRESET.create();
        }
        int removed = 0;
        for (Entity entity : entities)
        {
            if (entity.removeTag(preset.requiredTag))
            {
                removed++;
            }
        }
        final int count = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("Removed tag '" + preset.requiredTag + "' from " + count + " entity(ies)"), true);
        return count;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx)
    {
        PresetManager.reload();
        int count = PresetManager.getPresets().size();
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded MobWizardry config. Loaded " + count + " preset(s)."), true);
        return count;
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
    {
        Map<String, PresetDefinition> presets = PresetManager.getPresets();
        if (presets.isEmpty())
        {
            ctx.getSource().sendSuccess(() -> Component.literal("No MobWizardry presets loaded."), false);
            return 0;
        }
        for (Map.Entry<String, PresetDefinition> entry : presets.entrySet())
        {
            ctx.getSource().sendSuccess(() -> Component.literal(formatPreset(entry.getKey(), entry.getValue())), false);
        }
        return presets.size();
    }

    private static String formatPreset(String name, PresetDefinition p)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(name).append("]");
        sb.append("\n  Tag: ").append(p.requiredTag);
        sb.append("\n  Mobs: ").append(p.targetMobs);
        sb.append("\n  Speed: ").append(p.speed).append(" | Cast interval: ").append(p.castInterval).append(" | Mana: ").append(p.mana);
        if (!p.equipment.isEmpty())
        {
            sb.append("\n  Equipment: ").append(p.equipment);
        }
        if (!p.attributes.isEmpty())
        {
            sb.append("\n  Attributes: ").append(p.attributes);
        }
        sb.append("\n  Attack:   ").append(formatSpellList(p.spells.attack));
        sb.append("\n  Defense:  ").append(formatSpellList(p.spells.defense));
        sb.append("\n  Movement: ").append(formatSpellList(p.spells.movement));
        sb.append("\n  Support:  ").append(formatSpellList(p.spells.support));
        return sb.toString();
    }

    private static String formatSpellList(List<PresetDefinition.SpellEntry> entries)
    {
        if (entries.isEmpty())
        {
            return "(none)";
        }
        return entries.stream()
                .map(e -> e.id + " (lvl " + e.level + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static List<String> spellIds(List<PresetDefinition.SpellEntry> entries)
    {
        return entries.stream().map(e -> e.id).toList();
    }
}
