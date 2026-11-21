package com.shinobi.data;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Временные (не сохраняемые) состояния игрока. Все времена — в тиках мира. */
public class PlayerRuntime {
    private static final Map<UUID, PlayerRuntime> MAP = new ConcurrentHashMap<>();

    public static PlayerRuntime of(PlayerEntity p) {
        return MAP.computeIfAbsent(p.getUuid(), k -> new PlayerRuntime());
    }

    public static void remove(UUID id) {
        MAP.remove(id);
    }

    public long dashInvulUntil = 0;
    public long dashTick = -1000;
    public long dashCooldownUntil = 0;
    public long resInvulUntil = 0;
    public long umbrellaUntil = 0;
    public long mortalDrawUntil = 0;
    public float mortalDrawDamage = 0;
    public long sabimaruUntil = 0;
    public long sugarUntil = 0;
    public long artCooldownUntil = 0;
    public long grappleUntil = 0;
    public Vec3d grappleTarget = null;
    public UUID lastTarget = null;
    public long lastTargetTick = -1000;
}
