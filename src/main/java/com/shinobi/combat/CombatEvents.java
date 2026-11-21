package com.shinobi.combat;

import com.shinobi.ModItems;
import com.shinobi.ShinobiMod;
import com.shinobi.data.PlayerData;
import com.shinobi.data.PlayerRuntime;
import com.shinobi.data.ShinobiState;
import com.shinobi.item.KatanaItem;
import com.shinobi.net.ShinobiNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Обработчики боевых событий: отражение, блок, уклонение, воскрешение, добивание. */
public final class CombatEvents {
    /** Окно идеального отражения (тики с начала удержания блока). */
    public static final int DEFLECT_WINDOW = 8;

    public static int window(ServerPlayerEntity p) {
        return DEFLECT_WINDOW + (Skill.has(p, Skill.DEFLECT_WINDOW) ? 3 : 0);
    }

    private CombatEvents() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatEvents::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(CombatEvents::allowDeath);
        AttackEntityCallback.EVENT.register(CombatEvents::onAttack);
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(CombatEvents::afterKill);
    }

    // ------------------------------------------------------------------ урон

    private static boolean frontal(LivingEntity defender, DamageSource source) {
        Entity src = source.getSource() != null ? source.getSource() : source.getAttacker();
        if (src == null) return false;
        Vec3d look = defender.getRotationVec(1f);
        Vec3d to = src.getPos().subtract(defender.getPos());
        if (to.lengthSquared() < 1.0E-6) return true;
        return look.dotProduct(to.normalize()) > 0.15;
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        World world = entity.getWorld();
        if (world.isClient) return true;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        long now = world.getTime();

        // ----- Состояния неуязвимости игрока -----
        if (entity instanceof ServerPlayerEntity p) {
            PlayerRuntime rt = PlayerRuntime.of(p);
            if (rt.dashInvulUntil > now || rt.resInvulUntil > now) {
                spark((ServerWorld) world, p, false);
                return false;
            }
            if (rt.umbrellaUntil > now) {
                spark((ServerWorld) world, p, true);
                Entity atk = source.getAttacker();
                if (atk instanceof LivingEntity la) PostureSystem.add(la, amount * 2f + 10f, now);
                return false;
            }
        }

        if (Combat.internal || PerilousSystem.resolving) return true;

        Entity attacker = source.getAttacker();

        // ----- Защита игрока -----
        if (entity instanceof ServerPlayerEntity p) {
            PlayerRuntime rt = PlayerRuntime.of(p);

            // Смертельный контрудар (Mortal Draw)
            if (rt.mortalDrawUntil > now && attacker instanceof LivingEntity la && frontal(p, source)) {
                rt.mortalDrawUntil = 0;
                spark((ServerWorld) world, p, true);
                Combat.hit(p, la, rt.mortalDrawDamage, 60f);
                p.sendMessage(Text.translatable("msg.shinobi.counter"), true);
                return false;
            }

            if (p.isUsingItem() && p.getActiveItem().getItem() instanceof KatanaItem k && frontal(p, source)) {
                int useTime = p.getItemUseTime();
                ServerWorld sw = (ServerWorld) world;
                if (useTime <= window(p)) {
                    // Идеальное отражение
                    spark(sw, p, true);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.2f, 1.8f);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.4f, 2.0f);
                    if (attacker instanceof LivingEntity la) {
                        PostureSystem.add(la, (amount * 2f + 12f) * k.postureMult, now);
                    }
                    if (source.getSource() instanceof ProjectileEntity proj) {
                        proj.setVelocity(proj.getVelocity().multiply(-1.0));
                        proj.velocityModified = true;
                    }
                    PostureSystem.add(p, amount * 0.3f, now);
                    p.sendMessage(Text.translatable("msg.shinobi.deflect"), true);
                } else {
                    // Обычный блок: урон мал, осанка растёт быстро
                    spark(sw, p, false);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    PostureSystem.add(p, amount * 2.2f + 4f, now);
                    float chip = amount * 0.2f;
                    p.setHealth(Math.max(0.5f, p.getHealth() - chip));
                }
                return false;
            }

            // Обычный удар по игроку
            PostureSystem.add(p, amount * 1.5f, now);
            return true;
        }

        // ----- Удар игрока по мобу -----
        if (attacker instanceof ServerPlayerEntity pa && pa.getMainHandStack().getItem() instanceof KatanaItem k) {
            if (entity instanceof HostileEntity && !PostureSystem.isBroken(entity)) {
                float chance = PostureSystem.isBoss(entity) ? 0.45f : 0.22f;
                if (world.random.nextFloat() < chance) {
                    // Моб отражает удар
                    ServerWorld sw = (ServerWorld) world;
                    Vec3d c = entity.getPos().add(0, entity.getHeight() * 0.6, 0);
                    Combat.fx(sw, ParticleTypes.CRIT, c, 12, 0.3, 0.3);
                    sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0f, 1.3f);
                    PostureSystem.add(entity, amount * 2.5f + 8f, now);
                    PostureSystem.add(pa, 5f, now);
                    return false;
                }
            }
            PostureSystem.add(entity, amount * 1.3f * k.postureMult, now);
            return true;
        }

        // Любой другой урон
        if (entity instanceof MobEntity) {
            PostureSystem.add(entity, amount * 0.8f, now);
        }
        return true;
    }

    private static void spark(ServerWorld w, LivingEntity e, boolean big) {
        Vec3d c = e.getPos().add(0, e.getHeight() * 0.6, 0);
        Combat.fx(w, ParticleTypes.CRIT, c, big ? 20 : 8, 0.3, 0.4);
        if (big) Combat.fx(w, ParticleTypes.ENCHANTED_HIT, c, 12, 0.3, 0.2);
    }

    // ------------------------------------------------------------------ смерть / воскрешение

    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity p)) return true;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        PlayerData d = ShinobiState.of(p);
        if (d.resurrections <= 0) return true;

        d.resurrections--;
        ServerWorld w = p.getServerWorld();
        p.setHealth(p.getMaxHealth() * 0.5f);
        p.clearStatusEffects();
        PostureSystem.reset(p);
        PlayerRuntime.of(p).resInvulUntil = w.getTime() + 60;
        Combat.fx(w, ParticleTypes.TOTEM_OF_UNDYING, p.getPos().add(0, 1, 0), 60, 0.5, 0.5);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        p.sendMessage(Text.translatable("msg.shinobi.resurrected", d.resurrections), true);
        ShinobiState.save(p);
        ShinobiNet.sync(p);
        return false;
    }

    // ------------------------------------------------------------------ атака игрока

    private static ActionResult onAttack(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand, Entity entity,
                                         net.minecraft.util.hit.EntityHitResult hit) {
        if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp) || sp.isSpectator()) return ActionResult.PASS;
        if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

        PlayerRuntime rt = PlayerRuntime.of(sp);
        long now = world.getTime();
        rt.lastTarget = target.getUuid();
        rt.lastTargetTick = now;

        if (rt.sabimaruUntil > now) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 1), sp);
        }

        ItemStack held = sp.getMainHandStack();
        if (held.getItem() instanceof KatanaItem && !(target instanceof net.minecraft.entity.player.PlayerEntity)) {
            // Добивание сломанной осанки
            if (PostureSystem.isBroken(target)) {
                PostureSystem.deathblow(sp, target, false);
                return ActionResult.FAIL;
            }
            // Скрытное добивание со спины
            if (sp.isSneaking() && target instanceof MobEntity m && m.getTarget() == null) {
                Vec3d facing = m.getRotationVec(1f);
                Vec3d toPlayer = sp.getPos().subtract(m.getPos());
                if (toPlayer.lengthSquared() > 1.0E-6 && facing.dotProduct(toPlayer.normalize()) < -0.2) {
                    PostureSystem.deathblow(sp, target, true);
                    return ActionResult.FAIL;
                }
            }
        }
        return ActionResult.PASS;
    }

    // ------------------------------------------------------------------ убийства

    private static void afterKill(ServerWorld world, Entity killer, LivingEntity killed) {
        PostureSystem.forget(killed.getUuid());
        PerilousSystem.forget(killed.getUuid());
        boolean boss = PostureSystem.isBoss(killed);
        if (boss) BossManager.drops(killed);

        if (killer instanceof ServerPlayerEntity p && (killed instanceof HostileEntity || boss)) {
            PlayerData d = ShinobiState.of(p);
            if (boss) {
                d.skillPoints += 3;
                d.resurrections = d.maxResurrections();
                p.sendMessage(Text.translatable("msg.shinobi.boss_defeated"), true);
            } else {
                d.killCounter++;
                if (d.killCounter >= 8) {
                    d.killCounter = 0;
                    d.skillPoints++;
                    p.sendMessage(Text.translatable("msg.shinobi.skill_point"), true);
                }
                if (world.random.nextFloat() < 0.15f) {
                    killed.dropStack(new ItemStack(ModItems.SPIRIT_EMBLEM));
                }
                if (world.random.nextFloat() < 0.03f) {
                    killed.dropStack(new ItemStack(ModItems.PRAYER_BEAD));
                }
            }
            ShinobiState.save(p);
            ShinobiNet.sync(p);
        }
    }

    @SuppressWarnings("unused")
    private static String mod() {
        return ShinobiMod.MOD_ID;
    }
}
