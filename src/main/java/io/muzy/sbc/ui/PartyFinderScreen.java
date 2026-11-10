// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.dungeon.PartyListing;
import io.muzy.sbc.dungeon.PartyListing.Member;
import io.muzy.sbc.dungeon.PartyListing.Party;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// hypixels party finder in our design. like skyblocker every menu stays open on the server underneath and we
// just show it and click its slots
public final class PartyFinderScreen extends Screen {
    enum Page { FINDER, SETTINGS, OPTIONS, RANGE, SIGN }

    // a settings row or an option in one of its sub menus
    record Choice(int slot, String name, String selected, ItemStack icon) {}

    private static final Set<String> TITLES = Set.of("party finder", "search settings", "select floor", "select type", "class level range", "dungeon level range", "sort");
    private static final int ACCENT = 0xFFFF5D73, CARD_H = 58, MASTER = 0xFFFF5252, NORMAL = 0xFF5CD8FF;
    private static final Map<String, ItemStack> HEADS = new HashMap<>();
    private static boolean kuudra;

    private ChestMenu menu;
    private final Inventory inventory;
    private Component name;
    private final Ui ui = new Ui();
    private float scale = 1f, scroll, scrollTarget;
    private int contentH;

    Page page = Page.FINDER;
    final List<Party> parties = new ArrayList<>();
    final List<Choice> choices = new ArrayList<>();
    String subTitle = "", rangeText = "";
    boolean noParties;
    private String query = "", signInput = "", signWhat = "";
    private int refreshSlot = -1, prevSlot = -1, nextSlot = -1, createSlot = -1, settingsSlot = -1, delistSlot = -1, backSlot = -1, minSlot = -1, maxSlot = -1;
    private boolean waiting, aborted, dirty;
    private long dirtyAt;
    private int signature;
    private SignBlockEntity sign;
    private net.minecraft.world.level.block.entity.SignTextSlot signFront = net.minecraft.world.level.block.entity.SignTextSlot.FRONT;
    private final Map<String, String> selected = new HashMap<>();

    PartyFinderScreen(ChestMenu menu, Inventory inventory, Component name) {
        super(Component.literal("Party Finder"));
        this.menu = menu;
        this.inventory = inventory;
        this.name = name;
    }

    public static void register() {
        Features.add(new Feature("party_finder", "Dungeons", "Party Finder", "Hypixel's Party Finder in the mod's design: every party as a card with faces, classes and levels, search, filters, your class marked, paid carries marked, and level ranges typed right there.", "")
                .option(Option.bool("party_finder.hide_locked", "Hide locked parties", "Parties you cannot join stay out of the list.", false))
                .option(Option.bool("party_finder.hide_paid", "Hide paid carries", "Parties whose note sells a carry stay out of the list.", false))
                .option(Option.bool("party_finder.my_class", "Only where my class is free", "Parties that already have your class stay out of the list.", false)));
    }

    // runs as a menu opens before any screen exists. true = we took it
    public static boolean open(Minecraft mc, ChestMenu menu, Component name) {
        String t = name.getString().trim().toLowerCase(Locale.ENGLISH);
        if (t.startsWith("select tier")) kuudra = true;
        else if (t.startsWith("catacombs")) kuudra = false;
        if (kuudra || !TITLES.contains(t) || !Features.on("party_finder") || mc.player == null) return false;
        Screen now = mc.gui.screen();
        if (now != null) {
            String was = now.getTitle().getString().toLowerCase(Locale.ENGLISH);
            if (was.contains("group builder") || was.equals("training dummy")) return false;
        }
        if (t.equals("sort") && !(now instanceof PartyFinderScreen)) return false;
        mc.player.containerMenu = menu;
        if (now instanceof PartyFinderScreen s) s.changeMenu(menu, name);
        else mc.gui.setScreen(new PartyFinderScreen(menu, mc.player.getInventory(), name));
        return true;
    }

