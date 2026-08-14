package com.haylent.mobwizardry.command;

import com.haylent.mobwizardry.config.PresetDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Map;

/**
 * Pure formatting helpers for the {@code /mobwizardry} command output.
 */
public final class MobWizardryCommandOutput
{
    public static final int PRESETS_PER_PAGE = 5;

    private MobWizardryCommandOutput()
    {
    }

    public static int maxPage(int presetCount)
    {
        return Math.max(1, (presetCount + PRESETS_PER_PAGE - 1) / PRESETS_PER_PAGE);
    }

    public static Component pageNav(int currentPage, int maxPage)
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

    public static Component formatPreset(String name, PresetDefinition p)
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
        appendSpellCategory(root, "Attack", p.spells.attack);
        appendSpellCategory(root, "Defense", p.spells.defense);
        appendSpellCategory(root, "Movement", p.spells.movement);
        appendSpellCategory(root, "Support", p.spells.support);
        appendSpellCategory(root, "Escape", p.spells.escape);
        return root;
    }

    private static void appendSpellCategory(MutableComponent root, String label, List<PresetDefinition.SpellEntry> entries)
    {
        root.append(Component.literal("\n  " + label + ": ").withStyle(ChatFormatting.GRAY));
        root.append(formatSpellList(entries));
    }

    public static Component formatSpellList(List<PresetDefinition.SpellEntry> entries)
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

    public static void helpLine(CommandSourceStack source, String command, String description)
    {
        MutableComponent line = Component.literal("/mobwizardry " + command).withStyle(ChatFormatting.GOLD);
        line.append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> line, false);
    }

    public static void sendPresetsPage(CommandSourceStack source, Map<String, PresetDefinition> presets, int requestedPage)
    {
        if (presets.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No MobWizardry presets loaded."), false);
            return;
        }
        int maxPage = maxPage(presets.size());
        int currentPage = Math.min(Math.max(1, requestedPage), maxPage);
        List<Map.Entry<String, PresetDefinition>> all = new java.util.ArrayList<>(presets.entrySet());
        int from = (currentPage - 1) * PRESETS_PER_PAGE;
        int to = Math.min(from + PRESETS_PER_PAGE, all.size());

        MutableComponent header = Component.literal("=== MobWizardry presets (").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(String.valueOf(currentPage)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("/").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(String.valueOf(maxPage)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(") ===").withStyle(ChatFormatting.AQUA));
        source.sendSuccess(() -> header, false);

        for (int i = from; i < to; i++)
        {
            final Map.Entry<String, PresetDefinition> entry = all.get(i);
            source.sendSuccess(() -> formatPreset(entry.getKey(), entry.getValue()), false);
        }

        source.sendSuccess(() -> pageNav(currentPage, maxPage), false);
    }
}
