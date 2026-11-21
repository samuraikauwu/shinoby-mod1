package com.shinobi.data;

import net.minecraft.nbt.NbtCompound;

/** Постоянные данные игрока (сохраняются в мире). */
public class PlayerData {
    public int attackPower = 0;      // уровень силы атаки (память боссов)
    public int necklaces = 0;        // чётки: +здоровье и +осанка
    public int gourdSeeds = 0;       // семена тыквы
    public int gourdUses = 3;        // текущие глотки тыквы
    public int resurrections = 2;    // текущие воскрешения
    public int skillPoints = 0;
    public int unlocked = 1;         // битовая маска изученных приёмов (бит 0 = Ичимондзи)
    public int selectedArt = 0;
    public int killCounter = 0;

    public int maxGourd() {
        return 3 + gourdSeeds;
    }

    public int maxResurrections() {
        return 2;
    }

    public float maxPosture() {
        return 100f + 12f * necklaces;
    }

    public boolean has(int artOrdinal) {
        return (unlocked & (1 << artOrdinal)) != 0;
    }

    public void unlock(int artOrdinal) {
        unlocked |= (1 << artOrdinal);
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("attackPower", attackPower);
        n.putInt("necklaces", necklaces);
        n.putInt("gourdSeeds", gourdSeeds);
        n.putInt("gourdUses", gourdUses);
        n.putInt("resurrections", resurrections);
        n.putInt("skillPoints", skillPoints);
        n.putInt("unlocked", unlocked);
        n.putInt("selectedArt", selectedArt);
        n.putInt("killCounter", killCounter);
        return n;
    }

    public static PlayerData fromNbt(NbtCompound n) {
        PlayerData d = new PlayerData();
        d.attackPower = n.getInt("attackPower");
        d.necklaces = n.getInt("necklaces");
        d.gourdSeeds = n.getInt("gourdSeeds");
        d.gourdUses = n.contains("gourdUses") ? n.getInt("gourdUses") : 3;
        d.resurrections = n.contains("resurrections") ? n.getInt("resurrections") : 2;
        d.skillPoints = n.getInt("skillPoints");
        d.unlocked = n.contains("unlocked") ? n.getInt("unlocked") : 1;
        d.selectedArt = n.getInt("selectedArt");
        d.killCounter = n.getInt("killCounter");
        return d;
    }
}
