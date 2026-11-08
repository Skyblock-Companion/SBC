// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemIcons;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ah / bz search while you type: "hyp" lists hyperion and everything else starting like it, with bz and lbin prices
// picking one sends that markets own search
public final class MarketSearchScreen extends Screen {
    private record Cand(String id, String name, String lower, boolean bazaar, boolean ah, double value) {}

    private static List<Cand> index;
    private static int indexSize = -1;

    private static final int ROW = 24, MAX = 9;

    private final Ui ui = new Ui();
    private final StringBuilder query = new StringBuilder();
    private final Screen parent;
    private int mode; // 0 all, 1 bazaar, 2 auction
    private int sel;
    private List<Cand> results = List.of();
    private String shownFor = null;
    // matches in this mode + in each of the 3
    private int found;
    private final int[] modeCounts = new int[3];
    // rows that fit + which result the first row shows
    private int visible = MAX, first;
    // only a mouse that actually moved (or a list that moved under it) picks a row, otherwise keys do
    private int lastMx = Integer.MIN_VALUE, lastMy;
    private boolean wheeled;
    // the sign hypixel opened behind the Search button, it gets the answer instead of a command
    private net.minecraft.world.level.block.entity.SignBlockEntity sign;
    private boolean signFront, museum;
    private boolean maxPetLevel;
    private int dungeonStars;
    private List<Cand> history = List.of();

    public MarketSearchScreen(Screen parent, int mode) {
        super(Component.literal("Market search"));
        this.parent = parent;
        this.mode = mode;
    }

    // --- SearchOverManager + the sign check from LocalPlayerMixin ---

    private static dev.zmuzy.sbc.feature.Option option(String key) {
        var f = dev.zmuzy.sbc.feature.Features.get("market_search");
        return f == null ? null : f.option(key);
    }

    private static boolean on(String key, boolean def) {
        var o = option(key);
        return o == null ? def : o.bool();
    }

    private static int number(String key, int def) {
        var o = option(key);
        return o == null ? def : o.integer();
    }

    // a search sign over ah / bz / museum opens this instead
    public static boolean onSign(net.minecraft.world.level.block.entity.SignBlockEntity sign, boolean front) {
        Minecraft mc = Minecraft.getInstance();
        Screen open = mc.gui.screen();
        if (open == null || !dev.zmuzy.sbc.feature.Features.on("market_search")) return false;
        if (!sign.getText(front).getMessage(3, false).getString().equalsIgnoreCase("enter query")) return false;
        String title = open.getTitle().getString();
        int mode;
        boolean museum = false;
        if (on("market_search.auctions", true) && title.contains("Auction") || on("market_search.ironman", false) && title.contains("Cosmetics")) mode = 2;
        else if (on("market_search.bazaar", true) && title.contains("Bazaar")) mode = 1;
        else if (on("market_search.museum", true) && title.contains("Museum")) {
            mode = 0;
            museum = true;
        } else return false;
        MarketSearchScreen s = new MarketSearchScreen(null, mode);
        s.sign = sign;
        s.signFront = front;
        s.museum = museum;
        if (on("market_search.keepPrevious", false)) {
            net.minecraft.network.chat.Component[] messages = sign.getText(front).getMessages(mc.isTextFilteringEnabled());
            String search = messages[0].getString();
            if (!messages[1].getString().isEmpty()) search += (search.endsWith(" ") ? "" : " ") + messages[1].getString();
            s.query.append(search);
        }
        mc.gui.setScreen(s);
        return true;
    }

    private static String historyKey(int mode, boolean museum) {
        return "market_search.history." + (museum ? "museum" : mode == 1 ? "bazaar" : "auction");
    }

    @SuppressWarnings("unchecked")
    static List<String> savedHistory(int mode, boolean museum) {
        Object v = dev.zmuzy.sbc.core.Config.I.options.get(historyKey(mode, museum));
        return v instanceof List<?> l ? new ArrayList<>((List<String>) l) : new ArrayList<>();
    }

    // newest first, as long as the setting says
    static List<String> addToHistory(List<String> history, String search, int length) {
        List<String> out = new ArrayList<>(history);
        out.remove(search);
        out.addFirst(search);
        return out.size() > length ? new ArrayList<>(out.subList(0, length)) : out;
    }

