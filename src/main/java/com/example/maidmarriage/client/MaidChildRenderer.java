package com.example.maidmarriage.client;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;

/**
 * 子代女仆实体渲染器：沿用女仆渲染体系。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public class MaidChildRenderer extends EntityMaidRenderer {
    private static final float CHILD_RENDER_SCALE = 0.72F;

    public MaidChildRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Mob entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferIn, int packedLightIn) {
        // 在最外层统一缩放，确保 YSM 渲染分支也会按小女仆比例显示
        poseStack.pushPose();
        poseStack.scale(CHILD_RENDER_SCALE, CHILD_RENDER_SCALE, CHILD_RENDER_SCALE);
        super.render(entity, entityYaw, partialTicks, poseStack, bufferIn, packedLightIn);
        poseStack.popPose();
    }
}
