package com.haylent.mobwizardry.command;

import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.ai.WizardMobInit;
import com.haylent.mobwizardry.config.PresetDefinition;
import com.haylent.mobwizardry.config.PresetManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MobWizardryCommands
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PRESETS_PER_PAGE = 5;
    private static final SimpleCommandExceptionType UNKNOWN_PRESET = new SimpleCommandExceptionType(Component.literal("Unknown MobWizardry preset. Use /mobwizardry list to see available presets."));
    private static final SimpleCommandExceptionType UNKNOWN_MOB = new SimpleCommandExceptionType(Component.literal("Unknown mob type."));
    private static final SimpleCommandExceptionType NOT_A_MOB = new SimpleCommandExceptionType(Component.literal("That entity type cannot cast spells or use equipment - it must be a PathfinderMob (zombie, skeleton, etc.)."));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("mobwizardry")
                .then(Commands.literal("summon")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MobWizardryCommands::suggestPresets)
                                .then(Commands.argument("mobType", ResourceLocationArgument.id())
                                        .suggests(MobWizardryCommands::suggestMobTypes)
                                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "preset"), ResourceLocationArgument.getId(ctx, "mobType").toString(), ctx.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "preset"), ResourceLocationArgument.getId(ctx, "mobType").toString(), Vec3Argument.getVec3(ctx, "pos")))))))
                .then(Commands.literal("tag")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MobWizardryCommands::suggestPresets)
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> tag(ctx, StringArgumentType.getString(ctx, "preset"), EntityArgument.getEntities(ctx, "targets"))))))
                .then(Commands.literal("untag")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MobWizardryCommands::suggestPresets)
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> untag(ctx, StringArgumentType.getString(ctx, "preset"), EntityArgument.getEntities(ctx, "targets"))))))
                .then(Commands.literal("wizardify")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MobWizardryCommands::suggestPresets)
                                .executes(ctx -> wizardify(ctx, StringArgumentType.getString(ctx, "preset"), 16, ctx.getSource().getPosition()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> wizardify(ctx, StringArgumentType.getString(ctx, "preset"), IntegerArgumentType.getInteger(ctx, "radius"), ctx.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> wizardify(ctx, StringArgumentType.getString(ctx, "preset"), IntegerArgumentType.getInteger(ctx, "radius"), Vec3Argument.getVec3(ctx, "pos")))))))
                .then(Commands.literal("unwizardify")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MobWizardryCommands::suggestPresets)
                                .executes(ctx -> unwizardify(ctx, StringArgumentType.getString(ctx, "preset"), 16, ctx.getSource().getPosition()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> unwizardify(ctx, StringArgumentType.getString(ctx, "preset"), IntegerArgumentType.getInteger(ctx, "radius"), ctx.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> unwizardify(ctx, StringArgumentType.getString(ctx, "preset"), IntegerArgumentType.getInteger(ctx, "radius"), Vec3Argument.getVec3(ctx, "pos")))))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(MobWizardryCommands::reload))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .suggests(MobWizardryCommands::suggestPages)
                                .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
        );
    }

    private static CompletableFuture<Suggestions> suggestPresets(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder)
    {
        PresetManager.getPresets().keySet().stream().sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMobTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder)
    {
        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPages(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder)
    {
        int maxPage = maxPage(PresetManager.getPresets().size());
        for (int i = 1; i <= maxPage; i++)
        {
            builder.suggest(String.valueOf(i));
        }
        return builder.buildFuture();
    }

    private static int maxPage(int presetCount)
    {
        return Math.max(1, (presetCount + PRESETS_PER_PAGE - 1) / PRESETS_PER_PAGE);
    }

    private static PresetDefinition requirePreset(CommandContext<CommandSourceStack> ctx, String presetName) throws CommandSyntaxException
    {
        PresetDefinition preset = PresetManager.getPreset(presetName);
        if (preset == null)
        {
            throw UNKNOWN_PRESET.create();
        }
        return preset;
    }

    private static int summon(CommandContext<CommandSourceStack> ctx, String presetName, String mobTypeId, Vec3 pos) throws CommandSyntaxException
    {
        PresetDefinition preset = requirePreset(ctx, presetName);
        ResourceLocation rl = ResourceLocation.tryParse(mobTypeId);
        if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl))
        {
            throw UNKNOWN_MOB.create();
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);

        ServerLevel level = ctx.getSource().getLevel();
        Entity entity = type.create(level);
        if (entity == null)
        {
            throw UNKNOWN_MOB.create();
        }
        if (!(entity instanceof PathfinderMob mob))
        {
            throw NOT_A_MOB.create();
        }

        Vec3 safePos = findSafeSpawn(level, pos);
        boolean moved = !safePos.equals(pos);
        mob.moveTo(safePos.x, safePos.y, safePos.z, ctx.getSource().getRotation().y, 0);
        warnOnEquipmentMismatch(ctx, preset, mob);
        mob.addTag(preset.requiredTag);
        level.addFreshEntity(mob);

        WizardAiGoal.attach(mob, preset);

        final boolean finalMoved = moved;
        final Vec3 finalSafePos = safePos;
        ctx.getSource().sendSuccess(() -> Component.literal("Summoned " + mobTypeId + " with preset '" + presetName + "' (tag: " + preset.requiredTag + ")" + (finalMoved ? " at safe position " + finalSafePos : " at " + finalSafePos)), true);
        LOGGER.info("[MobWizardry] /mobwizardry summon {} {} at {} by {}", presetName, mobTypeId, safePos, ctx.getSource().getTextName());
        return 1;
    }

    private static void warnOnEquipmentMismatch(CommandContext<CommandSourceStack> ctx, PresetDefinition preset, PathfinderMob mob)
    {
        for (Map.Entry<String, String> entry : preset.equipment.entrySet())
        {
            EquipmentSlot slot = PresetDefinition.parseSlot(entry.getKey());
            if (slot == null)
            {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(entry.getValue());
            if (rl == null)
            {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null)
            {
                continue;
            }
            Equipable equipable = Equipable.get(new ItemStack(item));
            EquipmentSlot natural = equipable == null ? null : equipable.getEquipmentSlot();
            if (natural != null && natural != slot)
            {
                String message = "Preset '" + preset.requiredTag + "' equips " + entry.getValue() + " in " + slot.getName() + ", but the item naturally belongs in " + natural.getName() + " - it may not render or behave as expected";
                LOGGER.warn("[MobWizardry] {}", message);
                ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), false);
            }
        }
    }

    private static Vec3 findSafeSpawn(ServerLevel level, Vec3 pos)
    {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        if (isSpawnableAt(level, bp.set(pos.x, pos.y, pos.z)) && pos.y >= topSolidY(level, pos.x, pos.z))
        {
            return pos;
        }
        Vec3 surfacePos = new Vec3(Math.floor(pos.x) + 0.5, topSolidY(level, pos.x, pos.z) + 1, Math.floor(pos.z) + 0.5);
        if (isSpawnableAt(level, bp.set(surfacePos.x, surfacePos.y, surfacePos.z)))
        {
            return surfacePos;
        }
        int maxY = level.getMaxBuildHeight() - 1;
        int startY = Math.max(level.getMinBuildHeight() + 1, (int) Math.floor(pos.y) + 1);
        for (int y = startY; y <= maxY; y++)
        {
            bp.set(pos.x, y, pos.z);
            if (isSpawnableAt(level, bp))
            {
                return new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            }
        }
        return pos;
    }

    private static int topSolidY(ServerLevel level, double x, double z)
    {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--)
        {
            bp.set(x, y, z);
            BlockState state = level.getBlockState(bp);
            if (!state.getCollisionShape(level, bp).isEmpty() || !state.getFluidState().isEmpty())
            {
                return y;
            }
        }
        return level.getMinBuildHeight() - 1;
    }

    private static boolean isSpawnableAt(ServerLevel level, BlockPos pos)
    {
        if (pos.getY() < level.getMinBuildHeight() + 1 || pos.getY() > level.getMaxBuildHeight() - 1)
        {
            return false;
        }
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return !at.isSuffocating(level, pos) && !above.isSuffocating(level, pos.above());
    }

    private static int tag(CommandContext<CommandSourceStack> ctx, String presetName, Collection<? extends Entity> entities) throws CommandSyntaxException
    {
        PresetDefinition preset = requirePreset(ctx, presetName);
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
        PresetDefinition preset = requirePreset(ctx, presetName);
        int removed = 0;
        for (Entity entity : entities)
        {
            if (entity.removeTag(preset.requiredTag))
            {
                removed++;
                if (entity instanceof PathfinderMob mob)
                {
                    WizardMobInit.stripWizardEquipment(mob, preset);
                }
            }
        }
        final int count = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("Removed tag '" + preset.requiredTag + "' from " + count + " entity(ies)"), true);
        return count;
    }

    private static int wizardify(CommandContext<CommandSourceStack> ctx, String presetName, int radius, Vec3 center) throws CommandSyntaxException
    {
        PresetDefinition preset = requirePreset(ctx, presetName);
        ServerLevel level = ctx.getSource().getLevel();
        double radiusSqr = (double) radius * radius;
        int wizardified = 0;
        int already = 0;
        int skipped = 0;
        for (Entity entity : level.getAllEntities())
        {
            if (entity.distanceToSqr(center) > radiusSqr)
            {
                continue;
            }
            if (!(entity instanceof PathfinderMob mob))
            {
                skipped++;
                continue;
            }
            if (mob.getTags().contains(preset.requiredTag))
            {
                already++;
                WizardAiGoal.attach(mob, preset);
                continue;
            }
            mob.addTag(preset.requiredTag);
            WizardAiGoal.attach(mob, preset);
            wizardified++;
            playWizardifyEffect(level, mob.position());
        }
        final int wf = wizardified;
        final int alr = already;
        final int skp = skipped;
        final int r = radius;
        ctx.getSource().sendSuccess(() -> Component.literal("Wizardified " + wf + " mob(s) with preset '" + presetName + "' within " + r + " blocks" + (alr > 0 ? " (" + alr + " already wizards)" : "") + (skp > 0 ? " (" + skp + " non-mob entity/entities skipped)" : "")), true);
        LOGGER.info("[MobWizardry] /mobwizardry wizardify {} radius {} at {} by {} -> {} wizardified, {} already, {} skipped", presetName, radius, center, ctx.getSource().getTextName(), wizardified, already, skipped);
        return wizardified;
    }

    private static int unwizardify(CommandContext<CommandSourceStack> ctx, String presetName, int radius, Vec3 center) throws CommandSyntaxException
    {
        PresetDefinition preset = requirePreset(ctx, presetName);
        ServerLevel level = ctx.getSource().getLevel();
        double radiusSqr = (double) radius * radius;
        int removed = 0;
        for (Entity entity : level.getAllEntities())
        {
            if (entity.distanceToSqr(center) > radiusSqr)
            {
                continue;
            }
            if (entity.removeTag(preset.requiredTag))
            {
                removed++;
                playUnwizardifyEffect(level, entity.position());
                if (entity instanceof PathfinderMob mob)
                {
                    WizardMobInit.stripWizardEquipment(mob, preset);
                }
            }
        }
        final int count = removed;
        final int r = radius;
        ctx.getSource().sendSuccess(() -> Component.literal("De-wizardified " + count + " mob(s) - removed tag '" + presetName + "' within " + r + " blocks"), true);
        LOGGER.info("[MobWizardry] /mobwizardry unwizardify {} radius {} at {} by {} -> {} removed", presetName, radius, center, ctx.getSource().getTextName(), removed);
        return removed;
    }

    private static void playWizardifyEffect(ServerLevel level, Vec3 pos)
    {
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.0, pos.z, 250, 0.8, 0.6, 0.8, 0.4);
        level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void playUnwizardifyEffect(ServerLevel level, Vec3 pos)
    {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.0, pos.z, 120, 0.6, 0.5, 0.6, 0.15);
        level.playSound(null, pos.x, pos.y + 1.0, pos.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static int reload(CommandContext<CommandSourceStack> ctx)
    {
        PresetManager.reload();
        int count = PresetManager.getPresets().size();
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded MobWizardry config. Loaded " + count + " preset(s)."), true);
        return count;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int page)
    {
        Map<String, PresetDefinition> presets = PresetManager.getPresets();
        if (presets.isEmpty())
        {
            ctx.getSource().sendSuccess(() -> Component.literal("No MobWizardry presets loaded."), false);
            return 0;
        }
        int maxPage = maxPage(presets.size());
        int currentPage = Math.min(Math.max(1, page), maxPage);
        List<Map.Entry<String, PresetDefinition>> all = new ArrayList<>(presets.entrySet());
        int from = (currentPage - 1) * PRESETS_PER_PAGE;
        int to = Math.min(from + PRESETS_PER_PAGE, all.size());

        MutableComponent header = Component.literal("=== MobWizardry presets (").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(String.valueOf(currentPage)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("/").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(String.valueOf(maxPage)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(") ===").withStyle(ChatFormatting.AQUA));
        ctx.getSource().sendSuccess(() -> header, false);

        for (int i = from; i < to; i++)
        {
            final Map.Entry<String, PresetDefinition> entry = all.get(i);
            ctx.getSource().sendSuccess(() -> formatPreset(entry.getKey(), entry.getValue()), false);
        }

        ctx.getSource().sendSuccess(() -> pageNav(currentPage, maxPage), false);
        return presets.size();
    }

    private static Component pageNav(int currentPage, int maxPage)
    {
        MutableComponent nav = Component.literal("[<]").withStyle(style -> {
            Style s = style.withColor(ChatFormatting.GRAY);
            if (currentPage > 1)
            {
                s = s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mobwizardry list " + (currentPage - 1)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Previous page")));
            }
            return s;
        });
        nav.append(Component.literal(" " + currentPage + " / " + maxPage + " ").withStyle(ChatFormatting.YELLOW));
        nav.append(Component.literal("[>]").withStyle(style -> {
            Style s = style.withColor(ChatFormatting.GRAY);
            if (currentPage < maxPage)
            {
                s = s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mobwizardry list " + (currentPage + 1)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Next page")));
            }
            return s;
        }));
        return nav;
    }

    private static Component formatPreset(String name, PresetDefinition p)
    {
        MutableComponent root = Component.literal("");
        root.append(Component.literal("[" + name + "]").withStyle(ChatFormatting.GOLD));
        root.append(Component.literal("\n  Tag: ").withStyle(ChatFormatting.GRAY));
        root.append(Component.literal(p.requiredTag).withStyle(ChatFormatting.WHITE));
        root.append(Component.literal("\n  Speed: ").withStyle(ChatFormatting.GRAY));
        root.append(Component.literal(String.valueOf(p.speed)).withStyle(ChatFormatting.WHITE));
        root.append(Component.literal(" | Cast interval: ").withStyle(ChatFormatting.GRAY));
        root.append(Component.literal(String.valueOf(p.castInterval)).withStyle(ChatFormatting.WHITE));
        if (!p.equipment.isEmpty())
        {
            root.append(Component.literal("\n  Equipment: ").withStyle(ChatFormatting.GRAY));
            root.append(Component.literal(p.equipment.toString()).withStyle(ChatFormatting.WHITE));
        }
        if (!p.attributes.isEmpty())
        {
            root.append(Component.literal("\n  Attributes: ").withStyle(ChatFormatting.GRAY));
            root.append(Component.literal(p.attributes.toString()).withStyle(ChatFormatting.WHITE));
        }
        root.append(Component.literal("\n  Attack:   ").withStyle(ChatFormatting.GRAY));
        root.append(formatSpellList(p.spells.attack));
        root.append(Component.literal("\n  Defense:  ").withStyle(ChatFormatting.GRAY));
        root.append(formatSpellList(p.spells.defense));
        root.append(Component.literal("\n  Movement: ").withStyle(ChatFormatting.GRAY));
        root.append(formatSpellList(p.spells.movement));
        root.append(Component.literal("\n  Support:  ").withStyle(ChatFormatting.GRAY));
        root.append(formatSpellList(p.spells.support));
        return root;
    }

    private static Component formatSpellList(List<PresetDefinition.SpellEntry> entries)
    {
        if (entries.isEmpty())
        {
            return Component.literal("(none)").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }
        MutableComponent component = Component.literal("");
        for (int i = 0; i < entries.size(); i++)
        {
            PresetDefinition.SpellEntry entry = entries.get(i);
            component.append(Component.literal(entry.id).withStyle(ChatFormatting.WHITE));
            component.append(Component.literal(" (lvl " + entry.level + ")").withStyle(ChatFormatting.GRAY));
            if (i < entries.size() - 1)
            {
                component.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
        }
        return component;
    }
}
