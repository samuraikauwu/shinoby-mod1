package com.shinobi.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Общие боевые помощники. */
public final class Combat {
    /** true — урон наносится «изнутри» мода и не должен проходить через отражение/осанку. */
    public static boolean internal = false;

    private Combat() {
    }

    /** Нанести удар игрока: урон + урон по осанке. */
    public static void hit(ServerPlayerEntity p, LivingEntity t, float dmg, float posture) {
        internal = true;
        try {
            t.timeUntilRegen = 0;
            t.damage(p.getDamageSources().playerAttack(p), dmg);
        } finally {
            internal = false;
        }
        PostureSystem.add(t, posture, t.getWorld().getTime());
    }

    public static boolean canTarget(LivingEntity src, LivingEntity e) {
        if (e == src || !e.isAlive()) return false;
        if (e instanceof TameableEntity t && src instanceof PlayerEntity pl && t.isOwner(pl)) return false;
        if (e instanceof PlayerEntity pe) {
            if (pe.isCreative() || pe.isSpectator()) return false;
            if (src.getServer() != null && !src.getServer().isPvpEnabled()) return false;
        }
        return true;
    }

    /** Цели в горизонтальном конусе перед источником. cos — минимальный косинус угла. */
    public static List<LivingEntity> cone(ServerWorld w, LivingEntity src, double range, double cos) {
        Vec3d look = src.getRotationVec(1f);
        Vec3d fwd = new Vec3d(look.x, 0, look.z);
        fwd = fwd.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : fwd.normalize();
        Vec3d origin = src.getPos();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, src.getBoundingBox().expand(range, 1.5, range), x -> canTarget(src, x))) {
            Vec3d to = new Vec3d(e.getX() - origin.x, 0, e.getZ() - origin.z);
            double d = to.length();
            if (d > range + e.getWidth() * 0.5) continue;
            if (d < 0.6 || to.normalize().dotProduct(fwd) >= cos) out.add(e);
        }
        return out;
    }

    /** Цели вокруг источника в радиусе. */
    public static List<LivingEntity> around(ServerWorld w, LivingEntity src, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, src.getBoundingBox().expand(radius, 1.5, radius), x -> canTarget(src, x))) {
            if (e.distanceTo(src) <= radius) out.add(e);
        }
        return out;
    }

    /** Первая живая цель по лучу. */
    public static LivingEntity rayEntity(ServerWorld w, LivingEntity src, Vec3d dir, double range) {
        Vec3d start = src.getEyePos();
        Vec3d end = start.add(dir.multiply(range));
        BlockHitResult bhr = w.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, src));
        if (bhr.getType() != HitResult.Type.MISS) end = bhr.getPos();
        Box box = new Box(start, end).expand(1.0);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> canTarget(src, x))) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(start, end);
            if (hit.isPresent()) {
                double d = start.squaredDistanceTo(hit.get());
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /** Нарисовать линию частиц. */
    public static void trail(ServerWorld w, ParticleEffect fx, Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        Vec3d step = d.normalize().multiply(0.6);
        Vec3d pos = from;
        for (double t = 0; t < len; t += 0.6) {
            w.spawnParticles(fx, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            pos = pos.add(step);
        }
    }

    public static void fx(ServerWorld w, ParticleEffect fx, Vec3d pos, int count, double spread, double speed) {
        w.spawnParticles(fx, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }
}
