package com.shinobi.client;

import com.shinobi.ShinobiMod;
import com.shinobi.entity.ShinobiBossEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class ShinobiBossRenderer extends BipedEntityRenderer<ShinobiBossEntity, ShinobiBossModel> {
    private final Identifier texture;
    private final float scale;

    public ShinobiBossRenderer(EntityRendererFactory.Context ctx, String name, float scale) {
        super(ctx, new ShinobiBossModel(ctx.getPart(ShinobiBossModel.LAYER)), 0.5f * scale);
        this.texture = ShinobiMod.id("textures/entity/" + name + ".png");
        this.scale = scale;
        this.addFeature(new Glow(this, ShinobiMod.id("textures/entity/" + name + "_glow.png")));
    }

    @Override
    public Identifier getTexture(ShinobiBossEntity entity) {
        return texture;
    }

    @Override
    protected void scale(ShinobiBossEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(scale, scale, scale);
    }

    /** Светящиеся глаза и акценты: рисуются слоем «глаз», не зависящим от освещения. */
    private static class Glow extends FeatureRenderer<ShinobiBossEntity, ShinobiBossModel> {
        private final RenderLayer layer;

        Glow(FeatureRendererContext<ShinobiBossEntity, ShinobiBossModel> ctx, Identifier tex) {
            super(ctx);
            this.layer = RenderLayer.getEyes(tex);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider providers, int light, ShinobiBossEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            VertexConsumer vc = providers.getBuffer(layer);
            float pulse = 0.8f + 0.2f * (float) Math.sin(animationProgress * 0.15f);
            this.getContextModel().render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, pulse, pulse, pulse, 1.0f);
        }
    }
}
