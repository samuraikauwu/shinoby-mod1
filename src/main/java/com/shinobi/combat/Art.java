package com.shinobi.combat;

/** Боевые приёмы (Combat Arts). */
public enum Art {
    ICHIMONJI("ichimonji", 1, 1, 50),
    ASHINA_CROSS("ashina_cross", 2, 1, 60),
    WHIRLWIND("whirlwind", 2, 2, 80),
    NIGHTJAR("nightjar", 2, 1, 60),
    MORTAL_DRAW("mortal_draw", 3, 1, 100),
    SPIRAL_CLOUD("spiral_cloud", 3, 2, 90),
    DRAGON_FLASH("dragon_flash", 4, 3, 120);

    public final String id;
    /** Стоимость изучения (очки навыков). */
    public final int skillCost;
    /** Расход духовных эмблем при использовании. */
    public final int emblemCost;
    public final int cooldown;

    Art(String id, int skillCost, int emblemCost, int cooldown) {
        this.id = id;
        this.skillCost = skillCost;
        this.emblemCost = emblemCost;
        this.cooldown = cooldown;
    }

    public static Art byOrdinal(int i) {
        Art[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
