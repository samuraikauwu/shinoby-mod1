package com.shinobi.combat;

import com.shinobi.data.PlayerData;
import com.shinobi.data.PlayerRuntime;
import com.shinobi.data.ShinobiState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Система осанки (posture) — ядро боевой механики Sekiro. */
public final class PostureSystem {
    public static final class State {
        public float value;
        public long lastHit;
        public int brokenTicks;
    }

    private static final Map<UUID, State> MAP = new HashMap<>();

    private PostureSystem() {
    }

    // ---------- Боссы ----------

    public static boolean isBoss(Entity e) {
        return e.getCommandTags().contains("shinobi_boss");
    }

    public static int segments(Entity e) {
        for (int n = 3; n >= 1; n--) {
            if (e.getCommandTags().contains("shinobi_seg" + n)) return n;
        }
        return 1;
    }

    public static void setSegments(Entity e, int n) {
        for (int i = 1; i <= 3; i++) e.removeCommandTag("shinobi_seg" + i);
        e.addCommandTag("shinobi_seg" + n);
    }

    // ---------- Состояние ----------

    public static float max(LivingEntity e) {
        if (e instanceof ServerPlayerEntity p) return ShinobiState.of(p).maxPosture();
        if (e instanceof PlayerEntity) return 100f;
        if (isBoss(e)) return 60f + e.getMaxHealth() * 0.6f;
        return 20f + e.getMaxHealth();
    }

    private static State state(LivingEntity e) {
        return MAP.computeIfAbsent(e.getUuid(), k -> new State());
    }

    public static State peek(Entity e) {
        return MAP.get(e.getUuid());
    }

    public static boolean isBroken(Entity e) {
        State s = MAP.get(e.getUuid());
        return s != null && s.brokenTicks > 0;
    }

    public static float get(LivingEntity e) {
        State s = MAP.get(e.getUuid());
        return s == null ? 0f : s.value;
    }

    public static void forget(UUID id) {
        MAP.remove(id);
    }

    public static void reset(LivingEntity e) {
        State s = MAP.remove(e.getUuid());
        if (s != null && s.brokenTicks > 0) release(e);
    }

    // ---------- Изменение осанки ----------

    public static void add(LivingEntity e, float amount, long now) {
        if (amount <= 0 || e.getWorld().isClient) return;
        if (e instanceof PlayerEntity pl && (pl.isCreative() || pl.isSpectator())) return;
        State s = state(e);
        if (s.brokenTicks > 0) return;
        s.value += amount;
        s.lastHit = now;
        if (s.value >= max(e)) doBreak(e, s);
    }

    private static void doBreak(LivingEntity e, State s) {
        s.value = max(e);
        boolean player = e instanceof PlayerEntity;
        s.brokenTicks = player ? 70 : (isBoss(e) ? 80 : 110);
        if (player) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 3));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 2));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 80, 3));
            if (e instanceof ServerPlayerEntity sp) {
                sp.sendMessage(Text.translatable("msg.shinobi.posture_broken_self"), true);
            }
        } else {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, s.brokenTicks, 6));
            if (e instanceof MobEntity m) {
                m.setAiDisabled(true);
                m.setTarget(null);
            }
        }
        if (e.getWorld() instanceof ServerWorld w) {
            Vec3d c = e.getPos().add(0, e.getHeight() * 0.6, 0);
            Combat.fx(w, ParticleTypes.CRIT, c, 25, 0.5, 0.3);
            Combat.fx(w, ParticleTypes.ENCHANTED_HIT, c, 15, 0.4, 0.2);
            w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.7f, 1.6f);
            w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);
        }
    }

    private static void release(LivingEntity e) {
        if (e instanceof MobEntity m) m.setAiDisabled(false);
    }

    // ---------- Добивание ----------

    public static void deathblow(ServerPlayerEntity killer, LivingEntity target, boolean stealth) {
        ServerWorld w = killer.getServerWorld();
        Vec3d c = target.getPos().add(0, target.getHeight() * 0.6, 0);
        Combat.fx(w, ParticleTypes.DAMAGE_INDICATOR, c, 30, 0.4, 0.3);
        Combat.fx(w, ParticleTypes.CRIT, c, 20, 0.5, 0.4);
        w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2f, 0.6f);
        w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.7f);
        killer.sendMessage(Text.translatable(stealth ? "msg.shinobi.stealth" : "msg.shinobi.deathblow"), true);

        if (isBoss(target)) {
            int seg = segments(target);
            if (stealth) {
                Combat.hit(killer, target, target.getMaxHealth() * 0.2f, 0);
                return;
            }
            seg--;
            if (seg <= 0) {
                kill(killer, target);
            } else {
                setSegments(target, seg);
                Combat.hit(killer, target, target.getMaxHealth() * 0.2f, 0);
                State s = MAP.get(target.getUuid());
                if (s != null) {
                    s.brokenTicks = 0;
                    s.value = 0;
                }
                release(target);
                target.removeStatusEffect(StatusEffects.SLOWNESS);
            }
        } else {
            kill(killer, target);
        }
    }

    private static void kill(ServerPlayerEntity killer, LivingEntity target) {
        Combat.internal = true;
        try {
            target.timeUntilRegen = 0;
            target.damage(killer.getDamageSources().playerAttack(killer), Float.MAX_VALUE);
        } finally {
            Combat.internal = false;
        }
    }

    // ---------- Тик ----------

    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (MAP.isEmpty()) return;
        Iterator<Map.Entry<UUID, State>> it = MAP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> en = it.next();
            State s = en.getValue();
            LivingEntity e = find(server, en.getKey());
            if (e == null || !e.isAlive()) {
                if (e != null) release(e);
                it.remove();
                continue;
            }
            long now = e.getWorld().getTime();
            float max = max(e);
            if (s.brokenTicks > 0) {
                s.brokenTicks--;
                if (s.brokenTicks == 0) {
                    s.value = max * 0.4f;
                    release(e);
                    e.removeStatusEffect(StatusEffects.SLOWNESS);
                }
                continue;
            }
            long delay = (e instanceof PlayerEntity) ? 25 : 40;
            if (now - s.lastHit > delay) {
                float hpFrac = e.getHealth() / e.getMaxHealth();
                float rec = (0.12f + 0.28f * hpFrac) * (max / 100f);
                if (e instanceof PlayerEntity pl) {
                    PlayerRuntime rt = PlayerRuntime.of(pl);
                    if (rt.sugarUntil > now) rec *= 3f;
                }
                s.value = Math.max(0f, s.value - rec);
                if (s.value <= 0f) it.remove();
            }
        }
    }
}
