package com.shinobi.client;

import com.shinobi.entity.ShinobiBossEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.CrossbowPosing;

/** Гуманоидная модель босса: руки поднимаются в боевую стойку при атаке. */
public class ShinobiBossModel extends BipedEntityModel<ShinobiBossEntity> {
    public ShinobiBossModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setAngles(ShinobiBossEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        CrossbowPosing.meleeAttack(this.leftArm, this.rightArm, entity.isAttacking(), this.handSwingProgress, animationProgress);
    }
}
