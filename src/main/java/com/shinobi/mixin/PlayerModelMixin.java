package com.shinobi.mixin;

import com.shinobi.ModItems;
import com.shinobi.client.ShinobiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Оригинальные анимации игрока: стойка с катаной, рубящий удар, рывок и колено при сломанной осанке. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void shinobi$animate(LivingEntity entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        PlayerEntityModel<?> m = (PlayerEntityModel<?>) (Object) this;
        boolean local = player == MinecraftClient.getInstance().player;
        Item held = player.getMainHandStack().getItem();
        boolean katana = held == ModItems.KUSABIMARU || held == ModItems.ASHINA_KATANA || held == ModItems.MORTAL_BLADE;
        boolean changed = false;

        if (katana && !player.isUsingItem()) {
            // Боевая стойка: меч у бедра, корпус чуть повёрнут
            m.rightArm.pitch -= 0.35f;
            m.rightArm.yaw -= 0.15f;
            m.leftArm.pitch -= 0.15f;
            m.body.yaw += 0.12f;
            changed = true;
            float s = m.handSwingProgress;
            if (s > 0f) {
                // Рубящий удар: замах над головой, затем резкий взмах вниз с поворотом корпуса
                float k = s < 0.35f ? s / 0.35f : 1f - (s - 0.35f) / 0.65f;
                float down = s < 0.35f ? 0f : (s - 0.35f) / 0.65f;
                m.rightArm.pitch = -2.5f + down * 3.0f;
                m.rightArm.yaw = -0.3f * k;
                m.leftArm.pitch = -1.2f + down * 1.0f;
                m.body.yaw = (down - 0.3f) * 0.5f;
                m.head.pitch += down * 0.15f;
            }
        }
        if (local && ShinobiClient.dashAnim > 0) {
            float k = ShinobiClient.dashAnim / 8f;
            m.body.pitch += 0.55f * k;
            m.head.pitch -= 0.3f * k;
            m.rightArm.pitch = -0.5f * k + m.rightArm.pitch * (1 - k);
            m.leftArm.pitch = 0.9f * k + m.leftArm.pitch * (1 - k);
            m.rightLeg.pitch = -0.9f * k;
            m.leftLeg.pitch = 0.7f * k;
            changed = true;
        }
        if (local && ShinobiClient.broken) {
            // Осанка сломана: игрок пошатывается, голова опущена, руки висят
            float sway = MathHelper.sin(age * 0.25f) * 0.08f;
            m.body.pitch = 0.45f + sway;
            m.head.pitch = 0.55f;
            m.rightArm.pitch = 0.25f;
            m.leftArm.pitch = 0.25f;
            m.rightArm.yaw = 0f;
            m.leftArm.yaw = 0f;
            changed = true;
        }
        if (changed) {
            m.leftSleeve.copyTransform(m.leftArm);
            m.rightSleeve.copyTransform(m.rightArm);
            m.leftPants.copyTransform(m.leftLeg);
            m.rightPants.copyTransform(m.rightLeg);
            m.jacket.copyTransform(m.body);
            m.hat.copyTransform(m.head);
        }
    }
}
