// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.areas;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.CookieBuff;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.game.GameProfile;
import dev.zmuzy.sbc.core.game.Seen;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.garden.contest.Contests;
import dev.zmuzy.sbc.mc.Interactions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni fame/UpgradeReminder.kt + ReminderUtils.kt: your community shop upgrade (profile + account)
// and a clickable line when its done, every 30 sec till you claim it
public final class CommunityUpgrades {
    public static final String ID = "upgrade_reminder";
    private static final Patterns.P ACCOUNT = Patterns.p("SH.fame.upgrades.account"), PROFILE = Patterns.p("SH.fame.upgrades.profile"), DURATION = Patterns.p("SH.fame.upgrades.duration"),
            STARTED = Patterns.p("SH.fame.upgrades.started"), CLAIMED = Patterns.p("SH.fame.upgrades.claimed");

    private enum Type {PROFILE, ACCOUNT}

    private static boolean inMenu;
    private static Type clickedType;
    private static Seen.ShopUpgrade clicked;
    private static long clickedDuration, lastReminder;
    // from skyhanni IslandTypeTag.BUSY, in GameState ids
    private static final Set<String> BUSY = Set.of("DARK_AUCTION", "MINESHAFT", "THE_RIFT", "SAFARI", "");
    private static java.util.regex.Pattern started, claimed;

    private CommunityUpgrades() {}

    // from skyhanni ReminderUtils.isBusy
    public static boolean busy() {
        return "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island) || "THE_RIFT".equals(GameState.island) || Contests.inContest() || BUSY.contains(GameState.island);
    }

    private static boolean any(List<String> lore, Patterns.P p) {
        for (String l : lore) if (p.matcher(l).matches()) return true;
        return false;
    }

    private static Type type(List<String> lore) {
        return any(lore, ACCOUNT) ? Type.ACCOUNT : any(lore, PROFILE) ? Type.PROFILE : null;
    }

    // from skyhanni fromItem duration. -1 for "Instant!", never needs a reminder
    static long duration(List<String> lore) {
        for (String l : lore) {
            Matcher m = DURATION.matcher(l);
            if (!m.matches()) continue;
            if (m.group("duration").equals("Instant!")) return -1;
            return Math.max(0, CookieBuff.parse(m.group("duration")));
        }
        return 0;
    }

    private static Seen.ShopUpgrade upgrade(String name, long duration) {
        Seen.ShopUpgrade u = new Seen.ShopUpgrade();
        u.name = name;
        u.completionTime = System.currentTimeMillis() + duration;
        return u;
    }

    private static void store(Seen s, Type type, Seen.ShopUpgrade u) {
        if (type == Type.PROFILE && s != null) s.profileUpgrade = u;
        if (type == Type.ACCOUNT) Store.I.accountUpgrade = u;
        Store.markDirty();
    }

    // from skyhanni handleItems: community shop upgrade rows say whats running, the stored line goes when nothing is
    private static void readMenu(Seen s, List<net.minecraft.world.inventory.Slot> slots) {
        boolean profile = activeIn(s, slots, 27, 35, Type.PROFILE), account = activeIn(s, slots, 36, 44, Type.ACCOUNT);
        if (!profile && s != null && s.profileUpgrade != null) {
            Combat.say("§eRemoved invalid Profile Upgrade information.");
            store(s, Type.PROFILE, null);
        }
        if (!account && Store.I.accountUpgrade != null) {
            Combat.say("§eRemoved invalid Account Upgrade information.");
            store(s, Type.ACCOUNT, null);
        }
    }

    private static boolean activeIn(Seen s, List<net.minecraft.world.inventory.Slot> slots, int from, int to, Type expected) {
        for (int i = from; i <= to && i < slots.size(); i++) {
            ItemStack st = slots.get(i).getItem();
            if (st.isEmpty()) continue;
            List<String> lore = LegacyText.lore(st);
            if (!lore.contains("§aCurrently upgrading!") && !lore.contains("§cClick to claim!")) continue;
            Type type = type(lore);
            long d = duration(lore);
            if (type != null && d >= 0) store(s, type, upgrade(LegacyText.name(st), lore.contains("§cClick to claim!") ? 0 : d));
            return true;
        }
        return false;
    }

    private static boolean click(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        if (!inMenu || slot == null || slot.getItem().isEmpty()) return false;
        List<String> lore = LegacyText.lore(slot.getItem());
        Type type = type(lore);
        if (type == null) return false;
        long d = duration(lore);
        if (d < 0) return false;
        clickedType = type;
        clickedDuration = d;
        clicked = upgrade(LegacyText.name(slot.getItem()), d);
        return false;
    }

    // on the colorless line
    static String claimedName(String plain) {
        if (claimed == null) claimed = dev.zmuzy.sbc.core.Bits.colourless(CLAIMED);
        Matcher m = claimed.matcher(plain);
        return m.matches() ? m.group("upgrade") : null;
    }

    static boolean startedLine(String plain) {
        if (started == null) started = dev.zmuzy.sbc.core.Bits.colourless(STARTED);
        return started.matcher(plain).matches();
    }

    private static void chat(String message) {
        Seen s = GameProfile.current();
        if (startedLine(message)) {
            if (clicked != null) {
                clicked.completionTime = System.currentTimeMillis() + clickedDuration;
                store(s, clickedType, clicked);
            }
            return;
        }
        String name = claimedName(message);
        if (name != null) {
            if (s != null && s.profileUpgrade != null && GameState.strip(s.profileUpgrade.name).equals(GameState.strip(name))) store(s, Type.PROFILE, null);
            if (Store.I.accountUpgrade != null && GameState.strip(Store.I.accountUpgrade.name).equals(GameState.strip(name))) store(s, Type.ACCOUNT, null);
        }
    }

    // from skyhanni sendReminderIfClaimable
    private static void remind(Seen.ShopUpgrade u, long now) {
        if (u == null || u.name == null || u.name.isEmpty() || u.completionTime > now) return;
        Combat.sayClickable("The §a" + u.name + " §eupgrade has completed! §7Click to warp to Elizabeth.", "/warp elizabeth");
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        boolean menu = mc.gui.screen() instanceof AbstractContainerScreen<?> scr && GameState.strip(scr.getTitle().getString()).trim().equals("Community Shop")
                && scr.getMenu().slots.size() > 11 && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(scr.getMenu().slots.get(11).getItem().getItem()).getPath().equals("lime_stained_glass_pane");
        if (menu && !inMenu) readMenu(GameProfile.current(), ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots);
        inMenu = menu;
        if (mc.player.tickCount % 20 != 0 || !GameState.inSkyBlock || !Features.on(ID)) return;
        long now = System.currentTimeMillis();
        if (inMenu || busy() || "Community Center".equals(GameState.area) || now - lastReminder < 30_000) return;
        Seen s = GameProfile.current();
        remind(s == null ? null : s.profileUpgrade, now);
        remind(Store.I.accountUpgrade, now);
        lastReminder = now;
    }

    public static void register() {
        Safe.endTick("CommunityUpgrades.tick", CommunityUpgrades::tick);
        Interactions.onSlotClick(CommunityUpgrades::click);
        ChatBus.register(5, "community_upgrades", null, line -> {
            chat(line.plain().trim());
            return ChatBus.PASS;
        });
        Features.add(new Feature(ID, "Misc", "Community Shop upgrade done", "A line to click when your profile or account upgrade has finished, every half a minute until you claim it, but not while you are busy.", "SkyHanni: Account Upgrade Reminder")
                .when(() -> GameState.inSkyBlock));
    }
}
