package com.shinobi.combat;

import com.shinobi.data.PlayerData;
import com.shinobi.data.ShinobiState;
import com.shinobi.net.ShinobiNet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Дерево навыков: боевые приёмы и пассивные улучшения. Порядок важен: требование всегда объявлено выше. */
public enum Skill {
    // ---- Путь клинка (колонки 0-2) ----
    ICHIMONJI("ichimonji", Branch.BLADE, Art.ICHIMONJI, 0, null, 1, 0),
    ASHINA_CROSS("ashina_cross", Branch.BLADE, Art.ASHINA_CROSS, 2, ICHIMONJI, 0, 1),
    WHIRLWIND("whirlwind", Branch.BLADE, Art.WHIRLWIND, 2, ICHIMONJI, 2, 1),
    MORTAL_DRAW("mortal_draw", Branch.BLADE, Art.MORTAL_DRAW, 3, ASHINA_CROSS, 0, 2),
    DEFLECT_WINDOW("deflect_window", Branch.BLADE, null, 2, WHIRLWIND, 2, 2),
    DRAGON_FLASH("dragon_flash", Branch.BLADE, Art.DRAGON_FLASH, 4, MORTAL_DRAW, 0, 3),

    // ---- Путь тени (колонки 3-5) ----
    NIGHTJAR("nightjar", Branch.SHADOW, Art.NIGHTJAR, 2, null, 4, 0),
    SWIFT_DASH("swift_dash", Branch.SHADOW, null, 2, NIGHTJAR, 3, 1),
    SPIRAL_CLOUD("spiral_cloud", Branch.SHADOW, Art.SPIRAL_CLOUD, 3, NIGHTJAR, 5, 1),
    SHADOW_STEP("shadow_step", Branch.SHADOW, null, 3, SWIFT_DASH, 3, 2),
    VITAL_RECOVER("vital_recover", Branch.SHADOW, null, 3, SPIRAL_CLOUD, 5, 2),

    // ---- Путь протеза (колонки 6-8) ----
    TOOL_POWER("tool_power", Branch.PROSTHETIC, null, 2, null, 7, 0),
    EMBLEM_SAVER("emblem_saver", Branch.PROSTHETIC, null, 2, TOOL_POWER, 6, 1),
    POSTURE_RESIST("posture_resist", Branch.PROSTHETIC, null, 2, TOOL_POWER, 8, 1);

    public enum Branch {BLADE, SHADOW, PROSTHETIC}

    public final String id;
    public final Branch branch;
    /** Боевой приём, который открывает навык (null — пассивное улучшение). */
    public final Art art;
    public final int cost;
    public final Skill requires;
    public final int col;
    public final int row;

    Skill(String id, Branch branch, Art art, int cost, Skill requires, int col, int row) {
        this.id = id;
        this.branch = branch;
        this.art = art;
        this.cost = cost;
        this.requires = requires;
        this.col = col;
        this.row = row;
    }

    public boolean learned(int unlockedArts, int passives) {
        if (art != null) return (unlockedArts & (1 << art.ordinal())) != 0;
        return (passives & (1 << ordinal())) != 0;
    }

    public boolean learned(PlayerData d) {
        return learned(d.unlocked, d.passives);
    }

    public static boolean has(PlayerData d, Skill s) {
        return s.learned(d);
    }

    public static boolean has(ServerPlayerEntity p, Skill s) {
        return s.learned(ShinobiState.of(p));
    }

    /** Попытка изучить навык по запросу клиента. */
    public static void tryLearn(ServerPlayerEntity p, int index) {
        Skill[] all = values();
        if (index < 0 || index >= all.length) return;
        Skill s = all[index];
        PlayerData d = ShinobiState.of(p);
        if (s.learned(d)) return;
        if (s.requires != null && !s.requires.learned(d)) {
            p.sendMessage(Text.translatable("msg.shinobi.skill_locked"), true);
            return;
        }
        if (d.skillPoints < s.cost) {
            p.sendMessage(Text.translatable("msg.shinobi.scroll_cost", s.cost), true);
            return;
        }
        d.skillPoints -= s.cost;
        if (s.art != null) {
            d.unlock(s.art.ordinal());
            d.selectedArt = s.art.ordinal();
        } else {
            d.passives |= (1 << s.ordinal());
        }
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        p.sendMessage(Text.translatable("msg.shinobi.skill_learned", Text.translatable("skill.shinobi." + s.id)), true);
        ShinobiState.save(p);
        ShinobiNet.sync(p);
    }
}
