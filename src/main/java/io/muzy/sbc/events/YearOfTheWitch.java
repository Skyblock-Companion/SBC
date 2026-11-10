// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// year of the witch, beside agnes and alcina: what everything costs vs what you have, mob type + where.
// stews part from skyhanni StewHelper
public final class YearOfTheWitch {
    public static final String ID = "witch_stew";
    static final String SHOP_TITLE = "Year of the Witch";

    // mob types + chances from the wiki, places are its example mobs
    public record Ingredient(String id, String name, String mobType, int chance, String where) {}

    static final List<Ingredient> INGREDIENTS = List.of(
            new Ingredient("FESTERING_FLY", "Festering Fly", "Undead", 5, "Zombies, Hub Graveyard"),
            new Ingredient("VOID_OOZE", "Void Ooze", "Ender", 5, "Endermen and Zealots, The End"),
            new Ingredient("SPIDER_EGG", "Spider Egg", "Arthropod", 5, "Spiders, Spider's Den"),
            new Ingredient("DIRTY_FINGERNAILS", "Dirty Fingernails", "Humanoid", 5, "Goblins and Grunts, Crystal Hollows"),
            new Ingredient("SMOLDERING_EMBERS", "Smoldering Embers", "Infernal", 5, "Blazes, Crimson Isle"),
            new Ingredient("GELATINOUS_GLOOP", "Gelatinous Gloop", "Cubic", 5, "Slimes, Deep Caverns"),
            new Ingredient("NEVER_MELT_ICE", "Never-Melt Ice", "Frozen", 10, "Icy biome of the Critter Safari, any time"),
            new Ingredient("BRITTLE_POWDER", "Brittle Powder", "Wither", 5, "Wither Skeletons, Crimson Isle"),
            new Ingredient("JAWBREAKER", "Jawbreaker", "Subterranean", 5, "Treasure Hoarders, Dwarven Mines"),
            new Ingredient("WATERBORNE_LICE", "Waterbourne Lice", "Aquatic", 5, "Sea creatures, fishing"),
            new Ingredient("FROG_LEGS", "Frog Legs", "Animal", 5, "Pigs, cows, sheep, The Farming Islands"),
            new Ingredient("PIXIE_DUST", "Pixie Dust", "Elusive", 50, "Rare mobs such as Diamond Goblins"),
            new Ingredient("CONSOLE_PANEL", "Console Panel", "Construct", 5, "Automatons, Crystal Hollows"),
            new Ingredient("SPELL_SOOT", "Spell Soot", "Arcane", 5, "Arcane mobs, Crimson Isle"),
            new Ingredient("SHIELD_DUST", "Shield Dust", "Shielded", 10, "Shielded mobs such as Scatha"),
            new Ingredient("RUFFLED_FEATHER", "Ruffled Feather", "Airborne", 10, "Bats and other flying mobs"),
            new Ingredient("FUNNY_BONE", "Funny Bone", "Skeletal", 5, "Skeletons, The Catacombs"),
            new Ingredient("GLACIATED_BRAINS", "Glaciated Brains", "Glacial", 5, "Glacite Walkers, Glacite Mineshafts"),
            new Ingredient("WARMED_FLAKES", "Warmed Flakes", "Magmatic", 5, "Fire Eels, Crimson Isle"),
            new Ingredient("ROTTED_LEAF", "Rotted Leaf", "Woodland", 5, "Woodland mobs, Galatea"),
            new Ingredient("CRUNCHY_BUG", "Crunchy Bug", "Critter", 10, "Critters, the Critter Safari"));

    static final Map<String, Ingredient> BY_NAME = new LinkedHashMap<>();

    static {
        for (Ingredient i : INGREDIENTS) BY_NAME.put(i.name(), i);
    }

    // ingredient null = an item (witch's talisman)
    record Need(String name, long amount, Ingredient ingredient) {}

    record Offer(String name, List<Need> needs, boolean eaten, boolean ready, String stock) {}

    record Entry(String name, List<String> lore) {}

    // skyhanni stewItemNamePattern, no amount = 1
    private static final Pattern STEW_ITEM = io.muzy.sbc.core.Patterns.get("SH.event.year-of-the-witch.stew-item"),
            STEW_MENU = io.muzy.sbc.core.Patterns.get("SH.event.year-of-the-witch.stew-inventory");
    private static final Pattern STOCK = Pattern.compile("(?<left>\\d+) remaining");

    private YearOfTheWitch() {}

