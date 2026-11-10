// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.chat.sh;

import io.muzy.sbc.chat.ChatMatch;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.events.diana.RareMobWaypointShare;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;

// pure ports of skyhanni's chat compactors, each takes the formatted (and clean) line and says what to do
// from skyhanni CompactSplashPotionMessage, CompactBestiaryChatMessage, CompactExperimentRewards,
// SkyblockXPInChat, PlayerDeathMessages (far deaths), RareDropMessages (pet rarity)
public final class CompactMessages {
    // text, hover lines (can be empty), click command (can be null)
    public record Out(String text, List<String> hover, String command) {}

    private CompactMessages() {}

    // --- CompactSplashPotionMessage ---

    static final String[] POTION = {"Compact.POTION_0", "Compact.POTION_1", "Compact.POTION_2", "Compact.POTION_3", "Compact.POTION_4", "Compact.POTION_5"};

    // isPotionMessage: compact line to send instead, or null if its not a potion line
    public static Out potion(String formatted, boolean clickable) {
        for (String key : POTION) {
            Matcher m = ChatMatch.full(key, formatted);
            if (m == null) continue;
            String effectName = m.group("effectName");
            String byPlayer = "";
            try {
                String player = m.group("playerName");
                if (player != null) byPlayer = " §aby " + RareMobWaypointShare.cleanPlayerName(player);
            } catch (IllegalArgumentException ignored) {
            }
            String text = "§a§lPotion Effect! §r" + effectName + byPlayer;
            return clickable ? new Out(text, List.of("§eClick to view your potion effects."), "/effects") : new Out(text, List.of(), null);
        }
        return null;
    }

    // --- CompactBestiaryChatMessage ---

    static final Patterns.P BESTIARY_MILESTONE = Patterns.p("Compact.BESTIARY_MILESTONE");
    static final String BESTIARY_BORDER = "§3§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    static final String BESTIARY_TITLE = "§f                                  §6§lBESTIARY";

    static boolean inBestiary;
    static final List<String> bestiaryDescription = new ArrayList<>();
    static boolean acceptMoreDescription = true;
    static String bestiaryCommand = "";
    static int blockedLines;
    static String milestoneMessage;
    static long bestiaryStartedAt;
    // a block arrives all at once
    private static final long BESTIARY_MS = 1_000;

    // a bestiary block is going, its lines are ours
    public static boolean inBestiaryBlock() {
        return inBestiary && System.currentTimeMillis() - bestiaryStartedAt < BESTIARY_MS;
    }

    // every line inside is blank or indented to the middle of chat
    private static boolean partOfBestiary(String formatted) {
        String clean = strip(formatted);
        return clean.isBlank() || clean.startsWith(" ");
    }

    // block over, send what we gathered so nothing is lost even if the closing border never came
    private static void finishBestiary(Consumer<Out> send) {
        inBestiary = false;
        blockedLines = 0;
        if (bestiaryDescription.size() > 1) {
            List<String> list = new ArrayList<>();
            for (String l : bestiaryDescription) list.add(l.replace("§f", "").trim());
            send.accept(new Out("§6§lBESTIARY §r" + list.get(1), list.subList(0, list.size() - 1), bestiaryCommand.isEmpty() ? null : bestiaryCommand));
        }
        bestiaryDescription.clear();
        acceptMoreDescription = true;
        if (milestoneMessage != null) {
            send.accept(new Out("§6§lBESTIARY MILESTONE " + milestoneMessage, List.of(), null));
            milestoneMessage = null;
        }
    }

    // onChat for the bestiary block, true = line blocked, compact lines go to send
    // skyhanni waits for the closing border or 15 lines. so a block that never closes cant eat the chat after it,
    // this also ends after 1s or on the first line that cant be part of it
    // clickCommand = first click command in the title line's siblings, "" if none
    public static boolean bestiary(String formatted, String clickCommand, Consumer<Out> send) {
        if (formatted.equals(BESTIARY_TITLE)) {
            bestiaryCommand = clickCommand == null ? "" : clickCommand;
            inBestiary = true;
            bestiaryStartedAt = System.currentTimeMillis();
            blockedLines = 0;
            bestiaryDescription.clear();
            bestiaryDescription.add(formatted);
            return true;
        }
        if (!inBestiary) return false;
        if (!inBestiaryBlock() || (!formatted.equals(BESTIARY_BORDER) && !partOfBestiary(formatted))) {
            finishBestiary(send);
            return false;
        }
        if (++blockedLines > 15) {
            finishBestiary(send);
            return true;
        }
        if (formatted.equals(BESTIARY_BORDER)) {
            inBestiary = false;
            List<String> list = new ArrayList<>();
            for (String l : bestiaryDescription) list.add(l.replace("§f", "").trim());
            String title = list.size() > 1 ? list.get(1) : "";
            send.accept(new Out("§6§lBESTIARY §r" + title, list.subList(0, Math.max(0, list.size() - 1)), bestiaryCommand.isEmpty() ? null : bestiaryCommand));
            bestiaryDescription.clear();
            acceptMoreDescription = true;
        } else {
            if (milestoneMessage != null) {
                send.accept(new Out("§6§lBESTIARY MILESTONE " + milestoneMessage, List.of(), null));
                milestoneMessage = null;
            }
            // only colours tell a milestone from the level-up line so this one keeps them. resets arent part of it
            Matcher m = BESTIARY_MILESTONE.matcher(formatted.replace("§r", ""));
            if (m.matches()) {
                acceptMoreDescription = false;
                milestoneMessage = m.group(1);
            }
            if (acceptMoreDescription) bestiaryDescription.add(formatted);
        }
        return true;
    }

    // --- CompactExperimentRewards ---

