// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.Toasts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// safety for your own stuff: a locked slot cant be dragged, shift clicked, swapped or dropped
// a protected item can never be dropped or shift clicked into a shop
// only stops clicks you make, never acts for you
public final class SlotLocks {
    private static final String OTHER_MOD = "skyblock_enhancements", OTHER_KEY = "key.skyblock_enhancements.lock_slot",
            OTHER_CONFIG = "com.github.kd_gaming1.skyblockenhancements.config.SkyblockEnhancementsConfig", UNBOUND = "key.keyboard.unknown";
    private static long lastWarn;
    private static boolean settled;

    private SlotLocks() {}

    private static boolean on() {
        return Features.on("slotlocks");
    }

    // skyblock enhancements locks slots too, on the same L, so one press did two things and a slot locked there
    // couldnt be opened here
    // once a session when both want the same key, we keep it: their key gets unbound, their locks go off for this
    // session only, their settings file stays untouched
    // if the player gave it some other key on purpose, leave it. its locked slots move here once so nothing
    // valuable loses its lock
    public static void settleWithOtherMod(Minecraft mc, KeyMapping ours) {
        if (settled || mc.player == null) return;
        settled = true;
        try {
            if (!FabricLoader.getInstance().isModLoaded(OTHER_MOD)) return;
            for (KeyMapping other : mc.options.keyMappings) {
                if (!other.getName().equals(OTHER_KEY)) continue;
                if (other.saveString().equals(ours.saveString())) {
                    other.setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                    mc.options.save();
                }
                if (!other.saveString().equals(UNBOUND)) return;
            }
            Class.forName(OTHER_CONFIG).getField("enableSlotLocking").setBoolean(null, false);
            if (Config.I.otherLocksImported) return;
            Path file = FabricLoader.getInstance().getConfigDir().resolve(OTHER_MOD).resolve("slot_locks.json");
            if (Files.exists(file)) {
                Set<Integer> locked = locksOf(Files.readString(file), mc.player.getUUID().toString());
                if (Store.I.lockedSlots.addAll(locked)) Store.markDirty();
            }
            Config.I.otherLocksImported = true;
            Config.save();
        } catch (Throwable t) {
            SbcMod.LOG.warn("Could not settle the slot lock key", t);
        }
    }