    private void saveHistory(String search) {
        if (search.isBlank()) return;
        dev.zmuzy.sbc.core.Config.I.options.put(historyKey(mode, museum), addToHistory(savedHistory(mode, museum), search, number("market_search.historyLength", 3)));
        dev.zmuzy.sbc.core.Config.save();
    }

    // SearchOverManager.splitString: sign line = 15 chars, dont cut words
    static String[] splitString(String s) {
        if (s.length() <= 15) return new String[]{s, ""};
        int index = s.lastIndexOf(' ', 15);
        if (index == -1) return new String[]{s.substring(0, 15), ""};
        return new String[]{s.substring(0, index), s.substring(index + 1, Math.min(index + 16, s.length())).trim()};
    }

    // addExtras: pets only for pet names, top level if asked, stars for stuff that has them
    static String extras(String search, boolean isPet, int petMaxLevel, boolean maxPet, boolean starable, int stars) {
        String out = search;
        if (isPet) out = maxPet ? "[Lvl " + petMaxLevel + "] " + out : "] " + out;
        if (stars > 0 && starable) {
            StringBuilder sb = new StringBuilder(" ").append("✪".repeat(Math.min(stars, 5)));
            if (stars >= 6 && stars <= 10) sb.append("➊➋➌➍➎".charAt(stars - 6));
            out += sb;
        }
        return out;
    }

    private String withExtras(String search, boolean auction) {
        if (!auction) return search;
        String petKey = search.toUpperCase(Locale.ROOT).replace(' ', '_');
        boolean pet = ItemRepo.petTypes().contains(petKey);
        String id = ItemRepo.idForName(search);
        boolean starable = id != null && ItemRepo.upgradeCosts(id) != null;
        return extras(search, pet, pet ? dev.zmuzy.sbc.core.Pets.maxLevel(petKey) : 100, maxPetLevel, starable, dungeonStars);
    }

    // answer goes back on the sign, 2 lines max
    private void pushSign(String search) {
        Minecraft mc = Minecraft.getInstance();
        // search breaks if the input starts with "null" (null ovoid etc) so quote it
        String text = search.toLowerCase(Locale.ENGLISH).startsWith("null") ? "\"" + search + "\"" : search;
        String[] split = splitString(text);
        var net = mc.getConnection();
        if (net != null && sign != null) {
            net.minecraft.network.chat.Component[] messages = sign.getText(signFront).getMessages(mc.isTextFilteringEnabled());
            net.send(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(sign.getBlockPos(), signFront, split[0], split[1], messages[2].getString(), messages[3].getString()));
        }
        sign = null;
        mc.gui.setScreen(null);
    }

    private void answer(String search, boolean bazaar) {
        saveHistory(search);
        String text = withExtras(search.trim(), !bazaar && !museum);
        if (sign != null) pushSign(text);
        else send(bazaar ? "bz" : "auctionsearch", text);
    }

