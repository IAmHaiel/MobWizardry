package com.haylent.mobwizardry.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the wizard two-line name tag ("Name" with a smaller "{@code < Team >}" line beneath it,
 * each in its own configured color) for every mob, not just the {@code mobwizardry:wizard} NPC.
 * Vanilla 1.20.1 name-tag rendering draws the component as a single line, so the {@code \n}
 * between the lines renders as a missing-glyph box on mobs without a custom renderer (e.g. a
 * wizardified zombie). This mixin intercepts {@code EntityRenderer.renderNameTag}: when the name
 * contains a newline it draws the two lines exactly like the wizard NPC's renderer used to
 * (name at full size, team line at 0.6x scale), and cancels the vanilla drawing; names without a
 * newline fall through to the untouched vanilla path.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagMixin
{
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    public abstract Font getFont();

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void mobwizardry$renderTwoLineNameTag(Entity entity, Component displayName, PoseStack poseStack,
                                                  MultiBufferSource buffer, int packedLight, CallbackInfo ci)
    {
        String full = displayName.getString();
        int split = full.indexOf('\n');
        if (split < 0)
        {
            return;
        }
        String nameLine = full.substring(0, split);
        String teamLine = full.substring(split + 1);
        if (nameLine.isEmpty() && teamLine.isEmpty())
        {
            return;
        }
        if (!ForgeHooksClient.isNameplateInRenderDistance(entity, this.entityRenderDispatcher.distanceToSqr(entity)))
        {
            return;
        }

        Font font = this.getFont();
        float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int backgroundColor = (int) (opacity * 255.0F) << 24;
        int nameColor = colorOf(displayName.getStyle().getColor(), 0xFFFFFFFF);
        int teamColor = teamColorOf(displayName);
        boolean flag = !entity.isDiscrete();
        Font.DisplayMode mode = flag ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getNameTagOffsetY(), 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();

        font.drawInBatch(nameLine, -font.width(nameLine) / 2.0F, 0.0F, nameColor, flag,
                matrix, buffer, mode, packedLight, backgroundColor);

        if (!teamLine.isEmpty())
        {
            poseStack.pushPose();
            poseStack.scale(0.6F, 0.6F, 1.0F);
            Matrix4f teamMatrix = poseStack.last().pose();
            font.drawInBatch(teamLine, -font.width(teamLine) / 2.0F, 18.0F, teamColor, flag,
                    teamMatrix, buffer, mode, packedLight, backgroundColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        ci.cancel();
    }

    private static int colorOf(TextColor color, int fallback)
    {
        return color == null ? fallback : color.getValue();
    }

    private static int teamColorOf(Component displayName)
    {
        for (Component sibling : displayName.getSiblings())
        {
            TextColor color = sibling.getStyle().getColor();
            if (color != null)
            {
                return color.getValue();
            }
        }
        return 0xFFAAAAAA;
    }
}
