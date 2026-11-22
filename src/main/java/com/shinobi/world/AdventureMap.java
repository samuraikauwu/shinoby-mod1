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
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import com.shinobi.ModItems;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LightningEntity;
import com.shinobi.net.ShinobiNet;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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

    /** Начало горы (скальная порода) и конец подъёма. */
    private static final int ROCK_X = 301;
    private static final int PLATEAU_X = 333;
    /** Левые границы зон (относительно начала карты) и последний X. */
    private static final int[] ZONE_START = {-6, 21, 81, 93, 171, 216, 301, 391};
    private static final int MAP_END = 452;
    /** Зона, в которой игрок был в последний раз (для заставок). */
    private static final Map<UUID, Integer> LAST_ZONE = new HashMap<>();

    /** Высота земли вдоль оси X: ровно, затем подъём на гору и плато. */
    private static int groundH(int x) {
        if (x <= ROCK_X) return 0;
        if (x < PLATEAU_X) return (x - ROCK_X) / 2;
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

        // Названия зон при переходе
        if (st.origin != null) {
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.isSpectator()) continue;
                double rx = p.getX() - st.origin.getX();
                double rz = p.getZ() - st.origin.getZ();
                if (rx < ZONE_START[0] || rx > MAP_END || Math.abs(rz) > 24) {
                    LAST_ZONE.remove(p.getUuid());
                    continue;
                }
                int zone = 0;
                for (int i = 0; i < ZONE_START.length; i++) if (rx >= ZONE_START[i]) zone = i;
                Integer last = LAST_ZONE.put(p.getUuid(), zone);
                if (last == null || last != zone) {
                    ShinobiNet.cinematic(p, 1, "zone.shinobi." + zone, "zone.shinobi." + zone + ".sub", 70);
                }
            }
        }

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
                    a.spawned = true;
                    st.markDirty();
                    startIntro(w, a, type, p);
                    break;
                }
            }
        }
    }

    /** Катсцена вступления: колокол, удар молнии, карточка босса; затем появляется сам босс. */
    private static void startIntro(ServerWorld w, ShinobiState.Arena a, BossManager.Type type, ServerPlayerEntity summoner) {
        String key = a.boss.toLowerCase();
        Vec3d at = Vec3d.ofBottomCenter(a.pos.up());
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(at) > 40 * 40) continue;
            ShinobiNet.cinematic(p, 0, "cine.shinobi.boss." + key, "cine.shinobi.boss." + key + ".sub", 90);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 6, false, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false, false));
        }
        w.playSound(null, a.pos, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 3.0f, 0.5f);
        MinecraftServer server = w.getServer();
        Tasks.later(server, 25, () -> {
            w.playSound(null, a.pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 3.0f, 0.4f);
            w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 60, 1.2, 0.1, 1.2, 0.03);
        });
        Tasks.later(server, 45, () -> {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(at.x, at.y, at.z);
                bolt.setCosmetic(true);
                w.spawnEntity(bolt);
            }
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 70, 1.0, 1.2, 1.0, 0.04);
        });
        Tasks.later(server, 52, () -> {
            ServerPlayerEntity who = summoner.isAlive() ? summoner : null;
            if (who == null && !w.getPlayers().isEmpty()) who = w.getPlayers().get(0);
            if (who == null) {
                a.spawned = false;
                return;
            }
            if (!BossManager.spawn(w, at, type, who, "shinobi_arena_" + a.index)) a.spawned = false;
        });
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
                if (idx == LAST_ARENA) {
                    p.sendMessage(Text.translatable("msg.shinobi.adventure_victory"), false);
                    ShinobiNet.cinematic(p, 2, "cine.shinobi.final", "cine.shinobi.final.sub", 140);
                } else {
                    if (opened) p.sendMessage(Text.translatable("msg.shinobi.adventure_gate_open"), true);
                    ShinobiNet.cinematic(p, 2, "cine.shinobi.victory", "cine.shinobi.victory.sub", 90);
                }
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
        st.origin = new BlockPos(ox, gy, oz);
        Ctx c = new Ctx(w, ox, gy, oz, st);
        notify.accept(Text.translatable("msg.shinobi.adventure_building"));

        Runnable[] zones = {
                () -> hub(c), () -> village(c), () -> river(c), () -> grove(c), () -> graveyard(c), () -> castle(c), () -> mountain(c), () -> hall(c)
        };
        for (int i = 0; i < zones.length; i++) {
            final int n = i;
            Tasks.later(w.getServer(), 10 + i * 30, () -> {
                try {
                    zones[n].run();
                    notify.accept(Text.translatable("msg.shinobi.adventure_zone", n + 1, zones.length));
                } catch (Exception e) {
                    notify.accept(Text.literal("Ошибка при строительстве / Error building zone " + (n + 1) + ": " + e));
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
            boolean rock = x >= ROCK_X;
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

    /** Торговец-NPC: неуязвимый, стоит на месте; платят эмблемами духа, получают семена, чётки и эмблемы за металл. */
    private static void merchant(Ctx c, int x, int z) {
        VillagerEntity v = EntityType.VILLAGER.create(c.w());
        if (v == null) return;
        BlockPos p = c.p(x, groundH(x) + 1, z);
        v.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 90f, 0f);
        v.setVillagerData(v.getVillagerData().withProfession(VillagerProfession.WEAPONSMITH).withLevel(5));
        v.setCustomName(Text.translatable("npc.shinobi.merchant"));
        v.setCustomNameVisible(true);
        v.setAiDisabled(true);
        v.setInvulnerable(true);
        v.setPersistent();
        TradeOfferList offers = v.getOffers();
        offers.clear();
        offers.add(new TradeOffer(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(ModItems.SPIRIT_EMBLEM, 2), 32, 1, 0f));
        offers.add(new TradeOffer(new ItemStack(Items.GOLD_INGOT, 1), new ItemStack(ModItems.SPIRIT_EMBLEM, 6), 16, 1, 0f));
        offers.add(new TradeOffer(new ItemStack(ModItems.SPIRIT_EMBLEM, 10), new ItemStack(ModItems.PRAYER_BEAD, 1), 4, 5, 0f));
        offers.add(new TradeOffer(new ItemStack(ModItems.SPIRIT_EMBLEM, 16), new ItemStack(ModItems.GOURD_SEED, 1), 3, 8, 0f));
        c.w().spawnEntity(v);
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

    private static void fillW(Ctx c, int x1, int y1, int z1, int x2, int y2, int z2, BlockState b) {
        c.fill(x1, y1, z1, x2, y2, z2, b);
    }

    private static void tree(Ctx c, int x, int z, Random r) {
        int h = groundH(x);
        int t = 4 + r.nextInt(3);
        c.fill(x, h + 1, z, x, h + t, z, Blocks.SPRUCE_LOG.getDefaultState());
        BlockState leaf = Blocks.SPRUCE_LEAVES.getDefaultState();
        for (int k = 0; k < 4; k++) {
            int rad = 3 - k;
            if (rad < 1) rad = 1;
            c.fill(x - rad, h + t - 1 + k, z - rad, x + rad, h + t - 1 + k, z + rad, leaf);
        }
        c.set(x, h + t + 3, z, leaf);
    }

    private static void sakura(Ctx c, int x, int z, Random r) {
        int h = groundH(x);
        int t = 4 + r.nextInt(2);
        c.fill(x, h + 1, z, x, h + t, z, Blocks.CHERRY_LOG.getDefaultState());
        BlockState leaf = Blocks.CHERRY_LEAVES.getDefaultState();
        c.fill(x - 3, h + t, z - 3, x + 3, h + t + 1, z + 3, leaf);
        c.fill(x - 2, h + t + 2, z - 2, x + 2, h + t + 2, z + 2, leaf);
    }

    private static void hub(Ctx c) {
        terrain(c, -6, 20);
        path(c, -6, 20);
        torii(c, 6, 0, 7);
        idol(c, 13, 0);
        merchant(c, 17, -3);
        for (int x = 0; x <= 18; x += 6) {
            post(c, x, -3);
            post(c, x, 3);
        }
        // Каменные ступени-лантерны и цветущие деревья у святилища
        Random r = new Random(77);
        for (int x : new int[]{2, 9, 16}) {
            sakura(c, x, -9, r);
            sakura(c, x, 9, r);
        }
        // Колодец и скамьи
        fillW(c, 15, 1, 6, 17, 1, 8, COBBLE);
        fillW(c, 16, 1, 7, 16, 1, 7, Blocks.WATER.getDefaultState());
    }

    private static void village(Ctx c) {
        terrain(c, 21, 80);
        path(c, 21, 80);
        for (int x : new int[]{26, 40, 54, 68}) {
            house(c, x, -14, 7, 6, 4, true);
            house(c, x, 8, 7, 6, 4, false);
        }
        // Второй ряд — небольшие склады и сараи
        for (int x : new int[]{30, 58}) {
            house(c, x, -20, 5, 4, 3, true);
        }
        for (int x = 24; x <= 78; x += 8) {
            post(c, x, -3);
            post(c, x, 3);
        }
        // Рынок: навесы на красных столбах
        for (int x : new int[]{36, 50}) {
            fillW(c, x, 1, -6, x, 3, -6, LOG);
            fillW(c, x + 3, 1, -6, x + 3, 3, -6, LOG);
            fillW(c, x - 1, 4, -7, x + 4, 4, -5, RED);
            c.set(x + 1, 1, -6, Blocks.BARREL.getDefaultState());
            c.set(x + 2, 1, -6, Blocks.CRAFTING_TABLE.getDefaultState());
        }
        // Колодец деревни
        fillW(c, 46, 1, 6, 48, 2, 8, COBBLE);
        fillW(c, 47, 1, 7, 47, 2, 7, Blocks.WATER.getDefaultState());
        // Забор вдоль дороги
        for (int x = 22; x <= 78; x++) {
            if (x % 8 == 0) continue;
            c.set(x, 1, -4, Blocks.SPRUCE_FENCE.getDefaultState());
        }
        idol(c, 24, -6);
        merchant(c, 27, -5);
        spawnMob(c, EntityType.VINDICATOR, 34, 0);
        spawnMob(c, EntityType.VINDICATOR, 46, 2);
        spawnMob(c, EntityType.VINDICATOR, 58, -2);
        spawnMob(c, EntityType.VINDICATOR, 66, 0);
        spawnMob(c, EntityType.VINDICATOR, 74, 3);
        spawnMob(c, EntityType.PILLAGER, 50, 5);
        spawnMob(c, EntityType.PILLAGER, 62, -5);
        spawnMob(c, EntityType.PILLAGER, 38, 6);
    }

    /** Река с каменным мостом и мельницей. */
    private static void river(Ctx c) {
        terrain(c, 81, 92);
        path(c, 81, 81);
        path(c, 92, 92);
        for (int x = 84; x <= 89; x++) {
            for (int z = -21; z <= 21; z++) {
                c.fill(x, -3, z, x, 0, z, Blocks.WATER.getDefaultState());
                c.set(x, -4, z, Blocks.GRAVEL.getDefaultState());
            }
        }
        // Мост с перилами и фонарями
        for (int x = 82; x <= 91; x++)
            for (int z = -2; z <= 2; z++) c.set(x, 0, z, BRICKS);
        for (int x = 82; x <= 91; x++) {
            c.set(x, 1, -2, WALL);
            c.set(x, 1, 2, WALL);
        }
        for (int x : new int[]{82, 91}) {
            c.set(x, 2, -2, LANTERN);
            c.set(x, 2, 2, LANTERN);
        }
        // Мельница у реки
        house(c, 78, 9, 5, 5, 4, false);
        fillW(c, 84, 1, 8, 84, 6, 8, LOG);
        fillW(c, 84, 3, 8, 84, 3, 11, PLANKS);
        spawnMob(c, EntityType.PILLAGER, 80, 4);
        spawnMob(c, EntityType.VINDICATOR, 91, -3);
    }

    private static void grove(Ctx c) {
        terrain(c, 93, 170);
        path(c, 93, 170);
        Random rnd = new Random(12345);
        for (int i = 0; i < 320; i++) {
            int x = 94 + rnd.nextInt(52);
            int z = rnd.nextInt(41) - 20;
            if (Math.abs(z) <= 3) continue;
            if (x >= 108 && x <= 120 && z >= -18 && z <= -4) continue;
            int len = 5 + rnd.nextInt(8);
            c.fill(x, 1, z, x, len, z, Blocks.BAMBOO.getDefaultState());
        }
        // Храм в роще
        house(c, 108, -17, 11, 13, 5, true);
        c.set(114, 1, -11, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        merchant(c, 112, -9);
        // Каменные фонари вдоль тропы и мост через пруд
        for (int x = 96; x <= 140; x += 6) {
            post(c, x, -3);
            post(c, x, 3);
        }
        fillW(c, 126, 0, 6, 138, 0, 16, Blocks.WATER.getDefaultState());
        for (int x = 126; x <= 138; x++) c.set(x, 0, 11, BRICKS);
        torii(c, 125, 0, 9);
        torii(c, 130 + 0, 0, 9);
        arena(c, 0, 150, 11, "OWL", true);
    }

    /** Кладбище и пагода: покинутые руины, туман, враги-скелеты. */
    private static void graveyard(Ctx c) {
        terrain(c, 171, 215);
        path(c, 171, 215);
        Random r = new Random(4242);
        // Надгробия
        for (int i = 0; i < 60; i++) {
            int x = 174 + r.nextInt(40);
            int z = r.nextInt(41) - 20;
            if (Math.abs(z) <= 3 || (x >= 190 && x <= 204 && Math.abs(z) < 14)) continue;
            c.set(x, 1, z, Blocks.COBBLESTONE_WALL.getDefaultState());
            c.set(x, 2, z, Blocks.STONE_BRICK_SLAB.getDefaultState());
        }
        // Пагода в четыре яруса
        int px = 197;
        for (int t = 0; t < 4; t++) {
            int rad = 5 - t;
            int yb = t * 6;
            c.fill(px - rad, yb + 1, -rad, px + rad, yb + 5, rad, PLANKS);
            c.fill(px - rad + 1, yb + 1, -rad + 1, px + rad - 1, yb + 5, rad - 1, AIR);
            c.fill(px - rad - 1, yb + 6, -rad - 1, px + rad + 1, yb + 6, rad + 1, ROOF);
            c.set(px - rad, yb + 3, 0, GLASS);
            c.set(px + rad, yb + 3, 0, GLASS);
            c.set(px - rad + 1, yb + 1, -rad + 1, LANTERN);
        }
        c.fill(px - 5, 1, -1, px - 5, 3, 1, AIR);
        c.set(px, 1, 0, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        merchant(c, px + 2, 1);
        // Погасшие костры и тории
        torii(c, 178, 0, 9);
        torii(c, 212, 0, 9);
        for (int x = 174; x <= 214; x += 8) {
            post(c, x, -4);
            post(c, x, 4);
        }
        for (int x : new int[]{180, 186, 190, 208}) {
            spawnMob(c, EntityType.SKELETON, x, -8);
            spawnMob(c, EntityType.ZOMBIE, x + 2, 9);
        }
        spawnMob(c, EntityType.VINDICATOR, 200, 12);
        spawnMob(c, EntityType.VINDICATOR, 206, -12);
        spawnMob(c, EntityType.PILLAGER, 184, 4);
    }

    private static void castle(Ctx c) {
        terrain(c, 216, 300);
        path(c, 216, 300);
        // Ров с подъёмным мостом
        for (int x = 218; x <= 223; x++) {
            for (int z = -21; z <= 21; z++) {
                c.fill(x, -3, z, x, 0, z, Blocks.WATER.getDefaultState());
                c.set(x, -4, z, Blocks.GRAVEL.getDefaultState());
            }
        }
        for (int x = 218; x <= 223; x++)
            for (int z = -2; z <= 2; z++) c.set(x, 0, z, PLANKS);
        // Внешняя стена с проёмом и башнями
        c.fill(226, 1, -21, 296, 9, -21, BRICKS);
        c.fill(226, 1, 21, 296, 9, 21, BRICKS);
        c.fill(226, 1, -21, 228, 9, -3, BRICKS);
        c.fill(226, 1, 3, 228, 9, 21, BRICKS);
        c.fill(226, 10, -21, 228, 10, 21, ROOF);
        c.fill(226, 1, -2, 228, 9, 2, AIR);
        for (int z : new int[]{-8, 4}) tier(c, 225, z, 230, z + 4, 0, 12);
        // Угловые башни
        for (int z : new int[]{-21, 21}) tier(c, 293, z - 2, 297, z + 2, 0, 11);
        // Зубцы на стенах
        for (int x = 226; x <= 296; x += 2) {
            c.set(x, 10, -21, BRICKS);
            c.set(x, 10, 21, BRICKS);
        }
        // Главная башня замка
        tier(c, 240, -9, 262, 9, 0, 7);
        tier(c, 243, -7, 259, 7, 8, 6);
        tier(c, 246, -5, 256, 5, 15, 6);
        tier(c, 249, -3, 253, 3, 22, 5);
        c.fill(240, 1, -1, 240, 3, 1, AIR);
        c.set(251, 1, 0, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        merchant(c, 253, 2);
        c.set(250, 1, 2, LANTERN);
        c.set(252, 1, -2, LANTERN);
        // Внутренние дворы: колонны
        for (int x = 232; x <= 290; x += 6) {
            post(c, x, -4);
            post(c, x, 4);
        }
        for (int x = 266; x <= 276; x += 5) {
            fillW(c, x, 1, -12, x, 6, -12, BRICKS);
            fillW(c, x, 1, 12, x, 6, 12, BRICKS);
        }
        spawnMob(c, EntityType.VINDICATOR, 232, -14);
        spawnMob(c, EntityType.VINDICATOR, 248, 14);
        spawnMob(c, EntityType.VINDICATOR, 262, -14);
        spawnMob(c, EntityType.VINDICATOR, 270, 12);
        spawnMob(c, EntityType.PILLAGER, 238, 15);
        spawnMob(c, EntityType.PILLAGER, 258, 13);
        spawnMob(c, EntityType.PILLAGER, 266, -13);
        arena(c, 1, 282, 12, "GENICHIRO", true);
    }

    private static void mountain(Ctx c) {
        terrain(c, 301, 390);
        path(c, 301, 390);
        Random r = new Random(9001);
        // Сосны и валуны на склонах
        for (int i = 0; i < 70; i++) {
            int x = 304 + r.nextInt(62);
            int z = r.nextInt(37) - 18;
            if (Math.abs(z) <= 3) continue;
            tree(c, x, z, r);
        }
        for (int x = 306; x <= 384; x += 10) {
            post(c, x, -4);
            post(c, x, 4);
        }
        // Ступенчатые каменные террасы по пути вверх и тории
        for (int x : new int[]{320, 340, 360}) torii(c, x, 0, 9);
        // Горный храм на плато
        house(c, 344, -16, 9, 8, 4, true);
        c.set(348, groundH(344) + 1, -12, ModBlocks.SCULPTORS_IDOL.getDefaultState());
        spawnMob(c, EntityType.PILLAGER, 312, 3);
        spawnMob(c, EntityType.PILLAGER, 324, -4);
        spawnMob(c, EntityType.VINDICATOR, 334, 2);
        spawnMob(c, EntityType.VINDICATOR, 346, -3);
        spawnMob(c, EntityType.VINDICATOR, 352, 5);
        spawnMob(c, EntityType.PILLAGER, 362, -5);
        arena(c, 2, 372, 12, "GYOUBU", true);
    }

    private static void hall(Ctx c) {
        terrain(c, 391, MAP_END);
        path(c, 391, 408);
        torii(c, 395, 0, 9);
        for (int z : new int[]{-8, 8}) {
            for (int x = 398; x <= 410; x += 6) c.fill(x, groundH(x) + 1, z, x, groundH(x) + 8, z, BRICKS);
        }
        // Тронный зал вокруг арены: колоннады и красные знамёна
        for (int x = 414; x <= 448; x += 5) {
            for (int z : new int[]{-17, 17}) {
                c.fill(x, groundH(x) + 1, z, x, groundH(x) + 9, z, BRICKS);
                c.fill(x, groundH(x) + 5, z + (z < 0 ? 1 : -1), x, groundH(x) + 8, z + (z < 0 ? 1 : -1), RED);
            }
        }
        spawnMob(c, EntityType.VINDICATOR, 400, -4);
        spawnMob(c, EntityType.VINDICATOR, 404, 4);
        arena(c, 3, 430, 13, "ISSHIN", false);
    }
}
