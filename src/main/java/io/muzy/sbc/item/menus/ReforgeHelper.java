// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.ui.SideHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

// from skyhanni ReforgeHelper + ReforgeApi: next to the reforge menus, a list of reforges this item can take,
// sorted by the stat you want, what each gives at its rarity
// picking one stops the reforge button once its reached, and a reforge the blacksmith doesnt give is
// protected from a careless click
// numbers from neu reforges.json + reforgestones.json
public final class ReforgeHelper {
    // from skyhanni ReforgeApi.Reforge
    public static final class Reforge {
        final String name, type, nbtModifier, stone;
        final Set<String> specialItems;
        final Map<String, Map<String, Double>> stats;
        final Map<String, String> ability;

        Reforge(String name, String type, String nbtModifier, String stone, Set<String> specialItems, Map<String, Map<String, Double>> stats, Map<String, String> ability) {
            this.name = name;
            this.type = type;
            this.nbtModifier = nbtModifier;
            this.stone = stone;
            this.specialItems = specialItems;
            this.stats = stats;
            this.ability = ability;
        }

        boolean isStone() {
            return stone != null;
        }

        // skyhanni ReforgeType categories
        boolean validFor(String category, String id) {
            return switch (type) {
                case "SPECIAL_ITEMS" -> specialItems.contains(id);
                case "SWORD" -> Set.of("SWORD", "LONGSWORD", "GAUNTLET").contains(category);
                case "BOW" -> Set.of("BOW", "SHORT_BOW").contains(category);
                case "ARMOR" -> Set.of("CARNIVAL_MASK", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS").contains(category);
                case "CHESTPLATE", "HELMET", "CLOAK", "BELT", "AXE", "FARMING_TOOL", "VACUUM", "FISHING_NET" -> type.equals(category);
                case "PICKAXE" -> Set.of("PICKAXE", "DRILL", "GAUNTLET").contains(category);
                case "EQUIPMENT" -> Set.of("NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET").contains(category);
                case "ROD" -> "FISHING_ROD".equals(category);
                case "SWORD_AND_ROD" -> Set.of("SWORD", "GAUNTLET", "LONGSWORD", "FISHING_ROD").contains(category);
                default -> false;
            };
        }

        double stat(String rarity, String stat) {
            Map<String, Double> m = stats.get(rarity);
            return m == null ? 0 : m.getOrDefault(stat, 0.0);
        }
    }

    private static final int HEX_NEXT_DOWN = 35, HEX_NEXT_UP = 17, EXIT_BUTTON = 40;
    // skyhanni colors + opacity: hovered, picked, finished reforge
    static final int HOVER = 0x32FFAA00, SELECTED = 0x645555FF, FINISHED = 0x4B55FF55;

    private static List<Reforge> basic = List.of(), stones = List.of();
    private static boolean loaded;

    static String itemName = "", itemId = "", rarity = "", category = "";
    static Reforge current, chosen, hovered;
    static String sortBy;
    private static boolean rareBlocked, waitForChat, waitDelay;

    private ReforgeHelper() {}

    // the data

    private static JsonObject read(String file) {
        try (InputStream in = io.muzy.sbc.core.data.DataFiles.neu(file)) {
            if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read {}", file, e);
        }
        return new JsonObject();
    }

