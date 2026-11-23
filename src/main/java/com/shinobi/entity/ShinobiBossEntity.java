package com.shinobi.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/** Босс-воин: гуманоид с катаной. Параметры выставляет BossManager. */
public class ShinobiBossEntity extends HostileEntity {
    /** Поза: 0 — обычная, 1 — замах выпада, 2 — замах подсечки, 3 — захват, 4 — осанка сломана (колено). */
    private static final TrackedData<Integer> ANIM = DataTracker.registerData(ShinobiBossEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public ShinobiBossEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 50;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANIM, 0);
    }

    public int getAnim() {
        return this.dataTracker.get(ANIM);
    }

    public void setAnim(int v) {
        this.dataTracker.set(ANIM, v);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.15, true));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (!this.getWorld().isClient || !this.isAlive()) return;
        var r = this.getRandom();
        if (this.age % 3 != 0) return;
        double x = this.getX() + (r.nextDouble() - 0.5) * this.getWidth();
        double y = this.getY() + r.nextDouble() * this.getHeight();
        double z = this.getZ() + (r.nextDouble() - 0.5) * this.getWidth();
        net.minecraft.entity.EntityType<?> t = this.getType();
        if (this.getAnim() == 4) {
            this.getWorld().addParticle(net.minecraft.particle.ParticleTypes.CRIT, x, y, z, 0, 0.05, 0);
        } else if (t == com.shinobi.ModEntities.LORD) {
            this.getWorld().addParticle(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.02, 0);
        } else if (t == com.shinobi.ModEntities.SHADOW) {
            this.getWorld().addParticle(net.minecraft.particle.ParticleTypes.ASH, x, y, z, 0, -0.02, 0);
        } else if (t == com.shinobi.ModEntities.SAINT) {
            this.getWorld().addParticle(net.minecraft.particle.ParticleTypes.FLAME, x, this.getY() + 0.1, z, 0, 0.02, 0);
        } else if (t == com.shinobi.ModEntities.ONI) {
            this.getWorld().addParticle(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.03, 0);
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }
}
