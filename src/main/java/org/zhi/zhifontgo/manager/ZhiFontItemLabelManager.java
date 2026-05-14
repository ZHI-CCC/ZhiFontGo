package org.zhi.zhifontgo.manager;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class ZhiFontItemLabelManager {
    private static final double MAX_RENDER_DISTANCE_SQR = 20.0D * 20.0D;
    private static final double MIN_VERTICAL_OFFSET = 0.35D;
    private static final float BASE_SCALE = 0.025F;
    private static final int BASE_COLOR = 0xF5F5F5;
    private static final int COMMON_COLOR = 0x7DFFB3;
    private static final int STACK_COLOR = 0x66CCFF;
    private static final int BULK_COLOR = 0xFFCC66;
    private static final int HOARD_COLOR = 0xFF7A7A;

    private ZhiFontItemLabelManager() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Font font = ZhiFontManager.activeWorldRuntimeFont() != null ? ZhiFontManager.activeWorldRuntimeFont() : minecraft.font;

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) {
                continue;
            }
            if (!shouldRenderLabel(minecraft, itemEntity, cameraPosition)) {
                continue;
            }
            renderItemLabel(itemEntity, event, poseStack, bufferSource, font, cameraPosition);
        }

        bufferSource.endBatch();
    }

    private static boolean shouldRenderLabel(Minecraft minecraft, ItemEntity itemEntity, Vec3 cameraPosition) {
        if (!itemEntity.isAlive() || itemEntity.isInvisible()) {
            return false;
        }
        if (itemEntity.getItem().isEmpty()) {
            return false;
        }
        if (minecraft.player != null && minecraft.player.isScoping()) {
            return false;
        }
        return itemEntity.position().distanceToSqr(cameraPosition) <= MAX_RENDER_DISTANCE_SQR;
    }

    private static void renderItemLabel(
        ItemEntity itemEntity,
        RenderLevelStageEvent event,
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        Font font,
        Vec3 cameraPosition
    ) {
        ItemStack stack = itemEntity.getItem();
        ItemLabelStyle labelStyle = resolveStyle(stack.getCount());
        Component label = buildLabel(stack, labelStyle);
        String labelText = label.getString();
        if (labelText.isBlank()) {
            return;
        }

        Vec3 interpolatedPosition = itemEntity.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        double offsetY = itemEntity.getBbHeight() + MIN_VERTICAL_OFFSET + labelStyle.extraYOffset();

        poseStack.pushPose();
        poseStack.translate(
            interpolatedPosition.x - cameraPosition.x,
            interpolatedPosition.y - cameraPosition.y + offsetY,
            interpolatedPosition.z - cameraPosition.z
        );
        poseStack.mulPose(event.getCamera().rotation());
        poseStack.scale(BASE_SCALE, -BASE_SCALE, BASE_SCALE);
        poseStack.scale(labelStyle.scale(), labelStyle.scale(), labelStyle.scale());

        Matrix4f pose = poseStack.last().pose();
        float textWidth = -font.width(label) / 2.0F;
        int backgroundColor = (int)(Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;

        font.drawInBatch(label, textWidth, 0.0F, labelStyle.color(), false, pose, bufferSource, Font.DisplayMode.SEE_THROUGH, backgroundColor, LightTexture.FULL_BRIGHT);
        if (labelStyle.outline()) {
            font.drawInBatch8xOutline(label.getVisualOrderText(), textWidth, 0.0F, labelStyle.color(), 0x33000000, pose, bufferSource, LightTexture.FULL_BRIGHT);
        }
        if (labelStyle.shadowPass()) {
            font.drawInBatch(label, textWidth, 0.0F, labelStyle.color(), true, pose, bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        poseStack.popPose();
    }

    private static Component buildLabel(ItemStack stack, ItemLabelStyle labelStyle) {
        MutableComponent label = stack.getHoverName().copy().setStyle(Style.EMPTY.withColor(labelStyle.color()));
        if (stack.getCount() > 1) {
            label.append(Component.literal(" x" + stack.getCount()).withStyle(Style.EMPTY.withColor(labelStyle.countColor())));
        }
        return label;
    }

    private static ItemLabelStyle resolveStyle(int count) {
        if (count >= 128) {
            return new ItemLabelStyle(HOARD_COLOR, 0xFFF2A8, 1.22F, 0.08F, true, true);
        }
        if (count >= 64) {
            return new ItemLabelStyle(BULK_COLOR, 0xFFF0BF, 1.14F, 0.04F, true, false);
        }
        if (count >= 16) {
            return new ItemLabelStyle(STACK_COLOR, 0xD8F0FF, 1.08F, 0.02F, false, false);
        }
        if (count >= 2) {
            return new ItemLabelStyle(COMMON_COLOR, 0xD8FFE8, 1.0F, 0.0F, false, false);
        }
        return new ItemLabelStyle(BASE_COLOR, 0xFFFFFF, 0.96F, 0.0F, false, false);
    }

    private record ItemLabelStyle(
        int color,
        int countColor,
        float scale,
        float extraYOffset,
        boolean outline,
        boolean shadowPass
    ) {
    }
}
