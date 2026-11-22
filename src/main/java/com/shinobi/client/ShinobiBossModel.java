package com.shinobi.client;

import com.shinobi.ShinobiMod;
import com.shinobi.entity.ShinobiBossEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.math.MathHelper;

/**
 * Модель босса: гуманоид + рога, плащ, наплечники, юбка и ножны (части, не нужные боссу, просто прозрачны в его текстуре).
 * Анимации: покачивание, удар сверху, позы опасных атак и колено при сломанной осанке.
 */
public class ShinobiBossModel extends BipedEntityModel<ShinobiBossEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(ShinobiMod.id("boss"), "main");

    private final ModelPart cape;
    private final ModelPart sheath;

    public ShinobiBossModel(ModelPart root) {
        super(root);
        this.cape = root.getChild("body").getChild("cape");
        this.sheath = root.getChild("body").getChild("sheath");
    }

    public static TexturedModelData createModelData() {
        ModelData md = BipedEntityModel.getModelData(Dilation.NONE, 0.0f);
        ModelPartData root = md.getRoot();

        ModelPartData head = root.getChild("head");
        head.addChild("horn_l", ModelPartBuilder.create().uv(48, 32).cuboid(-0.5f, -4.0f, -0.5f, 1, 4, 1),
                ModelTransform.of(3.0f, -7.5f, -1.0f, 0.0f, 0.0f, 0.45f));
        head.addChild("horn_r", ModelPartBuilder.create().uv(48, 32).cuboid(-0.5f, -4.0f, -0.5f, 1, 4, 1),
                ModelTransform.of(-3.0f, -7.5f, -1.0f, 0.0f, 0.0f, -0.45f));

        ModelPartData body = root.getChild("body");
        body.addChild("cape", ModelPartBuilder.create().uv(0, 32).cuboid(-5.0f, 0.0f, 0.0f, 10, 15, 1),
                ModelTransform.of(0.0f, 0.5f, 2.2f, 0.12f, 0.0f, 0.0f));
        body.addChild("skirt", ModelPartBuilder.create().uv(0, 48).cuboid(-4.5f, 0.0f, -2.5f, 9, 4, 5),
                ModelTransform.pivot(0.0f, 11.5f, 0.0f));
        body.addChild("sheath", ModelPartBuilder.create().uv(30, 48).cuboid(-0.5f, 0.0f, -0.5f, 1, 12, 1),
                ModelTransform.of(3.2f, 9.0f, 2.6f, 0.0f, 0.0f, -1.0f));

        root.getChild("right_arm").addChild("pad", ModelPartBuilder.create().uv(24, 32).cuboid(-3.5f, -3.0f, -3.0f, 5, 2, 6),
                ModelTransform.NONE);
        root.getChild("left_arm").addChild("pad", ModelPartBuilder.create().uv(24, 32).cuboid(-1.5f, -3.0f, -3.0f, 5, 2, 6),
                ModelTransform.NONE);
        return TexturedModelData.of(md, 64, 64);
    }

    @Override
    public void setAngles(ShinobiBossEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        CrossbowPosing.meleeAttack(this.leftArm, this.rightArm, entity.isAttacking(), this.handSwingProgress, animationProgress);

        // Базовые опорные точки (на случай, если прошлый кадр сдвигал их)
        this.head.pivotY = 0.0f;
        this.body.pivotY = 0.0f;
        this.rightArm.pivotY = 2.0f;
        this.leftArm.pivotY = 2.0f;
        this.rightLeg.pivotY = 12.0f;
        this.leftLeg.pivotY = 12.0f;
        this.body.yaw = 0.0f;

        int pose = entity.getAnim();
        float t = animationProgress;

        // Дыхание и покачивание
        float breathe = MathHelper.sin(t * 0.07f);
        this.rightArm.roll += 0.04f + breathe * 0.03f;
        this.leftArm.roll -= 0.04f + breathe * 0.03f;
        this.body.pitch += breathe * 0.012f;

        // Удар сверху: замах вверх, затем рубящий удар вниз с поворотом корпуса
        float s = this.handSwingProgress;
        if (s > 0.0f && pose == 0) {
            float k = s < 0.35f ? s / 0.35f : 1.0f - (s - 0.35f) / 0.65f;
            float down = s < 0.35f ? 0.0f : (s - 0.35f) / 0.65f;
            this.rightArm.pitch = -2.7f + down * 3.3f;
            this.rightArm.yaw = -0.25f * k;
            this.leftArm.pitch = -1.6f + down * 1.5f;
            this.body.yaw = (down - 0.4f) * 0.55f;
            this.head.pitch += down * 0.25f;
        }

        switch (pose) {
            case 1 -> { // замах выпада: оружие отведено назад, корпус откинут
                this.rightArm.pitch = -1.3f;
                this.rightArm.yaw = -0.7f;
                this.leftArm.pitch = -1.0f;
                this.leftArm.yaw = 0.5f;
                this.body.pitch = -0.2f;
                this.body.yaw = 0.35f;
                this.rightLeg.pitch = 0.5f;
                this.leftLeg.pitch = -0.4f;
            }
            case 2 -> { // замах подсечки: приседание, руки разведены низко
                this.body.pitch = 0.4f;
                this.head.pitch -= 0.3f;
                this.rightArm.pitch = -0.3f;
                this.rightArm.roll = 1.35f;
                this.leftArm.pitch = -0.3f;
                this.leftArm.roll = -1.35f;
                this.rightLeg.pitch = -0.7f;
                this.leftLeg.pitch = -0.7f;
                this.rightLeg.yaw = 0.35f;
                this.leftLeg.yaw = -0.35f;
            }
            case 3 -> { // захват: обе руки вытянуты вперёд
                this.rightArm.pitch = -1.55f;
                this.rightArm.yaw = -0.3f;
                this.leftArm.pitch = -1.55f;
                this.leftArm.yaw = 0.3f;
                this.body.pitch = 0.18f;
                this.head.pitch += 0.15f;
            }
            case 4 -> { // осанка сломана: стоит на колене, голова опущена, руки обмякли
                this.head.pivotY = 5.0f;
                this.body.pivotY = 5.0f;
                this.rightArm.pivotY = 7.0f;
                this.leftArm.pivotY = 7.0f;
                this.rightLeg.pivotY = 17.0f;
                this.leftLeg.pivotY = 17.0f;
                this.body.pitch = 0.55f;
                this.head.pitch = 0.65f;
                this.rightArm.pitch = 0.35f + MathHelper.sin(t * 0.6f) * 0.04f;
                this.leftArm.pitch = 0.2f;
                this.rightArm.yaw = 0.0f;
                this.leftArm.yaw = 0.0f;
                this.rightLeg.pitch = -1.35f;
                this.leftLeg.pitch = 0.85f;
                this.rightLeg.yaw = 0.0f;
                this.leftLeg.yaw = 0.0f;
            }
            default -> {
            }
        }

        // Плащ колышется от движения, ножны покачиваются
        this.cape.pitch = 0.12f + Math.min(limbDistance, 1.0f) * 0.65f + MathHelper.sin(t * 0.09f) * 0.05f;
        this.sheath.roll = -1.0f + MathHelper.sin(limbAngle * 0.6662f) * limbDistance * 0.12f;
    }
}
