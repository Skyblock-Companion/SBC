// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.events.hoppity.HoppityEnv;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.tooltip.TipExtras;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// motes. from skyhanni rift/everywhere/motes: ShowMotesNpcSellPrice.kt (rift storage menu worth + burger
// stacks from chat) and MotesSession.kt (motes gained this visit)
public final class RiftMotes {
    public static final String ID = "rift_motes";
    static final Patterns.P BURGER = Patterns.p("SH.rift.everywhere.burger"), LIFETIME = Patterns.p("SH.rift.everywhere.motes.lifetime-nocolor"),
            EXPORTABLE = Patterns.p("SH.utils.item.rift.exportable");
    private static final String MOTES_GRUBBER = "Motes Grubber", CONSUMABLE_ITEMS = "Miscellaneous ➜ Consumable Items";
    private static final Pattern ORB_PICKUP = Pattern.compile("ORB! Picked up \\+(?<motes>\\d+) Motes, recovered \\+2[ф\uE020] Rift Time!");
    private static final Pattern MOTES_PRICE = Pattern.compile("(?<price>\\d+(\\.\\d)?) Motes");
    private static final Pattern PROGRESS = Pattern.compile("Total Progress: (?<percent>\\d+)%");

    private static Long initial, lifetime;
    private static long enteredAt;
    private static String lastIsland = "";

    private RiftMotes() {}

    // --- burger stacks ---

    private static Option stacksOption() {
        Feature f = Features.get(TipExtras.ID);
        return f == null ? null : f.option("tip_more.mcGrubber");
    }

    public static int stacks() {
        Option o = stacksOption();
        return o == null ? 0 : o.integer();
    }

    private static void setStacks(int n) {
        Option o = stacksOption();
        if (o != null && o.integer() != n && n >= 0 && n <= o.max) o.set(n);
    }

    // from skyhanni fromOrbPickup: 5 motes base + 60 more per stack
    static int stacksFromOrb(String clean) {
        Matcher m = ORB_PICKUP.matcher(clean);
        return m.matches() ? (Integer.parseInt(m.group("motes")) - 5) / 60 : -1;
    }

    // from skyhanni fromMotesGrubber: y = (1 + 0.05x) z, so x = 20y / z - 20
    static int stacksFromPrice(float price, int basePrice) {
        return Math.round(20 * price / basePrice - 20);
    }

    // from skyhanni fromConsumableItems: 20% progress per stack
    static int stacksFromProgress(int percent) {
        return percent / 20;
    }

    private static boolean auto() {
        return RiftScan.on(ID, "rift_motes.auto_stacks", true);
    }

    // the motes price on tooltips needs the stacks too, so they get read whether this feature is on or not, as
    // long as finding them is
    static void onChat(String formatted, String clean) {
        Matcher m = BURGER.matcher(formatted);
        if (m.matches()) setStacks(Integer.parseInt(m.group("amount")));
        if (auto()) {
            int n = stacksFromOrb(clean.trim());
            if (n >= 0) setStacks(n);
        }
    }

    // from skyhanni fromMotesGrubber: reads the price of the first item it knows the base price of
    static void onMenu(String title, ChestMenu menu) {
        if (!title.equals(MOTES_GRUBBER) || !auto()) return;
        var mc = Minecraft.getInstance();
        for (Slot slot : menu.slots) {
            if (slot.container != mc.player.getInventory() || slot.getItem().isEmpty()) continue;
            int base = ItemRepo.motesPrice(Gain.sbId(slot.getItem()));
            if (base <= 0) continue;
            for (String l : LegacyText.lore(slot.getItem())) {
                Matcher m = MOTES_PRICE.matcher(GameState.strip(l));
                if (!m.find()) continue;
                setStacks(stacksFromPrice(Float.parseFloat(m.group("price")), base));
                return;
            }
        }
    }

    private static int lastConsumableMenu = -1;

