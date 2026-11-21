package com.shinobi.net;

import com.shinobi.ShinobiMod;
import com.shinobi.ShinobiUtil;
import com.shinobi.combat.CombatArts;
import com.shinobi.combat.PostureSystem;
import com.shinobi.data.PlayerData;
import com.shinobi.data.PlayerRuntime;
import com.shinobi.data.ShinobiState;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class ShinobiNet {
    public static final Identifier SYNC = ShinobiMod.id("sync");
    public static final Identifier DASH = ShinobiMod.id("dash");
    public static final Identifier ART = ShinobiMod.id("art");
    public static final Identifier CYCLE = ShinobiMod.id("cycle");

    private ShinobiNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(DASH, (server, player, handler, buf, responseSender) -> {
            double x = buf.readDouble();
            double z = buf.readDouble();
            server.execute(() -> dash(player, x, z));
        });
        ServerPlayNetworking.registerGlobalReceiver(ART, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    if (player.isAlive() && !player.isSpectator()) CombatArts.cast(player);
                    sync(player);
                }));
        ServerPlayNetworking.registerGlobalReceiver(CYCLE, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    CombatArts.cycle(player);
                    ShinobiState.save(player);
                    sync(player);
                }));
    }

    /** Рывок / уклонение с кадрами неуязвимости. */
    private static void dash(ServerPlayerEntity p, double x, double z) {
        if (!p.isAlive() || p.isSpectator()) return;
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        PlayerRuntime rt = PlayerRuntime.of(p);
        if (now < rt.dashCooldownUntil || PostureSystem.isBroken(p)) return;
        Vec3d dir = new Vec3d(x, 0, z);
        if (dir.lengthSquared() < 1.0E-4 || !Double.isFinite(dir.lengthSquared())) return;
        dir = dir.normalize();

        rt.dashCooldownUntil = now + 22;
        rt.dashTick = now;
        rt.dashInvulUntil = now + 8;
        double power = p.isOnGround() ? 1.05 : 0.7;
        ShinobiUtil.velocity(p, dir.x * power, p.isOnGround() ? 0.18 : p.getVelocity().y, dir.z * power);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5f, 1.6f);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.25, 0.05, 0.25, 0.02);
    }

    /** Отправить игроку его данные для HUD. */
    public static void sync(ServerPlayerEntity p) {
        PlayerData d = ShinobiState.of(p);
        PlayerRuntime rt = PlayerRuntime.of(p);
        long now = p.getWorld().getTime();

        float tPosture = 0, tMax = 0;
        boolean tBroken = false, tBoss = false;
        if (rt.lastTarget != null && now - rt.lastTargetTick < 120) {
            Entity e = p.getServerWorld().getEntity(rt.lastTarget);
            if (e instanceof LivingEntity le && le.isAlive()) {
                tPosture = PostureSystem.get(le);
                tMax = PostureSystem.max(le);
                tBroken = PostureSystem.isBroken(le);
                tBoss = PostureSystem.isBoss(le);
            }
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(PostureSystem.get(p));
        buf.writeFloat(PostureSystem.max(p));
        buf.writeBoolean(PostureSystem.isBroken(p));
        buf.writeInt(d.resurrections);
        buf.writeInt(d.maxResurrections());
        buf.writeInt(d.gourdUses);
        buf.writeInt(d.maxGourd());
        buf.writeInt(d.skillPoints);
        buf.writeInt(d.unlocked);
        buf.writeInt(d.selectedArt);
        buf.writeInt(d.attackPower);
        buf.writeFloat(tPosture);
        buf.writeFloat(tMax);
        buf.writeBoolean(tBroken);
        buf.writeBoolean(tBoss);
        buf.writeBoolean(p.hasStatusEffect(StatusEffects.SPEED));
        ServerPlayNetworking.send(p, SYNC, buf);
    }
}
