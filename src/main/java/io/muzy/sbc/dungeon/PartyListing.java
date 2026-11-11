// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// one party in hypixels party finder, read from its head lore like skyblocker + skyhanni do
public final class PartyListing {
    private PartyListing() {}

    public static final String[] CLASSES = {"Healer", "Mage", "Berserk", "Archer", "Tank"};

    public record Member(String name, String dungeonClass, int level) {}

    // shortFloor = "F7", "M7" or "E". lock = why you cant join, empty if you can. missing = classes nobody plays yet
    public record Party(int slot, String leader, List<Member> members, String dungeon, String floor, String shortFloor, String note,
                        int minClass, int minCata, String lock, boolean paidCarry, boolean nonPug, List<String> missing, boolean yours) {
        public boolean master() {
            return shortFloor.startsWith("M");
        }

        public boolean locked() {
            return !lock.isEmpty() && !yours;
        }

        // note, floor, dungeon + names, like skyblockers search box checks a party
        public boolean matches(String query) {
            if (query.isEmpty() || yours) return true;
            String q = query.toLowerCase(Locale.ENGLISH);
            if (note.toLowerCase(Locale.ENGLISH).contains(q) || floor.toLowerCase(Locale.ENGLISH).contains(q) || shortFloor.toLowerCase(Locale.ENGLISH).equals(q)
                    || dungeon.toLowerCase(Locale.ENGLISH).contains(q)) return true;
            for (Member m : members) if (m.name().toLowerCase(Locale.ENGLISH).contains(q) || m.dungeonClass().toLowerCase(Locale.ENGLISH).startsWith(q)) return true;
            return false;
        }
    }

    private static int trailingNumber(String s) {
        int end = s.length(), start = end;
        while (start > 0 && Character.isDigit(s.charAt(start - 1))) start--;
        return start == end ? -1 : Integer.parseInt(s.substring(start, end));
    }

    private static int roman(String s) {
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) return s.isEmpty() ? 0 : Integer.parseInt(s);
        int total = 0, last = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = s.charAt(i) == 'X' ? 10 : s.charAt(i) == 'V' ? 5 : 1;
            total += v < last ? -v : v;
            last = v;
        }
        return total;
    }

    // from skyhanni getFloorName
    static String shortFloor(String floor, String dungeon) {
        if (Patterns.get("SH.dungeon.finder.new.entrance.colorless").matcher(floor).matches()) return "E";
        Matcher m = Patterns.get("SH.dungeon.finder.new.floor.number").matcher(floor);
        String number = m.matches() ? String.valueOf(roman(m.group("floorNum"))) : "?";
        return (Patterns.get("SH.dungeon.finder.new.floor.mastermode").matcher(dungeon).matches() ? "M" : "F") + number;
    }

    // lore = lines with color codes
    public static Party parse(int slot, String title, List<String> lore, boolean yours) {
        String host = GameState.strip(title).split("'s")[0].trim(), dungeon = "", floor = "", note = "", lock = "";
        int minClass = -1, minCata = -1;
        List<Member> members = new ArrayList<>();
        Matcher memberLine = Patterns.get("SH.dungeon.finder.new.member.colorless").matcher("");
        for (String raw : lore) {
            String line = GameState.strip(raw), lower = line.toLowerCase(Locale.ENGLISH);
            if (memberLine.reset(line).matches()) {
                members.add(new Member(memberLine.group("playerName").trim(), memberLine.group("className").trim(), Integer.parseInt(memberLine.group("level"))));
                continue;
            }
            if (line.startsWith(" ")) continue;
            Matcher noteLine = Patterns.get("SH.dungeon.finder.new.note").matcher(line);
            if (noteLine.matches()) note = noteLine.group("note").trim();
            else if (lower.contains("class level")) minClass = trailingNumber(lower);
            else if (lower.contains("dungeon level")) minCata = trailingNumber(lower);
            else if (lower.startsWith("floor:")) floor = line.substring(6).trim();
            else if (lower.startsWith("dungeon:")) dungeon = line.substring(8).trim();
            else if (Patterns.get("SH.dungeon.finder.new.ineligible").matcher(line).matches() || raw.startsWith("§c") && !line.isBlank() && !line.contains(":")) lock = line;
        }
        // host first whatever order the lines came in
        Member leader = null;
        for (Member m : members) if (m.name().equals(host)) leader = m;
        if (leader != null) {
            members.remove(leader);
            members.add(0, leader);
        }
        List<String> missing = new ArrayList<>(List.of(CLASSES));
        for (Member m : members) missing.remove(m.dungeonClass());
        String upperNote = note.toUpperCase(Locale.ENGLISH);
        boolean paid = Patterns.get("SH.dungeon.finder.new.price").matcher(upperNote).matches() && Patterns.get("SH.dungeon.finder.new.carry").matcher(upperNote).matches();
        boolean nonPug = Patterns.get("SH.dungeon.finder.new.nonpug").matcher(upperNote).matches();
        return new Party(slot, host, members, dungeon, floor, shortFloor(floor, dungeon), note, minClass, minCata, lock, paid, nonPug, missing, yours);
    }
}
