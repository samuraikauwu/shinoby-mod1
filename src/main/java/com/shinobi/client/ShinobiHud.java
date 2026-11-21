package com.shinobi.client;

import com.shinobi.ModItems;
import com.shinobi.combat.Art;
import com.shinobi.item.ProstheticItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Интерфейс в духе Sekiro: жизнь и осанка слева сверху, полоса цели по центру сверху, слоты внизу. */
public final class ShinobiHud {
    private static final int FRAME = 0xFF6E5B3A;
    private static final int FRAME_DARK = 0xFF1B1610;
    private static final int PANEL = 0xB0100C08;

    private ShinobiHud() {
    }

    public static void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.player.isSpectator()) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        long t = mc.world == null ? 0 : mc.world.getTime();

        playerBars(ctx, mc, tr, t);
        targetBar(ctx, tr, w, t);
        bottomSlots(ctx, mc, tr, w, h);
        perilous(ctx, tr, w, h);
    }

    // ---------------------------------------------------------------- рамка полосы

    private static void bar(DrawContext c, int x, int y, int w, int h, float frac, int top, int bottom) {
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, FRAME);
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME_DARK);
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) c.fillGradient(x, y, x + fw, y + h, top, bottom);
        c.fill(x, y, x + w, y + 1, 0x18FFFFFF);
    }

    private static void barCentered(DrawContext c, int cx, int y, int w, int h, float frac, int top, int bottom) {
        c.fill(cx - w / 2 - 2, y - 2, cx + w / 2 + 2, y + h + 2, FRAME);
        c.fill(cx - w / 2 - 1, y - 1, cx + w / 2 + 1, y + h + 1, FRAME_DARK);
        int half = Math.round(w / 2f * Math.max(0f, Math.min(1f, frac)));
        if (half > 0) c.fillGradient(cx - half, y, cx + half, y + h, top, bottom);
    }

    private static void bigText(DrawContext c, TextRenderer tr, Text text, int cx, int y, float scale, int color) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(cx, y, 0);
        m.scale(scale, scale, 1f);
        int tw = tr.getWidth(text);
        c.drawText(tr, text, -tw / 2, 0, color, true);
        m.pop();
    }

    // ---------------------------------------------------------------- игрок

    private static void playerBars(DrawContext c, MinecraftClient mc, TextRenderer tr, long t) {
        float maxHp = mc.player.getMaxHealth();
        float hp = mc.player.getHealth();
        int x = 16;
        int y = 14;
        int vw = (int) Math.min(260, 90 + (maxHp - 20f) * 6f + 70);
        bar(c, x, y, vw, 7, hp / maxHp, 0xFFB52A24, 0xFF5E0F0C);

        float pf = Math.min(1f, ShinobiClient.posture / ShinobiClient.postureMax);
        int pw = Math.max(70, vw - 20);
        int top = ShinobiClient.broken ? ((t / 3 % 2 == 0) ? 0xFFFF4A3A : 0xFFAA1A12) : (pf > 0.75f ? 0xFFFFC04A : 0xFFF2EDE0);
        int bot = ShinobiClient.broken ? 0xFF7A0E08 : (pf > 0.75f ? 0xFFB5651A : 0xFFB8B09C);
        bar(c, x, y + 14, pw, 4, ShinobiClient.broken ? 1f : pf, top, bot);

        // Воскрешения — ромбы под полосой
        for (int i = 0; i < ShinobiClient.resMax; i++) {
            int dx = x + i * 11;
            int dy = y + 26;
            boolean on = i < ShinobiClient.resurrections;
            diamond(c, dx + 4, dy + 4, 4, on ? 0xFFE8D6A0 : 0xFF3A3024);
        }
        if (ShinobiClient.attackPower > 0) {
            c.drawTextWithShadow(tr, Text.translatable("hud.shinobi.attack_power", ShinobiClient.attackPower), x + 30, y + 26, 0xFFB8AE96);
        }
    }

    private static void diamond(DrawContext c, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int span = r - Math.abs(i);
            c.fill(cx - span, cy + i, cx + span + 1, cy + i + 1, color);
        }
    }

    // ---------------------------------------------------------------- цель / босс

    private static void targetBar(DrawContext c, TextRenderer tr, int w, long t) {
        if (ShinobiClient.tMax <= 1f || (ShinobiClient.tPosture <= 0 && !ShinobiClient.tBroken && !ShinobiClient.tBoss)) return;
        int cx = w / 2;
        boolean boss = ShinobiClient.tBoss;
        float tf = Math.min(1f, ShinobiClient.tPosture / ShinobiClient.tMax);
        int y = 14;

        if (boss) {
            int bw = 240;
            c.drawCenteredTextWithShadow(tr, Text.literal(ShinobiClient.tName), cx, y - 2, 0xFFE8DCC0);
            bar(c, cx - bw / 2, y + 12, bw, 6, ShinobiClient.tHealth, 0xFFB52A24, 0xFF5E0F0C);
            // Сегменты осанки — ромбы по краям полосы
            for (int i = 0; i < ShinobiClient.tSegments; i++) {
                diamond(c, cx - bw / 2 - 10 - i * 11, y + 15, 4, 0xFFE8D6A0);
            }
            y += 28;
        } else {
            c.drawCenteredTextWithShadow(tr, Text.literal(ShinobiClient.tName), cx, y - 2, 0xFFD8D0BA);
            y += 14;
        }

        int pw = boss ? 200 : 130;
        int top = ShinobiClient.tBroken ? ((t / 3 % 2 == 0) ? 0xFFFF4A3A : 0xFFAA1A12) : (tf > 0.75f ? 0xFFFFC04A : 0xFFF2EDE0);
        int bot = ShinobiClient.tBroken ? 0xFF7A0E08 : (tf > 0.75f ? 0xFFB5651A : 0xFFB8B09C);
        barCentered(c, cx, y, pw, 4, ShinobiClient.tBroken ? 1f : tf, top, bot);

        if (ShinobiClient.tBroken) {
            bigText(c, tr, Text.literal("忍殺"), cx, y + 12, 3f, 0xFFD42A20);
            c.drawCenteredTextWithShadow(tr, Text.translatable("hud.shinobi.deathblow"), cx, y + 42, 0xFFE8B0A0);
        }
    }

    // ---------------------------------------------------------------- нижние слоты

    private static void slot(DrawContext c, int x, int y, int size) {
        c.fill(x - 2, y - 2, x + size + 2, y + size + 2, FRAME);
        c.fill(x - 1, y - 1, x + size + 1, y + size + 1, FRAME_DARK);
        c.fill(x, y, x + size, y + size, PANEL);
    }

    private static void bottomSlots(DrawContext c, MinecraftClient mc, TextRenderer tr, int w, int h) {
        int size = 28;

        // Слева: боевой приём + эмблемы
        int lx = 14;
        int ly = h - 54;
        slot(c, lx, ly, size);
        Art art = Art.byOrdinal(ShinobiClient.selectedArt);
        boolean known = (ShinobiClient.unlocked & (1 << art.ordinal())) != 0;
        ItemStack held = mc.player.getMainHandStack();
        if (held.getItem() instanceof ProstheticItem) {
            c.drawItem(held, lx + 6, ly + 6);
        } else {
            ItemStack scroll = new ItemStack(ModItems.SCROLLS.get(art));
            c.drawItem(scroll, lx + 6, ly + 6);
            if (!known) c.fill(lx, ly, lx + size, ly + size, 0x99000000);
        }
        int emblems = mc.player.getInventory().count(ModItems.SPIRIT_EMBLEM);
        c.drawTextWithShadow(tr, Text.translatable("art.shinobi." + art.id), lx + size + 8, ly + 2, known ? 0xFFE8DCC0 : 0xFF7A7466);
        c.drawItem(new ItemStack(ModItems.SPIRIT_EMBLEM), lx + size + 6, ly + 12);
        c.drawTextWithShadow(tr, Text.literal("x" + emblems), lx + size + 26, ly + 17, 0xFFD8D0BA);
        if (!(held.getItem() instanceof ProstheticItem)) {
            c.drawTextWithShadow(tr, Text.literal("[R]"), lx + 3, ly + size + 4, 0xFF9A9078);
        }

        // Справа: целебная тыква
        int rx = w - 14 - size;
        int ry = h - 54;
        slot(c, rx, ry, size);
        c.drawItem(new ItemStack(ModItems.HEALING_GOURD), rx + 6, ry + 6);
        Text cnt = Text.literal(ShinobiClient.gourd + "/" + ShinobiClient.gourdMax);
        c.drawTextWithShadow(tr, cnt, rx - tr.getWidth(cnt) - 6, ry + 10, ShinobiClient.gourd > 0 ? 0xFFE8DCC0 : 0xFFB04A40);
        if (ShinobiClient.sugar) {
            c.drawTextWithShadow(tr, Text.literal("»»"), rx + 8, ry - 12, 0xFFF0D060);
        }
    }

    // ---------------------------------------------------------------- 危

    private static void perilous(DrawContext c, TextRenderer tr, int w, int h) {
        int left = ShinobiClient.perilousTicks;
        if (left <= 0) return;
        float a = Math.min(1f, left / 8f);
        int alpha = (int) (255 * a) & 0xFF;
        int color = (alpha << 24) | 0xD42A20;
        float pulse = 1f + 0.18f * (left / 24f);
        bigText(c, tr, Text.literal("危"), w / 2, h / 2 - 70, 6f * pulse, color);
        Text hint = Text.translatable("hud.shinobi.perilous_" + ShinobiClient.perilousType);
        c.drawCenteredTextWithShadow(tr, hint, w / 2, h / 2 - 20, (alpha << 24) | 0xF0C8B8);
    }
}
