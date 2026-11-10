// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.contest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenData;
import io.github.zmuzy.sbc.garden.GardenItems;
import io.github.zmuzy.sbc.garden.milestones.CropType;
import io.github.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import io.github.zmuzy.sbc.garden.milestones.GardenMilestones;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.ui.SideHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// jacob contests from the Your Contests menu + sidebar. from skyhanni ContestBracket, FarmingContest,
// FarmingContestApi, FarmingContestPhase, JacobContestStatsSummary, FarmingPersonalBestGain,
// ZorroCapeContestProtection, plus the fortune you farm a crop with
// skyblocker's CurrentJacobCrop is contestCrop
public final class Contests {
    public static final String SUMMARY = "jacob_contest_summary", BEST = "jacob_personal_best", ZORRO = "jacob_zorro_protection";

    // ContestBracket
    public enum Bracket {
        DIAMOND('b'), PLATINUM('3'), GOLD('6'), SILVER('f'), BRONZE('c');

        public final char color;

        Bracket(char color) {
            this.color = color;
        }

        public String display() {
            return "§" + color + "§l" + name();
        }

        Pattern pattern() {
            return Patterns.get("SH.garden.farming.contest.bracket" + name().toLowerCase(Locale.ROOT));
        }
    }

    // FarmingContest
    public record Contest(long time, CropType crop, Map<Bracket, Integer> brackets) {}

    // FarmingContestPhase
    enum Phase { START, STOP, CHANGE }

    private static final Map<Long, Contest> CONTESTS = new HashMap<>();
    private static final Pattern FORTUNE_LINE = Pattern.compile("^\\s*(.+ Fortune): (.+)$");
    private static final Map<CropType, Integer> BLOCKS = new EnumMap<>(CropType.class);

    private static boolean internalContest, inInventory;
    public static CropType contestCrop;
    private static long startTime;
    private static long lastSessionBlocks;
    private static Map<String, Integer> increments;

    private static Double newCollected, oldCollected, newFF;
    private static String pbCrop;

    private Contests() {}

    private static Pattern sh(String key) {
        return Patterns.get("SH.garden." + key);
    }

