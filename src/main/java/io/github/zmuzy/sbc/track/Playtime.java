// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.track;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.ui.CompanionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// your real time per island from hypixels /playtimedetailed: slot 4 lore, the lines skyhannis LimboPlaytime
// reads ("304.7 hours on Hub", "28 minutes on Limbo")
public final class Playtime {
    public static final String MENU = "Detailed /playtime";
    private static final long STALE_MS = 7L * 24 * 3_600_000;
    private static final Pattern LINE = Pattern.compile("^\\s*([\\d.,]+) (hours?|minutes?) on (.+?)\\s*$");
    private static boolean asked;

    private Playtime() {}

    // one island: name like hypixel writes it, time there, time since the menu was read
    public record Place(String name, long ms) {}

    // menu lore lines: place -> ms
    static Map<String, Long> parse(List<String> lore) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String l : lore) {
            Matcher m = LINE.matcher(l);
            if (!m.matches()) continue;
            double amount;
            try {
                amount = Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            out.put(m.group(3), Math.round(amount * (m.group(2).startsWith("hour") ? 3_600_000d : 60_000d)));
        }
        return out;
    }

    // "The Garden", "Garden" and GARDEN are the same place
    static String key(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return n.startsWith("the") ? n.substring(3) : n;
    }

    public static boolean known() {
        return !Store.I.playtime.isEmpty();
    }

    // never read, or read so long ago its time to ask again
    public static boolean stale() {
        return !known() || System.currentTimeMillis() - Store.I.playtimeAt > STALE_MS;
    }

    public static long readAgo() {
        return System.currentTimeMillis() - Store.I.playtimeAt;
    }

    // the K menu button: the menu comes back once hypixels was read
    public static void ask() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        asked = true;
        conn.sendCommand("playtimedetailed");
    }

    // everything known so far, longest first
    public static List<Place> places() {
        Map<String, Long> since = new HashMap<>();
        for (var e : Store.I.islandTime.entrySet()) {
            long grown = e.getValue() - Store.I.playtimeBase.getOrDefault(e.getKey(), 0L);
            if (grown > 0) since.merge(key(GameState.pretty(e.getKey())), grown, Long::sum);
        }
        List<Place> out = new ArrayList<>();
        for (var e : Store.I.playtime.entrySet()) out.add(new Place(e.getKey(), e.getValue() + since.getOrDefault(key(e.getKey()), 0L)));
        out.sort((a, b) -> Long.compare(b.ms(), a.ms()));
        return out;
    }

    public static void tick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !GameState.strip(cs.getTitle().getString()).startsWith(MENU)) return;
        var slots = cs.getMenu().slots;
        if (slots.size() <= 4) return;
        ItemStack st = slots.get(4).getItem();
        var lore = st.get(DataComponents.LORE);
        if (lore == null) return;
        List<String> lines = new ArrayList<>();
        for (Component c : lore.lines()) lines.add(GameState.strip(c.getString()));
        Map<String, Long> read = parse(lines);
        if (read.isEmpty()) return;
        Store.I.playtime = read;
        Store.I.playtimeAt = System.currentTimeMillis();
        Store.I.playtimeBase = new HashMap<>(Store.I.islandTime);
        Store.markDirty();
        if (!asked) return;
        asked = false;
        mc.player.closeContainer();
        mc.gui.setScreen(new CompanionScreen());
    }
}
