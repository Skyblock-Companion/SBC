// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.visitor;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.garden.GardenData;
import dev.zmuzy.sbc.garden.GardenItems;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.item.sacks.Sacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;

// whos waiting in the garden and what they want. from skyhanni VisitorApi, VisitorListener,
// GardenVisitorStatus + NpcVisitorFix
// tab says whos there, a visitor's menu says what it wants, kept until it leaves. read here, the features only look
public final class Visitors {
    public static final int INFO_SLOT = 13, ACCEPT_SLOT = 29, REFUSE_SLOT = 33, VINYL_SLOT = 48;
    // GardenApi.barnArea
    private static final AABB BARN_AREA = new AABB(35.5, 70, -4.5, -32.5, 100, -46.5);
    // NpcVisitorFix.staticVisitors: on the garden twice when visiting
    private static final List<String> STATIC_VISITORS = List.of("Jacob", "Anita");

    // VisitorApi.VisitorStatus. colour = the one over the visitor, 0 = none
    public enum Status {
        NEW("§eNew", 0x64FFFF55), WAITING("Waiting", 0), READY("§aItems Ready", 0x5055FF55), ACCEPTED("§7Accepted", 0x50555555), REFUSED("§cRefused", 0x3CFF5555);

        public final String display;
        public final int color;

        Status(String display, int color) {
            this.display = display;
            this.color = color;
        }
    }

    // VisitorApi.VisitorBlockReason
    public enum BlockReason {
        NEVER_ACCEPTED("§cNever accepted", true), RARE_REWARD("§aRare visitor reward found", true), CHEAP_COPPER("§aCheap copper", true),
        EXPENSIVE_COPPER("§cExpensive copper", false), LOW_LOSS("§aLow Loss", true), HIGH_LOSS("§cHigh Loss", false);

        public final String description;
        public final boolean blockRefusing;

        BlockReason(String description, boolean blockRefusing) {
            this.description = description;
            this.blockRefusing = blockRefusing;
        }
    }

    public static final class Visitor {
        public final String name;
        public Status status = Status.NEW;
        public int entityId = -1, nameTagId = -1;
        public VisitorOffer offer;
        public Integer offersAccepted;
        public BlockReason blockReason;
        // when a refused visitor counts as gone if the tab doesnt say first
        public long removeAt;

        Visitor(String name) {
            this.name = name;
        }

        public Map<String, Integer> shopping() {
            return offer == null ? Map.of() : offer.shopping;
        }

        public boolean ignored() {
            return GardenData.get().ignoredVisitors.contains(name);
        }

        public void toggleIgnored() {
            var set = GardenData.get().ignoredVisitors;
            if (!set.remove(name)) set.add(name);
            Store.markDirty();
        }

        // getRewardWarningAwards
        public List<VisitorReward> warned() {
            List<VisitorReward> out = new ArrayList<>();
            if (offer == null) return out;
            for (VisitorReward r : offer.rareRewards) if (VisitorBlock.warnsFor(r)) out.add(r);
            return out;
        }
    }

    private static Map<String, Visitor> visitors = new LinkedHashMap<>();
    private static Visitor open;
    private static MenuView lastView;
    private static String lastProfile = "";
    private static Object lastLevel;
    private static long worldSwitch, staticCheckAt;
    private static Object staticScreen;
    private static int ticks;

    // new visitor
    static Consumer<Visitor> onArrival = v -> {};
    // visitor menu opened, status read
    static Consumer<Visitor> onOpen = v -> {};
    static Consumer<Visitor> onAccepted = v -> {}, onRefused = v -> {};

    private Visitors() {}

    public static boolean garden() {
        return "GARDEN".equals(GameState.island) || dev.zmuzy.sbc.SbcMod.DEV;
    }

    public static List<Visitor> all() {
        return new ArrayList<>(visitors.values());
    }

    // VisitorApi.inInventory: visitor whose menu is open, or null
    public static Visitor open() {
        return open;
    }

    public static long sinceWorldSwitch() {
        return System.currentTimeMillis() - worldSwitch;
    }

    // GardenApi.onBarnPlot
    public static boolean onBarnPlot() {
        var mc = Minecraft.getInstance();
        return garden() && mc != null && mc.player != null && Math.abs(mc.player.getX()) <= 48 && Math.abs(mc.player.getZ()) <= 48;
    }

    public static void reset() {
        visitors = new LinkedHashMap<>();
        open = null;
    }

    public static void changeStatus(Visitor v, Status status) {
        if (v.status == status) return;
        v.status = status;
        if (status == Status.ACCEPTED) onAccepted.accept(v);
        else if (status == Status.REFUSED) onRefused.accept(v);
    }

    private static boolean add(String name) {
        if (visitors.containsKey(name)) return false;
        Visitor v = new Visitor(name);
        visitors.put(name, v);
        onArrival.accept(v);
        return true;
    }

    static void remove(String name) {
        visitors.remove(name);
    }

