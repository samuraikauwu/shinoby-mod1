package com.shinobi;

import com.shinobi.combat.Art;
import com.shinobi.combat.BossManager;
import com.shinobi.data.PlayerData;
import com.shinobi.item.BossBellItem;
import com.shinobi.item.ConsumableItem;
import com.shinobi.item.KatanaItem;
import com.shinobi.item.ProstheticItem;
import com.shinobi.item.ProstheticTools;
import com.shinobi.item.ShinobiArmorMaterial;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
    private static final List<Item> ALL = new ArrayList<>();

    // ---------- Катаны ----------
    public static final Item KUSABIMARU = reg("kusabimaru",
            new KatanaItem(new KatanaItem.Material(900, Ingredient.ofItems(Items.IRON_INGOT)), 4, -2.0f, 1.0f, new Item.Settings()));
    public static final Item ASHINA_KATANA = reg("ashina_katana",
            new KatanaItem(new KatanaItem.Material(1200, Ingredient.ofItems(Items.IRON_INGOT)), 5, -2.2f, 1.2f, new Item.Settings()));
    public static final Item MORTAL_BLADE = reg("mortal_blade",
            new KatanaItem(new KatanaItem.Material(2200, Ingredient.ofItems(Items.NETHERITE_INGOT)), 7, -1.9f, 1.5f, new Item.Settings().fireproof()));

    // ---------- Протез шиноби ----------
    public static final Map<ProstheticTools.Tool, Item> TOOLS = new EnumMap<>(ProstheticTools.Tool.class);

    static {
        for (ProstheticTools.Tool t : ProstheticTools.Tool.values()) {
            TOOLS.put(t, reg(t.id, new ProstheticItem(t, new Item.Settings().maxCount(1))));
        }
    }

    // ---------- Расходники и улучшения ----------
    public static final Item SPIRIT_EMBLEM = reg("spirit_emblem", new Item(new Item.Settings()));

    public static final Item HEALING_GOURD = reg("healing_gourd", new ConsumableItem(new Item.Settings().maxCount(1), false, 20,
            (p, d, s) -> {
                if (d.gourdUses <= 0) {
                    p.sendMessage(Text.translatable("msg.shinobi.no_gourd"), true);
                    return false;
                }
                if (p.getHealth() >= p.getMaxHealth()) {
                    p.sendMessage(Text.translatable("msg.shinobi.full_health"), true);
                    return false;
                }
                d.gourdUses--;
                p.heal(p.getMaxHealth() * (0.4f + 0.04f * d.gourdSeeds));
                p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return true;
            }));

    public static final Item GOURD_SEED = reg("gourd_seed", new ConsumableItem(new Item.Settings(), true, 0,
            (p, d, s) -> {
                if (d.gourdSeeds >= 7) {
                    p.sendMessage(Text.translatable("msg.shinobi.seed_max"), true);
                    return false;
                }
                d.gourdSeeds++;
                d.gourdUses++;
                p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
                return true;
            }));

    public static final Item PRAYER_BEAD = reg("prayer_bead", new Item(new Item.Settings()));

    public static final Item PRAYER_NECKLACE = reg("prayer_necklace", new ConsumableItem(new Item.Settings(), true, 0,
            (p, d, s) -> {
                if (d.necklaces >= 10) {
                    p.sendMessage(Text.translatable("msg.shinobi.necklace_max"), true);
                    return false;
                }
                d.necklaces++;
                ShinobiMod.applyAttributes(p, d);
                p.setHealth(p.getMaxHealth());
                p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 0.9f);
                return true;
            }));

    public static final Item BOSS_MEMORY = reg("boss_memory", new ConsumableItem(new Item.Settings(), true, 0,
            (p, d, s) -> {
                if (d.attackPower >= 10) {
                    p.sendMessage(Text.translatable("msg.shinobi.memory_max"), true);
                    return false;
                }
                d.attackPower++;
                ShinobiMod.applyAttributes(p, d);
                p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                return true;
            }));

    // ---------- Свитки боевых приёмов ----------
    public static final Map<Art, Item> SCROLLS = new EnumMap<>(Art.class);

    static {
        for (Art art : Art.values()) {
            SCROLLS.put(art, reg("scroll_" + art.id, new ConsumableItem(new Item.Settings(), true, 0,
                    (p, d, s) -> learn(p, d, art))));
        }
    }

    // ---------- Колокола боссов ----------
    public static final Item BELL_GENICHIRO = reg("bell_genichiro", new BossBellItem(BossManager.Type.GENICHIRO, new Item.Settings().maxCount(1)));
    public static final Item BELL_OWL = reg("bell_owl", new BossBellItem(BossManager.Type.OWL, new Item.Settings().maxCount(1)));
    public static final Item BELL_ISSHIN = reg("bell_isshin", new BossBellItem(BossManager.Type.ISSHIN, new Item.Settings().maxCount(1)));
    public static final Item BELL_GYOUBU = reg("bell_gyoubu", new BossBellItem(BossManager.Type.GYOUBU, new Item.Settings().maxCount(1)));

    // ---------- Броня шиноби ----------
    public static final Item SHINOBI_HOOD = reg("shinobi_hood", new ArmorItem(ShinobiArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item SHINOBI_GARB = reg("shinobi_garb", new ArmorItem(ShinobiArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item SHINOBI_LEGGINGS = reg("shinobi_leggings", new ArmorItem(ShinobiArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item SHINOBI_BOOTS = reg("shinobi_boots", new ArmorItem(ShinobiArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new Item.Settings()));

    private ModItems() {
    }

    private static boolean learn(ServerPlayerEntity p, PlayerData d, Art art) {
        if (d.has(art.ordinal())) {
            p.sendMessage(Text.translatable("msg.shinobi.scroll_known"), true);
            return false;
        }
        if (d.skillPoints < art.skillCost && !p.isCreative()) {
            p.sendMessage(Text.translatable("msg.shinobi.scroll_cost", art.skillCost), true);
            return false;
        }
        if (!p.isCreative()) d.skillPoints -= art.skillCost;
        d.unlock(art.ordinal());
        d.selectedArt = art.ordinal();
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        p.sendMessage(Text.translatable("msg.shinobi.scroll_learned", Text.translatable("art.shinobi." + art.id)), true);
        return true;
    }

    private static Item reg(String id, Item item) {
        ALL.add(item);
        return Registry.register(Registries.ITEM, ShinobiMod.id(id), item);
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, ShinobiMod.id("main"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(KUSABIMARU))
                .displayName(Text.translatable("itemGroup.shinobi.main"))
                .entries((ctx, entries) -> {
                    entries.add(ModBlocks.SCULPTORS_IDOL_ITEM);
                    for (Item i : ALL) entries.add(i);
                })
                .build());
    }
}
