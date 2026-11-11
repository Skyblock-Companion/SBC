// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.Interactions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

// stuff that goes on an item wherever its drawn, hotbar too
// skyhanni UltimateEnchantStar, PersonalCompactorOverlay mark, AttributeOverlay, skyblocker ItemCooldowns +
// true quiver count
// computed per stack once in a while, like item/ItemNumbers
public final class ItemMarks {
    // text at a spot relative to the item
    record Tip(String text, int dx, int dy, float scale) {}

    private static final Map<ItemStack, List<Tip>> CACHE = new WeakHashMap<>();
    private static long clearedAt;

    // from skyblocker ItemCooldowns

    private static final List<String> BAT_ARMOR = List.of("BAT_PERSON_HELMET", "BAT_PERSON_CHESTPLATE", "BAT_PERSON_LEGGINGS", "BAT_PERSON_BOOTS");
    private static final Map<String, long[]> COOLDOWNS = new HashMap<>();

    private ItemMarks() {}

    // ms of an items right click, 0 if it has none
    static int cooldown(String id) {
        return switch (id) {
            case "FIG_AXE", "FIGSTONE_AXE", "JUNGLE_AXE", "TREECAPITATOR_AXE", "SILK_EDGE_SWORD", "LEAPING_SWORD" -> 1000;
            case "GRAPPLING_HOOK" -> 2000;
            case "ROGUE_SWORD", "SPIRIT_LEAP", "LIVID_DAGGER" -> 5000;
            case "SHADOW_FURY" -> 15000;
            case "INK_WAND", "GIANTS_SWORD" -> 30000;
            case "GREAT_SPOOK_STAFF" -> 60000;
            default -> 0;
        };
    }

    private static boolean onCooldown(String id, long now) {
        long[] entry = COOLDOWNS.get(id);
        if (entry == null) return false;
        if (entry[0] + entry[1] > now) return true;
        COOLDOWNS.remove(id);
        return false;
    }

    // how much of the cooldown is left, 0 = none
    private static float cooldownLeft(String id, long now) {
        if (!onCooldown(id, now)) return 0;
        long[] e = COOLDOWNS.get(id);
        return Math.max(0, e[0] + e[1] - now) / (float) e[1];
    }

