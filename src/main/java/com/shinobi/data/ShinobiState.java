package com.shinobi.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Хранилище данных всех игроков в мире. */
public class ShinobiState extends PersistentState {
    private final Map<UUID, PlayerData> players = new HashMap<>();

    /** Арена босса на карте-прохождении: босс появляется, когда игрок входит в арену. */
    public static class Arena {
        public BlockPos pos;
        public String boss;
        public boolean spawned;
        public int index;
    }

    /** Закрытые ворота: открываются после победы над боссом арены opensAfter. */
    public static class Gate {
        public BlockPos min;
        public BlockPos max;
        public int opensAfter;
    }

    public final List<Arena> arenas = new ArrayList<>();
    public final List<Gate> gates = new ArrayList<>();

    public PlayerData of(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    public static PlayerData of(ServerPlayerEntity p) {
        return get(p.getServer()).of(p.getUuid());
    }

    public static ShinobiState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(ShinobiState::fromNbt, ShinobiState::new, "shinobi_data");
    }

    public static void save(ServerPlayerEntity p) {
        get(p.getServer()).markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        players.forEach((id, d) -> all.put(id.toString(), d.toNbt()));
        nbt.put("players", all);
        NbtList la = new NbtList();
        for (Arena a : arenas) {
            NbtCompound c = new NbtCompound();
            c.putInt("x", a.pos.getX());
            c.putInt("y", a.pos.getY());
            c.putInt("z", a.pos.getZ());
            c.putString("boss", a.boss);
            c.putBoolean("spawned", a.spawned);
            c.putInt("index", a.index);
            la.add(c);
        }
        nbt.put("arenas", la);
        NbtList lg = new NbtList();
        for (Gate g : gates) {
            NbtCompound c = new NbtCompound();
            c.putInt("x1", g.min.getX());
            c.putInt("y1", g.min.getY());
            c.putInt("z1", g.min.getZ());
            c.putInt("x2", g.max.getX());
            c.putInt("y2", g.max.getY());
            c.putInt("z2", g.max.getZ());
            c.putInt("after", g.opensAfter);
            lg.add(c);
        }
        nbt.put("gates", lg);
        return nbt;
    }

    public static ShinobiState fromNbt(NbtCompound nbt) {
        ShinobiState s = new ShinobiState();
        NbtCompound all = nbt.getCompound("players");
        for (String k : all.getKeys()) {
            try {
                s.players.put(UUID.fromString(k), PlayerData.fromNbt(all.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NbtList la = nbt.getList("arenas", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < la.size(); i++) {
            NbtCompound c = la.getCompound(i);
            Arena a = new Arena();
            a.pos = new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            a.boss = c.getString("boss");
            a.spawned = c.getBoolean("spawned");
            a.index = c.getInt("index");
            s.arenas.add(a);
        }
        NbtList lg = nbt.getList("gates", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lg.size(); i++) {
            NbtCompound c = lg.getCompound(i);
            Gate g = new Gate();
            g.min = new BlockPos(c.getInt("x1"), c.getInt("y1"), c.getInt("z1"));
            g.max = new BlockPos(c.getInt("x2"), c.getInt("y2"), c.getInt("z2"));
            g.opensAfter = c.getInt("after");
            s.gates.add(g);
        }
        return s;
    }
}
