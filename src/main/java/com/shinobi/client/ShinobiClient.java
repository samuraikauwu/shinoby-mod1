package com.shinobi.client;

import com.shinobi.ModEntities;
import com.shinobi.combat.Art;
import com.shinobi.net.ShinobiNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
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
    static KeyBinding skillsKey;

    // Данные HUD (приходят с сервера)
    static float posture, postureMax = 100, tPosture, tMax;
    static boolean broken, tBroken, tBoss, sugar;
    static float tHealth;
    static int tSegments, perilousTicks, perilousType;
    public static int dashAnim;
    static String tName = "";
    static int passives;
    static int resurrections = 2, resMax = 2, gourd = 3, gourdMax = 3, skillPoints, unlocked = 1, selectedArt, attackPower;

    @Override
    public void onInitializeClient() {
        dashKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));
        artKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.art", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.cycle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

        skillsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.shinobi.skills", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

        ClientPlayNetworking.registerGlobalReceiver(ShinobiNet.SYNC, (client, handler, buf, sender) -> {
            float a = buf.readFloat(), b = buf.readFloat();
            boolean c = buf.readBoolean();
            int r = buf.readInt(), rm = buf.readInt(), g = buf.readInt(), gm = buf.readInt(), sp = buf.readInt(),
                    un = buf.readInt(), sel = buf.readInt(), ap = buf.readInt();
            float tp = buf.readFloat(), tm = buf.readFloat();
            boolean tb = buf.readBoolean(), tbo = buf.readBoolean(), su = buf.readBoolean();
            float th = buf.readFloat();
            int ts = buf.readInt();
            String tn = buf.readString();
            int pv = buf.readInt();
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
                tHealth = th;
                tSegments = ts;
                tName = tn;
                passives = pv;
            });
        });

        EntityModelLayerRegistry.registerModelLayer(ShinobiBossModel.LAYER, ShinobiBossModel::createModelData);
        EntityRendererRegistry.register(ModEntities.LORD, ctx -> new ShinobiBossRenderer(ctx, "lord", 1.05f));
        EntityRendererRegistry.register(ModEntities.SHADOW, ctx -> new ShinobiBossRenderer(ctx, "shadow", 1.0f));
        EntityRendererRegistry.register(ModEntities.SAINT, ctx -> new ShinobiBossRenderer(ctx, "saint", 1.15f));
        EntityRendererRegistry.register(ModEntities.ONI, ctx -> new ShinobiBossRenderer(ctx, "oni", 1.5f));

        ClientPlayNetworking.registerGlobalReceiver(ShinobiNet.PERILOUS, (client, handler, buf, sender) -> {
            int type = buf.readInt();
            client.execute(() -> {
                perilousType = type;
                perilousTicks = 26;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ShinobiNet.CINEMATIC, (client, handler, buf, sender) -> {
            int kind = buf.readInt();
            String title = buf.readString();
            String sub = buf.readString();
            int ticks = buf.readInt();
            client.execute(() -> ShinobiCinematic.start(kind, title, sub, ticks));
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (screen instanceof net.minecraft.client.gui.screen.DeathScreen) {
                long born = net.minecraft.util.Util.getMeasuringTimeMs();
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, d) ->
                        ShinobiDeath.render(ctx, sw, sh, (net.minecraft.util.Util.getMeasuringTimeMs() - born) / 1000f));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ShinobiClient::tick);
        HudRenderCallback.EVENT.register(ShinobiHud::render);
    }

    private static void tick(MinecraftClient mc) {
        if (perilousTicks > 0) perilousTicks--;
        if (dashAnim > 0) dashAnim--;
        ShinobiCinematic.tick();
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
            dashAnim = 8;
        }
        while (skillsKey.wasPressed()) {
            mc.setScreen(new ShinobiSkillScreen());
        }
        while (artKey.wasPressed()) {
            ClientPlayNetworking.send(ShinobiNet.ART, PacketByteBufs.create());
        }
        while (cycleKey.wasPressed()) {
            ClientPlayNetworking.send(ShinobiNet.CYCLE, PacketByteBufs.create());
        }
    }
}
