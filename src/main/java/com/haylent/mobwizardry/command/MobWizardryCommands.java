package com.haylent.mobwizardry.command;

import com.haylent.mobwizardry.ai.SpawnHelper;
import com.haylent.mobwizardry.ai.WizardAiGoal;
import com.haylent.mobwizardry.ai.WizardMobInit;
import com.haylent.mobwizardry.config.MobWizardryTeams;
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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MobWizardryCommands
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType UNKNOWN_PRESET = new SimpleCommandExceptionType(Component.literal("Unknown MobWizardry preset. Use /mobwizardry list to see available presets."));
    private static final SimpleCommandExceptionType UNKNOWN_MOB = new SimpleCommandExceptionType(Component.literal("Unknown mob type."));
    private static final SimpleCommandExceptionType NOT_A_MOB = new SimpleCommandExceptionType(Component.literal("That entity type cannot cast spells or use equipment - it must be a PathfinderMob (zombie, skeleton, etc.)."));
    private static final SimpleCommandExceptionType NOT_A_BOSS = new SimpleCommandExceptionType(Component.literal("That preset is not boss-enabled. Define a \"boss\" entry for it in config/mobwizardry/bosses.json."));

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
                .then(Commands.literal("boss")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MobWizardryCommands::suggestPresets)
                                .then(Commands.argument("mobType", ResourceLocationArgument.id())
                                        .suggests(MobWizardryCommands::suggestMobTypes)
                                        .executes(ctx -> boss(ctx, StringArgumentType.getString(ctx, "preset"), ResourceLocationArgument.getId(ctx, "mobType").toString(), ctx.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> boss(ctx, StringArgumentType.getString(ctx, "preset"), ResourceLocationArgument.getId(ctx, "mobType").toString(), Vec3Argument.getVec3(ctx, "pos")))))))
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
                .then(Commands.literal("help")
                        .executes(MobWizardryCommands::help))
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
        int maxPage = MobWizardryCommandOutput.maxPage(PresetManager.getPresets().size());
        for (int i = 1; i <= maxPage; i++)
        {
            builder.suggest(String.valueOf(i));
        }
        return builder.buildFuture();
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
        Vec3 safePos = spawnMob(ctx, preset, mobTypeId, pos);
        final boolean moved = !safePos.equals(pos);
        ctx.getSource().sendSuccess(() -> Component.literal("Summoned " + mobTypeId + " with preset '" + presetName + "' (tag: " + preset.requiredTag + ")" + (moved ? " at safe position " + safePos : " at " + safePos)), true);
        LOGGER.info("[MobWizardry] /mobwizardry summon {} {} at {} by {}", presetName, mobTypeId, safePos, ctx.getSource().getTextName());
        return 1;
    }

    /**
     * Spawns a boss-enabled preset's mob at the given position - identical to {@code summon}
     * but the preset must be a boss (see config/mobwizardry/bosses.json). The bossification
     * (lightning, name tag, arrival, phases) happens through the normal entity-join path.
     */
    private static int boss(CommandContext<CommandSourceStack> ctx, String presetName, String mobTypeId, Vec3 pos) throws CommandSyntaxException
    {
        PresetDefinition preset = requirePreset(ctx, presetName);
        if (preset.boss == null || !preset.boss.enabled)
        {
            throw NOT_A_BOSS.create();
        }
        Vec3 safePos = spawnMob(ctx, preset, mobTypeId, pos);
        final String bossName = preset.boss.name;
        ctx.getSource().sendSuccess(() -> Component.literal("Summoned boss '" + bossName + "' (" + mobTypeId + ") with preset '" + presetName + "' (tag: " + preset.requiredTag + ") at " + safePos), true);
        LOGGER.info("[MobWizardry] /mobwizardry boss {} {} at {} by {}", presetName, mobTypeId, safePos, ctx.getSource().getTextName());
        return 1;
    }

    /**
     * Creates the mob, finds a safe spawn, equips preset gear, tags it and adds it to the level,
     * then attaches the wizard AI (the join handler also runs and is idempotent). Returns the
     * safe position the mob landed at. Shared by the {@code summon} and {@code boss} commands.
     */
    private static Vec3 spawnMob(CommandContext<CommandSourceStack> ctx, PresetDefinition preset, String mobTypeId, Vec3 pos) throws CommandSyntaxException
    {
        ServerLevel level = ctx.getSource().getLevel();
        ResourceLocation rl = ResourceLocation.tryParse(mobTypeId);
        if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl))
        {
            throw UNKNOWN_MOB.create();
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        Entity entity = type.create(level);
        if (entity == null)
        {
            throw UNKNOWN_MOB.create();
        }
        if (!(entity instanceof PathfinderMob mob))
        {
            throw NOT_A_MOB.create();
        }

        Vec3 safePos = SpawnHelper.findSafeSpawn(level, pos);
        mob.moveTo(safePos.x, safePos.y, safePos.z, ctx.getSource().getRotation().y, 0);
        warnOnEquipmentMismatch(ctx, preset, mob);
        mob.addTag(preset.requiredTag);
        level.addFreshEntity(mob);
        WizardAiGoal.attach(mob, preset);
        return safePos;
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
            Item item = PresetDefinition.resolveItem(entry.getValue());
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
                MobWizardryTeams.setTeam(entity, "");
                MobWizardryTeams.setFaction(entity, "");
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

    private static int help(CommandContext<CommandSourceStack> ctx)
    {
        MutableComponent header = Component.literal("=== MobWizardry commands ===").withStyle(ChatFormatting.AQUA);
        ctx.getSource().sendSuccess(() -> header, false);
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "help", "Show this help.");
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "summon <preset> <mobType> [pos]", "Summon a new mob as a wizard using a preset's equipment, attributes and spells.");
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "boss <preset> <mobType> [pos]", "Summon a boss - like summon, but the preset must be boss-enabled (config/mobwizardry/bosses.json).");
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "wizardify <preset> [radius] [pos]", "Turn nearby mobs into wizards - adds the preset tag and applies equipment and wizard AI.");
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "unwizardify <preset> [radius] [pos]", "Remove wizard status from nearby mobs - removes the preset tag and strips wizard equipment.");
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "reload", "Reload presets.json and bosses.json from the config folder.");
        MobWizardryCommandOutput.helpLine(ctx.getSource(), "list [page]", "List all loaded presets and their spell setups.");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx)
    {
        PresetManager.reload();
        int count = PresetManager.getPresets().size();
        int reapplied = WizardAiGoal.reapplyAll(ctx.getSource().getServer());
        final int re = reapplied;
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded MobWizardry config. Loaded " + count + " preset(s). Re-applied config to " + re + " existing wizard(s)."), true);
        return count;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int page)
    {
        MobWizardryCommandOutput.sendPresetsPage(ctx.getSource(), PresetManager.getPresets(), page);
        return PresetManager.getPresets().size();
    }
}
