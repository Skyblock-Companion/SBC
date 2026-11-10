// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.sacks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// glue for the skyhanni sack port: reads open sack menus + the "[Sacks]" chat hover, keeps per profile sack
// contents in Store
// shows the SackDisplay lines next to the menu and highlights full sacks
public final class Sacks {
    public static final String DISPLAY = "sack_display";

    private static int lastMenuId = -1;
    private static String lastMenuTitle = "";
    private static int lastMenuHash;
    private static List<String> display = List.of();
    private static RichCard.Content card = new RichCard.Content("", List.of());
    private static Map<String, int[]> fillet;
    private static String profileKey = "";
    private Sacks() {}

    static boolean b(String key, boolean def) {
        Feature f = Features.get(DISPLAY);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static String choice(String key, String def) {
        Feature f = Features.get(DISPLAY);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.text();
    }

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    // skyhanni TrophyFishManager getFilletValue(rarity), from data/sh/TrophyFish.json
    static int filletPerTrophy(String internalName) {
        if (fillet == null) {
            fillet = new HashMap<>();
            try (var in = io.muzy.sbc.core.data.DataFiles.sh("TrophyFish.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonObject fish = root.has("trophy_fish") ? root.getAsJsonObject("trophy_fish") : root;
                    for (var e : fish.entrySet()) {
                        JsonObject f = e.getValue().getAsJsonObject().getAsJsonObject("fillet");
                        if (f == null) continue;
                        fillet.put(e.getKey().toLowerCase(Locale.ROOT), new int[]{f.get("bronze").getAsInt(), f.get("silver").getAsInt(), f.get("gold").getAsInt(), f.get("diamond").getAsInt()});
                    }
                }
            } catch (Exception e) {
                SbcMod.LOG.warn("Bad TrophyFish.json", e);
            }
        }
        int us = internalName.lastIndexOf('_');
        if (us < 0) return 0;
        String name = internalName.substring(0, us).replace("_", "").toLowerCase(Locale.ROOT);
        String rarity = internalName.substring(us + 1);
        int[] v = fillet.get(name);
        if (v == null) return 0;
        return switch (rarity) {
            case "SILVER" -> v[1];
            case "GOLD" -> v[2];
            case "DIAMOND" -> v[3];
            default -> v[0];
        };
    }

    // storage

    private static Store.SackProfile profile() {
        String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
        return Store.I.sacks.computeIfAbsent(key, k -> new Store.SackProfile());
    }

    private static void loadProfile() {
        Map<String, SackApi.Stored> data = new java.util.LinkedHashMap<>();
        for (var e : profile().contents.entrySet()) {
            int[] v = e.getValue();
            data.put(e.getKey(), new SackApi.Stored(v[0], v[1], SackApi.SackStatus.values()[Math.max(0, Math.min(3, v[2]))]));
        }
        SackApi.sackData = data;
    }

    private static void save() {
        Store.SackProfile p = profile();
        p.contents.clear();
        for (var e : SackApi.sackData.entrySet()) p.contents.put(e.getKey(), new int[]{e.getValue().amount(), e.getValue().lastChange(), e.getValue().status().ordinal()});
        Store.markDirty();
    }

    // menus

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (!GameState.profileName.equals(profileKey)) {
            profileKey = GameState.profileName;
            loadProfile();
        }
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            if (lastMenuId != -1) {
                lastMenuId = -1;
                lastMenuTitle = "";
                lastMenuHash = 0;
                SackApi.onInventoryClose();
                display = List.of();
                card = new RichCard.Content("", List.of());
            }
            return;
        }
        if (!sb()) return;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        if (!SackApi.isSackInventory(title)) return;
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int hash = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (!st.isEmpty()) hash = hash * 31 + st.getHoverName().getString().hashCode() + st.getCount();
        }
        if (menu.containerId == lastMenuId && title.equals(lastMenuTitle) && hash == lastMenuHash) return;
        lastMenuId = menu.containerId;
        lastMenuTitle = title;
        lastMenuHash = hash;
        if (hash == 0) return;
        Map<Integer, SackApi.Item> items = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            items.put(i, new SackApi.Item(LegacyText.name(st), GameState.strip(LegacyText.name(st)).trim(), LegacyText.lore(st), Gain.sbId(st)));
        }
        SackDisplay.sortingType = SackDisplay.SortingType.valueOf(choice("sortingType", "DESC_STORED"));
        SackDisplay.numberFormat = SackDisplay.NumberFormat.valueOf(choice("numberFormat", "DEFAULT"));
        SackDisplay.showPrice = b("showPrice", true);
        SackDisplay.showEmpty = b("showEmpty", true);
        SackDisplay.priceFormatted = choice("priceFormat", "FORMATTED").equals("FORMATTED");
        Feature feature = Features.get(DISPLAY);
        Option shown = feature == null ? null : feature.option("sack_display.itemToShow");
        if (shown != null) SackDisplay.itemToShow = shown.integer();
        SackApi.onSackOpen(title, items);
        display = SackDisplay.drawDisplay();
        card = SackCard.live();
    }

    // SlotOverlayMixin: full sacks in red
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!sb() || !Features.on(DISPLAY) || !b("highlightFull", true) || lastMenuId == -1) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof ChestMenu) || slot.container == mc.player.getInventory()) return;
        ItemStack st = slot.getItem();
        if (!st.isEmpty() && SackApi.isFull(LegacyText.lore(st))) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FF5555);
    }

    // chat

    private static String hover(Component c, String prefix) {
        for (Component sib : c.getSiblings()) {
            var h = sib.getStyle().getHoverEvent();
            if (h instanceof HoverEvent.ShowText st) {
                String text = GameState.strip(st.value().getString());
                if (text.startsWith(prefix)) return text;
            }
        }
        return null;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!sb() || !line.plain().startsWith("[Sacks]")) return ChatBus.PASS;
        String add = hover(line.component(), "Added"), remove = hover(line.component(), "Removed");
        SackApi.onSackChat(add, remove);
        return ChatBus.PASS;
    }

    public static void register() {
        SackApi.price = id -> {
            Feature feature = Features.get(DISPLAY);
            Option source = feature == null ? null : feature.option("sack_display.priceSource");
            return source == null ? Prices.sell(id) : io.muzy.sbc.item.market.PriceSource.of(source.choice(), id);
        };
        SackApi.filletPerTrophy = Sacks::filletPerTrophy;
        SackApi.idFromName = n -> {
            String id = ItemRepo.idForName(n);
            return id != null ? id : Prices.idFromName(n);
        };
        SackApi.onSaved = Sacks::save;
        SackDisplay.itemName = ItemRepo::name;

        Features.add(new Feature(DISPLAY, "Menus", "Sack display", "Next to an open sack: every item with stored/total and its Bazaar sell value, gemstones by quality, runes by level, Magmafish for trophy sacks, and the total; full sacks are marked red. The amounts are remembered per profile and kept current from the [Sacks] chat lines.", "SkyHanni: Sack Display")
                .when(Sacks::sb)
                .option(Option.choice("sortingType", "Sorted by", "", "DESC_STORED", "DESC_STORED", "ASC_STORED", "DESC_PRICE", "ASC_PRICE"))
                .option(Option.choice("numberFormat", "Number format", "", "DEFAULT", "DEFAULT", "FORMATTED", "UNFORMATTED"))
                .option(Option.choice("priceFormat", "Price format", "", "FORMATTED", "FORMATTED", "LONG"))
                .option(Option.bool("showPrice", "Show price", "", true))
                .option(Option.bool("showEmpty", "Show empty", "", true))
                .option(Option.bool("highlightFull", "Highlight full", "", true))
                .option(Option.integer("sack_display.itemToShow", "Items shown", "Some sacks hold more than fits on a small screen.", 15, 1, 60))
                .option(Option.choice("sack_display.priceSource", "Price source", "", io.muzy.sbc.item.market.PriceSource.INSTANT_SELL, io.muzy.sbc.item.market.PriceSource.CHOICES))
                .option(Option.bool("sack_display.messagePrice", "Prices on the [Sacks] message", "Its hover text adds what the items are worth at an NPC and on the Bazaar.", true)));
        ChatBus.register(ChatBus.OBSERVE + 24, "sacks", null, Sacks::onChat);
        OutsideSackValue.register();
        Safe.endTick("Sacks.tick", Sacks::tick);
        HudLayout.add(new RichCard("sack_display", "Sack display", "Items", Zone.TOP_LEFT, 60, DISPLAY) {
            @Override public boolean visible() {
                return sb() && lastMenuId != -1 && !display.isEmpty();
            }

            // the open sack, in the band with what its worth
            @Override public String heading() {
                if (demo) return "Mining Sack";
                return lastMenuTitle.isEmpty() ? title : lastMenuTitle;
            }

            // long item names + 3 number columns. only shows next to an open menu where theres room
            @Override protected int maxWidth() {
                return 276;
            }

            @Override protected Content live() {
                return card;
            }

            @Override protected Content sample() {
                return SackCard.sample();
            }

            @Override public List<Content> states() {
                return SackCard.states();
            }

            @Override protected List<Row> classicRows() {
                List<Row> out = new ArrayList<>();
                for (String l : display) out.add(new Row(l));
                return out;
            }

            @Override protected List<Row> classicDemo() {
                return List.of(new Row("§7Items in Sacks: §o(Rendering 2 of 2 items)"), new Row(" §7- Enchanted Coal §e28,183§7/§b60.5k §6412k"), new Row(" §7- Coal §80§7/§b60.5k"), new Row("§eTotal price: §6412k"));
            }
        });
    }

    // skyhanni getAmountInSacksOrNull for other features
    public static Integer amountInSacks(String internalName) {
        return SackApi.getAmountInSacksOrNull(internalName);
    }
}
