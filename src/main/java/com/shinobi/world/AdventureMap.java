package com.shinobi.world;

import com.shinobi.ModBlocks;
import com.shinobi.Tasks;
import com.shinobi.combat.BossManager;
import com.shinobi.combat.PostureSystem;
import com.shinobi.data.ShinobiState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Карта-прохождение (оригинальная): святилище -> деревня -> бамбуковая роща и храм -> замок -> гора -> зал.
 * Строится командой /shinobi adventure вдоль оси +X от игрока. На каждой арене появляется босс,
 * выход закрыт воротами, пока босс жив.
 */
public final class AdventureMap {
    private static final int LAST_ARENA = 3;
    private static boolean building = false;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final BlockState BRICKS = Blocks.STONE_BRICKS.getDefaultState();
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.getDefaultState();
    private static final BlockState ROOF = Blocks.DEEPSLATE_TILE_SLAB.getDefaultState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.getDefaultState();
    private static final BlockState GLASS = Blocks.GLASS_PANE.getDefaultState();
    private static final BlockState LANTERN = Blocks.LANTERN.getDefaultState();
    private static final BlockState WALL = Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState();
    private static final BlockState RED = Blocks.RED_TERRACOTTA.getDefaultState();
    private static final BlockState BLACK = Blocks.BLACKSTONE.getDefaultState();
    private static final BlockState GATE = Blocks.DEEPSLATE_BRICKS.getDefaultState();

    private AdventureMap() {
    }

    /** Контекст строительства: смещение от точки игрока. */
    private record Ctx(ServerWorld w, int ox, int gy, int oz, ShinobiState st) {
        BlockPos p(int x, int y, int z) {
            return new BlockPos(ox + x, gy + y, oz + z);
        }

        void set(int x, int y, int z, BlockState s) {
            w.setBlockState(p(x, y, z), s, 2);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                        set(x, y, z, s);
        }
    }

    /** Высота земли вдоль оси X: ровно, затем подъём на гору и плато. */
    private static int groundH(int x) {
        if (x <= 204) return 0;
        if (x < 236) return (x - 204) / 2;
        return 16;
    }