    private static int openFromCommand(boolean auction, String item) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            MarketSearchScreen s = new MarketSearchScreen(null, auction ? 2 : 1);
            s.query.append(item);
            mc.gui.setScreen(s);
        });
        return 1;
    }

    // only when the setting is on, otherwise they stay hypixels
    public static void registerCommands() {
        var f = dev.zmuzy.sbc.feature.Features.get("market_search");
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.bazaar", "For the Bazaar's Search button", "The button's sign opens this search.", true));
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.auctions", "For the Auction House's Search button", "", true));
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.ironman", "For the Ironman cosmetics Auction House", "", false));
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.museum", "For the Museum's Search button", "", true));
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.keepPrevious", "Keep the previous search", "The sign's text starts the search.", false));
        f.option(dev.zmuzy.sbc.feature.Option.integer("market_search.maxSuggestions", "Rows at once", "The list scrolls for the rest.", MAX, 1, MAX));
        f.option(dev.zmuzy.sbc.feature.Option.integer("market_search.historyLength", "Searches remembered", "Shown before you type.", 3, 0, 10));
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.commands", "Open from /ahs, /ahsearch and /bzs", "Off: /ahs stays Hypixel's own.", false));
        f.option(dev.zmuzy.sbc.feature.Option.bool("market_search.autocomplete", "Suggest names for /ahs, /ahsearch and /bzs", "", true));
        Safe.commands("MarketSearchScreen.commands", (dispatcher, registry) -> {
            java.util.function.Predicate<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> allowed = s -> on("market_search.commands", false) && GameState.inSkyBlock;
            for (String[] c : new String[][]{{"ahs", "a"}, {"ahsearch", "a"}, {"bzs", "b"}}) {
                boolean auction = c[1].equals("a");
                // off: fabric doesnt know it so it goes to hypixel, names still get suggested
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(c[0])
                        .executes(x -> {
                            if (!allowed.test(x.getSource())) throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
                            return openFromCommand(auction, "");
                        })
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .suggests((x, b) -> {
                                    if (on("market_search.autocomplete", true)) for (Cand cand : index()) if ((auction ? cand.ah : cand.bazaar) && cand.lower.startsWith(b.getRemainingLowerCase())) b.suggest(cand.name);
                                    return b.buildFuture();
                                })
                                .executes(x -> {
                                    if (!allowed.test(x.getSource())) throw com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
                                    return openFromCommand(auction, com.mojang.brigadier.arguments.StringArgumentType.getString(x, "item"));
                                })));
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    private static String enchantName(String bzId) {
        // ENCHANTMENT_ULTIMATE_WISE_1 -> "Ultimate Wise". level dropped so the search lists every level
        String s = bzId.substring("ENCHANTMENT_".length());
        int us = s.lastIndexOf('_');
        if (us > 0 && s.substring(us + 1).chars().allMatch(Character::isDigit)) s = s.substring(0, us);
        return GameState.pretty(s);
    }

    // AuctionBazaarAutocomplete: the names /bz takes
    public static List<String> bazaarNames() {
        List<String> out = new ArrayList<>();
        for (Cand c : index()) if (c.bazaar) out.add(c.name);
        return out;
    }

    private static List<Cand> index() {
        int size = ItemRepo.ids().size() + Prices.all().size();
        if (index != null && size == indexSize) return index;
        indexSize = size;
        List<Cand> out = new ArrayList<>(size);
        Set<String> seen = new HashSet<>();
        for (String id : ItemRepo.ids()) {
            if (id.startsWith("STARRED_") || id.contains(";") && !id.equals("PET")) continue;
            String name = ItemRepo.name(id);
            if (name == null || name.isBlank()) continue;
            boolean bz = Prices.get(id) != null;
            double bin = AuctionPrices.lowestBin(id);
            if (!seen.add(name.toLowerCase(Locale.ROOT) + (bz ? "|bz" : "|ah"))) continue;
            out.add(new Cand(id, name, name.toLowerCase(Locale.ROOT), bz, bin > 0, Math.max(bin, bz ? Prices.get(id).buy() : 0)));
        }
        for (var e : Prices.all().entrySet()) {
            String id = e.getKey();
            if (ItemRepo.ids().contains(id) || !id.startsWith("ENCHANTMENT_")) continue;
            String name = enchantName(id);
            if (!seen.add(name.toLowerCase(Locale.ROOT) + "|bz")) continue;
            out.add(new Cand(id, name, name.toLowerCase(Locale.ROOT), true, false, e.getValue().buy()));
        }
        index = out;
        return out;
    }

    private void search() {
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        if (q.equals(shownFor)) return;
        shownFor = q;
        sel = 0;
        first = 0;
        found = 0;
        java.util.Arrays.fill(modeCounts, 0);
        if (q.isEmpty()) {
            // getHistory: what you looked for last, till you type
            List<Cand> saved = new ArrayList<>();
            for (String name : savedHistory(mode, museum)) {
                String lower = name.toLowerCase(Locale.ROOT);
                for (Cand c : index()) {
                    if (c.lower.equals(lower)) {
                        saved.add(c);
                        break;
                    }
                }
            }
            results = saved;
            found = saved.size();
            return;
        }
        String[] words = q.split("\\s+");
        List<double[]> scored = new ArrayList<>();
        List<Cand> list = index();
        for (int i = 0; i < list.size(); i++) {
            Cand c = list.get(i);
            if (museum && !ItemRepo.museumEligible(c.id)) continue;
            boolean all = true;
            double score = 0;
            for (String w : words) {
                int at = c.lower.indexOf(w);
                if (at < 0) {
                    all = false;
                    break;
                }
                score += at == 0 ? 3 : c.lower.charAt(at - 1) == ' ' ? 2 : 0.5;
            }
            if (!all) continue;
            modeCounts[0]++;
            modeCounts[c.bazaar ? 1 : 2]++;
            if (mode == 1 && !c.bazaar || mode == 2 && c.bazaar) continue;
            if (c.lower.equals(q)) score += 5;
            scored.add(new double[]{i, score + Math.log10(1 + c.value) * 0.05 - c.lower.length() * 0.004});
        }
        found = scored.size();
        scored.sort((a, b) -> Double.compare(b[1], a[1]));
        List<Cand> out = new ArrayList<>(scored.size());
        for (double[] s : scored) out.add(list.get((int) s[0]));
        results = out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ui.begin(g, mouseX, mouseY);
        paint(ui);
        ui.flushTooltip();
    }

    void paint(Ui ui) {
        search();
        ui.rect(0, 0, width, height, 0xAA060810);
        paintModern(ui);
    }

    // for pictures
    void preview(String typed, int chosen, int selected) {
        query.setLength(0);
        query.append(typed);
        mode = chosen;
        shownFor = null;
        search();
        sel = selected;
    }

    private static final int BAZAAR = Modern.WARN, AUCTION = Modern.AQUA;
    private static final String[] MODES = {"All", "Bazaar", "Auction House"};
    private static final int[] MODE_COLORS = {Modern.GOLD, BAZAAR, AUCTION};

    private void paintModern(Ui ui) {
        int w = Math.min(width - 24, 380), x = width / 2 - w / 2, y = Math.max(6, height / 2 - 140);
        int head = Modern.BAND + 2 + 16 + 4 + 14 + 4 - 2, foot = 14;
        visible = Math.max(1, Math.min(number("market_search.maxSuggestions", MAX), (height - y - 4 - head - foot) / ROW));
        first = Math.max(0, Math.min(first, Math.max(0, results.size() - visible)));
        if (sel < first) first = sel;
        else if (sel >= first + visible) first = sel - visible + 1;
        int shown = Math.min(results.size(), visible), listH = shown > 0 ? shown * ROW : 34;
        Theme.current().panel(ui, x, y, w, head + listH + foot);
        ui.textLimit = x + w;
        Modern.band(ui, "Market Search", LegacyItems.stack("minecraft:gold_ingot"), "", Modern.GOLD, x, y, w);
        if (found > 0) {
            String right = found > shown ? "§f" + (first + 1) + "-" + (first + shown) + "§7/§f" + number(found) + " §b" + ItemList.percent((double) shown / found) : "§f" + number(found) + (found == 1 ? " §7result" : " §7results");
            ui.textShadow(right, x + w - Modern.PAD - ui.width(right), y + 3, Modern.LABEL);
        }
        int cx = x + Modern.PAD, cw = w - 2 * Modern.PAD, cy = y + Modern.BAND + 2;
        ItemList.searchBox(ui, cx, cy, cw, 16, query.toString(), true, "", Modern.GOLD);
        if (query.length() == 0) ui.textShadow("Type an item, e.g. hyp, shard, sharpness...", cx + Modern.ICON + 8, cy + 4, Modern.DIM);
        cy += 20;

        int tx = cx;
        for (int i = 0; i < MODES.length; i++) {
            final int m = i;
            String label = MODES[i], num = query.length() == 0 ? "" : " " + number(modeCounts[i]);
            int tw = ui.width(label + num) + 12, c = MODE_COLORS[i];
            boolean on = mode == i, over = ui.hover(tx, cy, tw, 14);
            ui.rect(tx, cy, tw, 14, on ? Ui.alpha(c, 0.26f) : over ? Ui.alpha(c, 0.14f) : 0x48000000);
            ui.rect(tx, cy + 13, tw, 1, on ? c : Ui.alpha(c, over ? 0.8f : 0.32f));
            ui.textShadow(label, tx + 6, cy + 3, on ? 0xFFFFFFFF : Modern.LABEL);
            if (!num.isEmpty()) ui.textShadow(num, tx + 6 + ui.width(label), cy + 3, on ? Modern.lighten(c, 0.25f) : Modern.DIM);
            ui.click(tx, cy, tw, 14, () -> {
                mode = m;
                shownFor = null;
            });
            tx += tw + 2;
        }
        // can we trust the numbers below: the 2 price feeds this screen reads
        if (mode == 2 && !museum) {
            // OverlayScreen's 2 buttons: max level pets + stars to search with
            String pet = (maxPetLevel ? "§a" : "§7") + "Max pet";
            int pw = ui.width(pet) + 8;
            ui.rect(tx, cy, pw, 14, maxPetLevel ? Ui.alpha(Modern.GOOD, 0.2f) : 0x48000000);
            ui.textShadow(pet, tx + 4, cy + 3, Modern.LABEL);
            ui.click(tx, cy, pw, 14, () -> maxPetLevel = !maxPetLevel);
            tx += pw + 2;
            String stars = dungeonStars == 0 ? "§7✪" : "§6" + "✪".repeat(Math.min(dungeonStars, 5)) + (dungeonStars > 5 ? "§c" + (dungeonStars - 5) : "");
            int sw = ui.width(stars) + 8;
            ui.rect(tx, cy, sw, 14, 0x48000000);
            ui.textShadow(stars, tx + 4, cy + 3, Modern.GOLD);
            ui.clickRight(tx, cy, sw, 14, () -> dungeonStars = (dungeonStars + 1) % 11, () -> dungeonStars = (dungeonStars + 10) % 11);
        }
        String feeds = (Prices.ready() ? "§a" : "§6") + "● §7Bazaar  " + (AuctionPrices.ready() ? "§a" : "§6") + "● §7BIN";
        ui.textShadow(feeds, x + w - Modern.PAD - ui.width(feeds), cy + 3, Modern.LABEL);
        cy += 18;

        if (results.isEmpty()) {
            String none = query.length() == 0 ? "Start typing to see prices as you go." : "Nothing matches. Enter searches the " + (mode == 1 ? "Bazaar" : "Auction House") + " for exactly this.";
            ui.textShadow(ui.ellipsize(none, cw), x + (w - Math.min(cw, ui.width(none))) / 2, cy + 13, Modern.DIM);
        }
        boolean moved = ui.mx != lastMx || ui.my != lastMy || wheeled;
        lastMx = ui.mx;
        lastMy = ui.my;
        wheeled = false;
        for (int i = 0; i < shown; i++) {
            final int idx = first + i;
            row(ui, results.get(idx), idx, moved, x, cy + i * ROW, w);
            ui.clickRight(x + 2, cy + i * ROW, w - 4, ROW - 1, () -> run(results.get(idx), false), () -> run(results.get(idx), true));
        }
        if (results.size() > visible) {
            int barH = visible * ROW - 1, th = Math.max(10, barH * visible / results.size());
            int ty = cy + (barH - th) * first / Math.max(1, results.size() - visible);
            ui.rect(x + w - 4, cy, 2, barH, 0x50000000);
            ui.rect(x + w - 4, ty, 2, th, Modern.GOLD);
        }
        cy += listH;
        Modern.rule(ui, x + Modern.PAD, cy, cw);
        String keys = "§eEnter§7 search  §eRight click§7 other market  §e↑↓§7/§eWheel§7 pick  §eTab§7 market  §eEsc§7 back";
        ui.textShadow(ui.ellipsize(keys, cw), x + (w - Math.min(cw, ui.width(keys))) / 2, cy + 3, Modern.LABEL);
    }

    private static String number(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // whole coins from 100 up, decimal under that
    private static String exact(double v) {
        return String.format(Locale.ROOT, Math.abs(v) >= 100 ? "%,.0f" : "%,.1f", v);
    }

    // 0 = none
    private static int level(String bzId) {
        String n = bzId.substring(bzId.lastIndexOf('_') + 1);
        return !n.isEmpty() && n.length() < 3 && n.chars().allMatch(Character::isDigit) ? Integer.parseInt(n) : 0;
    }

    private static String roman(int n) {
        String[] upTo = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n < upTo.length ? upTo[n] : String.valueOf(n);
    }

    // "§6§lLEGENDARY DUNGEON SWORD", "" when there is none
    private static String kindOf(String id) {
        List<String> lore = ItemRepo.lore(id);
        for (int i = lore.size() - 1; i >= 0; i--) {
            String l = GameState.strip(lore.get(i)).trim();
            if (!l.isEmpty()) return l.equals(l.toUpperCase(Locale.ROOT)) ? lore.get(i) : "";
        }
        return "";
    }

    private void row(Ui ui, Cand c, int i, boolean mouseMoved, int x, int y, int w) {
        boolean over = ui.hover(x + 2, y, w - 4, ROW - 1);
        if (over && mouseMoved) sel = i;
        boolean on = i == sel, book = c.id.startsWith("ENCHANTMENT_");
        int market = c.bazaar ? BAZAAR : AUCTION;
        if (on) {
            ui.rect(x + 2, y, w - 4, ROW - 1, Ui.alpha(market, 0.18f));
            ui.rect(x + 2, y, 2, ROW - 1, market);
        }
        int color = book ? ItemList.readable(0xFF5555FF) : ItemList.colorOf(ItemRepo.coloredName(c.id));
        ui.rect(x + 6, y + 2, 18, 18, 0x50000000);
        ui.rect(x + 6, y + 19, 18, 1, Ui.alpha(color, 0.75f));
        try {
            ui.icon(book ? ItemIcons.of("ENCHANTED_BOOK") : ItemIcons.of(c.id), x + 7, y + 3, 1f);
        } catch (Throwable ignored) {
        }
        String name = GameState.strip(ItemRepo.coloredName(c.id).isEmpty() || book ? c.name : ItemRepo.coloredName(c.id));
        if (book && level(c.id) > 0) name += " §7(" + roman(level(c.id)) + ")";
        int tx = x + 28, right = x + w - 6, line1 = y + 2, line2 = y + 12;

        Prices.Product p = Prices.get(c.id);
        double bin = AuctionPrices.lowestBin(c.id), npc = book ? 0 : ItemRepo.npcPrice(c.id);
        String top, bottom;
        if (p != null) {
            top = "§7Buy §6" + Prices.coins(p.buy());
            bottom = "§7Sell §e" + Prices.coins(p.sell());
        } else {
            top = bin > 0 ? "§7BIN §6" + Prices.coins(bin) : "§7no price";
            bottom = npc > 0 ? "§7NPC §e" + Prices.coins(npc) : "";
        }
        int rw = Math.max(ui.width(top), ui.width(bottom));
        ui.textShadow(ui.ellipsize(name, right - rw - 6 - tx), tx, line1, on ? Modern.lighten(color, 0.25f) : color);
        ui.textShadow(top, right - ui.width(top), line1, Modern.LABEL);
        if (!bottom.isEmpty()) ui.textShadow(bottom, right - ui.width(bottom), line2, Modern.LABEL);

        // under one market tab its name says nothing new, give the room to whats after
        int mx = tx;
        if (mode == 0) {
            String where = c.bazaar ? "Bazaar" : "Auction House";
            ui.textShadow(where, tx, line2, Modern.darken(market, 0.3f));
            mx += ui.width(where) + 6;
        }
        String more = "";
        if (p != null) {
            more = (p.buy() > 0 ? "§7spread §f" + Prices.coins(p.buy() - p.sell()) + " §b" + ItemList.percent((p.buy() - p.sell()) / p.buy()) + "  " : "")
                    + "§7week §f" + Prices.coins(p.buyWeek()) + " §7bought §f" + Prices.coins(p.sellWeek()) + " §7sold";
        } else if (!book) more = GameState.strip(kindOf(c.id));
        if (!more.isEmpty()) ui.textShadow(ui.ellipsize(more, right - rw - 6 - mx), mx, line2, Modern.DIM);
        if (over && on) rowTip(ui, c, book, p, bin, npc);
    }

    // everything we know, exact, nothing made up
    private void rowTip(Ui ui, Cand c, boolean book, Prices.Product p, double bin, double npc) {
        List<String> tip = new ArrayList<>();
        String colored = ItemRepo.coloredName(c.id);
        tip.add(book ? "§9" + c.name + (level(c.id) > 0 ? " " + roman(level(c.id)) : "") : colored == null || colored.isEmpty() ? c.name : colored);
        String kind = book ? "" : kindOf(c.id);
        if (!kind.isEmpty()) tip.add(kind);
        if (p != null) {
            tip.add("§7Buy: §6" + exact(p.buy()));
            tip.add("§7Sell: §e" + exact(p.sell()));
            if (p.buy() > 0) tip.add("§7Spread: §f" + exact(p.buy() - p.sell()) + " §b" + ItemList.percent((p.buy() - p.sell()) / p.buy()));
            tip.add("§7Bought this week: §f" + exact(p.buyWeek()));
            tip.add("§7Sold this week: §f" + exact(p.sellWeek()));
            tip.add("§7Buy orders: §f" + number(p.buyOrders()));
            tip.add("§7Sell orders: §f" + number(p.sellOrders()));
        }
        if (bin > 0) tip.add("§7Lowest BIN: §6" + exact(bin));
        if (npc > 0) tip.add("§7NPC sell: §e" + exact(npc));
        double value = AuctionPrices.value(c.id);
        if (value > 0) tip.add("§7Value: §6" + exact(value));
        if (book) {
            // every level on sale, the name opens a search that lists them all anyway
            String stem = c.id.substring(0, c.id.lastIndexOf('_') + 1);
            boolean headed = false;
            for (int l = 1; l <= 10; l++) {
                Prices.Product at = Prices.get(stem + l);
                if (at == null) continue;
                if (!headed) tip.add("");
                headed = true;
                tip.add("§9" + roman(l) + "  §7Buy §6" + Prices.coins(at.buy()) + "  §7Sell §e" + Prices.coins(at.sell()));
            }
        }
        tip.add("");
        tip.add("§eClick§7 search " + (c.bazaar ? "the Bazaar" : "the Auction House"));
        tip.add("§eRight click§7 search " + (c.bazaar ? "the Auction House" : "the Bazaar"));
        ui.tooltip(tip.toArray(new String[0]));
    }

    private void run(Cand c, boolean other) {
        answer(c.name, c.bazaar != other);
    }

    private void send(String cmd, String name) {
        Minecraft mc = Minecraft.getInstance();
        var conn = mc.getConnection();
        mc.gui.setScreen(null);
        if (conn != null && !name.isBlank()) conn.sendCommand(cmd + " " + name.trim());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit h = ui.hits.get(i);
            if (e.x() < h.x() || e.y() < h.y() || e.x() >= h.x() + h.w() || e.y() >= h.y() + h.h()) continue;
            Runnable r = e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && h.right() != null ? h.right() : h.click();
            if (r != null) {
                r.run();
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sideways, double dy) {
        first = Math.max(0, Math.min(Math.max(0, results.size() - visible), first - (int) Math.signum(dy)));
        sel = Math.max(first, Math.min(first + visible - 1, sel));
        wheeled = true;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        String c = e.codepointAsString();
        if (!c.isEmpty() && c.charAt(0) >= 32 && query.length() < 40) query.append(c);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (k == GLFW.GLFW_KEY_BACKSPACE && query.length() > 0) {
            query.setLength(query.length() - 1);
            return true;
        }
        if (k == GLFW.GLFW_KEY_DOWN) {
            sel = Math.max(0, Math.min((results.size()) - 1, sel + 1));
            return true;
        }
        if (k == GLFW.GLFW_KEY_UP) {
            sel = Math.max(0, sel - 1);
            return true;
        }
        if (k == GLFW.GLFW_KEY_TAB) {
            mode = (mode + 1) % 3;
            shownFor = null;
            return true;
        }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (!results.isEmpty() && sel < results.size()) run(results.get(sel), false);
            else if (query.length() > 0) answer(query.toString(), mode == 1);
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        // server waits for its sign to come back
        if (sign != null) pushSign(query.toString().trim());
        else Minecraft.getInstance().gui.setScreen(parent);
    }

    // ah + bz menus, the Search button opens this instead of the sign
    public static boolean isMarketTitle(String title) {
        String l = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return l.contains("bazaar") || l.contains("auction");
    }

    public static int modeFor(String title) {
        String l = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return l.contains("bazaar") ? 1 : l.contains("auction") ? 2 : 0;
    }
}