    private static List<Reforge> parse(JsonObject root) {
        List<Reforge> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            String name = o.get("reforgeName").getAsString();
            String type;
            Set<String> special = new LinkedHashSet<>();
            JsonElement types = o.get("itemTypes");
            if (types.isJsonPrimitive()) type = types.getAsString().replace("/", "_AND_").toUpperCase(Locale.ROOT);
            else {
                type = "SPECIAL_ITEMS";
                JsonObject t = types.getAsJsonObject();
                if (t.has("internalName")) for (JsonElement id : t.getAsJsonArray("internalName")) special.add(id.getAsString());
            }
            Map<String, Map<String, Double>> stats = new HashMap<>();
            if (o.has("reforgeStats")) {
                for (Map.Entry<String, JsonElement> r : o.getAsJsonObject("reforgeStats").entrySet()) {
                    Map<String, Double> values = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> s : r.getValue().getAsJsonObject().entrySet()) values.put(s.getKey().toUpperCase(Locale.ROOT), s.getValue().getAsDouble());
                    stats.put(r.getKey(), values);
                }
            }
            Map<String, String> ability = new HashMap<>();
            if (o.has("reforgeAbility")) {
                JsonElement a = o.get("reforgeAbility");
                if (a.isJsonPrimitive()) for (JsonElement rarity : o.getAsJsonArray("requiredRarities")) ability.put(rarity.getAsString(), a.getAsString());
                else for (Map.Entry<String, JsonElement> r : a.getAsJsonObject().entrySet()) ability.put(r.getKey(), r.getValue().getAsString());
            }
            String modifier = o.has("nbtModifier") ? o.get("nbtModifier").getAsString() : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s_-]", "").replaceAll("[\\s-]", "_");
            out.add(new Reforge(name, type, modifier, o.has("internalName") ? o.get("internalName").getAsString() : null, special, stats, ability));
        }
        return out;
    }

    static void load() {
        if (loaded) return;
        loaded = true;
        basic = parse(read("reforges.json"));
        stones = parse(read("reforgestones.json"));
    }

    static List<Reforge> all() {
        load();
        List<Reforge> out = new ArrayList<>(stones);
        out.addAll(basic);
        return out;
    }

    static boolean isBasic(Reforge r) {
        load();
        return basic.contains(r);
    }

    // the menu

    static boolean isHex(String title) {
        return Patterns.get("SH.reforge.menu.hex").matcher(title).matches();
    }

    static boolean isMenu(String title) {
        return isHex(title) || Patterns.get("SH.reforge.menu.blacksmith").matcher(title).matches();
    }

    private static boolean hexMenu() {
        MenuView v = MenuMarks.current();
        return v != null && isHex(v.title);
    }

    private static int itemSlot(boolean hex) {
        return hex ? 19 : 13;
    }

    private static int button(boolean hex) {
        return hex ? 48 : 22;
    }

    // skyhanni readItemCategoryAndRarity: both are on the last lore lines
    static String[] categoryAndRarity(MenuView.Item it) {
        for (int i = it.cleanLore.size() - 1; i >= 0; i--) {
            String line = it.cleanLore.get(i);
            if (Patterns.get("SH.utils.item.lore.not-rarity.line").matcher(line).matches()) continue;
            Matcher m = Patterns.get("SH.utils.item.lore.rarity.line.colorless").matcher(line);
            if (m.matches()) return new String[]{m.group("itemCategory").replace(" ", "_"), m.group("rarity").replace(" ", "_")};
        }
        return new String[]{"", ""};
    }

    private static Reforge byModifier(String modifier) {
        if (modifier.isEmpty()) return null;
        for (Reforge r : all()) if (r.nbtModifier.equals(modifier)) return r;
        return null;
    }

    // skyhanni itemUpdate + the onBackgroundDrawn highlights
    static void mark(MenuView v) {
        load();
        boolean hex = isHex(v.title);
        int slot = itemSlot(hex), btn = button(hex);
        MenuView.Item it = v.item(slot);
        if (!it.id.equals(itemId)) chosen = null;
        itemId = it.id;
        itemName = it.clean;
        if (it.id.isEmpty()) {
            category = rarity = "";
            current = null;
        } else {
            String[] read = categoryAndRarity(it);
            category = read[0];
            rarity = read[1];
            current = byModifier(it.tag.getStringOr("modifier", ""));
        }
        if (current != null) {
            v.label(slot, "§e" + current.name, -5, 0, 1f);
            if (rareBlocked) v.label(btn, "§cThis item has a non-Blacksmith reforge! (Ctrl to bypass)", -55, 20, 1f);
        }
        if (hovered != null && hex) {
            if (hovered != current) stone(v, HOVER, hovered.isStone() ? ItemRepo.name(hovered.stone) : "Random Basic Reforge");
            else v.back(slot, HOVER);
        }
        if (chosen == null) return;
        if (chosen == current) {
            v.back(slot, FINISHED);
        } else if (chosen.isStone()) {
            if (hex) stone(v, SELECTED, ItemRepo.name(chosen.stone));
            else v.back(EXIT_BUTTON, SELECTED);
        } else v.back(btn, SELECTED);
    }

    // skyhanni colorReforgeStone: the stone in the hex list, or the arrows to scroll to it
    private static void stone(MenuView v, int color, String name) {
        int at = -1;
        for (int i = 0; i < v.itemsLength(); i++) {
            if (v.items[i].clean.equals(name)) {
                at = i;
                break;
            }
        }
        if (at >= 0) {
            v.back(at, color);
            return;
        }
        if (v.item(HEX_NEXT_DOWN).model.equals("player_head")) v.back(HEX_NEXT_DOWN, color);
        if (v.item(HEX_NEXT_UP).model.equals("player_head")) v.back(HEX_NEXT_UP, color);
    }

    // skyhanni generateDisplay: what can go on this item, stones in their own list
    static List<Reforge> options(boolean wantStones, boolean hex) {
        List<Reforge> pool = new ArrayList<>();
        load();
        if (wantStones) {
            if (hex || !MenuMarks.on("reforge_helper", "reforge_helper.stones_hex_only")) pool.addAll(stones);
        } else pool.addAll(basic);
        pool.removeIf(r -> !r.validFor(category, itemId));
        Comparator<Reforge> order = sortBy == null ? Comparator.comparing(r -> r.name) : Comparator.<Reforge>comparingDouble(r -> -r.stat(rarity, sortBy)).thenComparing(r -> r.name);
        pool.sort(order);
        return pool;
    }

    static Set<String> statTypes() {
        Set<String> out = new LinkedHashSet<>();
        for (Reforge r : options(false, hexMenu())) if (r.stats.get(rarity) != null) out.addAll(r.stats.get(rarity).keySet());
        for (Reforge r : options(true, hexMenu())) if (r.stats.get(rarity) != null) out.addAll(r.stats.get(rarity).keySet());
        return out;
    }

    static void choose(Reforge r) {
        chosen = chosen == r ? null : r;
        MenuMarks.invalidate();
    }

    static void sort(String stat) {
        sortBy = stat;
    }

    static void hover(Reforge r) {
        if (hovered != r) {
            hovered = r;
            MenuMarks.invalidate();
        }
    }

    static void beep() {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.2f);
    }

    // the clicks: skyhanni onSlotClick, handleNonBasicReforgeBlock, handleReforgeButtonClick, in that order
    static boolean click(MenuView v, int slotId, int button, boolean bypass) {
        if (v == null || !isMenu(v.title)) return false;
        boolean wasBlocked = rareBlocked;
        rareBlocked = false;
        if (wasBlocked) MenuMarks.invalidate();
        if (slotId != button(isHex(v.title))) return false;
        if (!Patterns.get("SH.reforge.lore.click-to-reforge").matcher(v.item(slotId).lastClean()).matches()) return false;
        if (MenuMarks.on("reforge_helper", "reforge_helper.block_rare") && current != null && !isBasic(current) && !bypass) {
            rareBlocked = true;
            MenuMarks.invalidate();
            beep();
            return true;
        }
        if (chosen == null) return false;
        if (current == chosen) {
            waitForChat = false;
            beep();
            return true;
        }
        if (waitForChat) {
            waitDelay = true;
            return true;
        }
        if (button == 2) return false;
        if (waitDelay) waitDelay = false;
        else waitForChat = true;
        return false;
    }

    private static boolean inMenu() {
        MenuView v = MenuMarks.current();
        return v != null && isMenu(v.title);
    }

    static void reset() {
        chosen = current = hovered = null;
        sortBy = null;
        rareBlocked = waitForChat = waitDelay = false;
        itemId = itemName = "";
    }

    static boolean modifierDown() {
        return Keys.down(InputConstants.KEY_LCONTROL) || Keys.down(InputConstants.KEY_RCONTROL)
                || (System.getProperty("os.name", "").startsWith("Mac") && (Keys.down(InputConstants.KEY_LGUI) || Keys.down(InputConstants.KEY_RGUI)));
    }

    static void register(BooleanSupplier skyblock) {
        Feature f = new Feature("reforge_helper", "Menus", "Reforge helper", "Beside the Reforge menus: every reforge this item can take with what it gives. Choose one and the button stops when it is reached.", "SkyHanni: Reforge Helper")
                .when(skyblock)
                .option(Option.bool("reforge_helper.block_rare", "Block rare reforges", "Stops a click on Reforge while the item has a reforge the Blacksmith does not give. Hold Ctrl to do it anyway.", true))
                .option(Option.bool("reforge_helper.stones_hex_only", "Stones only at the Hex", "At the Blacksmith, list only what the Blacksmith can give.", true))
                .option(Option.bool("reforge_helper.show_diff", "Show the difference", "On a reforge's tooltip, how each stat changes from the current one and which effect goes away.", false))
                .option(Option.bool("reforge_helper.hide_chat", "Hide the reforge messages", "", false));
        Features.add(f);
        MenuMarks.add("reforge_helper", ReforgeHelper::isMenu, ReforgeHelper::mark);
        Interactions.onSlotClick((screen, slot, slotId, button) -> Features.on("reforge_helper") && click(MenuMarks.current(), slotId, button, modifierDown()));
        ChatBus.register(ChatBus.FILTER, "reforge_helper", f, line -> {
            if (!inMenu()) return ChatBus.PASS;
            String plain = GameState.strip(line.plain());
            boolean ok = Patterns.get("SH.reforge.chat.success.colorless").matcher(plain).matches();
            if (!ok && !Patterns.get("SH.reforge.chat.fail.colorless").matcher(plain).matches()) return ChatBus.PASS;
            waitForChat = false;
            MenuMarks.invalidate();
            return MenuMarks.on("reforge_helper", "reforge_helper.hide_chat") ? ChatBus.CANCEL : ChatBus.PASS;
        });
        SideHelper.add(ReforgeHelper::panel);
    }

    private static final ReforgePanel PANEL = new ReforgePanel();
    private static Screen lastScreen;

    private static SideHelper.Content panel(Screen s) {
        boolean open = Features.on("reforge_helper") && isMenu(GameState.strip(s.getTitle().getString()).trim());
        if (s != lastScreen) reset();
        lastScreen = s;
        return open ? PANEL : null;
    }
}