    private void changeMenu(ChestMenu menu, Component name) {
        this.menu = menu;
        this.name = name;
        sign = null;
        signature = 0;
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    // --- reading the menu ---

    private static String strip(String s) {
        return GameState.strip(s).trim();
    }

    private void read() {
        dirty = false;
        waiting = false;
        String title = name.getString().trim(), lower = title.toLowerCase(Locale.ENGLISH);
        if (lower.contains("party finder")) {
            if (isKuudra()) return;
            readFinder();
        } else if (lower.contains("search settings")) readSettings();
        else if (lower.contains("level range")) readRange(title);
        else if (lower.contains("floor") || lower.contains("select type") || lower.contains("sort")) readOptions(title);
        else abort();
    }

    private boolean isKuudra() {
        if (menu.slots.size() < 54 || !menu.slots.get(45).hasItem()) return false;
        for (String line : LegacyText.lore(menu.slots.get(45).getItem()))
            if (line.contains("Kuudra")) {
                kuudra = true;
                abort();
                return true;
            }
        return false;
    }

    private void readFinder() {
        page = Page.FINDER;
        parties.clear();
        prevSlot = nextSlot = refreshSlot = createSlot = settingsSlot = delistSlot = -1;
        noParties = false;
        int bottom = (menu.getRowCount() - 1) * 9;
        for (Slot slot : menu.slots) {
            if (slot.index >= bottom || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            String item = strip(stack.getHoverName().getString()).toLowerCase(Locale.ENGLISH);
            if (stack.is(Items.BEDROCK)) noParties = true;
            else if (stack.is(Items.PLAYER_HEAD)) parties.add(PartyListing.parse(slot.index, stack.getHoverName().getString(), LegacyText.lore(stack), false));
            else if (stack.is(Items.ARROW) && item.contains("previous")) prevSlot = slot.index;
            else if (stack.is(Items.ARROW) && item.contains("next")) nextSlot = slot.index;
        }
        ItemStack mine = null;
        for (int i = bottom; i < menu.getRowCount() * 9; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (stack.is(Items.EMERALD_BLOCK)) refreshSlot = i;
            else if (stack.is(Items.REDSTONE_BLOCK)) createSlot = i;
            else if (stack.is(Items.NETHER_STAR)) settingsSlot = i;
            else if (stack.is(Items.BOOKSHELF)) delistSlot = i;
            else if (stack.is(Items.PLAYER_HEAD)) mine = stack;
        }
        String me = minecraft != null ? minecraft.getUser().getName() : "";
        // your party can show in the results before it shows next to the de-list button
        if (mine == null && delistSlot != -1)
            for (Party p : parties)
                if (p.leader().equals(me)) {
                    mine = menu.slots.get(p.slot()).getItem();
                    break;
                }
        if (mine != null) {
            String title = delistSlot != -1 ? me + "'s party" : mine.getHoverName().getString();
            parties.removeIf(p -> p.leader().equals(me));
            parties.add(0, PartyListing.parse(delistSlot, title, LegacyText.lore(mine), true));
        }
        parties.sort((a, b) -> a.yours() != b.yours() ? Boolean.compare(b.yours(), a.yours()) : Boolean.compare(a.locked(), b.locked()));
    }

    private static String selectedIn(ItemStack stack) {
        for (String raw : LegacyText.lore(stack)) {
            String line = strip(raw);
            if (line.toLowerCase(Locale.ENGLISH).contains("selected:")) return line.substring(line.indexOf(':') + 1).trim();
        }
        return "";
    }

    private int backArrow() {
        int back = -1;
        for (int i = (menu.getRowCount() - 1) * 9; i < menu.getRowCount() * 9; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem() && slot.getItem().is(Items.ARROW)) {
                back = i;
                break;
            }
        }
        return back;
    }

    private void readSettings() {
        page = Page.SETTINGS;
        subTitle = "Search settings";
        choices.clear();
        backSlot = backArrow();
        int bottom = (menu.getRowCount() - 1) * 9;
        for (Slot slot : menu.slots) {
            if (slot.index >= bottom || !slot.hasItem() || slot.getItem().is(Items.STAINED_GLASS_PANE.black())) continue;
            ItemStack stack = slot.getItem();
            String item = strip(stack.getHoverName().getString()), value = selectedIn(stack);
            if (value.isEmpty()) continue;
            selected.put(item.toLowerCase(Locale.ENGLISH), value);
            choices.add(new Choice(slot.index, item, value, stack));
        }
    }

    private void readOptions(String title) {
        page = Page.OPTIONS;
        subTitle = title;
        choices.clear();
        backSlot = backArrow();
        int bottom = (menu.getRowCount() - 1) * 9;
        for (Slot slot : menu.slots) {
            if (slot.index >= bottom || !slot.hasItem() || slot.getItem().is(Items.STAINED_GLASS_PANE.black())) continue;
            ItemStack stack = slot.getItem();
            choices.add(new Choice(slot.index, strip(stack.getHoverName().getString()), "", stack));
        }
    }

    private void readRange(String title) {
        page = Page.RANGE;
        subTitle = title;
        backSlot = backArrow();
        minSlot = maxSlot = -1;
        int bottom = (menu.getRowCount() - 1) * 9;
        for (Slot slot : menu.slots) {
            if (slot.index >= bottom || !slot.hasItem()) continue;
            String item = strip(slot.getItem().getHoverName().getString()).toLowerCase(Locale.ENGLISH);
            if (item.contains("min")) minSlot = slot.index;
            else if (item.contains("max")) maxSlot = slot.index;
        }
        String key = title.toLowerCase(Locale.ENGLISH).contains("class") ? "class level" : "dungeon level";
        rangeText = "";
        for (var e : selected.entrySet()) if (e.getKey().contains(key)) rangeText = e.getValue();
    }

    // hypixel asks for a level on a sign
    public boolean onSign(SignBlockEntity sign, net.minecraft.world.level.block.entity.SignTextSlot front) {
        if (aborted || page != Page.RANGE) return false;
        String asks = sign.getText(front).getMessages(false).get(3).getString().toLowerCase(Locale.ENGLISH);
        if (!asks.contains("max") && !asks.contains("min")) return false;
        this.sign = sign;
        signFront = front;
        signWhat = asks.contains("max") ? "Maximum" : "Minimum";
        signInput = "";
        page = Page.SIGN;
        waiting = false;
        return true;
    }

    private void answerSign(String text) {
        ClientPacketListener net = minecraft.getConnection();
        if (net != null && sign != null) {
            java.util.List<Component> lines = sign.getText(signFront).getMessages(minecraft.isTextFilteringEnabled());
            net.send(new ServerboundSignUpdatePacket(sign.getBlockPos(), java.util.List.of(text, lines.get(1).getString(), lines.get(2).getString(), lines.get(3).getString()), signFront));
        }
        sign = null;
        waiting = true;
    }

    private void abort() {
        aborted = true;
        minecraft.gui.setScreen(new ContainerScreen(menu, inventory, name));
    }

    private void click(int slot) {
        if (slot < 0 || waiting || minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, minecraft.player);
        waiting = true;
        scrollTarget = 0;
    }

    // --- screen ---

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void tick() {
        // slots come in one at a time mostly, wait for a short quiet moment before reading
        int now = 0;
        for (Slot slot : menu.slots) now = 31 * now + System.identityHashCode(slot.getItem());
        if (now != signature) {
            signature = now;
            dirty = true;
            dirtyAt = System.currentTimeMillis();
        }
        if (dirty && page != Page.SIGN && System.currentTimeMillis() - dirtyAt > 60) read();
        if (minecraft.player != null && (!minecraft.player.isAlive() || minecraft.player.isRemoved()) && page != Page.SIGN) minecraft.player.closeContainer();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        scale = Math.min(1f, Math.min(width / 460f, height / 300f));
        int W = Math.round(width / scale), H = Math.round(height / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        ui.begin(g, Math.round(mouseX / scale), Math.round(mouseY / scale), mouseX, mouseY);
        paint(ui, g, W, H);
        g.pose().popMatrix();
        ui.flushTooltip();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / scale, my = e.y() / scale;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (mx < hit.x() || my < hit.y() || mx >= hit.x() + hit.w() || my >= hit.y() + hit.h() || hit.click() == null) continue;
            hit.click().run();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scrollTarget -= (float) (Math.signum(sy) * 30);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        String c = e.codepointAsString();
        if (c.isEmpty() || c.charAt(0) < 32) return true;
        if (page == Page.SIGN) {
            if (Character.isDigit(c.charAt(0)) && signInput.length() < 3) signInput += c;
        } else if (page == Page.FINDER && query.length() < 30) {
            query += c;
            scrollTarget = 0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k == InputConstants.KEY_ESCAPE) {
            if (page == Page.FINDER && !query.isEmpty()) query = "";
            else onClose();
            return true;
        }
        if (k == InputConstants.KEY_BACKSPACE) {
            if (page == Page.SIGN && !signInput.isEmpty()) signInput = signInput.substring(0, signInput.length() - 1);
            else if (page == Page.FINDER && !query.isEmpty()) query = query.substring(0, query.length() - 1);
            return true;
        }
        if ((k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) && page == Page.SIGN && !signInput.isEmpty()) {
            answerSign(signInput);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        if (page == Page.SIGN && sign != null) {
            // leave the sign as it was so nothing changes
            ClientPacketListener net = minecraft.getConnection();
            java.util.List<Component> lines = sign.getText(signFront).getMessages(minecraft.isTextFilteringEnabled());
            if (net != null) net.send(new ServerboundSignUpdatePacket(sign.getBlockPos(), java.util.List.of(lines.get(0).getString(), lines.get(1).getString(), lines.get(2).getString(), lines.get(3).getString()), signFront));
            sign = null;
        } else if (minecraft.player != null) minecraft.player.closeContainer();
        super.onClose();
    }

    @Override
    public void removed() {
        if (minecraft.player == null || aborted || page == Page.SIGN) return;
        menu.removed(minecraft.player);
    }

    // --- drawing ---

    private static boolean option(String key) {
        Feature f = Features.get("party_finder");
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    private static void flip(String key) {
        Feature f = Features.get("party_finder");
        Option o = f == null ? null : f.option(key);
        if (o != null) o.set(!o.bool());
    }

    // skyblocker DungeonFinderFeatures.markBelowClassLevel
    private static boolean lowLevel(Party p) {
        int low = io.muzy.sbc.dungeon.DungeonMarks.integer("dungeon_finder", "low_level", 0);
        if (low != 0) for (Member m : p.members()) if (m.level() <= low) return true;
        return false;
    }

    private static String myClass() {
        Profile.Data me = Profile.current;
        return me == null ? "" : me.selectedClass;
    }

    private static ItemStack head(String player) {
        return HEADS.computeIfAbsent(player, n -> {
            ItemStack st = new ItemStack(Items.PLAYER_HEAD);
            st.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(n));
            return st;
        });
    }

    private static int classColor(String c) {
        return switch (c) {
            case "Healer" -> 0xFFFF80AB;
            case "Mage" -> 0xFF7FB3FF;
            case "Berserk" -> 0xFFFF6B6B;
            case "Archer" -> 0xFF9CE37D;
            case "Tank" -> 0xFF90A4AE;
            default -> Modern.DIM;
        };
    }

    private static String letter(String c) {
        return c.isEmpty() ? "?" : c.substring(0, 1);
    }

    private List<Party> shown() {
        boolean hideLocked = option("hide_locked"), hidePaid = option("hide_paid"), onlyMine = option("my_class");
        String mine = myClass();
        List<Party> out = new ArrayList<>();
        for (Party p : parties) {
            if (!p.matches(query.trim())) continue;
            if (!p.yours() && (hideLocked && p.locked() || hidePaid && p.paidCarry() || onlyMine && !mine.isEmpty() && !p.missing().contains(mine))) continue;
            out.add(p);
        }
        return out;
    }

    void paint(Ui ui, GuiGraphicsExtractor g, int W, int H) {
        ui.rect(0, 0, W, H, 0xB0060A10);
        int[] box = Modern.window(W, H, 760);
        int x = box[0], y = box[1], w = box[2], h = box[3];
        Modern.card(ui, x, y, w, h, 0.97f);
        String right = page == Page.FINDER ? shown().size() + " of " + parties.size() + " parties" : subTitle;
        Modern.band(ui, "Party Finder", LegacyItems.stack("skeleton_skull"), right, ACCENT, x, y, w);
        int cx = x + 6, cw = w - 12, by = y + 18;

        int bx = cx;
        boolean sub = page == Page.OPTIONS || page == Page.RANGE || page == Page.SIGN;
        ui.button("pf_parties", bx, by, 60, 16, "Parties", page == Page.FINDER ? Ui.PRIMARY : Ui.GHOST, () -> {
            if (page == Page.SETTINGS) click(backSlot);
        });
        bx += 63;
        ui.button("pf_settings", bx, by, 90, 16, "Search settings", page == Page.SETTINGS || sub ? Ui.PRIMARY : Ui.GHOST, () -> {
            if (page == Page.FINDER) click(settingsSlot);
            else if (page == Page.OPTIONS || page == Page.RANGE) click(backSlot);
        });
        bx += 93;
        if (page == Page.FINDER) {
            boolean listed = delistSlot != -1;
            ui.button("pf_create", bx, by, 70, 16, listed ? "De-list" : "Create", listed ? Ui.DANGER : Ui.SOFT, () -> click(listed ? delistSlot : createSlot));
            int rx = cx + cw;
            rx -= 50;
            ui.button("pf_refresh", rx, by, 50, 16, "Refresh", Ui.SOFT, () -> click(refreshSlot));
            rx -= 19;
            if (nextSlot != -1) ui.button("pf_next", rx, by, 16, 16, "›", Ui.GHOST, () -> click(nextSlot));
            rx -= 19;
            if (prevSlot != -1) ui.button("pf_prev", rx, by, 16, 16, "‹", Ui.GHOST, () -> click(prevSlot));
        }
        if (waiting) ui.textRight("Waiting for server...", x + w - 8, y + h - 12, Modern.DIM);

        int top = by + 20;
        switch (page) {
            case SETTINGS -> settings(ui, cx, top, cw);
            case OPTIONS -> options(ui, cx, top, cw);
            case RANGE -> range(ui, cx, top, cw);
            case SIGN -> signPage(ui, cx, top, cw);
            default -> finder(ui, g, cx, top, cw, y + h - 4);
        }
    }

    private void finder(Ui ui, GuiGraphicsExtractor g, int cx, int top, int cw, int bottom) {
        // search box, filters, you
        ui.rect(cx, top, 150, 14, 0x40000000);
        ui.rect(cx, top + 13, 150, 1, ui.hover(cx, top, 150, 14) ? ACCENT : Modern.DIM);
        boolean blink = System.currentTimeMillis() / 500 % 2 == 0;
        if (query.isEmpty()) ui.textShadow("Type to search" + (blink ? "_" : ""), cx + 4, top + 3, Modern.DIM);
        else ui.textShadow(ui.ellipsize(query, 136) + (blink ? "_" : ""), cx + 4, top + 3, Modern.INK);
        int fx = cx + 155;
        fx = filter(ui, fx, top, "Hide locked", "hide_locked");
        fx = filter(ui, fx, top, "Hide paid", "hide_paid");
        String mine = myClass();
        if (!mine.isEmpty()) filter(ui, fx, top, mine + " free", "my_class");
        Profile.Data me = Profile.current;
        if (me != null && me.catacombs != null) {
            Profile.Level cl = mine.isEmpty() ? null : me.classes.get(mine);
            ui.textRight("You: Cata " + me.catacombs.level() + (cl != null ? "  " + mine + " " + cl.level() : ""), cx + cw, top + 3, Modern.LABEL);
        }
        int listTop = top + 18, viewH = bottom - listTop;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentH - viewH)));
        scroll += (scrollTarget - scroll) * 0.35f;
        g.enableScissor(cx - 1, listTop, cx + cw + 1, bottom);
        ui.clipX0 = cx - 1;
        ui.clipY0 = listTop;
        ui.clipX1 = cx + cw + 1;
        ui.clipY1 = bottom;
        int ly = listTop - Math.round(scroll), lw = cw - 6;
        int cols = lw >= 620 ? 2 : 1, colW = (lw - (cols - 1) * 4) / cols;
        List<Party> list = shown();
        for (int i = 0; i < list.size(); i++) card(ui, list.get(i), cx + (i % cols) * (colW + 4), ly + (i / cols) * (CARD_H + 4), colW);
        contentH = (list.size() + cols - 1) / cols * (CARD_H + 4);
        if (list.isEmpty()) {
            String why = noParties ? "No parties are listed right now." : parties.isEmpty() ? "Loading..." : "No party matches that.";
            ui.textCenter(why, cx + cw / 2, listTop + 30, Modern.LABEL);
        }
        g.disableScissor();
        ui.clipX0 = Integer.MIN_VALUE;
        ui.clipY0 = Integer.MIN_VALUE;
        ui.clipX1 = Integer.MAX_VALUE;
        ui.clipY1 = Integer.MAX_VALUE;
        if (contentH > viewH) {
            int barH = Math.max(14, viewH * viewH / contentH);
            int barY = listTop + (int) ((viewH - barH) * (scroll / Math.max(1, contentH - viewH)));
            ui.rect(cx + cw - 3, listTop, 3, viewH, 0x30000000);
            ui.rect(cx + cw - 3, barY, 3, barH, ACCENT);
        }
    }

