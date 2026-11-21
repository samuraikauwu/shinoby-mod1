package com.shinobi.client;

import com.shinobi.ModItems;
import com.shinobi.combat.Skill;
import com.shinobi.item.ProstheticTools;
import com.shinobi.net.ShinobiNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Меню шиноби: вкладки «Навыки» (дерево) и «Статус» (характеристики и управление). */
public class ShinobiSkillScreen extends Screen {
    private static final int W = 330;
    private static final int H = 246;
    private static final int NODE = 24;
    private static final int GAP_X = 35;
    private static final int GAP_Y = 38;

    private int tab = 0;
    private int left;
    private int top;

    public ShinobiSkillScreen() {
        super(Text.translatable("menu.shinobi.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.shinobi.tab_skills"), b -> tab = 0)
                .dimensions(left + 10, top + 10, 90, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.shinobi.tab_status"), b -> tab = 1)
                .dimensions(left + 104, top + 10, 90, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(left + W - 70, top + H - 26, 60, 18).build());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_K) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int nx(Skill s) {
        return left + 18 + s.col * GAP_X;
    }

    private int ny(Skill s) {
        return top + 78 + s.row * GAP_Y;
    }

    private boolean learned(Skill s) {
        return s.learned(ShinobiClient.unlocked, ShinobiClient.passives);
    }

    private boolean available(Skill s) {
        return !learned(s) && (s.requires == null || learned(s.requires));
    }

    private ItemStack icon(Skill s) {
        if (s.art != null) return new ItemStack(ModItems.SCROLLS.get(s.art));
        return switch (s) {
            case DEFLECT_WINDOW -> new ItemStack(ModItems.ASHINA_KATANA);
            case SWIFT_DASH -> new ItemStack(ModItems.SHINOBI_BOOTS);
            case SHADOW_STEP -> new ItemStack(ModItems.TOOLS.get(ProstheticTools.Tool.MIST_RAVEN));
            case VITAL_RECOVER -> new ItemStack(ModItems.PRAYER_NECKLACE);
            case TOOL_POWER -> new ItemStack(ModItems.TOOLS.get(ProstheticTools.Tool.AXE));
            case EMBLEM_SAVER -> new ItemStack(ModItems.SPIRIT_EMBLEM);
            default -> new ItemStack(ModItems.PRAYER_BEAD);
        };
    }

    // ------------------------------------------------------------------ рисование

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        // Панель
        ctx.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF6E5B3A);
        ctx.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF1B1610);
        ctx.fillGradient(left, top, left + W, top + H, 0xF0231C14, 0xF0100C08);

        Text pts = Text.translatable("hud.shinobi.skill_points", ShinobiClient.skillPoints);
        ctx.drawTextWithShadow(textRenderer, pts, left + W - 10 - textRenderer.getWidth(pts), top + 15, 0xFFE8D6A0);

        Skill hovered = null;
        if (tab == 0) hovered = drawTree(ctx, mx, my);
        else drawStatus(ctx);

        super.render(ctx, mx, my, delta);

        if (hovered != null) {
            List<Text> tip = new ArrayList<>();
            tip.add(Text.translatable("skill.shinobi." + hovered.id));
            tip.add(Text.translatable("skill.shinobi." + hovered.id + ".desc"));
            if (learned(hovered)) {
                tip.add(Text.translatable("menu.shinobi.learned"));
            } else {
                tip.add(Text.translatable("menu.shinobi.cost", hovered.cost));
                if (hovered.requires != null && !learned(hovered.requires)) {
                    tip.add(Text.translatable("menu.shinobi.requires", Text.translatable("skill.shinobi." + hovered.requires.id)));
                }
            }
            ctx.drawTooltip(textRenderer, tip, mx, my);
        }
    }

    private Skill drawTree(DrawContext ctx, int mx, int my) {
        // Заголовки путей
        branchTitle(ctx, "branch.shinobi.blade", 1);
        branchTitle(ctx, "branch.shinobi.shadow", 4);
        branchTitle(ctx, "branch.shinobi.prosthetic", 7);
        for (int c = 3; c <= 6; c += 3) {
            int x = left + 18 + c * GAP_X - 6;
            ctx.fill(x, top + 48, x + 1, top + H - 36, 0x44E8D6A0);
        }

        // Связи
        for (Skill s : Skill.values()) {
            if (s.requires == null) continue;
            int x1 = nx(s.requires) + NODE / 2;
            int y1 = ny(s.requires) + NODE;
            int x2 = nx(s) + NODE / 2;
            int y2 = ny(s);
            int ym = (y1 + y2) / 2;
            int col = learned(s) ? 0xFFD8B85A : (learned(s.requires) ? 0xFF9A8A64 : 0xFF3A3328);
            ctx.fill(x1 - 1, y1, x1 + 1, ym + 1, col);
            ctx.fill(Math.min(x1, x2) - 1, ym, Math.max(x1, x2) + 1, ym + 2, col);
            ctx.fill(x2 - 1, ym, x2 + 1, y2, col);
        }

        // Узлы
        Skill hovered = null;
        for (Skill s : Skill.values()) {
            int x = nx(s);
            int y = ny(s);
            boolean isLearned = learned(s);
            boolean isAvail = available(s);
            int border = isLearned ? 0xFFD8B85A : (isAvail ? 0xFFEDEDED : 0xFF4A4A4A);
            ctx.fill(x - 2, y - 2, x + NODE + 2, y + NODE + 2, border);
            ctx.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, 0xFF17120D);
            ctx.fill(x, y, x + NODE, y + NODE, isLearned ? 0xFF3A2E14 : 0xFF241E16);
            ctx.drawItem(icon(s), x + 4, y + 4);
            if (!isLearned && !isAvail) ctx.fill(x, y, x + NODE, y + NODE, 0xAA000000);
            if (!isLearned) {
                boolean afford = ShinobiClient.skillPoints >= s.cost;
                Text c = Text.literal(String.valueOf(s.cost));
                ctx.drawTextWithShadow(textRenderer, c, x + NODE / 2 - textRenderer.getWidth(c) / 2, y + NODE + 3,
                        isAvail && afford ? 0xFFE8D6A0 : 0xFF8A3A32);
            }
            if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) hovered = s;
        }
        return hovered;
    }

    private void branchTitle(DrawContext ctx, String key, int col) {
        Text t = Text.translatable(key);
        int cx = left + 18 + col * GAP_X + NODE / 2;
        ctx.drawTextWithShadow(textRenderer, t, cx - textRenderer.getWidth(t) / 2, top + 40, 0xFFE8DCC0);
    }

    private void drawStatus(DrawContext ctx) {
        int x = left + 16;
        int y = top + 44;
        String[] keys = {"menu.shinobi.stat.attack", "menu.shinobi.stat.posture", "menu.shinobi.stat.resurrection", "menu.shinobi.stat.gourd"};
        Object[] vals = {ShinobiClient.attackPower, (int) ShinobiClient.postureMax, ShinobiClient.resurrections + "/" + ShinobiClient.resMax,
                ShinobiClient.gourd + "/" + ShinobiClient.gourdMax};
        for (int i = 0; i < keys.length; i++) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable(keys[i], vals[i]), x, y, 0xFFE8DCC0);
            y += 13;
        }
        y += 8;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("menu.shinobi.controls"), x, y, 0xFFD8B85A);
        y += 14;
        for (int i = 1; i <= 8; i++) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("menu.shinobi.controls." + i), x, y, 0xFFB8AE96);
            y += 12;
        }
    }

    // ------------------------------------------------------------------ клики

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 0 && button == 0) {
            for (Skill s : Skill.values()) {
                int x = nx(s);
                int y = ny(s);
                if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) {
                    if (available(s) && ShinobiClient.skillPoints >= s.cost) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(s.ordinal());
                        ClientPlayNetworking.send(ShinobiNet.LEARN, buf);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
