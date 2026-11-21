package com.shinobi.item;

import com.shinobi.ShinobiUtil;
import com.shinobi.combat.Combat;
import com.shinobi.combat.PostureSystem;
import com.shinobi.data.PlayerRuntime;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/** Реализация инструментов протеза шиноби. */
public final class ProstheticTools {
    private ProstheticTools() {
    }

    public enum Tool {
        SHURIKEN("shuriken_wheel", 1, 10),
        FIRECRACKER("firecracker", 1, 40),
        FLAME_VENT("flame_vent", 1, 30),
        AXE("loaded_axe", 2, 40),
        UMBRELLA("loaded_umbrella", 2, 80),
        SPEAR("loaded_spear", 2, 30),
        SABIMARU("sabimaru", 1, 100),
        MIST_RAVEN("mist_raven", 1, 30),
        ABDUCTION("divine_abduction", 2, 120),
        WHISTLE("finger_whistle", 0, 100),
        SUGAR("gachiin_sugar", 1, 200),
        GRAPPLE("grappling_hook", 0, 20);

        public final String id;
        public final int cost;
        public final int cooldown;

        Tool(String id, int cost, int cooldown) {
            this.id = id;
            this.cost = cost;
            this.cooldown = cooldown;
        }
    }

    /** @return true, если инструмент сработал (эмблемы будут списаны). */
    public static boolean activate(Tool tool, ServerWorld w, ServerPlayerEntity p, int level) {
        PlayerRuntime rt = PlayerRuntime.of(p);
        long now = w.getTime();
        float scale = 1f + 0.25f * level + (com.shinobi.combat.Skill.has(p, com.shinobi.combat.Skill.TOOL_POWER) ? 0.15f : 0f);
        Vec3d look = p.getRotationVec(1f);
        Vec3d eye = p.getEyePos();

        switch (tool) {
            case SHURIKEN -> {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 1.6f);
                for (int i = -1; i <= 1; i++) {
                    Vec3d dir = Vec3d.fromPolar(p.getPitch(), p.getYaw() + i * 9f);
                    Combat.trail(w, ParticleTypes.CRIT, eye.add(0, -0.2, 0), eye.add(dir.multiply(16)));
                    LivingEntity t = Combat.rayEntity(w, p, dir, 16);
                    if (t != null) Combat.hit(p, t, 3f * scale, 8f * scale);
                }
                return true;
            }
            case FIRECRACKER -> {
                Vec3d c = p.getPos().add(look.x * 2, 1, look.z * 2);
                Combat.fx(w, ParticleTypes.FLASH, c, 1, 0, 0);
                Combat.fx(w, ParticleTypes.EXPLOSION, c, 3, 0.8, 0);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.8f);
                for (LivingEntity t : Combat.around(w, p, 6.0 + level)) {
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60 + 10 * level, 0));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 2));
                    if (t instanceof MobEntity m) m.setTarget(null);
                    PostureSystem.add(t, 25f * scale, now);
                }
                return true;
            }
            case FLAME_VENT -> {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.8f);
                for (int i = 1; i <= 5; i++) {
                    Combat.fx(w, ParticleTypes.FLAME, eye.add(look.multiply(i)), 8, 0.35, 0.02);
                }
                for (LivingEntity t : Combat.cone(w, p, 5.0, 0.6)) {
                    t.setOnFireFor(5 + level);
                    Combat.hit(p, t, 2f * scale, 10f * scale);
                }
                return true;
            }
            case AXE -> {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2f, 0.6f);
                Combat.fx(w, ParticleTypes.SWEEP_ATTACK, eye.add(look.multiply(2)), 1, 0, 0);
                for (LivingEntity t : Combat.cone(w, p, 3.6, 0.5)) {
                    Combat.hit(p, t, 7f * scale, 40f * scale);
                    double dx = p.getX() - t.getX();
                    double dz = p.getZ() - t.getZ();
                    t.takeKnockback(0.9, dx, dz);
                }
                return true;
            }
            case UMBRELLA -> {
                rt.umbrellaUntil = now + 30 + 6L * level;
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 0.7f);
                Combat.fx(w, ParticleTypes.ENCHANTED_HIT, p.getPos().add(0, 1, 0), 20, 0.7, 0.1);
                return true;
            }
            case SPEAR -> {
                Vec3d dir = look;
                Combat.trail(w, ParticleTypes.CRIT, eye, eye.add(dir.multiply(6)));
                LivingEntity t = Combat.rayEntity(w, p, dir, 6.5);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.2f);
                if (t != null) {
                    Combat.hit(p, t, 6f * scale, 20f * scale);
                    Vec3d h = ShinobiUtil.horizontalLook(p);
                    ShinobiUtil.velocity(p, h.x * 0.8, 0.2, h.z * 0.8);
                }
                return true;
            }
            case SABIMARU -> {
                rt.sabimaruUntil = now + 20L * (25 + 5 * level);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.PLAYERS, 1.0f, 1.2f);
                Combat.fx(w, ParticleTypes.ITEM_SLIME, p.getPos().add(0, 1, 0), 15, 0.4, 0.05);
                p.sendMessage(Text.translatable("msg.shinobi.sabimaru"), true);
                return true;
            }
            case MIST_RAVEN -> {
                Vec3d h = ShinobiUtil.horizontalLook(p);
                ShinobiUtil.velocity(p, h.x * 0.9, 1.0, h.z * 0.9);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.0f, 0.8f);
                Combat.fx(w, ParticleTypes.CLOUD, p.getPos(), 20, 0.4, 0.05);
                return true;
            }
            case ABDUCTION -> {
                Vec3d lure = p.getPos().add(ShinobiUtil.horizontalLook(p).multiply(7));
                for (LivingEntity t : Combat.around(w, p, 14.0)) {
                    if (t instanceof MobEntity m) {
                        m.setTarget(null);
                        m.getNavigation().startMovingTo(lure.x, lure.y, lure.z, 1.0);
                        m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
                    }
                }
                Combat.fx(w, ParticleTypes.SOUL_FIRE_FLAME, lure.add(0, 1, 0), 25, 0.5, 0.02);
                w.playSound(null, lure.x, lure.y, lure.z, SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.0f, 0.6f);
                return true;
            }
            case WHISTLE -> {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 2.0f);
                for (LivingEntity t : Combat.around(w, p, 24.0)) {
                    if (t instanceof MobEntity m) {
                        m.getNavigation().startMovingTo(p.getX(), p.getY(), p.getZ(), 1.1);
                    }
                }
                return true;
            }
            case SUGAR -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * (30 + 10 * level), 1));
                rt.sugarUntil = now + 20L * (30 + 10 * level);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.0f, 1.4f);
                return true;
            }
            case GRAPPLE -> {
                HitResult hr = p.raycast(32, 0f, false);
                if (hr.getType() != HitResult.Type.BLOCK) {
                    p.sendMessage(Text.translatable("msg.shinobi.no_target"), true);
                    return false;
                }
                rt.grappleTarget = hr.getPos();
                rt.grappleUntil = now + 45;
                Combat.trail(w, ParticleTypes.END_ROD, eye, hr.getPos());
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.3f);
                return true;
            }
        }
        return false;
    }
}