    // VisitorApi.visitorsInTabList on the tab's plain lines, the header says how many names follow
    public static List<String> visitorsInTabList(List<String> tab) {
        List<String> out = new ArrayList<>();
        int remaining = 0;
        boolean found = false;
        for (String line : tab) {
            String t = line.trim();
            if (t.startsWith("Visitors:")) {
                String n = t.substring("Visitors:".length()).replaceAll("\\D", "");
                remaining = n.isEmpty() ? 0 : Integer.parseInt(n);
                found = true;
                continue;
            }
            if (!found) continue;
            if (remaining <= 0) {
                found = false;
                continue;
            }
            String name = t.endsWith(" NEW!") ? t.substring(0, t.length() - 5).trim() : t;
            // a blank line ends the widget, even when Hypixel cut it short and fewer names came
            if (name.isEmpty()) {
                found = false;
                continue;
            }
            remaining--;
            out.add(name);
        }
        return out;
    }

    // how many visitors the header says there are, -1 without the widget
    public static int visitorsExpected(List<String> tab) {
        for (String line : tab) {
            String t = line.trim();
            if (!t.startsWith("Visitors:")) continue;
            String n = t.substring("Visitors:".length()).replaceAll("\\D", "");
            return n.isEmpty() ? 0 : Integer.parseInt(n);
        }
        return -1;
    }

    private static void readTab() {
        List<String> inTab = visitorsInTabList(GameState.tab);
        // VisitorListener.onWidgetUpdate: only if the widget is there at all
        boolean widget = GameState.tab.stream().anyMatch(l -> l.trim().startsWith("Visitors:"));
        if (!widget) return;
        // the tab list is often cut off after a name or two. a visitor that is not shown is only gone when every
        // name the header counts is there; otherwise the list kept flickering and lost its offers
        if (sinceWorldSwitch() > 2000 && inTab.size() >= visitorsExpected(GameState.tab)) visitors.keySet().removeIf(name -> !inTab.contains(name));
        for (String name : inTab) add(name);
    }

    private static final java.util.regex.Pattern ACCEPTED = java.util.regex.Pattern.compile("Offers Accepted: ([\\d,]+).*");

    // VisitorApi.isVisitorInfo, then what the menu says about the visitor. runs when the menu is read, tests too
    public static void onMenu(MenuView v) {
        if (v == lastView) return;
        lastView = v;
        if (v == null) {
            open = null;
            return;
        }
        MenuView.Item npc = v.item(INFO_SLOT);
        MenuView.Item accept = v.item(ACCEPT_SLOT);
        // skyhanni wants line 4 with its colors but hypixel moves lines and adds resets, the words are what matters
        String accepted = null;
        for (String l : npc.cleanLore) if (l.trim().startsWith("Offers Accepted: ")) accepted = l.trim();
        if (accepted == null || !accept.clean.equals("Accept Offer")) {
            open = null;
            return;
        }
        Visitor visitor = visitors.get(npc.clean);
        if (visitor == null) {
            // tab hasnt caught up yet
            add(npc.clean);
            visitor = visitors.get(npc.clean);
        }
        if (open == visitor) {
            checkCharmed(visitor, v);
            return;
        }
        open = visitor;
        Matcher offers = ACCEPTED.matcher(accepted);
        visitor.offersAccepted = offers.matches() ? Integer.valueOf(offers.group(1)) : null;
        boolean first = visitor.offer == null;
        visitor.offer = VisitorOffer.read(accept.lore);
        visitor.blockReason = VisitorBlock.reason(visitor);
        changeStatus(visitor, visitor.offer.ready ? Status.READY : Status.WAITING);
        checkCharmed(visitor, v);
        if (first) VisitorChat.foundRewards(visitor);
        VisitorCards.syncBuyList();
        onOpen.accept(visitor);
    }

    // GardenCharmedVisitors.checkCharmed
    private static void checkCharmed(Visitor visitor, MenuView v) {
        boolean charmed = Patterns.get("SH.garden.visitor.charmed.name").matcher(v.item(VINYL_SLOT).clean).matches();
        var set = GardenData.get().charmedVisitors;
        if (charmed ? set.add(visitor.name) : set.remove(visitor.name)) Store.markDirty();
    }

    // VisitorRewardWarning.onSlotClick: shift click is still blocked but refuses and accepts nothing
    static boolean countsAsAnswer(net.minecraft.world.inventory.ContainerInput input) {
        return input != net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;
    }

