package com.shinobi.combat;

import com.shinobi.ShinobiUtil;
import com.shinobi.Tasks;
import com.shinobi.data.PlayerData;
import com.shinobi.data.PlayerRuntime;
import com.shinobi.data.ShinobiState;
import com.shinobi.item.KatanaItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Реализация боевых приёмов. */
public final class CombatArts {
    private CombatArts() {
    }

    public static void cast(ServerPlayerEntity p) {
        PlayerData d = ShinobiState.of(p);
        PlayerRuntime rt = PlayerRuntime.of(p);
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Art art = Art.byOrdinal(d.selectedArt);

        if (!d.has(art.ordinal())) {
            p.sendMessage(Text.translatable("msg.shinobi.art_locked"), true);
            return;
        }
        if (now < rt.artCooldownUntil) {
            return;
        }
        if (!(p.getMainHandStack().getItem() instanceof KatanaItem katana)) {
            p.sendMessage(Text.translatable("msg.shinobi.no_katana"), true);
            return;
        }
        if (!ShinobiUtil.hasEmblems(p, art.emblemCost)) {
            p.sendMessage(Text.translatable("msg.shinobi.no_emblems"), true);
            return;
        }
        ShinobiUtil.takeEmblems(p, art.emblemCost);
        rt.artCooldownUntil = now + art.cooldown;

        float base = (float) p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float pm = katana.postureMult;
        p.swingHand(Hand.MAIN_HAND, true);

        switch (art) {
            case ICHIMONJI -> {
                slash(w, p);
                for (LivingEntity t : Combat.cone(w, p, 4.2, 0.45)) {
                    Combat.hit(p, t, base * 2.4f, 38f * pm);
                    knock(p, t, 0.8);
                }
            }
            case ASHINA_CROSS -> {
                slash(w, p);
                for (LivingEntity t : Combat.cone(w, p, 3.8, 0.4)) {
                    Combat.hit(p, t, base * 1.5f, 18f * pm);
                }
                Tasks.later(p.getServer(), 6, () -> {
                    if (!p.isAlive()) return;
                    slash(w, p);
                    for (LivingEntity t : Combat.cone(w, p, 3.8, 0.4)) {
                        Combat.hit(p, t, base * 1.7f, 24f * pm);
                        knock(p, t, 0.5);
                    }
                });
            }
            case WHIRLWIND -> {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.8f);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    Combat.fx(w, ParticleTypes.SWEEP_ATTACK, p.getPos().add(Math.cos(a) * 2.5, 1.0, Math.sin(a) * 2.5), 1, 0, 0);
                }
                for (LivingEntity t : Combat.around(w, p, 3.8)) {
                    Combat.hit(p, t, base * 1.4f, 24f * pm);
                    knock(p, t, 0.7);
                }
            }
            case NIGHTJAR -> {
                Vec3d dir = ShinobiUtil.horizontalLook(p);
                rt.dashInvulUntil = now + 6;
                for (LivingEntity t : Combat.cone(w, p, 5.5, 0.7)) {
                    Combat.hit(p, t, base * 1.9f, 26f * pm);
                }
                ShinobiUtil.velocity(p, dir.x * 1.6, 0.15, dir.z * 1.6);
                slash(w, p);
            }
            case MORTAL_DRAW -> {
                rt.mortalDrawUntil = now + 22;
                rt.mortalDrawDamage = base * 3.2f;
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0f, 1.4f);
                Combat.fx(w, ParticleTypes.ENCHANTED_HIT, p.getPos().add(0, 1, 0), 20, 0.5, 0.1);
            }
            case SPIRAL_CLOUD -> {
                Vec3d dir = ShinobiUtil.horizontalLook(p);
                rt.dashInvulUntil = now + 12;
                ShinobiUtil.velocity(p, dir.x * 0.5, 0.95, dir.z * 0.5);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.0f, 1.2f);
                Tasks.later(p.getServer(), 9, () -> {
                    if (!p.isAlive()) return;
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8;
                        Combat.fx(w, ParticleTypes.SWEEP_ATTACK, p.getPos().add(Math.cos(a) * 2.5, 0.5, Math.sin(a) * 2.5), 1, 0, 0);
                    }
                    for (LivingEntity t : Combat.around(w, p, 4.2)) {
                        Combat.hit(p, t, base * 1.7f, 32f * pm);
                        knock(p, t, 0.6);
                    }
                    ShinobiUtil.velocity(p, 0, -0.6, 0);
                });
            }
            case DRAGON_FLASH -> {
                Vec3d dir = ShinobiUtil.horizontalLook(p);
                Vec3d from = p.getEyePos();
                Combat.trail(w, ParticleTypes.END_ROD, from, from.add(dir.multiply(7)));
                for (LivingEntity t : Combat.cone(w, p, 7.0, 0.93)) {
                    Combat.hit(p, t, base * 2.8f, 46f * pm);
                    knock(p, t, 0.9);
                }
                rt.dashInvulUntil = now + 5;
                ShinobiUtil.velocity(p, dir.x * 1.8, 0.1, dir.z * 1.8);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.4f, 2.0f);
            }
        }
    }

    private static void slash(ServerWorld w, ServerPlayerEntity p) {
        Vec3d f = ShinobiUtil.horizontalLook(p);
        Vec3d at = p.getEyePos().add(f.multiply(1.8)).add(0, -0.4, 0);
        Combat.fx(w, ParticleTypes.SWEEP_ATTACK, at, 1, 0, 0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void knock(ServerPlayerEntity p, LivingEntity t, double strength) {
        double dx = p.getX() - t.getX();
        double dz = p.getZ() - t.getZ();
        t.takeKnockback(strength, dx, dz);
    }

    /** Переключить выбранный приём на следующий изученный. */
    public static void cycle(ServerPlayerEntity p) {
        PlayerData d = ShinobiState.of(p);
        int n = Art.values().length;
        for (int i = 1; i <= n; i++) {
            int idx = (d.selectedArt + i) % n;
            if (d.has(idx)) {
                d.selectedArt = idx;
                break;
            }
        }
        p.sendMessage(Text.translatable("msg.shinobi.art_selected", Text.translatable("art.shinobi." + Art.byOrdinal(d.selectedArt).id)), true);
    }

    @SuppressWarnings("unused")
    private static List<LivingEntity> none() {
        return List.of();
    }
}
