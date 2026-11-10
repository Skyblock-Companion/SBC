// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.market;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker newyearcakes: in an ah search, years your cake bag already has in red, missing ones in green
// what the bag holds is learned from opening it and from buying one
public final class Cakes {
    public static final String ID = "new_year_cakes";
    private static final Pattern NEW_YEAR_CAKE = Pattern.compile("New Year Cake \\(Year (?<year>\\d+)\\)");
    private static final Pattern NEW_YEAR_CAKE_PURCHASE = Pattern.compile("You purchased New Year Cake \\(Year (?<year>\\d+)\\) for .+ coins!");
    private static final Pattern SEARCH = Pattern.compile("Auctions: \".*\"");
    private static final Map<String, Set<Integer>> CAKES = new HashMap<>();

    private Cakes() {}

    static int year(Pattern pattern, String name) {
        Matcher m = pattern.matcher(name);
        return m.matches() ? Integer.parseInt(m.group("year")) : -1;
    }

    static boolean addCake(int year) {
        if (year < 0) return false;
        return CAKES.computeIfAbsent(GameState.profileName, k -> new HashSet<>()).add(year);
    }

    // skyblocker NewYearCakeBagHelper: every cake in the open bag
    static void onMenu(String title, ChestMenu menu, int n) {
        if (!Features.on(ID) || !title.equals("New Year Cake Bag")) return;
        for (int i = 0; i < n; i++) addCake(year(NEW_YEAR_CAKE, menu.slots.get(i).getItem().getHoverName().getString()));
    }

    // skyblocker NewYearCakesHelper.getColors: nothing till the bag has been seen
    static Integer color(String title, ItemStack st) {
        if (!Features.on(ID) || !SEARCH.matcher(title).matches()) return null;
        Set<Integer> have = CAKES.get(GameState.profileName);
        if (have == null || have.isEmpty()) return null;
        int year = year(NEW_YEAR_CAKE, st.getHoverName().getString());
        if (year < 0) return null;
        return have.contains(year) ? 0x80FF5555 : 0x8055FF55;
    }

    static void register() {
        Features.add(new Feature(ID, "Items", "New Year cakes in the Auction House", "In an Auction House search: the cakes your bag already has in red, the ones it lacks in green. Open your bag once so it knows.", "Skyblocker: new year cakes helper")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV));
        ChatBus.register(ChatBus.OBSERVE + 26, "new year cakes", null, line -> {
            if (Features.on(ID)) addCake(year(NEW_YEAR_CAKE_PURCHASE, line.plain().trim()));
            return ChatBus.PASS;
        });
    }
}