    private int filter(Ui ui, int x, int y, String label, String key) {
        int bw = ui.width(label) + 12;
        ui.button("pf_" + key, x, y, bw, 14, label, option(key) ? Ui.PRIMARY : Ui.GHOST, () -> flip(key));
        return x + bw + 3;
    }

    private void card(Ui ui, Party p, int x, int y, int w) {
        boolean locked = p.locked(), over = ui.hover(x, y, w, CARD_H), joinable = !locked && p.slot() >= 0;
        String mine = myClass();
        boolean myFree = !mine.isEmpty() && p.missing().contains(mine);
        int floorColor = p.master() ? MASTER : NORMAL;
        int edge = p.yours() ? Modern.GOLD : p.paidCarry() ? Modern.BAD : p.nonPug() ? 0xFFC77DFF : lowLevel(p) ? 0xFFFFFF55 : myFree ? Modern.GOOD : floorColor;
        ui.rect(x, y, w, CARD_H, over && joinable ? 0x34FFFFFF : 0x22000000);
        ui.rect(x, y, 2, CARD_H, locked ? Modern.DIM : edge);
        Member leader = p.members().isEmpty() ? null : p.members().get(0);

        // floor + host
        ui.textScaled(p.shortFloor(), x + w - 6 - ui.width(p.shortFloor()) * 1.5f, y + 3, 1.5f, floorColor, true);
        ui.icon(head(p.leader()), x + 6, y + 3, 0.75f);
        ui.textShadow(ui.ellipsize(p.leader(), w / 2), x + 20, y + 5, Modern.INK);
        int after = x + 20 + ui.width(ui.ellipsize(p.leader(), w / 2)) + 4;
        if (leader != null) ui.textShadow(letter(leader.dungeonClass()) + " " + leader.level(), after, y + 5, classColor(leader.dungeonClass()));
        if (p.yours()) ui.textRight(p.slot() >= 0 && over ? "De-list" : "Your party", x + w - 28, y + 5, Modern.GOLD);
        else if (over && joinable) ui.textRight("Click to join", x + w - 28, y + 5, Modern.GOOD);

        // everyone else, 2 by 2
        int col = (w - 12) / 2;
        for (int i = 1; i < 5; i++) {
            int mx = x + 6 + (i - 1) % 2 * col, my = y + 18 + (i - 1) / 2 * 11;
            if (i >= p.members().size()) {
                ui.textShadow("Empty", mx + 13, my + 1, 0xFF505862);
                continue;
            }
            Member m = p.members().get(i);
            ui.icon(head(m.name()), mx, my, 0.5f);
            String n = ui.ellipsize(m.name(), col - 50);
            ui.textShadow(n, mx + 13, my + 1, Modern.LABEL);
            ui.textShadow(letter(m.dungeonClass()) + " " + m.level(), mx + 13 + ui.width(n) + 4, my + 1, classColor(m.dungeonClass()));
        }

        // free classes, what it asks for and the note. or why you cant join
        int fy = y + 44, fx = x + 6;
        if (locked) {
            ui.rect(x, y, w, CARD_H, 0x60000000);
            ui.textShadow(ui.ellipsize(p.lock(), w - 12), fx, fy, Modern.BAD);
        } else {
            for (String c : PartyListing.CLASSES) {
                boolean free = p.missing().contains(c);
                ui.textShadow(letter(c), fx, fy, !free ? 0xFF3C434C : c.equals(mine) ? Modern.GOOD : classColor(c));
                fx += ui.width(letter(c)) + 3;
            }
            fx += 4;
            Profile.Data me = Profile.current;
            if (p.minCata() > 0) fx += Modern.chip(ui, fx, fy, "Cata " + p.minCata(), me != null && me.catacombs != null && me.catacombs.level() < p.minCata() ? Modern.BAD : Modern.LABEL) + 3;
            if (p.minClass() > 0) {
                Profile.Level cl = me == null ? null : me.classes.get(mine);
                fx += Modern.chip(ui, fx, fy, "Class " + p.minClass(), cl != null && cl.level() < p.minClass() ? Modern.BAD : Modern.LABEL) + 3;
            }
            if (p.paidCarry()) fx += Modern.chip(ui, fx, fy, "Paid carry", Modern.BAD) + 3;
            else if (p.nonPug()) fx += Modern.chip(ui, fx, fy, "Group", 0xFFC77DFF) + 3;
            if (!p.note().isEmpty()) ui.textShadow(ui.ellipsize(p.note(), x + w - 4 - fx), fx + 1, fy, 0xFFE8D9A8);
        }
        if (over) tooltip(ui, p);
        if (joinable) ui.click(x, y, w, CARD_H, () -> click(p.slot()));
    }

