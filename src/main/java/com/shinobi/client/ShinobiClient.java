package com.shinobi.client;

import com.shinobi.combat.Art;
import com.shinobi.net.ShinobiNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class ShinobiClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.shinobi";

    static KeyBinding dashKey;
    static KeyBinding artKey;
    static KeyBinding cycleKey;

    // Данные HUD (приходят с сервера)
    static float posture, postureMax = 100, tPosture, tMax;
    static boolean broken, tBroken, tBoss, sugar;
    static int resurrections = 2, resMax = 2, gourd = 3, gourdMax = 3, skillPoints, unlocked = 1, selectedArt, attackPower;

    @Override
    public void onInitializeClient() {
        dashKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));
        artKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.art", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.cycle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

        ClientPlayNetworking.registerGlobalReceiver(ShinobiNet.SYNC, (client, handler, buf, sender) -> {
            float a = buf.readFloat(), b = buf.readFloat();
            boolean c = buf.readBoolean();
            int r = buf.readInt(), rm = buf.readInt(), g = buf.readInt(), gm = buf.readInt(), sp = buf.readInt(),
                    un = buf.readInt(), sel = buf.readInt(), ap = buf.readInt();
            float tp = buf.readFloat(), tm = buf.readFloat();
            boolean tb = buf.readBoolean(), tbo = buf.readBoolean(), su = buf.readBoolean();
            client.execute(() -> {
                posture = a;
                postureMax = Math.max(1f, b);
                broken = c;
                resurrections = r;
                resMax = rm;
                gourd = g;
                gourdMax = gm;
                skillPoints = sp;
                unlocked = un;
                selectedArt = sel;
                attackPower = ap;
                tPosture = tp;
                tMax = Math.max(1f, tm);
                tBroken = tb;
                tBoss = tbo;
                sugar = su;
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(ShinobiClient::tick);
        HudRenderCallback.EVENT.register(ShinobiClient::renderHud);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) return;

        while (dashKey.wasPressed()) {
            // Направление рывка по нажатым клавишам движения (если ничего не нажато — назад)
            float fwd = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
            float side = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
            if (fwd == 0 && side == 0) fwd = -1;
            double yaw = Math.toRadians(mc.player.getYaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double sx = Math.cos(yaw), sz = Math.sin(yaw);
            Vec3d dir = new Vec3d(fx * fwd + sx * side, 0, fz * fwd + sz * side);
            if (dir.lengthSquared() < 1.0E-6) continue;
            dir = dir.normalize();
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeDouble(dir.x);
            buf.writeDouble(dir.z);
            ClientPlayNetworking.send(ShinobiNet.DASH, buf);
        }
        while (artKey.wasPressed()) {
            ClientPlayNetworking.send(ShinobiNet.ART, PacketByteBufs.create());
        }
        while (cycleKey.wasPressed()) {
            ClientPlayNetworking.send(ShinobiNet.CYCLE, PacketByteBufs.create());
        }
    }

    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // --- Полоса осанки игрока (над хотбаром по центру) ---
        int barW = 120;
        int x = w / 2 - barW / 2;
        int y = h - 62;
        float frac = Math.min(1f, posture / postureMax);
        ctx.fill(x - 1, y - 1, x + barW + 1, y + 5, 0xAA000000);
        int color = broken ? 0xFFFF2222 : (frac > 0.7f ? 0xFFFFB020 : 0xFFE8E8E8);
        ctx.fill(x, y, x + (int) (barW * frac), y + 4, color);

        // --- Полоса осанки цели (вверху по центру) ---
        if (tMax > 1f && (tPosture > 0 || tBroken)) {
            int tw = tBoss ? 200 : 140;
            int tx = w / 2 - tw / 2;
            int ty = tBoss ? 36 : 14;
            float tf = Math.min(1f, tPosture / tMax);
            ctx.fill(tx - 1, ty - 1, tx + tw + 1, ty + 5, 0xAA000000);
            ctx.fill(tx, ty, tx + (int) (tw * tf), ty + 4, tBroken ? 0xFFFF2222 : 0xFFFFB020);
            if (tBroken) {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("hud.shinobi.deathblow"), w / 2, ty + 8, 0xFFFF4444);
            }
        }

        // --- Информация слева внизу ---
        int ix = 6;
        int iy = h - 56;
        Art art = Art.byOrdinal(selectedArt);
        boolean known = (unlocked & (1 << art.ordinal())) != 0;
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.shinobi.art", Text.translatable("art.shinobi." + art.id)), ix, iy, known ? 0xFFFFD966 : 0xFF888888);
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.shinobi.gourd", gourd, gourdMax), ix, iy + 10, 0xFFE0C080);
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.shinobi.resurrection", resurrections, resMax), ix, iy + 20, 0xFFFF8888);
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.shinobi.skill_points", skillPoints), ix, iy + 30, 0xFF88CCFF);
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.shinobi.attack_power", attackPower), ix, iy + 40, 0xFFCCCCCC);
    }
}
