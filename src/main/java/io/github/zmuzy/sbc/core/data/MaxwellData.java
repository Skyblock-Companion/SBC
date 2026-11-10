// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.core.data.DataFiles.Source;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// selected power + magical power like maxwells menus show them, so no api key needed. port of skyhanni
// data/MaxwellApi.kt: chat lines, thaumaturgy (power from the stone marked selected), your bags
public final class MaxwellData {
    private static final String NO_POWER = "No Power";
    private static Set<String> powers;

    private MaxwellData() {}

    private static Pattern sh(String key) {
        return Patterns.get("SH.data.maxwell." + key);
    }

    static void register(Feature f) {
        ChatBus.register(ChatBus.OBSERVE + 30, "maxwell", f, line -> {
            onChat(LegacyText.of(line.component()));
            return ChatBus.PASS;
        });
        MenuMarks.add(GameData.ID, sh("gui.thaumaturgy"), MaxwellData::readThaumaturgy);
        MenuMarks.add(GameData.ID, sh("gui.yourbags"), MaxwellData::readYourBags);
    }

    private static boolean isPower(String name) {
        return powers().contains(name);
    }

    // from skyhanni onRepoReload: names MaxwellPowers.json lists
    private static synchronized Set<String> powers() {
        if (powers == null) {
            powers = new HashSet<>();
            JsonObject o = DataFiles.object(Source.SKYHANNI, "MaxwellPowers.json");
            if (o != null && o.has("powers")) for (JsonElement e : o.getAsJsonArray("powers")) powers.add(e.getAsString());
        }
        return powers;
    }

    // this profiles numbers, MaxwellApi keeps them in profileSpecific
    private static Store.MaxwellProfile data() {
        String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
        return Store.I.maxwell.computeIfAbsent(key, k -> new Store.MaxwellProfile());
    }

    // from skyhanni currentPower: empty till a menu or chat line showed it on this profile
    public static String power() {
        return data().power;
    }

    // from skyhanni magicalPower on this profile
    public static int magicalPower() {
        return data().magicalPower;
    }

    // a powers stat lines like its stone showed them on this profile, empty = not seen
    public static List<String> stats(String power) {
        return data().stats.getOrDefault(power, List.of());
    }

    // mp the stats were read at, 0 = not seen
    public static int statsReadAt(String power) {
        return data().statsAt.getOrDefault(power, 0);
    }

    private static void setPower(String power) {
        if (!isPower(power) || power.equals(data().power)) return;
        data().power = power;
        Store.markDirty();
    }

    private static void setMagicalPower(int mp) {
        if (mp == data().magicalPower) return;
        data().magicalPower = mp;
        Store.markDirty();
    }

    private static int number(String text) {
        try {
            return (int) Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // from skyhanni onChat: the 2 lines that name the chosen power
    static void onChat(String formatted) {
        String message = formatted.trim().replace("§r", "");
        for (Pattern p : List.of(sh("chat.power"), sh("chat.power.unlocked"))) {
            Matcher m = p.matcher(message);
            if (m.matches()) setPower(m.group("power"));
        }
    }

    // from skyhanni onInventoryOpen for thaumaturgy: current power, magical power, each stones stats
    static void readThaumaturgy(MenuView v) {
        Pattern selected = sh("gui.selectedpower"), total = sh("gui.thaumaturgy.magicalpower"), stat = sh("gui.thaumaturgy.data");
        MenuView.Item mpItem = v.item(48);
        for (String line : mpItem.lore) {
            Matcher m = total.matcher(line);
            if (m.matches()) {
                setMagicalPower(number(m.group("mp")));
                break;
            }
        }
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty() || !isPower(it.clean)) continue;
            if (!it.lore.isEmpty() && selected.matcher(it.lore.getLast()).matches()) setPower(it.clean);
            List<String> stats = new ArrayList<>();
            for (String line : it.lore) {
                Matcher m = stat.matcher(line);
                if (m.matches()) stats.add(line);
            }
            if (!stats.isEmpty() && !stats.equals(data().stats.get(it.clean))) {
                data().stats.put(it.clean, stats);
                data().statsAt.put(it.clean, data().magicalPower);
                Store.markDirty();
            }
        }
    }

    // from skyhanni onInventoryOpen for your bags: processStack on the acc bag
    static void readYourBags(MenuView v) {
        Pattern bag = sh("stack.accessorybag");
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (!it.empty() && bag.matcher(it.name).matches()) processStack(it);
        }
    }

    private static void processStack(MenuView.Item stack) {
        Pattern redstone = sh("collection.redstone.requirement"), noPower = sh("gui.noselectedpower"), mp = sh("inventory.magicalpower"), power = sh("inventory.power");
        boolean found = false;
        for (String line : stack.lore) {
            if (redstone.matcher(line).matches()) {
                data().power = NO_POWER;
                data().magicalPower = 0;
                Store.I.statTunings = new ArrayList<>();
                Store.I.statTuningNames = new ArrayList<>();
                Store.I.statTuningsRead = true;
                Store.markDirty();
                return;
            }
            if (noPower.matcher(line).matches()) setPower(NO_POWER);
            Matcher m = mp.matcher(line);
            if (m.matches()) {
                found = true;
                if (!"CATACOMBS".equals(GameState.island)) setMagicalPower(number(m.group("mp")));
            }
            m = power.matcher(line);
            if (m.matches()) setPower(m.group("power"));
        }
        if (!found) {
            setMagicalPower(0);
            if (!Store.I.statTunings.isEmpty() || !Store.I.statTuningsRead) {
                Store.I.statTunings = new ArrayList<>();
                Store.I.statTuningNames = new ArrayList<>();
                Store.I.statTuningsRead = true;
                Store.markDirty();
            }
        }
    }

    // from skyhanni calculateStatMultiplier: what a powers base stats get multiplied by at this mp
    public static double statMultiplier(double magicalPower) {
        return 29.97 * Math.pow(Math.log(0.0019 * magicalPower + 1), 1.2);
    }
}
