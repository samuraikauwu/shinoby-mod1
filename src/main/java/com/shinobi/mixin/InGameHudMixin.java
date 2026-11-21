package com.shinobi.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Скрывает ванильные сердца здоровья: вместо них полоса жизни в интерфейсе мода. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
    private void shinobi$hideHearts(CallbackInfo ci) {
        ci.cancel();
    }
}