    // ------------------------------------------------------------------ регистрация

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getWorld() instanceof ServerWorld w && PostureSystem.isBoss(entity)) {
                onBossDeath(w, entity);
            }
        });
    }

    // ------------------------------------------------------------------ игровая логика

    public static void tick(MinecraftServer server) {
        ShinobiState st = ShinobiState.get(server);
        if (st.arenas.isEmpty()) return;
        ServerWorld w = server.getOverworld();
        for (ShinobiState.Arena a : st.arenas) {
            if (a.spawned) continue;
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator()) continue;
                double dx = p.getX() - (a.pos.getX() + 0.5);
                double dz = p.getZ() - (a.pos.getZ() + 0.5);
                double dy = p.getY() - a.pos.getY();
                if (dx * dx + dz * dz < 12 * 12 && Math.abs(dy) < 8) {
                    BossManager.Type type;
                    try {
                        type = BossManager.Type.valueOf(a.boss);
                    } catch (IllegalArgumentException e) {
                        a.spawned = true;
                        break;
                    }
                    if (BossManager.spawn(w, Vec3d.ofBottomCenter(a.pos.up()), type, p, "shinobi_arena_" + a.index)) {
                        a.spawned = true;
                        st.markDirty();
                        w.playSound(null, a.pos, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 3.0f, 0.5f);
                    }
                    break;
                }
            }
        }
    }

    private static void onBossDeath(ServerWorld w, LivingEntity boss) {
        ShinobiState st = ShinobiState.get(w.getServer());
        for (String tag : boss.getCommandTags()) {
            if (!tag.startsWith("shinobi_arena_")) continue;
            int idx;
            try {
                idx = Integer.parseInt(tag.substring("shinobi_arena_".length()));
            } catch (NumberFormatException e) {
                continue;
            }
            boolean opened = false;
            for (var it = st.gates.iterator(); it.hasNext(); ) {
                ShinobiState.Gate g = it.next();
                if (g.opensAfter != idx) continue;
                for (int x = g.min.getX(); x <= g.max.getX(); x++)
                    for (int y = g.min.getY(); y <= g.max.getY(); y++)
                        for (int z = g.min.getZ(); z <= g.max.getZ(); z++)
                            w.setBlockState(new BlockPos(x, y, z), AIR, 2);
                Vec3d c = new Vec3d((g.min.getX() + g.max.getX()) / 2.0 + 0.5, (g.min.getY() + g.max.getY()) / 2.0, (g.min.getZ() + g.max.getZ()) / 2.0 + 0.5);
                w.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, 60, 1.5, 2.5, 1.5, 0.05);
                w.playSound(null, BlockPos.ofFloored(c), SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.BLOCKS, 1.5f, 0.6f);
                it.remove();
                opened = true;
            }
            st.markDirty();
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.squaredDistanceTo(boss) > 80 * 80) continue;
                if (idx == LAST_ARENA) p.sendMessage(Text.translatable("msg.shinobi.adventure_victory"), false);
                else if (opened) p.sendMessage(Text.translatable("msg.shinobi.adventure_gate_open"), true);
            }
        }
    }

    // ------------------------------------------------------------------ команда

    public static void build(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld w) || w != w.getServer().getOverworld()) {
            player.sendMessage(Text.translatable("msg.shinobi.adventure_overworld_only"), false);
            return;
        }
        buildAt(w, player.getBlockX() + 6, player.getBlockY() - 1, player.getBlockZ(), t -> player.sendMessage(t, false));
    }

    /** Построить карту, начиная с точки (x, gy, z): gy — высота верхнего блока земли. Сообщения идут в notify. */
    public static void buildAt(ServerWorld w, int ox, int gy, int oz, Consumer<Text> notify) {
        if (building) {
            notify.accept(Text.translatable("msg.shinobi.adventure_busy"));
            return;
        }
        building = true;
        ShinobiState st = ShinobiState.get(w.getServer());
        Ctx c = new Ctx(w, ox, gy, oz, st);
        notify.accept(Text.translatable("msg.shinobi.adventure_building"));

        Runnable[] zones = {
                () -> hub(c), () -> village(c), () -> grove(c), () -> castle(c), () -> mountain(c), () -> hall(c)
        };
        for (int i = 0; i < zones.length; i++) {
            final int n = i;
            Tasks.later(w.getServer(), 10 + i * 30, () -> {
                try {
                    zones[n].run();
                    notify.accept(Text.translatable("msg.shinobi.adventure_zone", n + 1, zones.length));
                } catch (Exception e) {
                    notify.accept(Text.literal("Ошибка при строительстве зоны " + (n + 1) + ": " + e));
                }
            });
        }
        Tasks.later(w.getServer(), 10 + zones.length * 30, () -> {
            st.markDirty();
            building = false;
            notify.accept(Text.translatable("msg.shinobi.adventure_done"));
        });
    }

    // ------------------------------------------------------------------ строительные помощники

    private static void terrain(Ctx c, int x1, int x2) {
        for (int x = x1; x <= x2; x++) {
            int h = groundH(x);
            boolean rock = x >= 204;
            for (int z = -21; z <= 21; z++) {
                for (int y = -4; y < h; y++) c.set(x, y, z, rock ? STONE : DIRT);
                c.set(x, h, z, rock ? STONE : GRASS);
                for (int y = h + 1; y <= h + 28; y++) c.set(x, y, z, AIR);
            }
            if (rock) {
                c.fill(x, h + 1, -21, x, h + 7, -21, STONE);
                c.fill(x, h + 1, 21, x, h + 7, 21, STONE);
            }
        }
    }

    private static void path(Ctx c, int x1, int x2) {
        for (int x = x1; x <= x2; x++)
            for (int z = -1; z <= 1; z++) c.set(x, groundH(x), z, BRICKS);
    }

    private static void post(Ctx c, int x, int z) {
        int h = groundH(x);
        c.fill(x, h + 1, z, x, h + 2, z, WALL);
        c.set(x, h + 3, z, LANTERN);
    }

    private static void idol(Ctx c, int x, int z) {
        int h = groundH(x);
        c.fill(x - 1, h, z - 1, x + 1, h, z + 1, BRICKS);
        c.set(x, h + 1, z, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        c.set(x - 1, h + 1, z - 1, LANTERN);
        c.set(x + 1, h + 1, z + 1, LANTERN);
        c.set(x - 1, h + 1, z + 1, LANTERN);
        c.set(x + 1, h + 1, z - 1, LANTERN);
    }

    private static void torii(Ctx c, int x, int z, int width) {
        int h = groundH(x);
        int a = z - width / 2;
        int b = z + width / 2;
        c.fill(x, h + 1, a, x, h + 7, a, RED);
        c.fill(x, h + 1, b, x, h + 7, b, RED);
        c.fill(x, h + 5, a - 1, x, h + 5, b + 1, RED);
        c.fill(x - 1, h + 7, a - 2, x + 1, h + 7, b + 2, BLACK);
        c.fill(x - 1, h + 8, a - 3, x + 1, h + 8, b + 3, ROOF);
    }

    /** Дом: doorSouth — дверной проём в стене со стороны +z, иначе со стороны -z. */
    private static void house(Ctx c, int x, int z, int w, int d, int h, boolean doorSouth) {
        int y0 = groundH(x);
        c.fill(x, y0, z, x + w - 1, y0, z + d - 1, COBBLE);
        c.fill(x, y0 + 1, z, x + w - 1, y0 + h, z + d - 1, PLANKS);
        c.fill(x + 1, y0 + 1, z + 1, x + w - 2, y0 + h, z + d - 2, AIR);
        for (int cx : new int[]{x, x + w - 1})
            for (int cz : new int[]{z, z + d - 1}) c.fill(cx, y0 + 1, cz, cx, y0 + h, cz, LOG);
        int dz = doorSouth ? z + d - 1 : z;
        c.fill(x + w / 2, y0 + 1, dz, x + w / 2, y0 + 2, dz, AIR);
        c.set(x, y0 + 2, z + d / 2, GLASS);
        c.set(x + w - 1, y0 + 2, z + d / 2, GLASS);
        c.set(x + 1, y0 + 1, z + 1, LANTERN);
        int i = 0;
        while (w - 2 * i > 0 && d - 2 * i > 0) {
            c.fill(x - 1 + i, y0 + h + 1 + i, z - 1 + i, x + w - i, y0 + h + 1 + i, z + d - i, ROOF);
            i++;
        }
    }

    private static void tier(Ctx c, int x1, int z1, int x2, int z2, int yb, int h) {
        c.fill(x1, yb + 1, z1, x2, yb + h, z2, BRICKS);
        c.fill(x1 + 1, yb + 1, z1 + 1, x2 - 1, yb + h, z2 - 1, AIR);
        c.fill(x1 - 1, yb + h + 1, z1 - 1, x2 + 1, yb + h + 1, z2 + 1, ROOF);
    }

    private static void spawnMob(Ctx c, EntityType<? extends MobEntity> type, int x, int z) {
        MobEntity m = type.create(c.w());
        if (m == null) return;
        BlockPos p = c.p(x, groundH(x) + 1, z);
        m.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, c.w().random.nextFloat() * 360f, 0f);
        m.initialize(c.w(), c.w().getLocalDifficulty(p), SpawnReason.COMMAND, null, null);
        m.setPersistent();
        c.w().spawnEntity(m);
    }

    /** Арена: круглая площадка со стеной, вход с запада; при hasExit — выход на восток, закрытый воротами. */
    private static void arena(Ctx c, int index, int cx, int r, String boss, boolean hasExit) {
        int h = groundH(cx);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= r) {
                    c.set(cx + dx, h, dz, ((dx + dz) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : BLACK);
                } else if (Math.round(d) == r + 1) {
                    boolean west = dx < 0 && Math.abs(dz) <= 2;
                    boolean east = dx > 0 && Math.abs(dz) <= 2;
                    if (west || (east && hasExit)) continue;
                    c.fill(cx + dx, h + 1, dz, cx + dx, h + 7, dz, BRICKS);
                    c.set(cx + dx, h + 8, dz, ROOF);
                }
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int px = cx + (int) Math.round(Math.cos(a) * (r - 1));
            int pz = (int) Math.round(Math.sin(a) * (r - 1));
            post(c, px, pz);
        }
        idol(c, cx - r - 6, 6);

        ShinobiState.Arena arena = new ShinobiState.Arena();
        arena.pos = c.p(cx, h, 0);
        arena.boss = boss;
        arena.index = index;
        c.st().arenas.add(arena);
        if (hasExit) {
            c.fill(cx + r, h + 1, -2, cx + r + 2, h + 7, 2, GATE);
            ShinobiState.Gate g = new ShinobiState.Gate();
            g.min = c.p(cx + r, h + 1, -2);
            g.max = c.p(cx + r + 2, h + 7, 2);
            g.opensAfter = index;
            c.st().gates.add(g);
        }
    }

    // ------------------------------------------------------------------ зоны

    private static void hub(Ctx c) {
        terrain(c, -6, 20);
        path(c, -6, 20);
        torii(c, 6, 0, 7);
        idol(c, 13, 0);
        for (int x = 0; x <= 18; x += 6) {
            post(c, x, -3);
            post(c, x, 3);
        }
    }

    private static void village(Ctx c) {
        terrain(c, 21, 70);
        path(c, 21, 70);
        for (int x : new int[]{26, 40, 54}) {
            house(c, x, -14, 7, 6, 4, true);
            house(c, x, 8, 7, 6, 4, false);
        }
        for (int x = 24; x <= 68; x += 8) {
            post(c, x, -3);
            post(c, x, 3);
        }
        idol(c, 24, -6);
        spawnMob(c, EntityType.VINDICATOR, 34, 0);
        spawnMob(c, EntityType.VINDICATOR, 46, 2);
        spawnMob(c, EntityType.VINDICATOR, 58, -2);
        spawnMob(c, EntityType.VINDICATOR, 66, 0);
        spawnMob(c, EntityType.PILLAGER, 50, 5);
        spawnMob(c, EntityType.PILLAGER, 62, -5);
    }

    private static void grove(Ctx c) {
        terrain(c, 71, 133);
        path(c, 71, 133);
        Random rnd = new Random(12345);
        for (int i = 0; i < 150; i++) {
            int x = 72 + rnd.nextInt(30);
            int z = rnd.nextInt(41) - 20;
            if (Math.abs(z) <= 3) continue;
            if (x >= 79 && x <= 91 && z >= -18 && z <= -4) continue;
            int len = 5 + rnd.nextInt(8);
            c.fill(x, 1, z, x, len, z, Blocks.BAMBOO.getDefaultState());
        }
        house(c, 80, -17, 11, 13, 5, true);
        c.set(85, 1, -11, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        torii(c, 103, 0, 9);
        post(c, 100, -3);
        post(c, 100, 3);
        arena(c, 0, 118, 11, "OWL", true);
    }

    private static void castle(Ctx c) {
        terrain(c, 134, 203);
        path(c, 134, 203);
        // Внешняя стена с проёмом и башнями
        c.fill(136, 1, -21, 200, 8, -21, BRICKS);
        c.fill(136, 1, 21, 200, 8, 21, BRICKS);
        c.fill(136, 1, -21, 138, 8, -3, BRICKS);
        c.fill(136, 1, 3, 138, 8, 21, BRICKS);
        c.fill(136, 9, -21, 138, 9, 21, ROOF);
        c.fill(136, 1, -2, 138, 8, 2, AIR);
        for (int z : new int[]{-8, 4}) tier(c, 135, z, 140, z + 4, 0, 11);
        // Главная башня
        tier(c, 148, -8, 168, 8, 0, 6);
        tier(c, 150, -6, 166, 6, 7, 5);
        tier(c, 153, -4, 163, 4, 13, 5);
        c.fill(148, 1, -1, 148, 3, 1, AIR);
        c.set(158, 1, 0, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        c.set(157, 1, 2, LANTERN);
        c.set(159, 1, -2, LANTERN);
        for (int x = 142; x <= 196; x += 9) {
            post(c, x, -4);
            post(c, x, 4);
        }
        spawnMob(c, EntityType.VINDICATOR, 142, -14);
        spawnMob(c, EntityType.VINDICATOR, 158, 14);
        spawnMob(c, EntityType.VINDICATOR, 172, -14);
        spawnMob(c, EntityType.PILLAGER, 150, 15);
        spawnMob(c, EntityType.PILLAGER, 170, 13);
        arena(c, 1, 188, 12, "GENICHIRO", true);
    }

    private static void mountain(Ctx c) {
        terrain(c, 204, 268);
        path(c, 204, 268);
        for (int x = 208; x <= 266; x += 10) {
            post(c, x, -4);
            post(c, x, 4);
        }
        spawnMob(c, EntityType.PILLAGER, 214, 3);
        spawnMob(c, EntityType.PILLAGER, 222, -4);
        spawnMob(c, EntityType.VINDICATOR, 230, 2);
        spawnMob(c, EntityType.VINDICATOR, 238, -3);
        torii(c, 240, 0, 9);
        arena(c, 2, 254, 12, "GYOUBU", true);
    }

    private static void hall(Ctx c) {
        terrain(c, 269, 306);
        path(c, 269, 276);
        torii(c, 273, 0, 9);
        for (int z : new int[]{-8, 8}) {
            for (int x = 270; x <= 276; x += 6) c.fill(x, groundH(x) + 1, z, x, groundH(x) + 6, z, BRICKS);
        }
        arena(c, 3, 290, 13, "ISSHIN", false);
    }
}
