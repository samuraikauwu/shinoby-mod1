package com.shinobi.combat;

import com.shinobi.data.PlayerRuntime;
import com.shinobi.entity.ShinobiBossEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Опасные атаки (危).
 * THRUST — выпад: ответить уклонением (Микири-контратака).
 * SWEEP  — подсечка: перепрыгнуть.
 * GRAB   — захват: уклониться рывком.
 */
public final class PerilousSystem {
    public enum Type {THRUST, SWEEP, GRAB}

    private static final class Telegraph {
        Type type;
        int ticksLeft;
        UUID target;
        long startTick;
    }

    private static final Map<UUID, Telegraph> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    /** true — идёт расчёт опасной атаки; урон не блокируется. */
    public static boolean resolving = false;

    private PerilousSystem() {
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();

        if (now % 10 == 0) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isSpectator() || player.isCreative()) continue;
                List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class,
                        player.getBoundingBox().expand(8), m -> m.isAlive()
                                && m.getTarget() == player
                                && !ACTIVE.containsKey(m.getUuid())
                                && m.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE));
                for (HostileEntity m : mobs) {
                    if (PostureSystem.isBroken(m)) continue;
                    long cd = COOLDOWN.getOrDefault(m.getUuid(), 0L);
                    if (now < cd) continue;
                    double dist = m.distanceTo(player);
                    if (dist > 5.0) continue;
                    boolean boss = PostureSystem.isBoss(m);
                    float chance = boss ? 0.25f : 0.07f;
                    if (world.random.nextFloat() > chance) continue;
                    Type type = Type.values()[world.random.nextInt(Type.values().length)];
                    if (type == Type.GRAB && dist > 3.4) type = Type.THRUST;
                    start(world, m, player, type, now);
                }
            }
        }

        Iterator<Map.Entry<UUID, Telegraph>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Telegraph> en = it.next();
            Telegraph t = en.getValue();
            if (!(world.getEntity(en.getKey()) instanceof HostileEntity mob)) {
                continue; // сущность в другом мире/выгружена — обработает её мир или очистка
            }
            if (!mob.isAlive() || PostureSystem.isBroken(mob)) {
                pose(mob, 0);
                it.remove();
                continue;
            }
            if (!(world.getEntity(t.target) instanceof ServerPlayerEntity target) || !target.isAlive()) {
                pose(mob, 0);
                it.remove();
                continue;
            }
            t.ticksLeft--;
            mob.getNavigation().stop();
            mob.getLookControl().lookAt(target, 60f, 60f);
            if (t.ticksLeft % 2 == 0) {
                Vec3d head = mob.getPos().add(0, mob.getHeight() + 0.5, 0);
                world.spawnParticles(DustParticleEffect.DEFAULT, head.x, head.y, head.z, 4, 0.25, 0.1, 0.25, 0);
            }
            if (t.ticksLeft <= 0) {
                pose(mob, 0);
                resolve(world, mob, target, t);
                COOLDOWN.put(mob.getUuid(), now + (PostureSystem.isBoss(mob) ? 90 : 160));
                it.remove();
            }
        }
    }

    private static void start(ServerWorld world, HostileEntity mob, ServerPlayerEntity target, Type type, long now) {
        Telegraph t = new Telegraph();
        t.type = type;
        t.ticksLeft = type == Type.GRAB ? 34 : 28;
        t.target = target.getUuid();
        t.startTick = now;
        ACTIVE.put(mob.getUuid(), t);
        pose(mob, 1 + type.ordinal());
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, t.ticksLeft + 4, 3));
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        com.shinobi.net.ShinobiNet.perilous(target, type.ordinal());
    }

    private static void resolve(ServerWorld world, HostileEntity mob, ServerPlayerEntity target, Telegraph t) {
        double reach = (t.type == Type.GRAB ? 3.6 : 4.4) + (PostureSystem.isBoss(mob) ? 1.0 : 0.0);
        long now = world.getTime();
        PlayerRuntime rt = PlayerRuntime.of(target);

        boolean dashed = (now - rt.dashTick) <= 16 || rt.dashInvulUntil > now;
        double dist = mob.distanceTo(target);

        // Рывок в сторону врага при ударе
        Vec3d to = target.getPos().subtract(mob.getPos());
        Vec3d dir = new Vec3d(to.x, 0, to.z);
        if (dir.lengthSquared() > 1.0E-4) {
            dir = dir.normalize();
            mob.setVelocity(dir.x * 0.45, 0.1, dir.z * 0.45);
            mob.velocityModified = true;
        }

        if (dist > reach + (dashed ? 2.5 : 0)) {
            return; // игрок вне досягаемости
        }

        // Контрприёмы
        if (dashed && t.type == Type.THRUST) {
            counter(world, mob, target, 60f, "msg.shinobi.mikiri");
            return;
        }
        if (dashed && t.type == Type.GRAB) {
            PostureSystem.add(mob, 20f, now);
            target.sendMessage(Text.translatable("msg.shinobi.dodged"), true);
            return;
        }
        if (t.type == Type.SWEEP && !target.isOnGround() && target.getVelocity().y > -0.8) {
            counter(world, mob, target, 50f, "msg.shinobi.jumped");
            return;
        }

        // Удар проходит — блокировать нельзя
        if (dist > reach) return;
        float dmg = 6f;
        if (mob.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            dmg = Math.max(6f, (float) mob.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 2.2f);
        }
        if (t.type == Type.GRAB) dmg *= 1.3f;
        resolving = true;
        try {
            target.damage(mob.getDamageSources().mobAttack(mob), dmg);
        } finally {
            resolving = false;
        }
        PostureSystem.add(target, 25f, now);
    }

    private static void counter(ServerWorld world, HostileEntity mob, ServerPlayerEntity target, float posture, String msgKey) {
        long now = world.getTime();
        PlayerRuntime rt = PlayerRuntime.of(target);
        rt.resInvulUntil = Math.max(rt.resInvulUntil, now + 6);
        PostureSystem.add(mob, posture, now);
        target.sendMessage(Text.translatable(msgKey), true);
        Vec3d c = mob.getPos().add(0, mob.getHeight() * 0.5, 0);
        Combat.fx(world, ParticleTypes.CRIT, c, 25, 0.5, 0.3);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2f, 0.7f);
    }

    private static void pose(HostileEntity m, int v) {
        if (m instanceof ShinobiBossEntity b && !PostureSystem.isBroken(m)) b.setAnim(v);
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id);
        COOLDOWN.remove(id);
    }

    @SuppressWarnings("unused")
    private static LivingEntity unused() {
        return null;
    }
}
