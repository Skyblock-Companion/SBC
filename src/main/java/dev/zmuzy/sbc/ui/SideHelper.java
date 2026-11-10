// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.mc.mixin.ContainerAccess;
import dev.zmuzy.sbc.plan.AccessoryHelper;
import dev.zmuzy.sbc.plan.PetHelper;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// helper next to hypixels chest, other side from the item list, for menus where you pick what to get next
// (acc bag, pets)
// the chest stays as is and works like always
public final class SideHelper {
    // what differs between the 2 helpers
    public interface Content {
        String title();

        ItemStack icon();

        int accent();

        String[] tabs();

        // re-reads the open menu, not every frame
        void refresh(AbstractContainerScreen<?> screen);

        // numbers that stay above the list, returns height
        int summary(Ui ui, int x, int y, int w);

        // returns height
        int list(Ui ui, int tab, int x, int y, int w);
    }

    private static final int TAB_H = 16, WIDE = 196, NARROWEST = 130, ROW = 22;
    private static final Ui ui = new Ui();
    private static final Accessories ACCESSORIES = new Accessories();
    private static final Sacks SACKS = new Sacks();
    private static final Pets PETS = new Pets();
    private static final List<java.util.function.Function<Screen, Content>> EXTRA = new ArrayList<>();

    private static boolean placed, onLeft = true;
    private static int px, py, pw, ph, contentH, tab;
    private static float scroll, scrollTarget;
    private static long refreshedAt;
    private static Content shown;

    private SideHelper() {}

    // another packages helper: open menu -> its panel, null where it has none
    public static void add(java.util.function.Function<Screen, Content> helper) {
        EXTRA.add(helper);
    }

