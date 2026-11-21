package com.shinobi;

import com.shinobi.combat.BossManager;
import com.shinobi.combat.CombatEvents;
import com.shinobi.combat.PerilousSystem;
import com.shinobi.combat.PostureSystem;
import com.shinobi.data.PlayerData;
import com.shinobi.data.PlayerRuntime;
import com.shinobi.data.ShinobiState;
import com.shinobi.net.ShinobiNet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class ShinobiMod implements ModInitializer {
    public static final String MOD_ID = "shinobi";

    private static final UUID HP_MOD = UUID.fromString("a7a2c5d0-3f1b-4b4e-9a53-6c1f0a0f5e01");
    private static final UUID ATK_MOD = UUID.fromString("a7a2c5d0-3f1b-4b4e-9a53-6c1f0a0f5e02");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModBlocks.register();
        ModItems.register();
        ShinobiNet.registerServer();
        CombatEvents.register();
        com.shinobi.world.AdventureMap.register();
        ShinobiCommands.register();

        ServerTickEvents.END_SERVER_TICK.register(ShinobiMod::serverTick);
        ServerTickEvents.END_WORLD_TICK.register(PerilousSystem::tick);

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            PostureSystem.forget(entity.getUuid());
            PerilousSystem.forget(entity.getUuid());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            applyAttributes(p, ShinobiState.of(p));
            ShinobiNet.sync(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerRuntime.remove(handler.getPlayer().getUuid());
            PostureSystem.forget(handler.getPlayer().getUuid());
        });
    }

    private static void serverTick(MinecraftServer server) {
        Tasks.tick(server);
        PostureSystem.tick(server);
        BossManager.tick(server);

        int t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            tickGrapple(p);
            if (t % 5 == 0) ShinobiNet.sync(p);
            if (t % 40 == 0) applyAttributes(p, ShinobiState.of(p));
        }
        if (t % 20 == 0) com.shinobi.world.AdventureMap.tick(server);
        if (t % 200 == 0) ShinobiState.get(server).markDirty();
    }

    private static void tickGrapple(ServerPlayerEntity p) {
        PlayerRuntime rt = PlayerRuntime.of(p);
        if (rt.grappleTarget == null) return;
        long now = p.getWorld().getTime();
        if (now >= rt.grappleUntil || !p.isAlive()) {
            rt.grappleTarget = null;
            return;
        }
        Vec3d to = rt.grappleTarget.subtract(p.getPos().add(0, 1.0, 0));
        if (to.length() < 1.8) {
            rt.grappleTarget = null;
            ShinobiUtil.velocity(p, p.getVelocity().x * 0.3, 0.35, p.getVelocity().z * 0.3);
            return;
        }
        Vec3d v = to.normalize().multiply(0.95);
        ShinobiUtil.velocity(p, v.x, v.y, v.z);
    }

    /** Применить бонусы здоровья (чётки) и силы атаки (память боссов). */
    public static void applyAttributes(ServerPlayerEntity p, PlayerData d) {
        setModifier(p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH), HP_MOD, "shinobi_vitality", d.necklaces * 2.0);
        setModifier(p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE), ATK_MOD, "shinobi_attack_power", d.attackPower * 0.75);
    }

    private static void setModifier(EntityAttributeInstance inst, UUID id, String name, double value) {
        if (inst == null) return;
        EntityAttributeModifier m = inst.getModifier(id);
        if (value == 0) {
            if (m != null) inst.removeModifier(id);
            return;
        }
        if (m != null && m.getValue() == value) return;
        if (m != null) inst.removeModifier(id);
        inst.addPersistentModifier(new EntityAttributeModifier(id, name, value, EntityAttributeModifier.Operation.ADDITION));
    }
}
