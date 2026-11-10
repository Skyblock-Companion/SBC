// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.core.AuctionPrices;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemIcons;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Pets;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.plan.PetHelper;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// pet menu in the classic look: everything you own (level, xp, rarity), whats maxed, whats missing + the PetHelper plan
// pets come from the api profile when there is one
public final class PetMenu {
    public static final String[] TABS = {"Missing", "Owned"};
    public static final String[] SORTS = {"Level", "Rarity", "Name", "XP left", "Value"};
    private static final int ACCENT = 0xFFFF9E80, ROW_H = 26;
    private static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF7C7CFF, 0xFFC060E0, 0xFFFFAA00, 0xFFFF55FF};
    private static final Pattern PET_NAME = Pattern.compile("^\\[Lvl (\\d+)\\] (?:\\[[^]]*\\] )?(.+)$");
    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private PetMenu() {}

    // what the screen remembers between frames
    public static final class View {
        public int tab, sort, contentH;
        public float scroll, scrollTarget;
    }

    // your pets + prices for the plan
    public record Data(List<PetHelper.Owned> mine, Map<String, Set<String>> tiers, PetHelper.Plan plan, boolean fromMenu) {}

    public static String pretty(String type) {
        StringBuilder sb = new StringBuilder();
        for (String w : type.toLowerCase(Locale.ROOT).split("_")) sb.append(sb.isEmpty() ? "" : " ").append(w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        return sb.toString();
    }

    static int color(String tier) {
        int r = PetHelper.rarity(tier);
        return r < 0 ? Modern.LABEL : COLORS[r];
    }

    static ItemStack icon(String type, String tier) {
        return ICONS.computeIfAbsent(type + "/" + tier, k -> {
            try {
                return ItemIcons.pet(type, tier);
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        });
    }

    static String word(String tier) {
        return tier.isEmpty() ? "" : tier.charAt(0) + tier.substring(1).toLowerCase(Locale.ROOT);
    }

    // --- where the pets come from ---

    private static PetHelper.Owned owned(String type, String name, String tier, int level, double exp, boolean active, String held, double value) {
        return new PetHelper.Owned(type, name, tier, level, Pets.maxLevel(type), exp, Pets.xpForMax(tier, type), active, held == null ? "" : held, value);
    }

    public static List<PetHelper.Owned> mine() {
        Profile.Data d = Profile.current;
        List<PetHelper.Owned> out = new ArrayList<>();
        if (d != null && !d.pets.isEmpty()) {
            for (Profile.Pet p : d.pets) out.add(owned(p.type(), p.name(), p.tier(), p.level(), p.exp(), p.active(), p.heldItem(), p.value()));
            return out;
        }
        // profile read from the game has its own pets, none = an answer too. another profiles pets arent yours here
        if (d != null && d.fromGame) return out;
        for (Store.SeenPet p : Store.I.petsSeen.values()) {
            out.add(owned(p.type, p.name, p.tier, p.level, Pets.xpAtLevel(p.tier, p.level), p.active, p.held, AuctionPrices.pet(p.type, p.tier, Pets.xpAtLevel(p.tier, p.level), p.held)));
        }
        return out;
    }

    public static Data read() {
        List<PetHelper.Owned> mine = mine();
        Map<String, Set<String>> tiers = ItemRepo.petTiers();
        PetHelper.Plan plan = PetHelper.plan(mine, tiers, (type, tier) -> AuctionPrices.lowestBin(AuctionPrices.petKey(type, tier, false)));
        return new Data(mine, tiers, plan, Profile.current == null || Profile.current.fromGame || Profile.current.pets.isEmpty());
    }

    private static String tierOf(String coloredName) {
        String s = coloredName;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            char c = s.charAt(i + 1);
            switch (c) {
                case 'f' -> {
                    return "COMMON";
                }
                case 'a' -> {
                    return "UNCOMMON";
                }
                case '9' -> {
                    return "RARE";
                }
                case '5' -> {
                    return "EPIC";
                }
                case '6' -> {
                    return "LEGENDARY";
                }
                case 'd' -> {
                    return "MYTHIC";
                }
                default -> {
                }
            }
        }
        return "";
    }

    // any page of hypixels pets menu
    static boolean isPetsTitle(String title) {
        return title.equals("Pets") || title.matches("\\(\\d+/\\d+\\) Pets") || title.matches("Pets \\(\\d+/\\d+\\)");
    }

    // remember what hypixels pets menu shows
    public static void tick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        if (!isPetsTitle(GameState.strip(cs.getTitle().getString()).trim())) return;
        boolean changed = false;
        for (var slot : cs.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = LegacyText.name(st);
            Matcher m = PET_NAME.matcher(GameState.strip(name).trim());
            if (!m.matches()) continue;
            String tier = tierOf(name);
            if (tier.isEmpty()) continue;
            Store.SeenPet p = new Store.SeenPet();
            p.name = m.group(2).replace("✦", "").trim();
            p.type = p.name.toUpperCase(Locale.ROOT).replace(' ', '_');
            p.tier = tier;
            p.level = Integer.parseInt(m.group(1));
            var lore = st.get(DataComponents.LORE);
            if (lore != null) for (Component line : lore.lines()) {
                String l = GameState.strip(line.getString()).trim();
                if (l.startsWith("Held Item: ")) p.held = ItemRepo.idForName(l.substring(11));
                if (l.contains("Click to despawn")) p.active = true;
            }
            Store.SeenPet old = Store.I.petsSeen.put(p.type + "/" + p.tier, p);
            if (old == null || old.level != p.level || old.active != p.active) changed = true;
        }
        if (changed) Store.markDirty();
    }

    // --- drawing ---

    private static void tile(Ui ui, int x, int y, int w, String label, String value, String note, double meter, int color) {
        ui.rect(x, y, w, 30, 0x44000000);
        ui.rect(x, y, w, 1, 0x18FFFFFF);
        ui.textShadow(label, x + 4, y + 3, Modern.LABEL);
        ui.textShadow(value, x + 4, y + 13, color);
        if (!note.isEmpty()) ui.textShadow(note, x + w - 4 - ui.width(note), y + 13, Modern.DIM);
        if (meter >= 0) Modern.meter(ui, x + 4, y + 25, w - 8, Modern.METER, meter, color);
    }

    private static void pips(Ui ui, int x, int y, String type, String tier, Map<String, Set<String>> tiers) {
        Set<String> exists = tiers.getOrDefault(type, Set.of());
        int have = PetHelper.rarity(tier);
        for (int i = 0; i < PetHelper.RARITY.length; i++) {
            boolean there = exists.contains(PetHelper.RARITY[i]) || i == have;
            ui.rect(x + i * 7, y, 6, 4, 0xB0050911);
            if (!there) continue;
            ui.rect(x + i * 7 + 1, y + 1, 4, 2, i <= have ? COLORS[i] : Ui.alpha(COLORS[i], 0.28f));
        }
    }

    private static int petRow(Ui ui, PetHelper.Owned p, Map<String, Set<String>> tiers, int x, int y, int w) {
        int c = color(p.tier());
        boolean over = ui.hover(x, y, w, ROW_H - 2);
        ui.rect(x, y, w, ROW_H - 2, over ? 0x34FFFFFF : 0x22000000);
        if (p.atMax()) ui.gradH(x, y, w, ROW_H - 2, Ui.alpha(Modern.GOLD, 0.16f), Ui.alpha(Modern.GOLD, 0.01f));
        ui.rect(x, y, 2, ROW_H - 2, c);
        ui.icon(icon(p.type(), p.tier()), x + 5, y + 4, 1f);
        int tx = x + 25;
        int nx = tx + ui.width(p.name());
        ui.textShadow(ui.ellipsize(p.name(), 118), tx, y + 3, c);
        if (p.active() && nx + 40 < x + w / 2) Modern.chip(ui, nx + 4, y + 3, "ACTIVE", Modern.GOOD);
        pips(ui, tx, y + 15, p.type(), p.tier(), tiers);
        ui.textShadow(word(p.tier()), tx + 46, y + 13, Modern.DIM);
        // middle: how far
        int mx = x + w * 40 / 100, mw = w * 30 / 100;
        String level = "Lvl " + p.level() + "/" + p.maxLevel();
        ui.textShadow(level, mx, y + 3, p.atMax() ? Modern.GOLD : Modern.INK);
        if (p.atMax()) {
            Modern.chip(ui, mx + mw - ui.width("MAX") - 6, y + 3, "MAX", Modern.GOLD);
            Modern.meter(ui, mx, y + 15, mw, 3, 1, Modern.GOLD);
        } else {
            double pct = p.xpForMax() > 0 ? Math.min(1, p.exp() / p.xpForMax()) : 0;
            String left = Prices.coins(p.xpLeft()) + " XP left";
            ui.textShadow(left, mx + mw - ui.width(left), y + 3, Modern.DIM);
            Modern.meter(ui, mx, y + 15, mw, 3, pct, c);
        }
        // right: worth + what it holds
        int rx = x + w - 4;
        String value = p.value() > 0 ? Prices.coins(p.value()) : "-";
        ui.textShadow(value, rx - ui.width(value), y + 3, p.value() > 0 ? Modern.GOLD : Modern.DIM);
        String held = p.held().isEmpty() ? "no item" : ItemRepo.name(p.held());
        if (held == null || held.isEmpty()) held = pretty(p.held());
        int room = rx - (mx + mw) - 8;
        String shown = ui.ellipsize(held, room);
        ui.textShadow(shown, rx - ui.width(shown), y + 13, p.held().isEmpty() ? Modern.DIM : Modern.LABEL);
        return ROW_H;
    }

    // --- missing and owned: one row per pet type with all its rarities ---

    // lowest first, plus the one you hold even if the list doesnt know it
    private static List<String> raritiesOf(Data d, String type, String heldTier) {
        Set<String> exists = d.tiers().getOrDefault(type, Set.of());
        List<String> out = new ArrayList<>();
        for (String r : PetHelper.RARITY) if (exists.contains(r) || r.equals(heldTier)) out.add(r);
        return out;
    }

    // from the game before the pets menu was read: we dont know whats missing
    static boolean unread(Data d) {
        return d.mine().isEmpty() && Profile.current != null && !Profile.current.knows(io.github.zmuzy.sbc.core.game.GameProfile.PETS);
    }

    // not at top rarity yet. most pet score per coin first, no score / no price at the end
    static List<String> missingTypes(Data d) {
        if (unread(d)) return List.of();
        Map<String, PetHelper.Owned> best = PetHelper.best(d.mine());
        Map<String, Integer> order = new HashMap<>();
        for (PetHelper.Step s : d.plan().buys()) order.putIfAbsent(s.type(), order.size());
        List<String> out = new ArrayList<>();
        for (String type : d.tiers().keySet()) {
            PetHelper.Owned have = best.get(type);
            List<String> all = raritiesOf(d, type, have == null ? "" : have.tier());
            if (all.isEmpty()) continue;
            if (have == null || !have.tier().equals(all.get(all.size() - 1))) out.add(type);
        }
        out.sort(Comparator.<String>comparingInt(t -> order.getOrDefault(t, Integer.MAX_VALUE)).thenComparing(Comparator.naturalOrder()));
        return out;
    }

    // rarest + highest first
    static List<PetHelper.Owned> ownedPets(Data d) {
        List<PetHelper.Owned> out = new ArrayList<>(PetHelper.best(d.mine()).values());
        out.sort(Comparator.<PetHelper.Owned>comparingInt(p -> -PetHelper.rarity(p.tier())).thenComparingInt(p -> -p.level()).thenComparing(PetHelper.Owned::type));
        return out;
    }

    private static SideHelper.Links links(Data d, String type, PetHelper.Owned have) {
        List<String> all = raritiesOf(d, type, have == null ? "" : have.tier());
        int heldAt = have == null ? -1 : all.indexOf(have.tier());
        return new SideHelper.Links() {
            @Override
            public int count() {
                return all.size();
            }

            @Override
            public ItemStack icon(int i) {
                return PetMenu.icon(type, all.get(i));
            }

            @Override
            public String card(int i) {
                return io.github.zmuzy.sbc.core.PetCard.id(type, all.get(i));
            }

            @Override
            public int color(int i) {
                return PetMenu.color(all.get(i));
            }

            @Override
            public boolean have(int i) {
                return i <= heldAt;
            }

            @Override
            public int held() {
                return heldAt;
            }

            @Override
            public String[] tip(int i) {
                String head = "§f" + pretty(type) + " §7" + word(all.get(i));
                if (i == heldAt) {
                    String held = have.held().isEmpty() ? "" : ItemRepo.name(have.held());
                    return new String[]{head, "§7Lvl §f" + have.level() + "§7/" + have.maxLevel() + (have.atMax() ? " §6MAX" : " §7· §a" + Prices.coins(have.xpLeft()) + " §7XP left"),
                            held == null || held.isEmpty() ? "§7No item" : "§7Holds §f" + held, have.value() > 0 ? "§7Worth §6" + Prices.coins(have.value()) : "§7Worth not known", "§aYou have this."};
                }
                if (i < heldAt) return new String[]{head, "§7You have a better one."};
                double price = AuctionPrices.lowestBin(AuctionPrices.petKey(type, all.get(i), false));
                return new String[]{head, "§cMissing.", price > 0 ? "§6" + Prices.coins(price) + " §7lowest BIN" : "§7No price right now."};
            }
        };
    }

    static int missingRow(Ui ui, Data d, String type, int x, int y, int w) {
        PetHelper.Owned have = PetHelper.best(d.mine()).get(type);
        PetHelper.Step step = null;
        for (PetHelper.Step s : d.plan().buys()) {
            if (s.type().equals(type)) {
                step = s;
                break;
            }
        }
        List<String> all = raritiesOf(d, type, have == null ? "" : have.tier());
        String target = step != null ? step.to() : all.get(all.size() - 1);
        int color = color(target);
        String right = step == null ? "§7no score" : "§b+" + step.points() + " " + (step.cost() < 0 ? "§7no price" : "§6" + Prices.coins(step.cost()));
        return SideHelper.chain(ui, pretty(type) + " §7" + word(target), color, right, color, links(d, type, have), x, y, w);
    }

    static int ownedRow(Ui ui, Data d, PetHelper.Owned p, int x, int y, int w) {
        int color = color(p.tier());
        String right = p.atMax() ? "§6Lvl " + p.level() : "§fLvl " + p.level() + " §a" + Prices.coins(p.xpLeft());
        return SideHelper.chain(ui, p.name(), color, right, color, links(d, p.type(), p), x, y, w);
    }

    private static int missing(Ui ui, Data d, int x, int y, int w) {
        List<String> types = missingTypes(d);
        if (types.isEmpty()) {
            if (unread(d)) ui.textShadow(io.github.zmuzy.sbc.core.game.GameProfile.hint(io.github.zmuzy.sbc.core.game.GameProfile.PETS), x, y + 2, Modern.DIM);
            else ui.textShadow(d.tiers().isEmpty() ? "The pet list is still loading." : "You have every pet at its top rarity.", x, y + 2, Modern.GOOD);
            return 14;
        }
        int cols = Math.max(1, w / 200), cw = (w - (cols - 1) * 4) / cols, y0 = y, rowH = 0;
        for (int i = 0; i < types.size(); i++) {
            int h = missingRow(ui, d, types.get(i), x + i % cols * (cw + 4), y, cw);
            rowH = Math.max(rowH, h);
            if (i % cols == cols - 1 || i == types.size() - 1) {
                y += rowH;
                rowH = 0;
            }
        }
        return y - y0;
    }

    private static List<PetHelper.Owned> sorted(List<PetHelper.Owned> list, int sort) {
        Comparator<PetHelper.Owned> byLevel = Comparator.comparingInt((PetHelper.Owned p) -> -p.level());
        Comparator<PetHelper.Owned> cmp = switch (sort) {
            case 1 -> Comparator.comparingInt((PetHelper.Owned p) -> -PetHelper.rarity(p.tier())).thenComparing(byLevel);
            case 2 -> Comparator.comparing(PetHelper.Owned::name);
            case 3 -> Comparator.comparingDouble(PetHelper.Owned::xpLeft);
            case 4 -> Comparator.comparingDouble((PetHelper.Owned p) -> -p.value());
            default -> byLevel.thenComparing(PetHelper.Owned::name);
        };
        List<PetHelper.Owned> out = new ArrayList<>(list);
        out.sort(cmp);
        return out;
    }

    private static int list(Ui ui, Data d, List<PetHelper.Owned> pets, int x, int y, int w, String empty) {
        if (pets.isEmpty()) {
            ui.textShadow(empty, x, y + 2, Modern.DIM);
            return 14;
        }
        int cy = y;
        for (PetHelper.Owned p : pets) cy += petRow(ui, p, d.tiers(), x, cy, w);
        return cy - y;
    }

    // clicks get registered with the Ui
    public static void paint(Ui ui, GuiGraphicsExtractor g, Data d, View v, int W, int H, Runnable back) {
        ui.rect(0, 0, W, H, 0xB0060A10);
        int[] box = Modern.window(W, H, 640);
        int x = box[0], y = box[1], w = box[2], h = box[3];
        Modern.card(ui, x, y, w, h, 0.97f);
        List<PetHelper.Owned> mine = d.mine();
        int maxed = 0;
        for (PetHelper.Owned p : mine) if (p.atMax()) maxed++;
        Map<String, PetHelper.Owned> best = PetHelper.best(mine);
        int total = d.tiers().size(), have = 0;
        for (String t : best.keySet()) if (d.tiers().containsKey(t)) have++;
        double worth = 0;
        for (PetHelper.Owned p : mine) worth += Math.max(0, p.value());
        Modern.band(ui, "Pets", io.github.zmuzy.sbc.core.LegacyItems.stack("bone"), mine.size() + " owned", ACCENT, x, y, w);

        int cx = x + 6, cw = w - 12, ty = y + 18, tw = (cw - 3 * 4) / 4;
        tile(ui, cx, ty, tw, "Kinds owned", have + "/" + total, total > 0 ? RowTextPercent(have / (double) total) : "", total > 0 ? have / (double) total : 0, Modern.AQUA);
        tile(ui, cx + tw + 4, ty, tw, "At top level", maxed + "/" + mine.size(), mine.isEmpty() ? "" : RowTextPercent(maxed / (double) mine.size()), mine.isEmpty() ? 0 : maxed / (double) mine.size(), Modern.GOLD);
        PetHelper.Plan p = d.plan();
        tile(ui, cx + 2 * (tw + 4), ty, tw, "Pet Score", String.valueOf(p.score()), "+" + p.magicFind() + " MF  +" + p.xp() + " XP", -1, 0xFFFF9EC7);
        tile(ui, cx + 3 * (tw + 4), ty, tw, "Worth", worth > 0 ? Prices.coins(worth) : "-", "", -1, Modern.GOLD);

        int by = ty + 34, bx = cx;
        int[] counts = {missingTypes(d).size(), mine.size()};
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i] + (counts[i] >= 0 ? " " + counts[i] : "");
            int bw = ui.width(label) + 12;
            final int idx = i;
            ui.button("pm_tab" + i, bx, by, bw, 16, label, v.tab == i ? Ui.PRIMARY : Ui.GHOST, () -> {
                v.tab = idx;
                v.scrollTarget = 0;
            });
            bx += bw + 3;
        }
        ui.button("pm_back", cx + cw - 50, by, 50, 16, "‹ Back", Ui.GHOST, back);
        if (v.tab == 1) {
            String label = "Sort: " + SORTS[v.sort];
            int bw = ui.width(label) + 12;
            ui.button("pm_sort", cx + cw - 54 - bw, by, bw, 16, label, Ui.GHOST, () -> v.sort = (v.sort + 1) % SORTS.length);
        }

        int top = by + 20, bottom = y + h - (d.fromMenu() ? 14 : 4), viewH = bottom - top;
        v.scrollTarget = Math.max(0, Math.min(v.scrollTarget, Math.max(0, v.contentH - viewH)));
        v.scroll += (v.scrollTarget - v.scroll) * 0.35f;
        g.enableScissor(cx - 1, top, cx + cw + 1, bottom);
        ui.clipX0 = cx - 1;
        ui.clipY0 = top;
        ui.clipX1 = cx + cw + 1;
        ui.clipY1 = bottom;
        int ly = top - Math.round(v.scroll), lw = cw - 6;
        v.contentH = (v.tab == 1 ? list(ui, d, sorted(mine, v.sort), cx, ly, lw, "Open Hypixel's Pets menu once and your pets show here.") : missing(ui, d, cx, ly, lw)) + 4;
        SideHelper.flushDark(ui);
        g.disableScissor();
        ui.clipX0 = Integer.MIN_VALUE;
        ui.clipY0 = Integer.MIN_VALUE;
        ui.clipX1 = Integer.MAX_VALUE;
        ui.clipY1 = Integer.MAX_VALUE;
        if (v.contentH > viewH) {
            int barH = Math.max(14, viewH * viewH / v.contentH);
            int barY = top + (int) ((viewH - barH) * (v.scroll / Math.max(1, v.contentH - viewH)));
            ui.rect(cx + cw - 3, top, 3, viewH, 0x30000000);
            ui.rect(cx + cw - 3, barY, 3, barH, ACCENT);
        }
        if (d.fromMenu()) ui.textShadow(ui.ellipsize("From Hypixel's Pets menu; XP may be counted from the level.", cw), cx, y + h - 11, Modern.DIM);
    }

    private static String RowTextPercent(double f) {
        return io.github.zmuzy.sbc.ui.hud.RowText.percent(f);
    }
}