    private static void used(Player player) {
        String id = Gain.sbId(player.getMainHandItem());
        int time = cooldown(id);
        if (time == 0 || onCooldown(id, System.currentTimeMillis())) return;
        if (id.equals("GRAPPLING_HOOK")) {
            // only when the hook comes back, and not with full bat armor which has none
            boolean bat = true;
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) bat &= BAT_ARMOR.contains(Gain.sbId(player.getItemBySlot(s)));
            if (player.fishing == null || bat) return;
        }
        COOLDOWNS.put(id, new long[]{System.currentTimeMillis(), time});
    }

    // the attribute overlay

    private static final Map<String, String> SHORT = new HashMap<>();
    private static final Map<String, String> DISPLAY = new HashMap<>();

    static {
        String[][] types = {{"Arachno", "arachno", "AR"}, {"Attack Speed", "attack_speed", "AS"}, {"Blazing", "blazing", "BL"}, {"Combo", "combo", "CO"}, {"Elite", "elite", "EL"}, {"Ender", "ender", "EN"}, {"Ignition", "ignition", "IG"},
                {"Life Recovery", "life_recovery", "LR"}, {"Mana Steal", "mana_steal", "MS"}, {"Midas Touch", "midas_touch", "MT"}, {"Undead", "undead", "UN"}, {"Warrior", "warrior", "WA"}, {"Deadeye", "deadeye", "DE"},
                {"Arachno Resistance", "arachno_resistance", "AR"}, {"Blazing Resistance", "blazing_resistance", "BR"}, {"Breeze", "breeze", "BZ"}, {"Dominance", "dominance", "DO"}, {"Ender Resistance", "ender_resistance", "ER"},
                {"Experience", "experience", "EX"}, {"Fortitude", "fortitude", "FO"}, {"Life Regeneration", "life_regeneration", "LR"}, {"Lifeline", "lifeline", "LL"}, {"Magic Find", "magic_find", "MF"}, {"Mana Pool", "mana_pool", "MP"},
                {"Mana Regeneration", "mana_regeneration", "MR"}, {"Vitality", "mending", "VI"}, {"Speed", "speed", "SP"}, {"Undead Resistance", "undead_resistance", "UR"}, {"Veteran", "veteran", "VE"}, {"Blazing Fortune", "blazing_fortune", "BF"},
                {"Fishing Experience", "fishing_experience", "FE"}, {"Infection", "infection", "IN"}, {"Double Hook", "double_hook", "DH"}, {"Fisherman", "fisherman", "FM"}, {"Fishing Speed", "fishing_speed", "FS"}, {"Hunter", "hunter", "HU"}, {"Trophy Hunter", "trophy_hunter", "TH"}};
        for (String[] t : types) {
            SHORT.put(t[1], t[2]);
            DISPLAY.put(t[1].toUpperCase(Locale.ROOT), t[1]);
        }
    }

    private record GoodRoll(Pattern regex, List<String[]> pairs) {}

    private static List<GoodRoll> rolls;

    private static List<GoodRoll> rolls() {
        if (rolls != null) return rolls;
        List<GoodRoll> out = new ArrayList<>();
        JsonObject o = Repo.constant("AttributeGoodRolls.json");
        if (o != null && o.has("good_rolls")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("good_rolls").entrySet()) {
                JsonObject r = e.getValue().getAsJsonObject();
                List<String[]> pairs = new ArrayList<>();
                for (JsonElement p : r.getAsJsonArray("list")) pairs.add(new String[]{p.getAsJsonArray().get(0).getAsString(), p.getAsJsonArray().get(1).getAsString()});
                out.add(new GoodRoll(Pattern.compile(r.get("regex").getAsString()), pairs));
            }
        }
        rolls = out;
        return out;
    }

    // skyhanni AttributeApi.getRollType: 2 = good roll, 1 = one of the two is wanted, 0 = neither
    static int rollType(String id, String first, String second) {
        for (GoodRoll r : rolls()) {
            if (!r.regex().matcher(id).matches()) continue;
            boolean partial = false;
            for (String[] pair : r.pairs()) {
                if ((pair[0].equals(first) && pair[1].equals(second)) || (pair[0].equals(second) && pair[1].equals(first))) return 2;
                if (pair[0].equals(first) || pair[0].equals(second) || pair[1].equals(first) || pair[1].equals(second)) partial = true;
            }
            return partial ? 1 : 0;
        }
        return 0;
    }

    private static boolean partOfRoll(String id, String attribute) {
        for (GoodRoll r : rolls()) {
            if (!r.regex().matcher(id).matches()) continue;
            for (String[] pair : r.pairs()) if (pair[0].equals(attribute) || pair[1].equals(attribute)) return true;
            return false;
        }
        return false;
    }

    private static void attributes(String id, CompoundTag attributes, List<Tip> out) {
        Feature f = Features.get("attribute_overlay");
        if (f == null || !f.active() || attributes.size() != 2 || id.equals("ATTRIBUTE_SHARD")) return;
        List<String> keys = new ArrayList<>(attributes.keySet());
        String a = keys.get(0).toLowerCase(Locale.ROOT), b = keys.get(1).toLowerCase(Locale.ROOT);
        if (!SHORT.containsKey(a) || !SHORT.containsKey(b)) return;
        int roll = rollType(id, a, b), index = 0;
        boolean goodRolls = f.option("attribute_overlay.good_rolls").bool(), goodAttributes = f.option("attribute_overlay.good_attributes").bool();
        boolean ignoreList = f.option("attribute_overlay.ignore_list").bool(), override = f.option("attribute_overlay.good_rolls_override").bool(), hideBad = f.option("attribute_overlay.hide_bad").bool();
        int min = f.option("attribute_overlay.min_level").integer();
        Set<String> listed = new HashSet<>(Arrays.asList(f.option("attribute_overlay.attributes").text().toLowerCase(Locale.ROOT).split("\\s*,\\s*")));
        boolean all = f.option("attribute_overlay.attributes").text().isBlank();
        for (String attribute : List.of(a, b)) {
            int level = attributes.getIntOr(keys.get(attribute.equals(a) ? 0 : 1), 0);
            int place = index++;
            if (!ignoreList && !all && !listed.contains(attribute) && !listed.contains(displayLower(attribute))) continue;
            if (level < min && !override) continue;
            if (hideBad) {
                boolean show = roll == 1 && goodAttributes && partOfRoll(id, attribute) || roll == 2 && goodRolls;
                if (!show) continue;
            }
            String color = roll == 2 && goodRolls ? "§e" : roll == 1 && goodAttributes ? "§a" : "§b";
            out.add(new Tip(color + SHORT.get(attribute), place == 1 ? 8 : 0, 0, 0.5714286f));
            out.add(new Tip("§a" + level, place == 1 ? 12 : 0, 5, 0.5714286f));
        }
    }

    private static String displayLower(String key) {
        return key.replace('_', ' ');
    }

    // the marks of a stack

    private static List<Tip> read(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return List.of();
        CompoundTag tag = data.copyTag();
        String id = tag.getStringOr("id", "");
        List<Tip> out = new ArrayList<>(2);
        if (Features.on("ultimate_star") && stack.getItem() == net.minecraft.world.item.Items.ENCHANTED_BOOK) {
            CompoundTag enchants = tag.getCompoundOrEmpty("enchantments");
            if (enchants.size() == 1 && enchants.keySet().iterator().next().startsWith("ultimate_")) out.add(new Tip("§d✦", 7, -1, 1f));
        }
        if (id.startsWith("PERSONAL_COMPACTOR_") || id.startsWith("PERSONAL_DELETOR_")) {
            Feature f = Features.get("personal_compactor");
            if (f != null && f.active() && f.option("personal_compactor.show_toggle").bool() && isCompactor(id)) out.add(new Tip(active(tag) ? "§a✔" : "§c✖", 9, -1, 1f));
        }
        CompoundTag attributes = tag.getCompoundOrEmpty("attributes");
        if (!attributes.isEmpty()) attributes(id, attributes, out);
        return out.isEmpty() ? List.of() : out;
    }

    private static boolean isCompactor(String id) {
        return io.muzy.sbc.core.Patterns.get("SH.inventory.personalcompactor.internalname").matcher(id).matches();
    }

    // from skyhanni getPersonalCompactorActive
    static boolean active(CompoundTag tag) {
        return tag.getByteOr("PERSONAL_DELETOR_ACTIVE", (byte) 0) == 1;
    }

    // called with an items decorations
    public static void draw(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || !GameState.inSkyBlock) return;
        long now = System.currentTimeMillis();
        if (now - clearedAt > 2000) {
            CACHE.clear();
            clearedAt = now;
        }
        List<Tip> tips = CACHE.computeIfAbsent(stack, ItemMarks::read);
        for (Tip t : tips) {
            var p = g.pose();
            p.pushMatrix();
            p.translate(x + t.dx(), y + t.dy());
            if (t.scale() != 1f) p.scale(t.scale(), t.scale());
            g.text(font, t.text(), 0, 0, 0xFFFFFFFF, true);
            p.popMatrix();
        }
        if (!COOLDOWNS.isEmpty() && Features.on("item_cooldown")) {
            float left = cooldownLeft(Gain.sbId(stack), now);
            if (left > 0) {
                int top = y + (int) Math.floor(16.0f * (1.0f - left));
                g.fill(x, top, x + 16, top + (int) Math.ceil(16.0f * left), 0x7FFFFFFF);
            }
        }
    }

    private static final Map<ItemStack, java.util.Optional<String>> QUIVER = new WeakHashMap<>();
    private static long quiverClearedAt;

    // skyblocker HudMixin: quiver slots real arrow count from the lore, or null. checked once in a while per stack
    public static String quiver(ItemStack stack) {
        if (!Features.on("true_quiver")) return null;
        long now = System.currentTimeMillis();
        if (now - quiverClearedAt > 1000) {
            QUIVER.clear();
            quiverClearedAt = now;
        }
        return QUIVER.computeIfAbsent(stack, s -> java.util.Optional.ofNullable(readQuiver(s))).orElse(null);
    }

    private static String readQuiver(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.getBooleanOr("quiver_arrow", false) && !"true".equals(tag.getStringOr("quiver_arrow", ""))) return null;
        for (String l : io.muzy.sbc.core.LegacyText.lore(stack)) {
            String plain = GameState.strip(l).trim();
            if (!plain.startsWith("Arrows Remaining")) continue;
            int at = plain.lastIndexOf(':');
            if (at < 0) return null;
            try {
                return shortCount(Integer.parseInt(plain.substring(at + 1).replace(",", "").trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // skyblocker SHORT_INTEGER_NUMBERS: at most a whole number and a letter
    static String shortCount(int v) {
        if (v < 1000) return String.valueOf(v);
        if (v < 1_000_000) return v / 1000 + "K";
        return v / 1_000_000 + "M";
    }

    static void register(BooleanSupplier skyblock) {
        Features.add(new Feature("ultimate_star", "Items", "Star on ultimate enchant books", "A small pink star on an enchanted book that holds an ultimate enchantment.", "SkyHanni: Ultimate Enchant Star").when(skyblock));
        Features.add(new Feature("item_cooldown", "Items", "Cooldown on items", "A rising fill over items whose right click has a cooldown, like the Grappling Hook or a Treecapitator, as the game does for its own items.", "Skyblocker: Item Cooldown").when(skyblock));
        Features.add(new Feature("true_quiver", "Items", "True quiver count", "The arrows you really have in the quiver slot of the hotbar, not 64 at most.", "Skyblocker: Show True Quiver Count").when(skyblock));
        Features.add(new Feature("attribute_overlay", "Items", "Attributes on items", "Two letters and a level for each attribute of an item that still has them. Yellow is a good roll, green one wanted attribute.", "SkyHanni: Attribute Overlay")
                .when(skyblock).defaultOff()
                .option(Option.text("attribute_overlay.attributes", "Attributes shown", "Names separated by commas; empty shows all.", ""))
                .option(Option.integer("attribute_overlay.min_level", "Lowest level shown", "", 1, 1, 10))
                .option(Option.bool("attribute_overlay.ignore_list", "Good rolls ignore the list", "", false))
                .option(Option.bool("attribute_overlay.good_rolls", "Highlight good rolls", "", true))
                .option(Option.bool("attribute_overlay.good_attributes", "Highlight a good attribute", "", false))
                .option(Option.bool("attribute_overlay.good_rolls_override", "Good rolls ignore the lowest level", "", true))
                .option(Option.bool("attribute_overlay.hide_bad", "Hide what is not a good roll", "", false)));
        Interactions.onItemClick(right -> {
            var p = Minecraft.getInstance().player;
            if (right && p != null && Features.on("item_cooldown")) used(p);
            return false;
        });
    }
}
