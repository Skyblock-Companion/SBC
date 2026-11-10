// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.chat;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// squashes multi-line server blocks into one line: jacob claims, powder chests, crystal nucleus runs
// if the closing line never shows up the held lines go out as they were (after 1s, 40 lines, or a line thats
// not part of it) so nothing ever gets lost
public final class ChatBlocks {
    private record Kind(String feature, Pattern start, Pattern end, boolean endIsPartOfBlock, java.util.function.Function<List<String>, String> summary, Pattern firstLine, java.util.function.Predicate<List<String>> mayEnd) {}

    private static final List<Kind> KINDS = new ArrayList<>();
    private static Kind active;
    private static final List<String> collected = new ArrayList<>();
    private static final List<Component> collectedRaw = new ArrayList<>();
    private static long startedAt;
    private static boolean replay;

    private ChatBlocks() {}

    static {
        // jacob rewards: "FARMING CONTEST REWARDS CLAIMED" until an empty line after the rewards. blank lines before
        // the rewards count too
        KINDS.add(new Kind("chat_compact_jacob", Pattern.compile("^\\s*FARMING CONTEST REWARDS CLAIMED\\s*$"), Pattern.compile("^\\s*$"), false, lines -> {
            List<String> contests = new ArrayList<>(), rewards = new ArrayList<>();
            for (String l : lines) {
                String t = l.trim();
                if (t.startsWith("»")) contests.add(t.substring(1).trim().replaceAll(" on .*$", ""));
                else if (t.equalsIgnoreCase("REWARDS") || t.isEmpty()) continue;
                else rewards.add(t);
            }
            if (rewards.isEmpty()) return null;
            return "§6Jacob rewards §7(" + contests.size() + " contest" + (contests.size() == 1 ? "" : "s") + "): §e" + String.join("§7 · §e", rewards);
        }, null, ChatBlocks::hasJacobReward));
        // powder chest: border line ... border line
        Pattern wrap = Pattern.compile("^\\u25AC{20,}$");
        KINDS.add(new Kind("chat_compact_chest", wrap, wrap, false, lines -> {
            List<String> rewards = new ArrayList<>();
            String head = "Chest";
            for (String l : lines) {
                String t = l.trim();
                if (t.isEmpty()) continue;
                if (t.equals("CHEST LOCKPICKED") || t.equals("LOOT CHEST COLLECTED")) {
                    head = t.equals("CHEST LOCKPICKED") ? "Lockpicked chest" : "Loot chest";
                    continue;
                }
                if (t.equals("REWARDS")) continue;
                rewards.add(t);
            }
            if (rewards.isEmpty()) return null;
            return "§a" + head + ": §f" + String.join("§7 · §f", rewards);
            // hypixel opens tons of blocks with this border (bestiary, slayer, experiments), only the first words say its a chest
        }, Pattern.compile("^\\s*(?:CHEST LOCKPICKED|LOOT CHEST COLLECTED)\\s*$"), null));
    }

    // anything held other than the contest line, the rewards header or a blank line
    private static boolean hasJacobReward(List<String> lines) {
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("»") && !t.equalsIgnoreCase("REWARDS")) return true;
        }
        return false;
    }

    public static void register() {
        Features.add(new Feature("chat_compact_jacob", "Chat", "Compact Jacob reward claims", "The multi-line FARMING CONTEST REWARDS CLAIMED block becomes one line.", "").when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Features.add(new Feature("chat_compact_chest", "Chat", "Compact treasure chest rewards", "The mining treasure chest reward block becomes one line.", "").when(() -> GameState.inSkyBlock || SbcMod.DEV));
        ChatBus.register(ChatBus.COMPACT - 10, "blocks", null, ChatBlocks::on);
    }

    // pure, for tests. null = pass, "" = swallow, else the summary line
    public static String feed(String plain) {
        long now = System.currentTimeMillis();
        if (active == null) {
            // closing border of a bestiary block belongs to that block, not to a chest about to start
            if (io.github.zmuzy.sbc.chat.sh.CompactMessages.inBestiaryBlock()) return null;
            for (Kind k : KINDS) {
                Feature f = Features.get(k.feature);
                if (f != null && !f.enabled()) continue;
                if (k.start.matcher(plain).matches()) {
                    active = k;
                    collected.clear();
                    collectedRaw.clear();
                    startedAt = now;
                    return "";
                }
            }
            return null;
        }
        // first line with words decides if this is the block at all. if not, let the held stuff go and pass this line
        if (active.firstLine != null && !plain.isBlank() && collected.stream().allMatch(String::isBlank) && !active.firstLine.matcher(plain).matches()) return letGo();
        Matcher end = active.end.matcher(plain);
        boolean isEnd = end.matches() && !(active.start == active.end && collected.isEmpty()) && (active.mayEnd == null || active.mayEnd.test(collected));
        // every line of these blocks is blank or starts with spaces. party and private lines dont
        if (!isEnd && !plain.isBlank() && !plain.startsWith(" ")) return letGo();
        if (isEnd) {
            Kind k = active;
            active = null;
            if (k.endIsPartOfBlock) collected.add(plain);
            String s = k.summary.apply(new ArrayList<>(collected));
            if (s == null) return letGo();
            collected.clear();
            collectedRaw.clear();
            return s;
        }
        if (collected.size() >= 40) return letGo();
        collected.add(plain);
        return "";
    }

    private static String letGo() {
        active = null;
        collected.clear();
        replay = true;
        return null;
    }

    private static ChatBus.Result on(ChatBus.Line line) {
        String r = feed(line.plain());
        if (replay) {
            replay = false;
            replayHeld();
        }
        if (r == null) return ChatBus.PASS;
        if (active != null) collectedRaw.add(line.component());
        if (r.isEmpty()) return ChatBus.CANCEL;
        return new ChatBus.Replace(Component.literal(r));
    }

    // no closing line within 1s, show what we collected as it was
    public static void tick() {
        if (active == null || System.currentTimeMillis() - startedAt < 1000) return;
        flush();
    }

    private static void flush() {
        active = null;
        collected.clear();
        replayHeld();
    }

    private static void replayHeld() {
        List<Component> raw = new ArrayList<>(collectedRaw);
        collectedRaw.clear();
        for (Component c : raw) replayTo.accept(c);
    }

    // tests read the let-go lines here
    static java.util.function.Consumer<Component> replayTo = ChatBlocks::backToChat;

    private static void backToChat(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        ChatBus.replaying = true;
        try {
            mc.gui.hud.getChat().addServerSystemMessage(c);
        } finally {
            ChatBus.replaying = false;
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
