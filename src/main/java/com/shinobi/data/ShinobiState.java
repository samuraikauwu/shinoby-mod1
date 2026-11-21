package com.shinobi.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Хранилище данных всех игроков в мире. */
public class ShinobiState extends PersistentState {
    private final Map<UUID, PlayerData> players = new HashMap<>();

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
        return s;
    }
}
