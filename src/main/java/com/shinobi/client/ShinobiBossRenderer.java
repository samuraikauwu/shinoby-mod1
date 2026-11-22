package com.shinobi.client;

import com.shinobi.ShinobiMod;
import com.shinobi.entity.ShinobiBossEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class ShinobiBossRenderer extends BipedEntityRenderer<ShinobiBossEntity, ShinobiBossModel> {
    private final Identifier texture;
    private final float scale;

    public ShinobiBossRenderer(EntityRendererFactory.Context ctx, String name, float scale) {
        super(ctx, new ShinobiBossModel(ctx.getPart(ShinobiBossModel.LAYER)), 0.5f * scale);
        this.texture = ShinobiMod.id("textures/entity/" + name + ".png");
        this.scale = scale;
    }

    @Override
    public Identifier getTexture(ShinobiBossEntity entity) {
        return texture;
    }

    @Override
    protected void scale(ShinobiBossEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(scale, scale, scale);
    }
}
