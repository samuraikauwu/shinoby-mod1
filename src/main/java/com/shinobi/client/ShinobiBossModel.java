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
    private final ModelPart skirtF;
    private final ModelPart skirtB;
    private final ModelPart sashL;
    private final ModelPart sashR;

    public ShinobiBossModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body");
        this.cape = body.getChild("cape");
        this.sheath = body.getChild("sheath");
        this.skirtF = body.getChild("skirt_f");
        this.skirtB = body.getChild("skirt_b");
        this.sashL = body.getChild("sash_l");
        this.sashR = body.getChild("sash_r");
    }

    public static TexturedModelData createModelData() {
        ModelData md = BipedEntityModel.getModelData(Dilation.NONE, 0.0f);
        ModelPartData root = md.getRoot();

        // Голова: рога, гребень, маска
        ModelPartData head = root.getChild("head");
        head.addChild("horn_l", ModelPartBuilder.create().uv(64, 102).cuboid(-0.5f, -5.0f, -0.5f, 1, 5, 1),
                ModelTransform.of(3.0f, -7.5f, -1.0f, 0.0f, 0.0f, 0.5f));
        head.addChild("horn_r", ModelPartBuilder.create().uv(64, 102).cuboid(-0.5f, -5.0f, -0.5f, 1, 5, 1),
                ModelTransform.of(-3.0f, -7.5f, -1.0f, 0.0f, 0.0f, -0.5f));
        head.addChild("crest", ModelPartBuilder.create().uv(64, 90).cuboid(-0.5f, -13.5f, -2.5f, 1, 6, 5), ModelTransform.NONE);
        head.addChild("mask", ModelPartBuilder.create().uv(64, 84).cuboid(-3.0f, -3.6f, -4.6f, 6, 4, 1), ModelTransform.NONE);

        // Корпус: нагрудник, пояс, юбка из панелей, концы кушака, плащ, ножны
        ModelPartData body = root.getChild("body");
        body.addChild("chest", ModelPartBuilder.create().uv(64, 44).cuboid(-4.5f, 0.8f, -3.1f, 9, 6, 2), ModelTransform.NONE);
        body.addChild("belt", ModelPartBuilder.create().uv(64, 54).cuboid(-4.5f, 9.6f, -2.5f, 9, 2, 5), ModelTransform.NONE);
        body.addChild("skirt_f", ModelPartBuilder.create().uv(64, 62).cuboid(-4.0f, 0.0f, -0.5f, 8, 7, 1), ModelTransform.pivot(0.0f, 11.0f, -2.9f));
        body.addChild("skirt_b", ModelPartBuilder.create().uv(64, 62).cuboid(-4.0f, 0.0f, -0.5f, 8, 7, 1), ModelTransform.pivot(0.0f, 11.0f, 2.9f));
        body.addChild("skirt_l", ModelPartBuilder.create().uv(64, 72).cuboid(-0.5f, 0.0f, -2.0f, 1, 7, 4), ModelTransform.pivot(4.9f, 11.0f, 0.0f));
        body.addChild("skirt_r", ModelPartBuilder.create().uv(64, 72).cuboid(-0.5f, 0.0f, -2.0f, 1, 7, 4), ModelTransform.pivot(-4.9f, 11.0f, 0.0f));
        body.addChild("sash_l", ModelPartBuilder.create().uv(30, 64).cuboid(-1.0f, 0.0f, -0.5f, 2, 8, 1), ModelTransform.pivot(2.0f, 10.8f, -2.8f));
        body.addChild("sash_r", ModelPartBuilder.create().uv(30, 64).cuboid(-1.0f, 0.0f, -0.5f, 2, 8, 1), ModelTransform.pivot(-1.0f, 10.8f, -2.8f));
        body.addChild("cape", ModelPartBuilder.create().uv(0, 64).cuboid(-5.0f, 0.0f, 0.0f, 10, 16, 1),
                ModelTransform.of(0.0f, 0.5f, 2.4f, 0.12f, 0.0f, 0.0f));
        body.addChild("sheath", ModelPartBuilder.create().uv(24, 64).cuboid(-0.5f, 0.0f, -0.5f, 1, 12, 1),
                ModelTransform.of(3.4f, 9.0f, 2.8f, 0.0f, 0.0f, -1.0f));

        // Руки: двухъярусные наплечники и наручи
        ModelPartData ra = root.getChild("right_arm");
        ra.addChild("pauldron_a", ModelPartBuilder.create().uv(64, 0).cuboid(-4.0f, -3.0f, -3.5f, 6, 2, 7), ModelTransform.NONE);
        ra.addChild("pauldron_b", ModelPartBuilder.create().uv(64, 10).cuboid(-3.5f, -1.8f, -3.0f, 5, 2, 6), ModelTransform.NONE);
        ra.addChild("bracer", ModelPartBuilder.create().uv(64, 20).cuboid(-3.5f, 5.2f, -2.5f, 5, 5, 5), ModelTransform.NONE);
        ModelPartData la = root.getChild("left_arm");
        la.addChild("pauldron_a", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0f, -3.0f, -3.5f, 6, 2, 7), ModelTransform.NONE);
        la.addChild("pauldron_b", ModelPartBuilder.create().uv(64, 10).cuboid(-1.5f, -1.8f, -3.0f, 5, 2, 6), ModelTransform.NONE);
        la.addChild("bracer", ModelPartBuilder.create().uv(64, 20).cuboid(-1.5f, 5.2f, -2.5f, 5, 5, 5), ModelTransform.NONE);

        // Ноги: наголенники
        root.getChild("right_leg").addChild("greave", ModelPartBuilder.create().uv(64, 32).cuboid(-2.5f, 5.5f, -2.5f, 5, 6, 5), ModelTransform.NONE);
        root.getChild("left_leg").addChild("greave", ModelPartBuilder.create().uv(64, 32).cuboid(-2.5f, 5.5f, -2.5f, 5, 6, 5), ModelTransform.NONE);
        return TexturedModelData.of(md, 128, 128);
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
        float move = Math.min(limbDistance, 1.0f);
        float flutter = MathHelper.sin(t * 0.11f) * 0.05f;
        this.skirtF.pitch = -move * 0.45f + flutter + (pose == 4 ? 0.9f : 0f);
        this.skirtB.pitch = move * 0.45f - flutter;
        this.sashL.pitch = -move * 0.5f + MathHelper.sin(t * 0.13f) * 0.08f;
        this.sashR.pitch = -move * 0.5f + MathHelper.sin(t * 0.13f + 1.3f) * 0.08f;
        this.sashL.roll = MathHelper.sin(t * 0.09f) * 0.07f;
        this.sashR.roll = -MathHelper.sin(t * 0.09f) * 0.07f;
    }
}