    private static Content of(Screen s) {
        if (!(s instanceof ContainerScreen) && !(s instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return null;
        for (var helper : EXTRA) {
            Content c = helper.apply(s);
            if (c != null) return c;
        }
        if (!(s instanceof ContainerScreen)) return MarketPanel.buyFallback();
        String title = GameState.strip(s.getTitle().getString()).trim();
        Content market = MarketPanel.of(title);
        if (market != null) return market;
        if (Features.on("accessory_helper") && Patterns.get("SH.api.accessory.bag.inventory.name").matcher(title.replaceFirst("^\\(\\d+/\\d+\\)\\s*", "")).matches()) return ACCESSORIES;
        if (Features.on("sack_helper") && title.replaceFirst("^\\(\\d+/\\d+\\)\\s*", "").equals("Sack of Sacks")) return SACKS;
        if (Features.on("pet_helper") && PetMenu.isPetsTitle(title)) return PETS;
        if (Features.on("museum_helper") && dev.zmuzy.sbc.ui.panels.MuseumPanel.isMuseum(title)) return dev.zmuzy.sbc.ui.panels.MuseumPanel.I;
        // the buy list follows you to every other menu while it holds something
        return MarketPanel.buyFallback();
    }

    private static void setOpen(boolean open) {
        if (MarketPanel.isBuy(shown)) Config.I.buyHelperOpen = open;
        else Config.I.helperOpen = open;
        Config.save();
    }

    private static boolean open(Content c) {
        return MarketPanel.isBuy(c) ? Config.I.buyHelperOpen : Config.I.helperOpen;
    }

    public static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        placed = false;
        Content c = of(s);
        if (c == null) {
            shown = null;
            return;
        }
        ContainerAccess a = (ContainerAccess) s;
        int left = a.sbc$left(), top = a.sbc$top(), cw = a.sbc$width(), sw = s.width, sh = s.height;
        long now = System.currentTimeMillis();
        if (c != shown || now - refreshedAt > 1000) {
            if (c != shown) {
                tab = 0;
                scroll = scrollTarget = 0;
            }
            shown = c;
            refreshedAt = now;
            c.refresh((AbstractContainerScreen<?>) s);
        }
        ui.begin(g, mx, my);
        // other side from the item list
        onLeft = !ItemList.onLeftSide(left, cw, sw);
        int room = onLeft ? left - 10 : sw - left - cw - 10;
        int y = Math.max(4, top - 24), h = Math.min(sh - y - 6, 270);
        if (open(c) && room >= NARROWEST && h >= 120) {
            int w = Math.min(WIDE, room);
            paintOpen(ui, c, onLeft ? left - 8 - w : left + cw + 8, y, w, h);
        } else paintClosed(ui, c, left, top, cw, sh, ContainerValue.hudBoxes(s));
        placed = true;
        ui.flushTooltip();
    }

    static void paintClosed(Ui ui, Content c, int left, int top, int cw, int sh, List<int[]> hud) {
        String label = c.title().toUpperCase(Locale.ROOT);
        int w = ui.width("‹ " + label) + 10;
        px = onLeft ? Math.max(2, left - 4 - w) : left + cw + 4;
        py = ItemList.tabY(px, w, top, sh, hud);
        pw = w;
        ph = TAB_H;
        boolean hv = ui.hover(px, py, pw, ph);
        Theme.current().panel(ui, px, py, pw, ph);
        if (hv) ui.rect(px + 1, py + 1, pw - 2, ph - 2, Ui.alpha(c.accent(), 0.2f));
        ui.gradH(px + 2, py + ph - 2, pw - 3, 1, Ui.alpha(c.accent(), 0.08f), Ui.alpha(c.accent(), hv ? 1f : 0.7f));
        ui.textShadow("‹", px + 5, py + 4, c.accent());
        ui.textShadow(label, px + 5 + ui.width("‹ "), py + 4, hv ? 0xFFFFFFFF : Modern.LABEL);
        if (hv) ui.tooltip("§f" + c.title(), "", "§eClick to open");
        ui.click(px, py, pw, ph, () -> setOpen(true));
    }

    public static void paintOpen(Ui ui, Content c, int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
        Theme.current().panel(ui, x, y, w, h);
        Modern.band(ui, c.title(), c.icon(), "", c.accent(), x, y, w);
        boolean closeHover = ui.hover(x + w - 16, y + 1, 15, 12);
        if (closeHover) ui.rect(x + w - 16, y + 1, 15, 12, Ui.alpha(c.accent(), 0.35f));
        ui.textShadow(onLeft ? "‹" : "›", x + w - 10, y + 3, closeHover ? 0xFFFFFFFF : Modern.LABEL);
        ui.click(x + w - 16, y + 1, 15, 12, () -> setOpen(false));

        int cx = x + 4, cw = w - 8, cy = y + Modern.BAND + 3;
        cy += c.summary(ui, cx, cy, cw) + 2;
        int bx = cx;
        String[] tabs = c.tabs();
        int each = (cw - (tabs.length - 1) * 2) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            ui.button("side_tab" + i, bx, cy, each, 14, tabs[i], tab == i ? Ui.PRIMARY : Ui.GHOST, () -> {
                tab = idx;
                scrollTarget = 0;
            });
            bx += each + 2;
        }
        cy += 16;
        int bottom = y + h - 3, viewH = bottom - cy;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentH - viewH)));
        scroll += (scrollTarget - scroll) * 0.35f;
        ui.g.enableScissor(cx - 1, cy, cx + cw + 1, bottom);
        ui.clipX0 = cx - 1;
        ui.clipY0 = cy;
        ui.clipX1 = cx + cw + 1;
        ui.clipY1 = bottom;
        contentH = c.list(ui, tab, cx, cy - Math.round(scroll), cw - 4) + 2;
        flushDark(ui);
        ui.g.disableScissor();
        ui.clipX0 = Integer.MIN_VALUE;
        ui.clipY0 = Integer.MIN_VALUE;
        ui.clipX1 = Integer.MAX_VALUE;
        ui.clipY1 = Integer.MAX_VALUE;
        if (contentH > viewH) {
            int barH = Math.max(14, viewH * viewH / contentH);
            int barY = cy + (int) ((viewH - barH) * (scroll / Math.max(1, contentH - viewH)));
            ui.rect(x + w - 4, cy, 2, viewH, 0x30000000);
            ui.rect(x + w - 4, barY, 2, barH, c.accent());
        }
    }

    // false = swallow the click. clicking the panel must not throw your held item
    public static boolean click(Screen s, double x, double y, int button) {
        if (!placed || of(s) == null || x < px || y < py || x >= px + pw || y >= py + ph) return true;
        ItemList.pressTaken();
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (x < hit.x() || y < hit.y() || x >= hit.x() + hit.w() || y >= hit.y() + hit.h() || hit.click() == null) continue;
            if (button == 0) hit.click().run();
            break;
        }
        return false;
    }

    public static boolean scroll(Screen s, double x, double y, double dy) {
        if (!placed || of(s) == null || x < px || y < py || x >= px + pw || y >= py + ph) return true;
        scrollTarget -= (float) (Math.signum(dy) * 26);
        return false;
    }

    // --- shared pieces ---

    public static void tile(Ui ui, int x, int y, int w, String label, String value, String note, int color) {
        ui.rect(x, y, w, 23, 0x44000000);
        ui.rect(x, y, w, 1, 0x18FFFFFF);
        ui.textShadow(ui.ellipsize(label, w - 6), x + 3, y + 3, Modern.LABEL);
        ui.textShadow(value, x + 3, y + 13, color);
        if (!note.isEmpty()) ui.textShadow(ui.ellipsize(note, w - 8 - ui.width(value)), x + w - 3 - ui.width(ui.ellipsize(note, w - 8 - ui.width(value))), y + 13, Modern.DIM);
    }

    // everything a family or pet can be: what you have lit, rest dark
    interface Links {
        int count();

        ItemStack icon(int i);

        int color(int i);

        boolean have(int i);

        // -1 = none
        int held();

        String[] tip(int i);

        // the item card a click opens (like the item list), null = none
        default String card(int i) {
            return null;
        }
    }

    private static final int CELL = 16, ICON = 14, TEXT_LINE = 12;
    private static final List<int[]> DARK = new ArrayList<>();

    static int chainHeight(int count, int w) {
        int perLine = Math.max(1, (w - 6) / CELL);
        return TEXT_LINE + (count + perLine - 1) / perLine * CELL + 3;
    }

    // name + what the next step asks on top, every tier under. stuff scrolled out doesnt get drawn
    static int chain(Ui ui, String title, int titleColor, String right, int edge, Links links, int x, int y, int w) {
        int h = chainHeight(links.count(), w);
        if (y + h < ui.clipY0 || y > ui.clipY1) return h;
        ui.rect(x, y, w, h - 2, 0x22000000);
        ui.rect(x, y, 2, h - 2, edge);
        int rw = right.isEmpty() ? 0 : ui.width(right), perLine = Math.max(1, (w - 6) / CELL), room = w - 8 - (rw > 0 ? rw + 4 : 0);
        int lines = (links.count() + perLine - 1) / perLine, onLastLine = links.count() == 0 ? 0 : (links.count() - 1) % perLine + 1;
        // long name keeps all its words, the ask goes next to the last icons if theres room
        boolean beside = rw > 0 && ui.width(title) > room && lines > 0 && 5 + onLastLine * CELL + 4 + rw + 3 <= w;
        ui.textShadow(ui.ellipsize(title, beside ? w - 8 : room), x + 5, y + 2, titleColor);
        if (rw > 0) ui.textShadow(right, x + w - 3 - rw, beside ? y + TEXT_LINE + (lines - 1) * CELL + 3 : y + 2, Modern.INK);
        for (int i = 0; i < links.count(); i++) {
            int ix = x + 5 + i % perLine * CELL, iy = y + TEXT_LINE + i / perLine * CELL, color = links.color(i);
            boolean have = links.have(i);
            if (i == links.held()) ui.rect(ix - 1, iy - 1, ICON + 2, ICON + 2, Ui.alpha(color, 0.3f));
            ui.icon(links.icon(i), ix, iy, ICON / 16f);
            ui.rect(ix, iy + ICON, ICON, 1, have ? color : Ui.alpha(color, 0.45f));
            if (!have) DARK.add(new int[]{ix, iy, ICON, ICON});
            String card = links.card(i);
            if (ui.hover(ix - 1, iy - 1, CELL, CELL)) {
                String[] tip = links.tip(i);
                if (card != null) {
                    tip = java.util.Arrays.copyOf(tip, tip.length + 1);
                    tip[tip.length - 1] = "§eClick to open";
                }
                ui.tooltip(tip);
            }
            if (card != null) ui.click(ix - 1, iy - 1, CELL, CELL, () -> ItemScreen.open(card));
        }
        return h;
    }

    // the dark overlay goes over the items, so it waits a stratum till theyre drawn
    static void flushDark(Ui ui) {
        if (DARK.isEmpty()) return;
        ui.g.nextStratum();
        for (int[] d : DARK) ui.rect(d[0], d[1], d[2], d[3], 0xC00C1016);
        DARK.clear();
    }

    public static int emptyText(Ui ui, String text, int x, int y, int w) {
        ui.textShadow(ui.ellipsize(text, w), x, y + 2, Modern.DIM);
        return 14;
    }

    // --- acc bag ---

    private static final int[] RARITY_COLOR = {0xFFFFFFFF, 0xFF55FF55, 0xFF7C7CFF, 0xFFC060E0, 0xFFFFAA00, 0xFFFF55FF, 0xFFFF5555, 0xFFFF5555};

    private static double accessoryCost(String id) {
        double v = AuctionPrices.value(id);
        return v > 0 ? v : Prices.sell(id);
    }

    private static String market(String id) {
        return Prices.get(id) != null ? "BZ" : AuctionPrices.lowestBin(id) > 0 ? "AH" : "";
    }

    // api bag + every page of the real bag youve opened
    private static final class Accessories implements Content {
        private AccessoryHelper.Plan plan = new AccessoryHelper.Plan(List.of(), 0, 0, 0, 0, 0);
        private List<AccessoryHelper.Family> missing = List.of(), owned = List.of();
        private final Map<String, AccessoryHelper.Step> stepOf = new HashMap<>();
        private int families, power;
        private boolean loaded, bagUnread;

        void set(List<AccessoryHelper.Family> all, AccessoryHelper.Plan plan, int apiPower) {
            this.plan = plan;
            families = all.size();
            stepOf.clear();
            for (AccessoryHelper.Step s : plan.steps()) stepOf.put(s.family().root(), s);
            List<AccessoryHelper.Family> lacking = new ArrayList<>(), have = new ArrayList<>();
            int knownPower = 0;
            for (AccessoryHelper.Family f : all) {
                if (f.owned() < f.tiers().size() - 1) lacking.add(f);
                if (f.owned() >= 0) {
                    have.add(f);
                    knownPower += f.ownedMp();
                }
            }
            // most power per coin first, no price at the end
            lacking.sort(Comparator.<AccessoryHelper.Family>comparingDouble(f -> {
                AccessoryHelper.Step s = stepOf.get(f.root());
                return s == null ? Double.MAX_VALUE : s.perMp();
            }).thenComparing(f -> f.tiers().get(0).name()));
            have.sort(Comparator.<AccessoryHelper.Family>comparingInt(f -> -f.tiers().get(f.owned()).rarity()).thenComparing(f -> f.tiers().get(f.owned()).name()));
            missing = lacking;
            owned = have;
            power = apiPower > 0 ? apiPower : knownPower;
            loaded = !all.isEmpty();
            bagUnread = false;
        }

        @Override
        public String title() {
            return "Accessory Helper";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("ender_eye");
        }

        @Override
        public int accent() {
            return 0xFFC77DFF;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Missing " + missing.size(), "Owned " + owned.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            Map<String, String[]> known = ItemRepo.accessories();
            boolean grew = false;
            Set<String> here = new HashSet<>();
            var inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
            for (var slot : screen.getMenu().slots) {
                if (slot.container == inventory) continue;
                String id = Gain.sbId(slot.getItem());
                if (id.isEmpty() || !known.containsKey(id)) continue;
                here.add(id);
                if (Store.I.accessoriesSeen.add(id)) grew = true;
            }
            if (grew) Store.markDirty();
            Profile.Data d = Profile.current;
            Set<String> have = dev.zmuzy.sbc.core.game.GameProfile.ownedAccessories(d);
            have.addAll(here);
            List<AccessoryHelper.Family> all = AccessoryHelper.families(known, have);
            set(all, AccessoryHelper.plan(all, SideHelper::accessoryCost, Math.max(0, GameState.purse)), d == null ? 0 : d.magicalPower);
            if (d != null && d.fromGame && have.isEmpty()) markUnread();
        }

        // from the game with nothing read yet: no zero and no "everything is missing" list
        void markUnread() {
            bagUnread = true;
            missing = List.of();
            owned = List.of();
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            if (bagUnread) {
                tile(ui, x, y, half, "Magical Power", "?", "", 0xFFC77DFF);
                tile(ui, x + half + 3, y, w - half - 3, "Families", "?/" + families, "", Modern.INK);
                Modern.row(ui, "To finish", "§7?", x, y + 26, w, Modern.LABEL, Modern.INK);
                return 36;
            }
            tile(ui, x, y, half, "Magical Power", String.format(Locale.ROOT, "%,d", power), "", 0xFFC77DFF);
            tile(ui, x + half + 3, y, w - half - 3, "Families", owned.size() + "/" + families, "", Modern.INK);
            Modern.row(ui, "To finish", "§a+" + plan.totalMp() + " MP §7for §6" + Prices.coins(plan.totalCost()), x, y + 26, w, Modern.LABEL, Modern.INK);
            return 36;
        }

        private Links links(AccessoryHelper.Family f) {
            return new Links() {
                @Override
                public int count() {
                    return f.tiers().size();
                }

                @Override
                public ItemStack icon(int i) {
                    return ItemInfo.stack(f.tiers().get(i).id());
                }

                @Override
                public String card(int i) {
                    return f.tiers().get(i).id();
                }

                @Override
                public int color(int i) {
                    return RARITY_COLOR[f.tiers().get(i).rarity()];
                }

                @Override
                public boolean have(int i) {
                    return i <= f.owned();
                }

                @Override
                public int held() {
                    return f.owned();
                }

                @Override
                public String[] tip(int i) {
                    AccessoryHelper.Tier t = f.tiers().get(i);
                    String head = "§f" + t.name(), rarity = "§7" + AccessoryHelper.RARITIES[t.rarity()] + " · " + AccessoryHelper.MP[t.rarity()] + " MP";
                    if (i == f.owned()) return new String[]{head, rarity, "§aYou have this."};
                    if (i < f.owned()) return new String[]{head, rarity, "§7You have a better one."};
                    double cost = accessoryCost(t.id());
                    int gain = AccessoryHelper.MP[t.rarity()] - f.ownedMp();
                    String tag = market(t.id());
                    return new String[]{head, rarity, "§cMissing.", cost > 0 ? "§a+" + Math.max(0, gain) + " MP §7for §6" + Prices.coins(cost) + (tag.isEmpty() ? "" : " §7" + tag) : "§7No price right now."};
                }
            };
        }

        private int missingRow(Ui ui, AccessoryHelper.Family f, int x, int y, int w) {
            AccessoryHelper.Step s = stepOf.get(f.root());
            AccessoryHelper.Tier next = s != null ? s.tier() : f.tiers().get(f.owned() + 1);
            int color = RARITY_COLOR[next.rarity()];
            String right = s == null ? "§7no price" : "§a+" + s.gain() + " §6" + Prices.coins(s.cost());
            return chain(ui, next.name(), color, right, color, links(f), x, y, w);
        }

        private int ownedRow(Ui ui, AccessoryHelper.Family f, int x, int y, int w) {
            AccessoryHelper.Tier best = f.tiers().get(f.owned());
            int color = RARITY_COLOR[best.rarity()];
            String right = f.owned() == f.tiers().size() - 1 ? "§a" + f.ownedMp() + " MP" : "§7" + f.ownedMp() + " MP";
            return chain(ui, best.name(), color, right, color, links(f), x, y, w);
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (!loaded) return emptyText(ui, "The accessory list is still loading.", x, y, w);
            if (bagUnread) return emptyText(ui, dev.zmuzy.sbc.core.game.GameProfile.hint(dev.zmuzy.sbc.core.game.GameProfile.ACCESSORIES), x, y, w);
            for (AccessoryHelper.Family f : tab == 0 ? missing : owned) y += tab == 0 ? missingRow(ui, f, x, y, w) : ownedRow(ui, f, x, y, w);
            if (y == y0) y += emptyText(ui, tab == 0 ? "You have every accessory at its top tier." : "Open your bag once.", x, y, w);
            return y - y0;
        }
    }

    // --- sack of sacks ---

    // every sack the repo knows, id -> name. built once
    private static Map<String, String> sackCatalog;

    static Map<String, String> sackCatalog() {
        if (sackCatalog != null && !sackCatalog.isEmpty()) return sackCatalog;
        Map<String, String> out = new HashMap<>();
        for (String id : ItemRepo.ids()) if (dev.zmuzy.sbc.plan.SackHelper.isSack(id, ItemRepo.lore(id))) out.put(id, ItemRepo.name(id));
        return sackCatalog = out;
    }

    private static int sackColor(String id) {
        return ItemList.colorOf(ItemRepo.coloredName(id));
    }

    // "Capacity: 20,160 of each item" from the sacks lore, "" if it has none
    private static String capacity(String id) {
        for (String l : ItemRepo.lore(id)) {
            String plain = GameState.strip(l);
            if (plain.startsWith("Capacity")) return plain;
        }
        return "";
    }

    // the api sack of sacks + every sack seen in it, like the accessory bag
    private static final class Sacks implements Content {
        private List<dev.zmuzy.sbc.plan.SackHelper.Family> missing = List.of(), owned = List.of();
        private int families;
        private double toFinish;
        private boolean loaded;

        void set(List<dev.zmuzy.sbc.plan.SackHelper.Family> all) {
            families = all.size();
            List<dev.zmuzy.sbc.plan.SackHelper.Family> lacking = new ArrayList<>(), have = new ArrayList<>();
            double cost = 0;
            for (var f : all) {
                if (!f.done()) {
                    lacking.add(f);
                    cost += accessoryCost(f.top().id());
                }
                if (f.owned() >= 0) have.add(f);
            }
            // cheapest to finish first, no price at the end
            lacking.sort(Comparator.<dev.zmuzy.sbc.plan.SackHelper.Family>comparingDouble(f -> {
                double c = accessoryCost(f.top().id());
                return c > 0 ? c : Double.MAX_VALUE;
            }).thenComparing(dev.zmuzy.sbc.plan.SackHelper.Family::name));
            have.sort(Comparator.<dev.zmuzy.sbc.plan.SackHelper.Family, Boolean>comparing(f -> !f.done()).thenComparing(dev.zmuzy.sbc.plan.SackHelper.Family::name));
            missing = lacking;
            owned = have;
            toFinish = cost;
            loaded = !all.isEmpty();
        }

        @Override
        public String title() {
            return "Sack Helper";
        }

        @Override
        public ItemStack icon() {
            return ItemInfo.stack("LARGE_AGRONOMY_SACK");
        }

        @Override
        public int accent() {
            return 0xFFD7B98E;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Missing " + missing.size(), "Owned " + owned.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            Map<String, String> known = sackCatalog();
            boolean grew = false;
            var inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
            for (var slot : screen.getMenu().slots) {
                if (slot.container == inventory) continue;
                String id = Gain.sbId(slot.getItem());
                if (id.isEmpty() || !known.containsKey(id)) continue;
                if (Store.I.sacksSeen.add(id)) grew = true;
            }
            if (grew) Store.markDirty();
            set(dev.zmuzy.sbc.plan.SackHelper.families(known, dev.zmuzy.sbc.core.game.GameProfile.ownedSacks(Profile.current)));
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            long top = owned.stream().filter(dev.zmuzy.sbc.plan.SackHelper.Family::done).count();
            tile(ui, x, y, half, "Sacks", owned.size() + "/" + families, "", 0xFFD7B98E);
            tile(ui, x + half + 3, y, w - half - 3, "Biggest size", top + "/" + families, "", Modern.INK);
            Modern.row(ui, "To finish", missing.isEmpty() ? "§aNothing left" : "§6" + Prices.coins(toFinish), x, y + 26, w, Modern.LABEL, Modern.INK);
            return 36;
        }

        private Links links(dev.zmuzy.sbc.plan.SackHelper.Family f) {
            return new Links() {
                @Override
                public int count() {
                    return f.tiers().size();
                }

                @Override
                public ItemStack icon(int i) {
                    return ItemInfo.stack(f.tiers().get(i).id());
                }

                @Override
                public String card(int i) {
                    return f.tiers().get(i).id();
                }

                @Override
                public int color(int i) {
                    return sackColor(f.tiers().get(i).id());
                }

                @Override
                public boolean have(int i) {
                    return i <= f.owned();
                }

                @Override
                public int held() {
                    return f.owned();
                }

                @Override
                public String[] tip(int i) {
                    var t = f.tiers().get(i);
                    String head = "§f" + t.name(), size = "§7" + capacity(t.id());
                    if (i == f.owned()) return new String[]{head, size, "§aYou have this."};
                    if (i < f.owned()) return new String[]{head, size, "§7You have a bigger one."};
                    double cost = accessoryCost(t.id());
                    String tag = market(t.id());
                    return new String[]{head, size, "§cMissing.", cost > 0 ? "§7Costs §6" + Prices.coins(cost) + (tag.isEmpty() ? "" : " §7" + tag) : "§7No price right now."};
                }
            };
        }

        private int missingRow(Ui ui, dev.zmuzy.sbc.plan.SackHelper.Family f, int x, int y, int w) {
            var next = f.top();
            int color = sackColor(next.id());
            double cost = accessoryCost(next.id());
            return chain(ui, next.name(), color, cost > 0 ? "§6" + Prices.coins(cost) : "§7no price", color, links(f), x, y, w);
        }

        private int ownedRow(Ui ui, dev.zmuzy.sbc.plan.SackHelper.Family f, int x, int y, int w) {
            var best = f.tiers().get(f.owned());
            int color = sackColor(best.id());
            return chain(ui, best.name(), color, f.done() ? "§aBiggest" : "§7" + (f.owned() + 1) + "/" + f.tiers().size(), color, links(f), x, y, w);
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (!loaded) return emptyText(ui, "The sack list is still loading.", x, y, w);
            for (var f : tab == 0 ? missing : owned) y += tab == 0 ? missingRow(ui, f, x, y, w) : ownedRow(ui, f, x, y, w);
            if (y == y0) y += emptyText(ui, tab == 0 ? "You have every sack at its biggest size." : "Open your Sack of Sacks once.", x, y, w);
            return y - y0;
        }
    }

    static Content sacksFor(List<dev.zmuzy.sbc.plan.SackHelper.Family> all) {
        SACKS.set(all);
        return SACKS;
    }

    // --- pets menu ---

    private static final class Pets implements Content {
        private PetMenu.Data data = new PetMenu.Data(List.of(), Map.of(), new PetHelper.Plan(0, 0, 0, -1, 0, List.of(), List.of(), 0, 0, false), true);

        void set(PetMenu.Data data) {
            this.data = data;
        }

        @Override
        public String title() {
            return "Pet Helper";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("bone");
        }

        @Override
        public int accent() {
            return 0xFFFF9E80;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Missing " + PetMenu.missingTypes(data).size(), "Owned " + PetHelper.best(data.mine()).size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            data = PetMenu.read();
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            PetHelper.Plan p = data.plan();
            int half = (w - 3) / 2, have = 0;
            if (PetMenu.unread(data)) {
                tile(ui, x, y, half, "Pet Score", "?", "", 0xFFFF9EC7);
                tile(ui, x + half + 3, y, w - half - 3, "Kinds owned", "?/" + data.tiers().size(), "", Modern.INK);
                return 26;
            }
            Map<String, PetHelper.Owned> best = PetHelper.best(data.mine());
            for (String t : best.keySet()) if (data.tiers().containsKey(t)) have++;
            tile(ui, x, y, half, "Pet Score", String.valueOf(p.score()), "+" + p.magicFind() + " MF", 0xFFFF9EC7);
            tile(ui, x + half + 3, y, w - half - 3, "Kinds owned", have + "/" + data.tiers().size(), "", Modern.INK);
            int ry = y + 26;
            if (p.nextAt() < 0) {
                ui.textShadow(ui.ellipsize("All the Magic Find a score can give.", w), x, ry, Modern.GOOD);
                return 37;
            }
            int prev = 0;
            for (int[] t : PetHelper.MAGIC_FIND) if (p.score() >= t[0]) prev = t[0];
            Modern.row(ui, "Next Magic Find", "§f" + (p.nextAt() - p.score()) + " §7points", x, ry, w, Modern.LABEL, Modern.INK);
            Modern.meter(ui, x, ry + 9, w, 2, (p.score() - prev) / (double) Math.max(1, p.nextAt() - prev), Modern.AQUA);
            ry += 12;
            if (p.canReachNext()) Modern.row(ui, "Cheapest way", "§f" + p.stepsToNext() + " §7pets, §6" + Prices.coins(p.costToNext()), x, ry, w, Modern.LABEL, Modern.INK);
            else ui.textShadow(ui.ellipsize("Not enough known prices to reach it.", w), x, ry, Modern.DIM);
            return 48;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (tab == 0) {
                for (String type : PetMenu.missingTypes(data)) y += PetMenu.missingRow(ui, data, type, x, y, w);
                if (y == y0) y += emptyText(ui, PetMenu.unread(data) ? dev.zmuzy.sbc.core.game.GameProfile.hint(dev.zmuzy.sbc.core.game.GameProfile.PETS) : data.tiers().isEmpty() ? "The pet list is still loading." : "You have every pet at its top rarity.", x, y, w);
            } else {
                for (PetHelper.Owned p : PetMenu.ownedPets(data)) y += PetMenu.ownedRow(ui, data, p, x, y, w);
                if (y == y0) y += emptyText(ui, "Turn the pages of this menu to read your pets.", x, y, w);
            }
            return y - y0;
        }
    }

    // --- for pictures ---

    static Content accessoriesUnread(List<AccessoryHelper.Family> all, AccessoryHelper.Plan plan) {
        ACCESSORIES.set(all, plan, 0);
        ACCESSORIES.markUnread();
        return ACCESSORIES;
    }

    static Content accessoriesFor(List<AccessoryHelper.Family> all, AccessoryHelper.Plan plan, int power) {
        ACCESSORIES.set(all, plan, power);
        return ACCESSORIES;
    }

    static Content petsFor(PetMenu.Data data) {
        PETS.set(data);
        return PETS;
    }

    public static void previewTab(int chosen) {
        tab = chosen;
        scroll = scrollTarget = 0;
    }
}