    private static void tooltip(Ui ui, Party p) {
        List<String> tip = new ArrayList<>();
        tip.add("§f" + p.leader() + "'s Party");
        tip.add("§7" + p.dungeon() + " §8- §7" + p.floor());
        if (!p.note().isEmpty()) tip.add("§7Note: §e" + p.note());
        tip.add("");
        for (Member m : p.members()) tip.add("§b" + m.name() + "§f: §e" + m.dungeonClass() + " §7(" + m.level() + ")");
        if (!p.missing().isEmpty()) tip.add("§cMissing: §7" + String.join(", ", p.missing()));
        ui.tooltip(tip.toArray(new String[0]));
    }

    private void settings(Ui ui, int x, int y, int w) {
        Modern.section(ui, "What the list shows", x, y, w, ACCENT);
        int top = y + 12, cols = w >= 500 ? 2 : 1, colW = (w - (cols - 1) * 4) / cols;
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            int cx = x + (i % cols) * (colW + 4), cy = top + (i / cols) * 28;
            boolean over = ui.hover(cx, cy, colW, 25);
            ui.rect(cx, cy, colW, 25, over ? 0x34FFFFFF : 0x22000000);
            ui.rect(cx, cy, 2, 25, ACCENT);
            ui.icon(c.icon(), cx + 6, cy + 4, 1f);
            ui.textShadow(ui.ellipsize(c.name(), colW - 32), cx + 26, cy + 4, Modern.INK);
            ui.textShadow(ui.ellipsize(c.selected(), colW - 32), cx + 26, cy + 14, Modern.GOLD);
            ui.click(cx, cy, colW, 25, () -> click(c.slot()));
        }
    }

    private void options(Ui ui, int x, int y, int w) {
        Modern.section(ui, subTitle, x, y, w, ACCENT);
        int top = y + 12, perRow = Math.max(2, w / 150), colW = (w - (perRow - 1) * 4) / perRow;
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            int cx = x + (i % perRow) * (colW + 4), cy = top + (i / perRow) * 24;
            boolean over = ui.hover(cx, cy, colW, 21);
            ui.rect(cx, cy, colW, 21, over ? 0x34FFFFFF : 0x22000000);
            ui.icon(c.icon(), cx + 4, cy + 2, 1f);
            ui.textShadow(ui.ellipsize(c.name(), colW - 26), cx + 23, cy + 6, over ? Modern.GOLD : Modern.INK);
            ui.click(cx, cy, colW, 21, () -> click(c.slot()));
        }
    }

    private void range(Ui ui, int x, int y, int w) {
        Modern.section(ui, subTitle, x, y, w, ACCENT);
        if (!rangeText.isEmpty()) ui.textShadow("Now: " + rangeText, x, y + 14, Modern.GOLD);
        ui.button("pf_min", x, y + 28, 110, 18, "Set minimum", Ui.SOFT, () -> click(minSlot));
        ui.button("pf_max", x + 114, y + 28, 110, 18, "Set maximum", Ui.SOFT, () -> click(maxSlot));
    }

    private void signPage(Ui ui, int x, int y, int w) {
        Modern.section(ui, signWhat + " level", x, y, w, ACCENT);
        boolean blink = System.currentTimeMillis() / 500 % 2 == 0;
        ui.rect(x, y + 16, 80, 16, 0x40000000);
        ui.rect(x, y + 31, 80, 1, ACCENT);
        ui.textShadow(signInput + (blink ? "_" : ""), x + 4, y + 20, Modern.INK);
        ui.button("pf_ok", x + 84, y + 16, 50, 16, "Set", signInput.isEmpty() ? Ui.GHOST : Ui.PRIMARY, () -> {
            if (!signInput.isEmpty()) answerSign(signInput);
        });
        ui.textShadow("Type the level and press Enter. Esc leaves it as it is.", x, y + 38, Modern.DIM);
    }
}
