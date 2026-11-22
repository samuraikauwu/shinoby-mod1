package com.shinobi.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Оригинальные катсцены: кинематографические полосы, карточка босса, названия зон, баннеры победы и воскрешения. */
public final class ShinobiCinematic {
    private static int kind = -1;
    private static String title = "";
    private static String sub = "";
    private static int total = 0;
    private static int left = 0;

    private ShinobiCinematic() {
    }

    public static void start(int k, String t, String s, int ticks) {
        kind = k;
        title = t;
        sub = s;
        total = Math.max(10, ticks);
        left = total;
    }

    public static void tick() {
        if (left > 0) left--;
    }

    public static boolean active() {
        return left > 0;
    }

    private static float fade(float p) {
        // плавное появление в первых 20% и исчезновение в последних 25%
        float in = Math.min(1f, p / 0.2f);
        float out = Math.min(1f, (1f - p) / 0.25f);
        return Math.max(0f, Math.min(in, out));
    }

    private static void big(DrawContext c, TextRenderer tr, Text text, int cx, int y, float scale, int rgb, float alpha) {
        int a = Math.max(4, Math.min(255, (int) (alpha * 255)));
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(cx, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(tr, text, -tr.getWidth(text) / 2, 0, (a << 24) | rgb, true);
        m.pop();
    }

    private static void line(DrawContext c, int cx, int y, int halfW, int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        c.fill(cx - halfW, y, cx + halfW, y + 1, (a << 24) | rgb);
        c.fill(cx - 3, y - 2, cx + 3, y + 3, (a << 24) | rgb);
    }

    public static void render(DrawContext c, float delta) {
        if (left <= 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int w = c.getScaledWindowWidth();
        int h = c.getScaledWindowHeight();
        float prog = 1f - (left - delta) / total;
        prog = Math.max(0f, Math.min(1f, prog));
        float f = fade(prog);
        int cx = w / 2;
        Text t = Text.translatable(title);
        Text s = sub.isEmpty() ? Text.empty() : Text.translatable(sub);

        switch (kind) {
            case 0 -> { // вступление босса
                int bar = (int) (h * 0.13f * f);
                c.fill(0, 0, w, bar, 0xFF000000);
                c.fill(0, h - bar, w, h, 0xFF000000);
                // тёмная виньетка
                c.fillGradient(0, bar, w, bar + h / 5, (int) (110 * f) << 24, 0x00000000);
                c.fillGradient(0, h - bar - h / 5, w, h - bar, 0x00000000, (int) (110 * f) << 24);
                float ta = Math.max(0f, Math.min(1f, (prog - 0.3f) / 0.2f)) * Math.min(1f, (1f - prog) / 0.15f);
                big(c, tr, t, cx, h - bar - 56, 3.0f, 0xE8DCC0, ta);
                line(c, cx, h - bar - 24, (int) (110 * ta), 0xB02A20, ta);
                big(c, tr, s, cx, h - bar - 18, 1.2f, 0xC8BCA0, ta);
            }
            case 1 -> { // название зоны
                float a = f;
                big(c, tr, t, cx, h / 2 - 64, 2.4f, 0xF0E4C4, a);
                line(c, cx, h / 2 - 36, (int) (80 * a), 0xD8B85A, a);
                big(c, tr, s, cx, h / 2 - 30, 1.0f, 0xB8AE96, a);
            }
            case 2 -> { // победа
                int bar = (int) (h * 0.09f * f);
                c.fill(0, 0, w, bar, 0xFF000000);
                c.fill(0, h - bar, w, h, 0xFF000000);
                c.fillGradient(0, h / 2 - 40, w, h / 2 + 34, 0x00000000, (int) (150 * f) << 24);
                c.fillGradient(0, h / 2 + 34, w, h / 2 + 110, (int) (150 * f) << 24, 0x00000000);
                big(c, tr, t, cx, h / 2 - 14, 4.0f, 0xE8C860, f);
                line(c, cx, h / 2 + 30, (int) (140 * f), 0xE8C860, f);
                big(c, tr, s, cx, h / 2 + 38, 1.1f, 0xE0D4B0, f);
            }
            default -> { // воскрешение
                float flash = (1f - prog) * (1f - prog);
                c.fill(0, 0, w, h, ((int) (60 * flash) << 24) | 0xFFFFFF);
                big(c, tr, t, cx, h / 2 - 20, 3.0f, 0xF2EEE2, f);
                big(c, tr, s, cx, h / 2 + 12, 1.0f, 0xB8AE96, f);
            }
        }
    }
}