    // from skyhanni fromConsumableItems: this menu is outside the rift
    private static void tickConsumables(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ContainerScreen s) || !GameState.inSkyBlock) {
            lastConsumableMenu = -1;
            return;
        }
        if (s.getMenu().containerId == lastConsumableMenu || !GameState.strip(s.getTitle().getString()).equals(CONSUMABLE_ITEMS)) return;
        for (Slot slot : s.getMenu().slots) {
            if (!"MCGRUBBER_BURGER".equals(Gain.sbId(slot.getItem()))) continue;
            lastConsumableMenu = s.getMenu().containerId;
            for (String l : LegacyText.lore(slot.getItem())) {
                Matcher m = PROGRESS.matcher(GameState.strip(l));
                if (m.find()) setStacks(stacksFromProgress(Integer.parseInt(m.group("percent"))));
            }
            return;
        }
    }

    // --- session ---

    private static void tickSession(Minecraft mc) {
        if (!GameState.island.equals(lastIsland)) {
            if ("THE_RIFT".equals(lastIsland)) {
                sendSession(mc);
                initial = lifetime = null;
            }
            lastIsland = GameState.island;
        }
        if (!"THE_RIFT".equals(GameState.island) || mc.player == null || mc.player.tickCount % 20 != 0) return;
        for (String line : GameState.tab) {
            Matcher m = LIFETIME.matcher(" " + line.trim());
            if (!m.matches()) continue;
            long amount = Long.parseLong(m.group("motes").replace(",", "").replace(".", ""));
            if (initial == null) {
                initial = amount;
                enteredAt = System.currentTimeMillis();
            }
            lifetime = amount;
            return;
        }
    }

    // from skyhanni sendMotesInfo
    static String[] sessionLines(long gained, long millis) {
        double hours = millis / 3_600_000.0;
        long perHour = hours <= 0 ? 0 : (long) (gained / hours);
        String g = String.format(Locale.ROOT, "%,d", gained), h = String.format(Locale.ROOT, "%,d", perHour);
        return new String[]{"Gained §d" + g + " motes §ethis Rift session! (§d" + h + "/h§e)", "§7Gained: §d" + g + " motes\n§7Time spent: §d" + HoppityEnv.format(millis) + "\n§7Motes/h: §d" + h};
    }

    private static void sendSession(Minecraft mc) {
        if (!Features.on(ID) || !RiftScan.on(ID, "rift_motes.session", true) || initial == null || lifetime == null || mc.player == null) return;
        long gained = lifetime - initial;
        if (gained < 1) return;
        String[] s = sessionLines(gained, System.currentTimeMillis() - enteredAt);
        mc.player.sendSystemMessage(Component.literal("§b[SBC] §e" + s[0]).withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(s[1])))));
    }

    // --- worth of a menu ---

    // from skyhanni SafeItemStack.motesNpcPrice: an exported item that came back sells for nothing
    public static double price(ItemStack st) {
        int base = ItemRepo.motesPrice(Gain.sbId(st));
        if (base <= 0) return 0;
        var cd = st.get(DataComponents.CUSTOM_DATA);
        if (cd != null && cd.copyTag().contains("rift_transferred"))
            for (String l : LegacyText.lore(st)) if (EXPORTABLE.matcher(l).matches()) return 0;
        return value(base, st.getCount(), stacks());
    }

    // from skyhanni motesNpcPrice times the count
    static double value(int base, int count, int stacks) {
        return (base + (stacks * 5) * base / 100.0) * count;
    }

    private static String motes(double v) {
        boolean shortForm = "Short".equals(choice());
        if (shortForm && v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
        if (shortForm && v >= 1_000) return String.format(Locale.ROOT, "%.1fk", v / 1_000);
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String choice() {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option("rift_motes.format");
        return o == null ? "Short" : o.choice();
    }

    private record Worth(String name, ItemStack icon, double total, int count) {}

    private static final class Panel implements SideHelper.Content {
        private final List<Worth> rows = new ArrayList<>();
        private double total;

        @Override public String title() {
            return "Motes value";
        }

        @Override public ItemStack icon() {
            return LegacyItems.stack("amethyst_shard");
        }

        @Override public int accent() {
            return 0xFFFF7FE0;
        }

        @Override public String[] tabs() {
            return new String[]{"Items"};
        }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            Map<String, Worth> byId = new HashMap<>();
            total = 0;
            var mc = Minecraft.getInstance();
            for (Slot s : screen.getMenu().slots) {
                if (s.container == mc.player.getInventory() || s.getItem().isEmpty()) continue;
                double p = price(s.getItem());
                if (p <= 0) continue;
                total += p;
                String id = Gain.sbId(s.getItem());
                Worth old = byId.get(id);
                byId.put(id, old == null ? new Worth(LegacyText.cleanName(s.getItem()), s.getItem(), p, s.getItem().getCount()) : new Worth(old.name(), old.icon(), old.total() + p, old.count() + s.getItem().getCount()));
            }
            rows.clear();
            rows.addAll(byId.values());
            rows.sort((a, b) -> Double.compare(b.total(), a.total()));
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            SideHelper.tile(ui, x, y, w, "Total", motes(total) + " Motes", stacks() > 0 ? stacks() + " burger stacks" : "", 0xFFFF7FE0);
            return 28;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            if (rows.isEmpty()) return SideHelper.emptyText(ui, "Nothing here sells for motes.", x, y, w);
            int at = y;
            for (Worth r : rows) {
                Modern.row(ui, r.name() + (r.count() > 1 ? " x" + r.count() : ""), "§d" + motes(r.total()), x, at, w, Modern.LABEL, Modern.INK);
                at += 10;
            }
            return at - y;
        }
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!"THE_RIFT".equals(GameState.island) || !Features.on(ID) || !RiftScan.on(ID, "rift_motes.value", true)) return null;
        return s.getTitle().getString().contains("Rift Storage") ? PANEL : null;
    }

    public static void register() {
        Features.add(new Feature(ID, "Rift", "Motes", "Beside Rift Storage, what its items sell for; when you leave, the motes you gained on this visit; and your burger stacks kept right for the motes price on items.", "SkyHanni: Motes; Skyblocker: McGrubber")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("rift_motes.value", "Value of Rift Storage", "A panel beside the menu with the motes each item sells for.", true))
                .option(Option.choice("rift_motes.format", "Number format", "Short: 1.2M. Long: 1,200,000.", "Short", "Short", "Long"))
                .option(Option.bool("rift_motes.session", "Motes per visit", "A chat line when you leave the Rift.", true))
                .option(Option.bool("rift_motes.auto_stacks", "Find the burger stacks", "From picking up orbs, from the Motes Grubber and from the SkyBlock Levels menu.", true)));
        SideHelper.add(RiftMotes::panel);
        // not the features tick, that one stops when you leave skyblock and thats one of the ways out of the rift
        Safe.endTick("RiftMotes.endTick", mc -> {
            tickSession(mc);
            if (auto() && mc.player != null && mc.player.tickCount % 10 == 0) tickConsumables(mc);
        });
    }
}
