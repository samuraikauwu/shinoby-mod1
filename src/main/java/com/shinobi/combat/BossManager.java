package com.shinobi.combat;

import com.shinobi.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Боссы в стиле Sekiro: вызываются колоколами, имеют сегменты осанки и полосу здоровья. */
public final class BossManager {
    public enum Type {
        GENICHIRO("genichiro", EntityType.VINDICATOR, 180, 10, 2, 0.30, true),
        OWL("owl", EntityType.VINDICATOR, 140, 8, 2, 0.40, true),
        ISSHIN("isshin", EntityType.WITHER_SKELETON, 320, 14, 3, 0.32, true),
        GYOUBU("gyoubu", EntityType.RAVAGER, 220, 12, 2, 0.35, false);

        public final String id;
        public final EntityType<? extends MobEntity> entity;
        public final double health;
        public final double damage;
        public final int segments;
        public final double speed;
        public final boolean katana;

        Type(String id, EntityType<? extends MobEntity> entity, double health, double damage, int segments, double speed, boolean katana) {
            this.id = id;
            this.entity = entity;
            this.health = health;
            this.damage = damage;
            this.segments = segments;
            this.speed = speed;
            this.katana = katana;
        }
    }

    private static final Map<UUID, ServerBossBar> BARS = new HashMap<>();

    private BossManager() {
    }

    public static boolean spawn(ServerWorld world, Vec3d pos, Type type, ServerPlayerEntity summoner) {
        MobEntity mob = type.entity.create(world);
        if (mob == null) return false;
        mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, summoner.getYaw() + 180f, 0f);

        setBase(mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH), type.health);
        setBase(mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE), type.damage);
        setBase(mob.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), 0.7);
        if (type.id.equals("owl")) {
            setBase(mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED), type.speed);
        }
        mob.setHealth((float) type.health);
        mob.setPersistent();
        mob.setCustomName(Text.translatable("boss.shinobi." + type.id));
        mob.setCustomNameVisible(true);
        mob.addCommandTag("shinobi_boss");
        PostureSystem.setSegments(mob, type.segments);
        if (type.katana) {
            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(type == Type.ISSHIN ? ModItems.MORTAL_BLADE : ModItems.ASHINA_KATANA));
            mob.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        }
        mob.setTarget(summoner);
        world.spawnEntity(mob);

        ServerBossBar bar = new ServerBossBar(Text.translatable("boss.shinobi." + type.id), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        BARS.put(mob.getUuid(), bar);
        bar.addPlayer(summoner);
        return true;
    }

    private static void setBase(EntityAttributeInstance inst, double v) {
        if (inst != null) inst.setBaseValue(v);
    }

    public static void tick(MinecraftServer server) {
        if (BARS.isEmpty()) return;
        long t = server.getTicks();
        Iterator<Map.Entry<UUID, ServerBossBar>> it = BARS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ServerBossBar> en = it.next();
            LivingEntity boss = null;
            for (ServerWorld w : server.getWorlds()) {
                if (w.getEntity(en.getKey()) instanceof LivingEntity le) {
                    boss = le;
                    break;
                }
            }
            ServerBossBar bar = en.getValue();
            if (boss == null || !boss.isAlive()) {
                bar.clearPlayers();
                it.remove();
                continue;
            }
            bar.setPercent(Math.max(0f, boss.getHealth() / boss.getMaxHealth()));
            if (t % 20 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    boolean near = p.getWorld() == boss.getWorld() && p.squaredDistanceTo(boss) < 48 * 48;
                    if (near) bar.addPlayer(p);
                    else bar.removePlayer(p);
                }
            }
        }
    }

    /** Награда за убийство босса. */
    public static void drops(LivingEntity boss) {
        boss.dropStack(new ItemStack(ModItems.BOSS_MEMORY));
        boss.dropStack(new ItemStack(ModItems.PRAYER_BEAD, 2 + boss.getWorld().random.nextInt(2)));
        boss.dropStack(new ItemStack(ModItems.SPIRIT_EMBLEM, 6));
        if (boss.getWorld().random.nextFloat() < 0.5f) {
            boss.dropStack(new ItemStack(ModItems.GOURD_SEED));
        }
    }

    public static Item bellItem(Type t) {
        return switch (t) {
            case GENICHIRO -> ModItems.BELL_GENICHIRO;
            case OWL -> ModItems.BELL_OWL;
            case ISSHIN -> ModItems.BELL_ISSHIN;
            case GYOUBU -> ModItems.BELL_GYOUBU;
        };
    }
}