    // VisitorRewardWarning.onSlotClick, true swallows the click
    static boolean onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        Visitor visitor = open;
        if (visitor == null || slot == null) return false;
        String name = GameState.strip(slot.getItem().getHoverName().getString());
        boolean refuse = name.equals("Refuse Offer"), accept = name.equals("Accept Offer");
        if (!refuse && !accept) return false;
        if (VisitorBlock.blocks(visitor, refuse)) return true;
        if (!countsAsAnswer(dev.zmuzy.sbc.mc.Interactions.clickInput())) return false;
        if (refuse) {
            changeStatus(visitor, Status.REFUSED);
            // fallback if the tab list is off
            visitor.removeAt = System.currentTimeMillis() + 10_000;
            return false;
        }
        if (MenuView.Item.of(slot.getItem()).cleanHas("Click to give!")) changeStatus(visitor, Status.ACCEPTED);
        return false;
    }

    // GardenVisitorStatus.hasItems
    public static boolean hasItems(Visitor v) {
        for (var need : v.shopping().entrySet()) if (having(need.getKey()) < need.getValue()) return false;
        return true;
    }

    public static int having(String id) {
        Integer sacks = Sacks.amountInSacks(id);
        return GardenItems.inInventory(id) + (sacks == null ? 0 : sacks);
    }

    // GardenVisitorStatus.checkVisitorsReady
    private static void checkReady(Minecraft mc) {
        for (Visitor v : visitors.values()) {
            if (mc.level != null && (v.entityId < 0 || mc.level.getEntity(v.entityId) == null)) {
                ArmorStand tag = findNametag(mc, v.name);
                if (tag != null) findEntity(mc, tag, v);
            }
            if (v.status == Status.WAITING || v.status == Status.READY) changeStatus(v, hasItems(v) ? Status.READY : Status.WAITING);
        }
    }

    // GardenVisitorStatus.findEntity
    private static void findEntity(Minecraft mc, ArmorStand tag, Visitor v) {
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, tag.getBoundingBox().inflate(5))) {
            if (e instanceof ArmorStand || e == mc.player) continue;
            double dx = e.getX() - tag.getX(), dz = e.getZ() - tag.getZ();
            if (dx * dx + dz * dz >= 0.25) continue;
            v.entityId = e.getId();
            v.nameTagId = tag.getId();
        }
    }

    private static List<ArmorStand> findNametags(Minecraft mc, String name) {
        List<ArmorStand> out = new ArrayList<>();
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, BARN_AREA)) {
            if (GameState.strip(st.getName().getString()).equals(name)) out.add(st);
        }
        return out;
    }

    // NpcVisitorFix.findNametag: of jacob and anita, not the one that always stands there
    private static ArmorStand findNametag(Minecraft mc, String name) {
        List<ArmorStand> tags = findNametags(mc, name);
        if (tags.isEmpty()) return null;
        if (!STATIC_VISITORS.contains(name)) return tags.get(0);
        double[] at = GardenData.get().npcVisitorLocations.get(name);
        if (at == null) return null;
        for (ArmorStand tag : tags) {
            double dx = tag.getX() - at[0], dy = tag.getY() - at[1], dz = tag.getZ() - at[2];
            if (dx * dx + dy * dy + dz * dz >= 9) return tag;
        }
        return null;
    }

    // NpcVisitorFix.onInventoryOpen: the menu of the real jacob or anita (not a visitor) says where they stand
    private static void saveStaticVisitor(Minecraft mc, MenuView v) {
        if (open != null || mc.player == null || mc.level == null) return;
        for (String name : STATIC_VISITORS) {
            if (!v.title.contains(name)) continue;
            ArmorStand nearest = null;
            for (ArmorStand tag : findNametags(mc, name)) if (tag.distanceTo(mc.player) < 3) nearest = tag;
            if (nearest == null) continue;
            double[] known = GardenData.get().npcVisitorLocations.get(name);
            if (known != null && nearest.distanceToSqr(known[0], known[1], known[2]) < 1) return;
            GardenData.get().npcVisitorLocations.put(name, new double[]{nearest.getX(), nearest.getY(), nearest.getZ()});
            Store.markDirty();
        }
    }

    // NpcVisitorFix.onChat: a new barn skin moves them
    static void barnSkinChanged() {
        GardenData.get().npcVisitorLocations.clear();
        Store.markDirty();
    }

    public static Entity nameTagOf(Minecraft mc, Visitor v) {
        return mc.level == null || v.nameTagId < 0 ? null : mc.level.getEntity(v.nameTagId);
    }

    public static Entity entityOf(Minecraft mc, Visitor v) {
        return mc.level == null || v.entityId < 0 ? null : mc.level.getEntity(v.entityId);
    }

    static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldSwitch = System.currentTimeMillis();
            for (Visitor v : visitors.values()) v.entityId = v.nameTagId = -1;
        }
        if (!GameState.profileName.equals(lastProfile)) {
            lastProfile = GameState.profileName;
            reset();
        }
        if (!garden()) {
            open = null;
            lastView = null;
            return;
        }
        ticks++;
        if (ticks % 2 == 0) {
            Object screen = mc.gui.screen();
            MenuView now = screen instanceof AbstractContainerScreen<?> ? MenuMarks.current() : null;
            onMenu(now);
            // NpcVisitorFix: 200ms later, when a visitor menu would have shown its one
            long time = System.currentTimeMillis();
            if (screen != staticScreen) {
                staticScreen = screen;
                staticCheckAt = now == null ? 0 : time + 200;
            }
            if (staticCheckAt != 0 && time >= staticCheckAt && now != null) {
                staticCheckAt = 0;
                saveStaticVisitor(mc, now);
            }
        }
        if (ticks % 10 != 2) return;
        readTab();
        long time = System.currentTimeMillis();
        visitors.values().removeIf(v -> v.removeAt != 0 && time > v.removeAt);
        if (onBarnPlot() && mc.level != null) checkReady(mc);
    }
}
