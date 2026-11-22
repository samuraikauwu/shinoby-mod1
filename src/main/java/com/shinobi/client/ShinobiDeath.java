package com.shinobi.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Оформление экрана смерти: кроваво-красная виньетка, огромный иероглиф «смерть» и подпись. */
public final class ShinobiDeath {
    private ShinobiDeath() {
    }

    public static void render(DrawContext c, int w, int h, float t) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float f = Math.min(1f, t / 1.6f);
        int a = (int) (120 * f);
        int e = h / 3;
        c.fillGradient(0, 0, w, e, (a << 24) | 0x600000, 0x00000000);
        c.fillGradient(0, h - e, w, h, 0x00000000, (a << 24) | 0x600000);
        int ta = Math.max(4, (int) (255 * f));
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(w / 2f, h - 86, 0);
        m.scale(5f, 5f, 1f);
        Text k = Text.literal("死");
        c.drawText(tr, k, -tr.getWidth(k) / 2, 0, (ta << 24) | 0xC02018, true);
        m.pop();
        Text sub = Text.translatable("death.shinobi.sub");
        c.drawCenteredTextWithShadow(tr, sub, w / 2, h - 32, ((int) (255 * f) << 24) | 0xC8BCA0);
    }
}