    private static boolean opt(String key, boolean def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    // lines under "Cost" / "Requires:"
    static List<Need> needs(List<String> lore) {
        List<Need> out = new ArrayList<>();
        boolean in = false;
        for (String raw : lore) {
            String l = raw.trim();
            if (l.equals("Cost") || l.equals("Requires:")) {
                in = true;
                continue;
            }
            if (!in) continue;
            if (l.isEmpty() || l.endsWith("!") || l.endsWith(":") || l.startsWith("Annual Stock") || STOCK.matcher(l).matches()) break;
            if (l.endsWith(" Coins") || l.endsWith(" Coin")) continue;
            Matcher m = STEW_ITEM.matcher(l);
            String name = m.matches() ? m.group("item").trim() : l;
            long amount = m.matches() ? Long.parseLong(m.group("amount").replace(",", "")) : 1;
            out.add(new Need(name, amount, BY_NAME.get(name)));
        }
        return out;
    }

    // skyhanni getStewStatus
    static boolean eaten(List<String> lore) {
        return !lore.isEmpty() && lore.get(lore.size() - 1).trim().equals("You've already eaten this stew!");
    }

    // stews tell you themselves. her shop slaps "Click to trade!" on everything, so there we check amounts
    static boolean ready(List<String> lore) {
        return !lore.isEmpty() && lore.get(lore.size() - 1).trim().equals("Click to give ingredients!");
    }

    static boolean affordable(List<Need> needs) {
        for (Need n : needs) if (!known(n) || have(n) < n.amount()) return false;
        return !needs.isEmpty();
    }

    static List<Offer> offers(List<Entry> items, boolean stews) {
        List<Offer> out = new ArrayList<>();
        for (Entry e : items) {
            if (e.lore().stream().anyMatch(l -> l.trim().equals("Click to buyback!"))) continue;
            List<Need> needs = needs(e.lore());
            if (needs.isEmpty()) continue;
            String stock = "";
            for (String l : e.lore()) {
                Matcher m = STOCK.matcher(l.trim());
                if (m.matches()) stock = m.group("left") + " left";
            }
            out.add(new Offer(e.name(), needs, stews && eaten(e.lore()), stews ? ready(e.lore()) : affordable(needs), stock));
        }
        return out;
    }

    // did we ever read this one from the sacks. items (not ingredients) just get counted in the inventory
    static boolean known(Need n) {
        return n.ingredient() == null || io.muzy.sbc.item.sacks.Sacks.amountInSacks(n.ingredient().id()) != null;
    }

    // "?" when the sacks never said
    static String amount(Need n, long have) {
        return known(n) || have > 0 ? count(have) : "?";
    }

    // inventory + sacks
    static long have(Need n) {
        var mc = Minecraft.getInstance();
        long count = 0;
        if (mc != null && mc.player != null) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                boolean same = n.ingredient() != null ? n.ingredient().id().equals(Gain.sbId(st)) : GameState.strip(st.getHoverName().getString()).trim().equals(n.name());
                if (same) count += st.getCount();
            }
        }
        if (n.ingredient() != null) {
            Integer sacks = io.muzy.sbc.item.sacks.Sacks.amountInSacks(n.ingredient().id());
            if (sacks != null) count += sacks;
        }
        return count;
    }

    private static List<Entry> read(AbstractContainerScreen<?> screen) {
        List<Entry> out = new ArrayList<>();
        var mc = Minecraft.getInstance();
        for (Slot s : screen.getMenu().slots) {
            if (s.container == mc.player.getInventory() || s.getItem().isEmpty()) continue;
            List<String> lore = new ArrayList<>();
            for (String l : LegacyText.lore(s.getItem())) lore.add(GameState.strip(l));
            out.add(new Entry(GameState.strip(s.getItem().getHoverName().getString()).trim(), lore));
        }
        return out;
    }

    static String timeLeft(long now) {
        for (SbTime.Event e : SbTime.events(now, 0)) if (e.id().equals("witch") && e.active(now)) return SbTime.dur(e.end() - now);
        return "";
    }

    // per ingredient, the most one thing here asks for (a stew: 64). eaten stews ask for nothing
    static Map<String, Long> asked(List<Offer> offers) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Offer o : offers) {
            if (o.eaten()) continue;
            for (Need n : o.needs()) if (n.ingredient() != null) out.merge(n.ingredient().id(), n.amount(), Math::max);
        }
        return out;
    }

    static java.util.Set<String> eatenWith(List<Offer> offers) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Offer o : offers) if (o.eaten()) for (Need n : o.needs()) if (n.ingredient() != null) out.add(n.ingredient().id());
        return out;
    }

    enum Tile { DONE, ENOUGH, SOME, NONE, NOT_ASKED }

    static Tile tile(long have, Long need, boolean eaten) {
        if (eaten && need == null) return Tile.DONE;
        if (need == null) return have > 0 ? Tile.NOT_ASKED : Tile.NONE;
        if (have >= need) return Tile.ENOUGH;
        return have > 0 ? Tile.SOME : Tile.NONE;
    }

    private static int color(Tile t) {
        return switch (t) {
            case DONE, ENOUGH -> Modern.GOOD;
            case SOME -> Modern.WARN;
            case NOT_ASKED -> Modern.INK;
            case NONE -> Modern.DIM;
        };
    }

    private static String count(long n) {
        return n < 1000 ? String.valueOf(n) : io.muzy.sbc.events.hoppity.HoppityEnv.shortFormat(n);
    }

    private static final class Panel implements SideHelper.Content {
        private static final int TILE_H = 29, GAP = 2;
        private final boolean stews;
        private final String[] tabs;
        private List<Offer> offers = List.of();

        Panel(boolean stews, String... tabs) {
            this.stews = stews;
            this.tabs = tabs;
        }

        @Override public String title() { return stews ? "Witches Stew" : "Year of the Witch"; }
        @Override public ItemStack icon() { return LegacyItems.stack(stews ? "mushroom_stew" : "cauldron"); }
        @Override public int accent() { return 0xFFA66BFF; }
        @Override public String[] tabs() { return tabs; }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            offers = offers(read(screen), stews);
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            int at = y;
            String left = timeLeft(System.currentTimeMillis());
            if (!left.isEmpty()) {
                Modern.row(ui, "Event ends in", "§e" + left, x, at, w, Modern.LABEL, Modern.WARN);
                at += 10;
            }
            if (stews) {
                long eaten = offers.stream().filter(Offer::eaten).count();
                Modern.row(ui, "Stews eaten", "§a" + eaten + "§7/" + offers.size(), x, at, w, Modern.LABEL, Modern.GOOD);
                at += 10;
            }
            ui.textShadow(ui.ellipsize("§aenough §8· §esome §8· §7none §8· §fhover = where", w), x, at + 1, Modern.DIM);
            at += 11;
            boolean unknown = false;
            for (Ingredient i : INGREDIENTS) if (!known(new Need(i.name(), 0, i))) unknown = true;
            if (unknown) {
                // the sacks were never read whole: the counts would say 0 for what is in them
                ui.textShadow(ui.ellipsize("§eOpen your Witch's Sack once for the counts", w), x, at + 1, Modern.WARN);
                at += 11;
            }
            return at - y;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            return tab == 0 ? grid(ui, x, y, w) : items(ui, x, y, w);
        }

        // every ingredient as its picture with what you have, what this menu asks for first
        private int grid(Ui ui, int x, int y, int w) {
            Map<String, Long> asked = asked(offers);
            java.util.Set<String> eaten = eatenWith(offers);
            List<Ingredient> order = new ArrayList<>(INGREDIENTS);
            order.sort(java.util.Comparator.comparingInt(i -> asked.containsKey(i.id()) ? 0 : 1));
            String[] labels = new String[order.size()];
            long[] haves = new long[order.size()];
            Tile[] tiles = new Tile[order.size()];
            int widest = 16;
            for (int idx = 0; idx < order.size(); idx++) {
                Ingredient i = order.get(idx);
                haves[idx] = have(new Need(i.name(), 0, i));
                Long need = asked.get(i.id());
                tiles[idx] = tile(haves[idx], need, eaten.contains(i.id()));
                String own = amount(new Need(i.name(), 0, i), haves[idx]);
                labels[idx] = tiles[idx] == Tile.DONE ? "✓" : need != null ? own + "§8/" + count(need) : own;
                widest = Math.max(widest, ui.width(labels[idx]));
            }
            // as many a row as the widest label lets, so 256/64 stays whole
            int cols = Math.max(2, Math.min(5, (w + GAP) / (widest + 6 + GAP))), tw = (w - (cols - 1) * GAP) / cols;
            for (int idx = 0; idx < order.size(); idx++) {
                Ingredient i = order.get(idx);
                int tx = x + (idx % cols) * (tw + GAP), ty = y + (idx / cols) * (TILE_H + GAP);
                int c = color(tiles[idx]);
                boolean hover = ui.hover(tx, ty, tw, TILE_H);
                ui.rect(tx, ty, tw, TILE_H, Ui.alpha(c, hover ? 0.3f : 0.14f));
                ui.rect(tx, ty + TILE_H - 1, tw, 1, Ui.alpha(c, 0.9f));
                ui.icon(io.muzy.sbc.core.ItemIcons.of(i.id()), tx + (tw - 16) / 2, ty + 2, 1f);
                ui.textShadow(labels[idx], tx + (tw - ui.width(labels[idx])) / 2, ty + 19, c);
                if (hover) ui.tooltip(tip(i, haves[idx]).toArray(new String[0]));
            }
            int rows = (order.size() + cols - 1) / cols;
            return rows * (TILE_H + GAP);
        }

        private List<String> tip(Ingredient i, long have) {
            List<String> tip = new ArrayList<>(List.of("§f" + i.name(), "§7You have: §e" + String.format(java.util.Locale.ROOT, "%,d", have)));
            for (Offer o : offers) {
                if (o.eaten()) continue;
                for (Need n : o.needs()) if (n.ingredient() == i) tip.add("§7" + o.name() + ": §e" + n.amount());
            }
            tip.add("");
            tip.add("§d" + i.mobType() + " §7mobs, §e" + i.chance() + "%§7 a kill");
            tip.add("§7" + i.where());
            List<String> guide = Store.I.witchGuide.get(i.name());
            if (guide != null && !guide.isEmpty()) {
                tip.add("");
                for (String l : guide) tip.add("§7" + l);
            }
            return tip;
        }

        // her shop item by item: the name, then each cost as a small picture with have/need
        private int items(Ui ui, int x, int y, int w) {
            if (offers.isEmpty()) return SideHelper.emptyText(ui, "Nothing here asks for anything.", x, y, w);
            int at = y;
            List<Offer> order = new ArrayList<>(offers);
            // what you can take now first
            order.sort((a, b) -> Boolean.compare(!a.ready(), !b.ready()));
            for (Offer o : order) {
                int top = at, lines = 1 + o.needs().size();
                int c = o.ready() ? Modern.GOOD : Modern.INK;
                ui.rect(x, at, w, lines * 10 + 4, 0x30000000);
                ui.rect(x, at, 2, lines * 10 + 4, o.ready() ? Modern.GOOD : Modern.DIM);
                String right = o.ready() ? "§aTrade" : o.stock();
                Modern.row(ui, "§f" + o.name(), right, x + 5, at + 2, w - 7, c, o.ready() ? Modern.GOOD : Modern.DIM);
                at += 12;
                for (Need n : o.needs()) {
                    long have = have(n);
                    boolean ok = have >= n.amount();
                    String value = (ok ? "§a" : have > 0 ? "§e" : "§c") + amount(n, have) + "§8/" + count(n.amount());
                    if (n.ingredient() != null) ui.icon(io.muzy.sbc.core.ItemIcons.of(n.ingredient().id()), x + 6, at, 0.625f);
                    Modern.row(ui, (ok ? "§7" : "§f") + n.name(), value, x + 18, at + 1, w - 20, Modern.LABEL, Modern.INK);
                    if (n.ingredient() != null && ui.hover(x + 5, at, w - 5, 10)) ui.tooltip(tip(n.ingredient(), have).toArray(new String[0]));
                    at += 10;
                }
                at = top + lines * 10 + 4 + 3;
            }
            return at - y;
        }
    }

    private static final Panel SHOP = new Panel(false, "Ingredients", "Items"), STEWS = new Panel(true, "Ingredients"), SACK = new Panel(false, "Ingredients");

    private static SideHelper.Content panel(Screen s) {
        if (!opt("witch_stew.panel", true) || !(s instanceof AbstractContainerScreen<?>)) return null;
        String title = GameState.strip(s.getTitle().getString()).trim();
        // her sack opens from any island: what you hold and which mob drops each thing. the feature itself
        // only runs in the hub, so here its switch is enough
        if (title.equals("Witch's Sack")) {
            var f = Features.get(ID);
            return f != null && f.enabled() && (GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV) ? SACK : null;
        }
        if (!Features.on(ID)) return null;
        if (title.equals(SHOP_TITLE)) return SHOP;
        if (STEW_MENU.matcher(title).matches()) return STEWS;
        return null;
    }

    // from skyhanni stewHighlighter, also keeps the ingredient guide for the tooltips
    // hovered thing in her shop or the stews, what it lacks or that you can get it
    static List<String> lacking(List<String> cleanLore) {
        List<Need> needs = needs(cleanLore);
        if (needs.isEmpty() || eaten(cleanLore)) return List.of();
        List<String> out = new ArrayList<>();
        if (ready(cleanLore) || affordable(needs)) {
            out.add("§aYou have everything for this");
            return out;
        }
        boolean unknown = false;
        for (Need n : needs) {
            long have = have(n);
            if (have >= n.amount()) continue;
            if (!known(n) && have == 0) {
                out.add("§7- " + n.name() + " §e? §8(open your Witch's Sack once)");
                unknown = true;
                continue;
            }
            out.add("§7- " + n.name() + " §c" + String.format(java.util.Locale.ROOT, "%,d", n.amount() - have) + " more §8(" + String.format(java.util.Locale.ROOT, "%,d", have) + "/" + String.format(java.util.Locale.ROOT, "%,d", n.amount()) + ")");
        }
        if (!out.isEmpty()) out.add(0, unknown ? "§cMissing or not read yet:" : "§cMissing:");
        return out;
    }

    private static boolean herMenu() {
        var s = Minecraft.getInstance().gui.screen();
        if (s == null) return false;
        String t = GameState.strip(s.getTitle().getString()).trim();
        return t.equals(SHOP_TITLE) || STEW_MENU.matcher(t).matches();
    }

    private static void tooltip(ItemStack st, List<net.minecraft.network.chat.Component> lines) {
        if (!opt("witch_stew.highlight", true) || !herMenu()) return;
        List<String> lore = new ArrayList<>();
        for (String l : LegacyText.lore(st)) lore.add(GameState.strip(l));
        List<String> add = lacking(lore);
        if (add.isEmpty()) return;
        lines.add(net.minecraft.network.chat.Component.empty());
        for (String l : add) lines.add(net.minecraft.network.chat.Component.literal(l));
    }

    private static void marks(MenuView v) {
        if (v.title.equals(SHOP_TITLE) && opt("witch_stew.highlight", true)) {
            for (int slot = 0; slot < v.top; slot++) {
                MenuView.Item it = v.item(slot);
                if (!it.empty() && affordable(needs(it.cleanLore))) v.back(slot, MenuView.YELLOW);
            }
        }
        if (STEW_MENU.matcher(v.title).matches() && opt("witch_stew.highlight", true)) {
            for (int slot = 0; slot < v.top; slot++) {
                MenuView.Item it = v.item(slot);
                if (it.empty() || !it.model.equals("player_head")) continue;
                if (eaten(it.cleanLore)) v.back(slot, MenuView.GREEN);
                else if (ready(it.cleanLore)) v.back(slot, MenuView.YELLOW);
            }
        }
        if (v.title.contains("Ingredient Guide")) {
            boolean changed = false;
            for (int slot = 0; slot < v.top; slot++) {
                MenuView.Item it = v.item(slot);
                if (it.empty() || !BY_NAME.containsKey(it.clean)) continue;
                List<String> lines = guideLines(it.cleanLore);
                if (!lines.isEmpty() && !lines.equals(Store.I.witchGuide.get(it.clean))) {
                    Store.I.witchGuide.put(it.clean, lines);
                    changed = true;
                }
            }
            if (changed) Store.markDirty();
        }
    }

    static List<String> guideLines(List<String> lore) {
        List<String> out = new ArrayList<>();
        for (String l : lore) {
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("Click") || t.startsWith("Right-click") || t.startsWith("Left-click")) continue;
            out.add(t);
        }
        return out;
    }

    static void register() {
        Features.add(new Feature(ID, "Events", "Year of the Witch", "Beside Agnes's shop and Alcina's stews: what each thing asks for against what you have in your inventory and sacks, which mob type drops every ingredient and where to find those mobs. Eaten stews are green, ones you can make now yellow.", "SkyHanni: Year of the Witch")
                .when(() -> "HUB".equals(GameState.island) || io.muzy.sbc.SbcMod.DEV)
                .option(Option.bool("witch_stew.panel", "Panel beside her menus", "", true))
                .option(Option.bool("witch_stew.highlight", "Colour what you can take", "Yellow in her shop and the stews, eaten stews green, and what a thing still lacks on its tooltip.", true)))
                .tooltip(YearOfTheWitch::tooltip);
        SideHelper.add(YearOfTheWitch::panel);
        MenuMarks.add(ID, t -> true, YearOfTheWitch::marks);
    }
}