    // slots an account locked in that mods file, any profile: {"buckets": {"account|profile": {"locked": [0, 1]}}}
    static Set<Integer> locksOf(String json, String account) {
        Set<Integer> out = new TreeSet<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("buckets")) return out;
        for (Map.Entry<String, JsonElement> bucket : root.getAsJsonObject("buckets").entrySet()) {
            if (!bucket.getKey().startsWith(account + "|")) continue;
            JsonObject b = bucket.getValue().getAsJsonObject();
            if (!b.has("locked")) continue;
            for (JsonElement slot : b.getAsJsonArray("locked")) out.add(slot.getAsInt());
        }
        return out;
    }

    // the items own uuid if hypixel gave it one, else its id (protects all copies)
    public static String key(ItemStack st) {
        if (st.isEmpty()) return "";
        var cd = st.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "";
        var tag = cd.copyTag();
        String uuid = tag.getStringOr("uuid", "");
        return !uuid.isEmpty() ? uuid : tag.getStringOr("id", "");
    }

    public static boolean isProtected(ItemStack st) {
        String k = key(st);
        return !k.isEmpty() && Store.I.protectedItems.contains(k);
    }

    public static boolean isLocked(Slot s) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && s.container == mc.player.getInventory() && Store.I.lockedSlots.contains(s.getContainerSlot());
    }

    public static boolean lockedIndex(int inventoryIndex) {
        return Store.I.lockedSlots.contains(inventoryIndex);
    }

    private static void warn(String what) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1500) return;
        lastWarn = now;
        Toasts.push("Protected", what, 0xFFFFC857);
    }

    // skyblocker ItemProtection displayChatNotification
    private static boolean notifies() {
        return io.github.zmuzy.sbc.item.market.Market.b("slotlocks", "slotlocks.notify", true);
    }

    // skyhanni PreventItemSell.onToggle (/sbc preventsell): held items uuid. protected stuff cant go into a shop,
    // trade or auction
    public static void toggleHeldForSale(Player p) {
        if (p == null) return;
        ItemStack st = p.getMainHandItem();
        var say = (java.util.function.Consumer<String>) s -> p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[SBC] " + s));
        if (st.isEmpty()) {
            say.accept("§cHold an item in hand!");
            return;
        }
        var cd = st.get(DataComponents.CUSTOM_DATA);
        String uuid = cd == null ? "" : cd.copyTag().getStringOr("uuid", "");
        if (uuid.isEmpty()) {
            say.accept("§cCan only lock in items that have a uuid!");
            return;
        }
        boolean now = Store.I.protectedItems.add(uuid);
        if (!now) Store.I.protectedItems.remove(uuid);
        Store.markDirty();
        say.accept(st.getHoverName().getString() + " §eis " + (now ? "§anow §eprotected from selling." : "§cno longer §eprotected from selling."));
    }

    private static long lastToggle;
    private static int lastToggleSlot = -1;

    // a key over a slot: lock key locks/unlocks it (skyblocker HotbarSlotLock), protection key
    // protects/unprotects the item (skyblocker ItemProtection.handleKeyPressed)
    // 2nd call for the same slot within 200 ms is ignored (repeat key event from a reinit screen)
    public static void toggle(Slot s, boolean protect) {
        if (s == null) return;
        long now = System.currentTimeMillis();
        if (s.index == lastToggleSlot && now - lastToggle < 200) return;
        lastToggle = now;
        lastToggleSlot = s.index;
        if (protect) protectItem(s.getItem());
        else lockSlot(s);
    }

    // skyblocker ItemProtection.handleKeyPressed: toggle the items uuid (or id) in the protected set
    public static void protectItem(ItemStack st) {
        String k = key(st);
        if (k.isEmpty()) {
            Toasts.push("Not protectable", "This item has no SkyBlock id", 0xFFFFC857);
            return;
        }
        boolean now = Store.I.protectedItems.add(k);
        if (!now) Store.I.protectedItems.remove(k);
        if (notifies()) Toasts.push(now ? "Item protected" : "Protection removed", st.getHoverName().getString(), now ? 0xFF4CE08A : 0xFFFFC857);
        Store.markDirty();
    }

    // skyblocker HotbarSlotLock: toggle the lock on your own inventory slot
    public static void lockSlot(Slot s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || s.container != mc.player.getInventory()) return;
        lockIndex(s.getContainerSlot());
    }

    // skyblocker HotbarSlotLock.handleInputEvents for the key in the world: the selected hotbar slot
    public static void lockIndex(int i) {
        boolean now = Store.I.lockedSlots.add(i);
        if (!now) Store.I.lockedSlots.remove(i);
        if (notifies()) Toasts.push(now ? "Slot locked" : "Slot unlocked", "Inventory slot " + (i + 1), now ? 0xFF4CE08A : 0xFFFFC857);
        Store.markDirty();
    }

    // lock key with no screen open locks the held hotbar slot
    public static void toggleHotbar(Player p) {
        if (p == null) return;
        lockIndex(p.getInventory().getSelectedSlot());
    }

    // true when this click must not go through
    // a locked slot never changes. a protected item moves freely, it just never leaves you: like skyblocker
    // ItemProtection, no throwing, salvaging, trading, auctioning, dungeon chest swap or npc selling
    public static boolean blocks(int slotNum, int button, ContainerInput input, Player p) {
        if (!on() || p == null) return false;
        var menu = p.containerMenu;
        // picked up and clicked outside the window = dropped
        if (slotNum == -999 && isProtected(menu.getCarried())) {
            warn("That item is protected");
            return true;
        }
        Slot slot = slotNum >= 0 && slotNum < menu.slots.size() ? menu.slots.get(slotNum) : null;
        if (slot == null) return false;
        boolean hotbarLocked = input == ContainerInput.SWAP && button >= 0 && button < 9 && lockedIndex(button);
        if (isLocked(slot) || hotbarLocked) {
            warn(isLocked(slot) ? "That slot is locked" : "A locked slot is in the way");
            return true;
        }
        ItemStack st = slot.getItem();
        ItemStack hb = input == ContainerInput.SWAP && button >= 0 && button < 9 ? p.getInventory().getItem(button) : ItemStack.EMPTY;
        if (input == ContainerInput.THROW && isProtected(st) || (isProtected(st) || isProtected(hb)) && losesIt(menu, slot)) {
            warn("That item is protected");
            return true;
        }
        return false;
    }

    // menus where a click gives the item away
    private static boolean losesIt(net.minecraft.world.inventory.AbstractContainerMenu menu, Slot slot) {
        var screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
        String title = screen == null ? "" : io.github.zmuzy.sbc.core.GameState.strip(screen.getTitle().getString());
        if (title.equals("Salvage Items") || title.startsWith("You  ")) return true;
        if (title.endsWith("Auction House") || title.equals("Create Auction") || title.equals("Create BIN Auction")) return true;
        // a dungeon chest drops what you swapped in if you close it first
        if (io.github.zmuzy.sbc.dungeon.DungeonRun.inDungeon() && title.equals("Chest")) return true;
        return menu instanceof net.minecraft.world.inventory.ChestMenu && npcSellMenu(menu) && !npcSellButton(slot.getItem());
    }

    private static boolean npcSellButton(ItemStack st) {
        if (st.isEmpty()) return false;
        String name = io.github.zmuzy.sbc.core.GameState.strip(st.getHoverName().getString());
        if (name.equals("Sell Item") || name.equals("Sell Inventory")) return true;
        for (String l : io.github.zmuzy.sbc.core.LegacyText.lore(st)) if (l.contains("buyback")) return true;
        return false;
    }

    static boolean npcSellMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        for (Slot s : menu.slots) if (npcSellButton(s.getItem())) return true;
        return false;
    }

    // Q key: refuse if the held slot is locked or the item protected
    public static boolean blocksDrop(Player p) {
        if (!on() || p == null) return false;
        // skyblocker LocalPlayerMixin: in a dungeon the drop key is the class ability
        if (io.github.zmuzy.sbc.dungeon.DungeonRun.started() && io.github.zmuzy.sbc.dungeon.DungeonMarks.opt("slotlocks", "dungeon_abilities")) return false;
        int i = p.getInventory().getSelectedSlot();
        if (lockedIndex(i) || isProtected(p.getInventory().getItem(i))) {
            warn(lockedIndex(i) ? "That slot is locked" : "That item is protected");
            return true;
        }
        return false;
    }

    public static boolean any(ItemStack st) {
        return !Gain.sbId(st).isEmpty() && isProtected(st);
    }
}
