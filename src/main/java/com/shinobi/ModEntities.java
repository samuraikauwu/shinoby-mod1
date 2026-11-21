package com.shinobi;

import com.shinobi.entity.ShinobiBossEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<ShinobiBossEntity> LORD = build(0.65f, 2.0f, false);
    public static final EntityType<ShinobiBossEntity> SHADOW = build(0.6f, 1.95f, false);
    public static final EntityType<ShinobiBossEntity> SAINT = build(0.7f, 2.2f, true);
    public static final EntityType<ShinobiBossEntity> ONI = build(0.95f, 2.9f, false);

    private ModEntities() {
    }

    private static EntityType<ShinobiBossEntity> build(float w, float h, boolean fireImmune) {
        FabricEntityTypeBuilder<ShinobiBossEntity> b = FabricEntityTypeBuilder
                .<ShinobiBossEntity>create(SpawnGroup.MONSTER, ShinobiBossEntity::new)
                .dimensions(EntityDimensions.fixed(w, h))
                .trackRangeBlocks(80);
        if (fireImmune) b.fireImmune();
        return b.build();
    }

    private static void reg(String id, EntityType<ShinobiBossEntity> type) {
        Registry.register(Registries.ENTITY_TYPE, ShinobiMod.id(id), type);
        FabricDefaultAttributeRegistry.register(type, ShinobiBossEntity.createAttributes());
    }

    public static void register() {
        reg("lord_boss", LORD);
        reg("shadow_boss", SHADOW);
        reg("saint_boss", SAINT);
        reg("oni_boss", ONI);
    }
}