    private static boolean option(String feature, String key, boolean otherwise) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? otherwise : o.bool();
    }

    // FarmingContestApi.inInventory
    public static boolean inYourContests() {
        return inInventory;
    }

    public static boolean inContest() {
        return internalContest;
    }

    private static int month(String name) {
        for (int i = 0; i < SbTime.MONTHS.length; i++) if (SbTime.MONTHS[i].equals(name)) return i;
        return -1;
    }

    // getSBTimeFor: real time of "Spring 31st, Year 494", or null
    public static Long timeFor(String text) {
        Matcher m = sh("farming.contest.time.colorless").matcher(text);
        if (!m.matches()) return null;
        int month = month(m.group("month"));
        if (month < 0) return null;
        try {
            return SbTime.sbToReal(Integer.parseInt(m.group("year")), month, Integer.parseInt(m.group("day")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // createContest on the plain lines of a contest in the menu
    public static Contest readContest(long time, List<String> cleanLore) {
        CropType crop = null;
        Map<Bracket, Integer> brackets = new EnumMap<>(Bracket.class);
        for (String line : cleanLore) {
            Matcher c = sh("farming.contest.crop.colorless").matcher(line);
            if (crop == null && c.matches()) crop = CropType.getByNameOrNull(c.group("crop"));
            for (Bracket b : Bracket.values()) {
                Matcher m = b.pattern().matcher(line);
                if (m.matches()) brackets.putIfAbsent(b, (int) GameState.parseShort(m.group("amount")));
            }
        }
        return crop == null ? null : new Contest(time, crop, brackets);
    }

    public static void addContest(Contest c) {
        if (c != null) CONTESTS.putIfAbsent(c.time(), c);
    }

    public static Contest contestAt(long time) {
        return CONTESTS.get(time);
    }

    // calculateAverages over the last 10 of this crop
    public static Averages averages(CropType crop) {
        List<Contest> of = new ArrayList<>();
        for (Contest c : CONTESTS.values()) if (c.crop() == crop) of.add(c);
        of.sort(Comparator.comparingLong(Contest::time).reversed());
        Map<Bracket, Integer> sum = new EnumMap<>(Bracket.class), count = new EnumMap<>(Bracket.class);
        int amount = 0;
        for (Contest c : of) {
            amount++;
            for (var e : c.brackets().entrySet()) {
                sum.merge(e.getKey(), e.getValue(), Integer::sum);
                count.merge(e.getKey(), 1, Integer::sum);
            }
            if (amount == 10) break;
        }
        Map<Bracket, Integer> averages = new EnumMap<>(Bracket.class);
        for (var e : sum.entrySet()) averages.put(e.getKey(), e.getValue() / count.get(e.getKey()));
        return new Averages(amount, averages);
    }

    public record Averages(int size, Map<Bracket, Integer> brackets) {}

    // onInventoryUpdated of FarmingContestApi + JacobFarmingContestsInventory
    static void readMenu(MenuView v) {
        inInventory = false;
        if (!sh("farming.contest.yourcontests").matcher(v.title).matches()) return;
        inInventory = v.item(50).cleanLore.stream().findFirst().map(l -> sh("farming.contest.bulkclaim.colorless").matcher(l).matches()).orElse(false);
        if (!inInventory) return;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty() || !it.cleanHas("Your score: ")) continue;
            Long time = timeFor(it.clean);
            if (time != null) addContest(readContest(time, it.cleanLore));
        }
    }

    private static MenuView lastView;

    // the open menu whatever is on, zorro's cape needs to know where you are too
    static void menuTickView(MenuView v) {
        if (v == lastView) return;
        lastView = v;
        if (v == null) inInventory = false;
        else readMenu(v);
    }

    public static void menuTick(Minecraft mc) {
        menuTickView(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> ? MenuMarks.current() : null);
    }

    // --- the running contest ---

    // readCurrentCrop
    static CropType readCurrentCrop(List<String> scoreboard) {
        for (int i = 0; i + 1 < scoreboard.size(); i++) {
            if (!scoreboard.get(i).trim().equals("Jacob's Contest")) continue;
            Matcher m = sh("farming.contest.sidebarcrop.colorless").matcher(scoreboard.get(i + 1));
            return m.matches() ? CropType.getByNameOrNull(m.group("crop")) : null;
        }
        return null;
    }

    private static void phase(CropType crop, Phase phase) {
        if (!Features.on(SUMMARY)) return;
        int broken = BLOCKS.getOrDefault(crop, 0);
        if (phase == Phase.STOP && option(SUMMARY, "jacob_contest_summary.hide_zero", true) && broken == 0) return;
        long now = System.currentTimeMillis();
        switch (phase) {
            case START -> {
                BLOCKS.clear();
                say("§eStarted tracking your Jacob Contest Blocks Per Second!");
                startTime = now;
            }
            case STOP -> {
                long duration = now - startTime;
                double bps = duration < 1000 ? 0 : Math.round(broken * 100.0 / (duration / 1000)) / 100.0;
                say("§eStats for " + crop.cropName + " Contest:");
                say("§7Blocks Broken in total: §e" + String.format(Locale.ROOT, "%,d", broken));
                say("§7Average Blocks Per Second: " + (bps > 19 ? "§c" : "§a") + bps);
                say("§7Participated for §b" + GardenItems.time(duration));
            }
            case CHANGE -> {
                say("§eYou changed the crop during the contest, resetting the Blocks Per Second calculation..");
                startTime = now;
            }
        }
    }

    private static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] " + text));
    }

    // FarmingContestApi.onSecondPassed
    public static void second() {
        long now = System.currentTimeMillis();
        if (internalContest && now - startTime > 20 * 60_000L && contestCrop != null) {
            phase(contestCrop, Phase.STOP);
            internalContest = false;
        }
        if (!"GARDEN".equals(GameState.island)) return;
        // JacobContestStatsSummary.onCropClick
        long blocks = GardenCropSpeed.sessionBlocks;
        if (blocks > lastSessionBlocks && internalContest && GardenCropSpeed.lastBrokenCrop == contestCrop && contestCrop != null)
            BLOCKS.merge(contestCrop, (int) (blocks - lastSessionBlocks), Integer::sum);
        lastSessionBlocks = blocks;
        CropType crop = readCurrentCrop(GameState.scoreboard);
        boolean current = crop != null;
        if (internalContest != current) {
            if (current) {
                phase(crop, Phase.START);
                startTime = now;
            } else if (now - startTime > 2 * 60_000L && contestCrop != null) phase(contestCrop, Phase.STOP);
            internalContest = current;
        } else if (crop != contestCrop && crop != null) {
            phase(crop, Phase.CHANGE);
            startTime = now;
        }
        contestCrop = crop;
        readFortune();
    }

    // fortune you farm a crop with: the tab's Farming Fortune + that crop's own (skyhanni sums tool, armor and pet instead)
    private static void readFortune() {
        CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
        if (crop == null) return;
        Double fortune = fortune(GameState.tab, crop);
        if (fortune == null) return;
        Double old = GardenData.get().latestTrueFF.put(crop.name(), fortune);
        if (old == null || !old.equals(fortune)) Store.markDirty();
    }

    static Double fortune(List<String> tab, CropType crop) {
        double sum = 0;
        boolean any = false;
        for (String line : tab) {
            Matcher m = FORTUNE_LINE.matcher(line);
            if (!m.matches()) continue;
            String name = m.group(1).trim();
            if (!name.equals("Farming Fortune") && !name.equals(crop.cropName + " Fortune")) continue;
            String digits = m.group(2).replaceAll("[^\\d.]", "");
            if (digits.isEmpty()) continue;
            try {
                sum += Double.parseDouble(digits);
                any = true;
            } catch (NumberFormatException ignored) {
                // not a number after all
            }
        }
        return any ? sum : null;
    }

    public static Double latestFF(CropType crop) {
        return GardenData.get().latestTrueFF.get(crop.name());
    }

    public static Double latestBps(CropType crop) {
        return GardenCropSpeed.storage.get().latestBlocksPerSecond.get(crop.name());
    }

    // --- personal bests ---

    private static Map<String, Integer> increments() {
        if (increments != null) return increments;
        Map<String, Integer> out = new HashMap<>();
        try (var in = io.github.zmuzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in != null) {
                JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("personal_best_increment").entrySet()) out.put(e.getKey(), e.getValue().getAsInt());
            }
        } catch (Exception e) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("No personal best data in Garden.json", e);
        }
        increments = out;
        return out;
    }

    private static double number(String text) {
        return Double.parseDouble(text.replace(",", ""));
    }

    // FarmingPersonalBestGain.onChat
    static ChatBus.Result onBestChat(ChatBus.Line line) {
        if (!"GARDEN".equals(GameState.island)) return ChatBus.PASS;
        for (String out : readBestLine(line.plain().trim(), option(BEST, "jacob_personal_best.overflow", false))) say(out);
        return ChatBus.PASS;
    }

    // one plain chat line, what to say once all 3 lines are in
    static List<String> readBestLine(String message, boolean overflow) {
        Matcher m = sh("contest.personal.best.collection.new.colorless").matcher(message);
        if (m.matches()) newCollected = number(m.group("collected"));
        m = sh("contest.personal.best.collection.old.colorless").matcher(message);
        if (m.matches()) oldCollected = number(m.group("collected"));
        m = sh("contest.personal.best.ff.new.colorless").matcher(message);
        if (m.matches()) {
            pbCrop = m.group("crop");
            newFF = number(m.group("ff"));
            CropType crop = CropType.getByNameOrNull(pbCrop);
            if (crop != null) {
                GardenData.get().personalBestFF.put(crop.name(), newFF);
                Store.markDirty();
            }
        }
        if (newCollected == null || oldCollected == null || newFF == null || pbCrop == null) return List.of();
        double collected = newCollected, previous = oldCollected, ff = newFF;
        String cropName = pbCrop;
        newCollected = oldCollected = newFF = null;
        pbCrop = null;
        CropType crop = CropType.getByNameOrNull(cropName);
        Integer increment = crop == null ? null : increments().get(crop.name());
        if (increment == null) return List.of();
        double oldFF = previous / (increment * 100.0), newOverflowFF = collected / (increment * 100.0);
        double ffDiff = ff - oldFF, overflowDiff = newOverflowFF - oldFF;
        if (oldFF < 100 && !overflow) return List.of("§eThis is §6" + round(ffDiff) + "☘ " + cropName + " Fortune §emore than previously!");
        if (newOverflowFF > 100 && overflow) {
            return List.of("§eYou have §6" + round(overflowDiff) + "☘ " + cropName + " Fortune §eincluding overflow!",
                    "§eThis is §6" + round(overflowDiff) + "☘ " + cropName + " Fortune §emore than previously!");
        }
        return List.of();
    }

    private static String round(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // --- zorro's cape ---

    // JacobFarmingContestsInventory.isClaimableContest
    static boolean claimable(MenuView.Item it) {
        return it.lastLore().equals("§eClick to claim reward!");
    }

    // what the Stats and Equipment menu last said you wear, or the profile's
    private static boolean wearsZorro() {
        var data = io.github.zmuzy.sbc.core.game.GameProfile.current();
        return data != null && data.equipment.stream().anyMatch(i -> "ZORROS_CAPE".equals(i.id));
    }

    private static boolean zorroClick(net.minecraft.world.inventory.Slot slot) {
        if (!Features.on(ZORRO) || !inInventory || slot == null || wearsZorro()) return false;
        MenuView.Item it = MenuView.Item.of(slot.getItem());
        if (!claimable(it) && !it.clean.equals("Bulk Claim")) return false;
        var mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.hud != null) mc.gui.hud.setTitle(Component.literal("§cNo Zorro's Cape equipped!"));
        say("§cBlocked Jacob's Contest reward claim! §eEquip §6Zorro's Cape §ewhen claiming contest rewards.");
        return true;
    }

    public static void register() {
        ContestPanel.register();
        SideHelper.add((Screen s) -> ContestPanel.of(s));
        Feature summary = new Feature(SUMMARY, "Garden", "Contest summary", "At the end of a Jacob's Contest, in chat: the blocks you broke, your average blocks per second and how long you took part.", "SkyHanni: Contest Summary")
                .when(() -> "GARDEN".equals(GameState.island))
                .option(Option.bool("jacob_contest_summary.hide_zero", "Hide 0-crop stats", "No stats for a contest you broke no crops in, which is useful for pest farming.", true));
        Features.add(summary);
        Feature best = new Feature(BEST, "Garden", "Personal best fortune", "After beating your contest record, in chat: how much more farming fortune the Personal Bests perk now gives you.", "SkyHanni: Personal Best Increase FF")
                .when(() -> "GARDEN".equals(GameState.island))
                .option(Option.bool("jacob_personal_best.overflow", "Count past the cap", "How much more it would have been if the perk did not stop at 100.", false));
        Features.add(best);
        ChatBus.register(ChatBus.OBSERVE + 17, BEST, best, Contests::onBestChat);
        Features.add(new Feature(ZORRO, "Garden", "Zorro's Cape protection", "Blocks claiming a contest's rewards while you don't wear Zorro's Cape, which gives more of them. Opens no menu for you: the Stats and Equipment menu has to have been opened to know what you wear.", "SkyHanni: Zorro's Cape Protection")
                .defaultOff()
                .when(() -> "GARDEN".equals(GameState.island)));
        Interactions.onSlotClick((screen, slot, slotId, button) -> zorroClick(slot));
        NextContest.register();
    }
}
