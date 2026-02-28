package com.example.maidmarriage.client;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;

/**
 * 子代女仆实体渲染器：沿用女仆渲染体系。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public class MaidChildRenderer extends EntityMaidRenderer {
    public MaidChildRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void scale(Mob maid, PoseStack poseStack, float partialTickTime) {
        super.scale(maid, poseStack, partialTickTime);
        poseStack.scale(0.72F, 0.72F, 0.72F);
    }
}