    static final Patterns.P EXPERIMENT_INVENTORIES = Patterns.p("SH.chat.experiments.compact.inventories");
    static final String EXPERIMENT_CLAIM = "SH.chat.experiments.compact.message", EXPERIMENT_DROP = "SH.chat.experiments.compact.drop";

    static final List<String> gainedRewards = new ArrayList<>();
    static long lastTimeTableOpened = Long.MIN_VALUE / 4;
    static String currentExperimentMessage = "";

    public static boolean isExperimentInventory(String name) {
        return EXPERIMENT_INVENTORIES.matcher(name).matches();
    }

    // onInventoryClose of an experiment menu
    public static void onExperimentInventoryClose(long now) {
        lastTimeTableOpened = now;
    }

    // onChat: true = blocked, experimentFlush makes the compact line
    public static boolean experiment(String formatted, long now) {
        if (now - lastTimeTableOpened >= 3000) return false;
        // claim line goes out again as is, with its colours however it matched
        Matcher m = ChatMatch.full(EXPERIMENT_CLAIM, formatted);
        if (m != null) {
            currentExperimentMessage = formatted.trim();
            return true;
        }
        m = ChatMatch.full(EXPERIMENT_DROP, formatted);
        if (m != null) {
            gainedRewards.add(m.group("reward"));
            return true;
        }
        return false;
    }

    // DelayedRun 100 ms after the last drop line: claim line with rewards on hover
    public static Out experimentFlush() {
        // if only one half came it was hidden, show it now or its lost
        if (gainedRewards.isEmpty() && currentExperimentMessage.isEmpty()) return null;
        List<String> expList = new ArrayList<>();
        for (String r : gainedRewards) expList.add("§8+" + r);
        Out out = currentExperimentMessage.isEmpty() ? new Out(String.join(" ", expList), List.of(), null) : new Out(currentExperimentMessage, expList, null);
        gainedRewards.clear();
        currentExperimentMessage = "";
        return out;
    }

    // --- SkyblockXPInChat ---

    static String lastSkyblockXp = "";

    // ActionBarValueUpdate for SKYBLOCK_XP: the "§b+5 SkyBlock XP §7(...)§b (12/100)" part when it changed, else null
    public static String skyblockXp(String actionBarFormatted) {
        Matcher m = ChatMatch.full("SH.actionbar.skyblock.xp", actionBarFormatted);
        if (m == null) return null;
        String v = m.group(1);
        if (v.equals(lastSkyblockXp)) return null;
        lastSkyblockXp = v;
        return v;
    }

    // --- PlayerDeathMessages ---

    static final Patterns.P DEATH = Patterns.p("SH.chat.player.death-nocolor");

    // PlayerDeathManager.handleDeath: {name, reason} or null. "You" becomes your own name
    public static String[] death(String clean, String ownName) {
        Matcher m = DEATH.matcher(clean);
        if (!m.matches()) return null;
        String name = m.group("name");
        return new String[]{name.equals("You") ? ownName : name, m.group("reason")};
    }

    // onAllowPlayerDeath: hide someone elses death if they werent seen within 25 blocks in the last 30s
    public static boolean hideFarDeath(String clean, String ownName, java.util.function.ToLongFunction<String> lastSeen, long now, boolean inDungeonOrKuudra) {
        if (inDungeonOrKuudra) return false;
        String[] d = death(clean, ownName);
        if (d == null || d[0].equals(ownName)) return false;
        return now - lastSeen.applyAsLong(d[0]) > 30_000;
    }

    // --- RareDropMessages.petRarity ---

    static final Patterns.P[] PET_PATTERNS = {Patterns.p("SH.raredrop.pet.droppedmessage"), Patterns.p("SH.raredrop.pet.fishedmessage"), Patterns.p("SH.raredrop.pet.claimedmessage"),
            Patterns.p("SH.raredrop.pet.obtainedmessage"), Patterns.p("SH.raredrop.pet.oringomessage")};

    // LorenzRarity.getByColorCode
    public static String rarityByColor(char code) {
        return switch (code) {
            case 'f' -> "COMMON";
            case 'a' -> "UNCOMMON";
            case '9' -> "RARE";
            case '5' -> "EPIC";
            case '6' -> "LEGENDARY";
            case 'd' -> "MYTHIC";
            case 'b' -> "DIVINE";
            case 'c' -> "SPECIAL";
            case '4' -> "ULTIMATE";
            default -> null;
        };
    }

    // onChat(petRarity): line with "§6§lLEGENDARY " before the pet name, or null
    public static String petRarity(String formatted) {
        for (Patterns.P p : PET_PATTERNS) {
            Matcher m = p.matcher(formatted);
            if (!m.matches()) continue;
            String start = m.group("start");
            String rarityColor = m.group("rarityColor");
            String rarityName = rarityByColor(rarityColor.charAt(0));
            if (rarityName == null) return null;
            String petName = m.group("petName");
            String end = m.group("end");
            if (start.endsWith("a ") && "AEIOU".indexOf(rarityName.charAt(0)) >= 0) start = start.substring(0, start.length() - 1) + "n "; // kotlin substring(0..len-2) keeps the "a", drops the space
            return start + "§" + rarityColor + "§l" + rarityName + " §" + rarityColor + petName + end;
        }
        return null;
    }

    public static String BESTIARY_TITLE_FOR_TEST() {
        return BESTIARY_TITLE;
    }

    public static void reset() {
        inBestiary = false;
        blockedLines = 0;
        bestiaryDescription.clear();
        acceptMoreDescription = true;
        milestoneMessage = null;
        gainedRewards.clear();
        currentExperimentMessage = "";
        lastSkyblockXp = "";
    }

    static String strip(String s) {
        return GameState.strip(s);
    }
}
