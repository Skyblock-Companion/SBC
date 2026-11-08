// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.enchants.EnchantParser;
import dev.zmuzy.sbc.item.market.LoreCosts;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni CityProjectFeatures: in a city project menu what you can contribute is yellow, what all
// components still need sits beside it with price, and a daily reminder when you can contribute again
// hypixel rounds its time down to the minute so short times get +1 min
public final class CityProject {
    public static final String ID = "city_project";
    private static long lastReminder = Long.MIN_VALUE / 4, lastSecond;

    // one menu item: plain name + lore in legacy colours
    record Entry(String plainName, List<String> lore) {}

    private CityProject() {}

    private static boolean opt(String key, boolean def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    // slot 4 says so
    static boolean inCityProject(List<String> slotFourLore) {
        return !slotFourLore.isEmpty() && slotFourLore.get(0).equals("§8City Project");
    }

    private static boolean completed(List<String> lore) {
        return !lore.isEmpty() && Patterns.get("SH.fame.projects.completed").matcher(lore.get(lore.size() - 1)).matches();
    }

    // showWhenReady: when you can contribute next, Long.MAX_VALUE if nothing says
    static long nextParticipation(List<Entry> items, long now) {
        long next = Long.MAX_VALUE;
        for (Entry e : items) {
            if (completed(e.lore())) continue;
            for (String line : e.lore()) {
                Matcher m = Patterns.get("SH.fame.projects.contribute").matcher(line);
                if (!m.matches()) continue;
                String raw = m.group("time");
                long duration;
                if (raw.contains("Soon!")) duration = 5000;
                else {
                    Long d = dev.zmuzy.sbc.events.hoppity.HoppityEnv.duration(raw);
                    if (d == null) break;
                    duration = d < 3_600_000 ? d + 60_000 : d;
                }
                next = Math.min(next, now + duration);
                break;
            }
            if (e.plainName().equals("Contribute this component!")) next = now;
        }
        return next;
    }

    // fetchMaterials over the menu, everything but bits
    static Map<String, Long> materials(List<Entry> items) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Entry e : items) {
            if (!e.plainName().equals("Contribute this component!") || completed(e.lore())) continue;
            for (LoreCosts.Entry c : LoreCosts.read(e.lore())) if (!c.id().equals("SKYBLOCK_BIT")) out.merge(c.id(), c.amount(), Long::sum);
        }
        return out;
    }

    private static List<Entry> read(AbstractContainerScreen<?> screen) {
        List<Entry> out = new ArrayList<>();
        var mc = Minecraft.getInstance();
        for (Slot s : screen.getMenu().slots) {
            if (s.container == mc.player.getInventory() || s.getItem().isEmpty()) continue;
            out.add(new Entry(s.getItem().getHoverName().getString(), LegacyText.lore(s.getItem())));
        }
        return out;
    }

    private static final class Panel implements SideHelper.Content {
        private Map<String, Long> materials = Map.of();

        @Override public String title() { return "City Project"; }
        @Override public ItemStack icon() { return LegacyItems.stack("bricks"); }
        @Override public int accent() { return 0xFFFFC857; }
        @Override public String[] tabs() { return new String[]{"Materials"}; }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            List<Entry> items = read(screen);
            materials = materials(items);
            long next = nextParticipation(items, System.currentTimeMillis());
            if (opt("city_project.show_ready", true) && next != Long.MAX_VALUE) {
                Store.I.cityProjectNext = next;
                Store.markDirty();
            }
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            double total = 0;
            for (Map.Entry<String, Long> e : materials.entrySet()) total += Gain.ofId(e.getKey()) * e.getValue();
            Modern.row(ui, "All of it", "§6" + EnchantParser.shortFormat(total), x, y, w, Modern.LABEL, Modern.GOLD);
            return 12;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            if (materials.isEmpty()) return SideHelper.emptyText(ui, "No materials to contribute.", x, y, w);
            int at = y;
            for (Map.Entry<String, Long> e : materials.entrySet()) {
                Modern.row(ui, dev.zmuzy.sbc.track.Profit.pretty(e.getKey()) + " §ex" + String.format(Locale.ROOT, "%,d", e.getValue()), "§6" + EnchantParser.shortFormat(Gain.ofId(e.getKey()) * e.getValue()), x, at, w, Modern.LABEL, Modern.GOLD);
                at += 10;
            }
            return at - y;
        }
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || !opt("city_project.show_materials", true) || !(s instanceof AbstractContainerScreen<?> c) || c.getMenu().slots.size() <= 4 || !inCityProject(LegacyText.lore(c.getMenu().getSlot(4).getItem()))) return null;
        return PANEL;
    }

    // onBackgroundDrawn: what you can give now. also reads the time when the panel is off
    private static void marks(MenuView v) {
        if (!inCityProject(v.item(4).lore)) return;
        List<Entry> items = new ArrayList<>();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            items.add(new Entry(it.clean, it.lore));
            if (opt("city_project.show_ready", true) && it.lastLore().equals("§eClick to contribute!")) v.back(slot, MenuView.YELLOW);
        }
        long next = nextParticipation(items, System.currentTimeMillis());
        if (next != Long.MAX_VALUE) {
            Store.I.cityProjectNext = next;
            Store.markDirty();
        }
    }

    // onSecondPassed
    private static void reminder(long now) {
        if (!Features.on(ID) || !opt("city_project.daily_reminder", true) || !GameState.inSkyBlock) return;
        if ("CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island) || "Community Center".equals(GameState.area)) return;
        long next = Store.I.cityProjectNext;
        if (next <= 0 || next > now || now - lastReminder < 30_000) return;
        lastReminder = now;
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §rDaily City Project Reminder! §7Click to warp to Elizabeth.").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/warp elizabeth")).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to warp to Elizabeth!")))));
    }

    static void register() {
        Features.add(new Feature(ID, "Events", "City Project", "In a City Project's menu: what you can contribute now is yellow, what all the components still need stands beside it with its price, and a reminder comes when you may contribute again.", "SkyHanni: City Project")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("city_project.show_materials", "Materials beside the menu", "", true))
                .option(Option.bool("city_project.show_ready", "Mark what is ready", "", true))
                .option(Option.bool("city_project.daily_reminder", "Daily reminder", "", true)));
        SideHelper.add(CityProject::panel);
        MenuMarks.add(ID, t -> true, CityProject::marks);
        Safe.endTick("CityProject.endTick", mc -> {
            long now = System.currentTimeMillis();
            if (now - lastSecond < 1000) return;
            lastSecond = now;
            reminder(now);
        });
    }
}
