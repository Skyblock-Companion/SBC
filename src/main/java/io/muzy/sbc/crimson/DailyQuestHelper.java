// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.crimson;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.core.TabWidgets;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni reputationhelper: CrimsonIsleReputationHelper (display), DailyQuestHelper + QuestLoader (5
// daily quests from the FACTION_QUESTS widget, town board menus, completion line, fetch items in inventory,
// trophy fish), DailyMiniBossHelper (5 minibosses from DOWN! lines), DailyKuudraBossHelper (5 tiers from
// KUUDRA DOWN!)
// data from CrimsonIsleReputation.json, state saved per profile
public final class DailyQuestHelper {
    public enum QuestCategory {
        FISHING("Trophy Fish"), RESCUE("Rescue Mission"), MINIBOSS("Mini Boss"), FETCH("Item Fetch"), DOJO("Dojo Task"), KUUDRA("Kuudra Kill"), UNKNOWN("§4Unknown");

        public final String displayName;

        QuestCategory(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum QuestState {
        ACCEPTED("Accepted", "§b"), READY_TO_COLLECT("Ready to collect", "§a"), COLLECTED("Collected", "§7");

        public final String displayName, color;

        QuestState(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    // Quest + ProgressQuest fields, needAmount < 0 for quests without progress
    public static final class Quest {
        public final QuestCategory category;
        public final String internalName, displayName, displayItem;
        public final Vec3 location;
        public QuestState state;
        public final int needAmount;
        public int haveAmount;
        // MiniBossQuest.miniBoss / KuudraQuest.kuudraTier / DojoQuest.dojoName / FetchQuest.itemName / TrophyFishQuest.fishName
        public final String target;

        Quest(QuestCategory category, String internalName, String displayName, String displayItem, Vec3 location, QuestState state, int needAmount, String target) {
            this.category = category;
            this.internalName = internalName;
            this.displayName = displayName;
            this.displayItem = displayItem;
            this.location = location;
            this.state = state;
            this.needAmount = needAmount;
            this.target = target;
        }

        public boolean isProgress() {
            return needAmount >= 0;
        }
    }

    // CrimsonMiniBoss
    public static final class MiniBoss {
        public final String displayName, displayItem;
        public final Vec3 location;
        public final Pattern pattern;
        public boolean doneToday;

        MiniBoss(String displayName, String displayItem, Vec3 location) {
            this.displayName = displayName;
            this.displayItem = displayItem;
            this.location = location;
            // DailyMiniBossHelper.processRepoData: "§f *§r§6§l${displayName.uppercase()} DOWN!"
            this.pattern = Pattern.compile("§f *§r§6§l" + Pattern.quote(displayName.toUpperCase(Locale.ROOT)) + " DOWN!");
        }
    }

    // KuudraTier
    public enum KuudraTier {
        BASIC("Basic"), HOT("Hot"), BURNING("Burning"), FIERY("Fiery"), INFERNAL("Infernal");

        public final String displayName;
        public boolean doneToday;
        public Vec3 location;
        public int tierNumber = ordinal() + 1;
        public String displayItem = "KUUDRA_" + name() + "_TIER_KEY";

        KuudraTier(String displayName) {
            this.displayName = displayName;
        }

        public String getTieredDisplayName() {
            return "Tier " + tierNumber + " (" + displayName + ")";
        }

        static KuudraTier byName(String name) {
            for (KuudraTier t : values()) if (t.displayName.equalsIgnoreCase(name) || t.name().equalsIgnoreCase(name)) return t;
            return null;
        }
    }

    static final Patterns.P TAB_QUEST = Patterns.p("SH.crimson.reputationhelper.tablist.quest-no-color");
    static final Patterns.P MINIBOSS_AMOUNT = Patterns.p("SH.crimson.reputationhelper.quest.townboard.minibossamount");
    static final Patterns.P TOWN_BOARD_COMPLETED = Patterns.p("SH.crimson.reputationhelper.quest.townboard.completed");
    static final Patterns.P CHAT_COMPLETED = Patterns.p("SH.crimson.reputationhelper.quest.chat.completed");
    static final Patterns.P KUUDRA_QUEST = Patterns.p("SH.crimson.kuudra.quest.identifier");
    // TrophyFishMessages.trophyFishPattern
    static final Patterns.P TROPHY_FISH = Patterns.p("SH.fishing.trophy.trophyfish");

    public static final List<Quest> quests = new ArrayList<>();
    public static final List<MiniBoss> miniBosses = new ArrayList<>();
    static Vec3 kuudraLocation;
    static boolean tabListQuestsMissing;
    public static boolean hideComplete;
    public static boolean showLocations = true;

    private DailyQuestHelper() {}

    // --- data ---

    public static synchronized void loadData() {
        ReputationData.load();
        miniBosses.clear();
        for (var e : ReputationData.category("MINIBOSS").entrySet()) miniBosses.add(new MiniBoss(e.getKey(), e.getValue().item(), e.getValue().location()));
        int tier = 1;
        for (var e : ReputationData.category("KUUDRA").entrySet()) {
            if (e.getValue().location() != null) kuudraLocation = e.getValue().location();
            for (KuudraTier t : KuudraTier.values()) if (t.displayName.equals(e.getKey())) {
                t.location = e.getValue().location();
                t.displayItem = e.getValue().item();
                t.tierNumber = tier;
            }
            tier++;
        }
    }

    // --- QuestLoader ---

    // QuestLoader.loadFromTabList: FACTION_QUESTS widget lines, plain
    public static void loadFromTabList(List<String> tab) {
        int found = 0;
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals("FACTION_QUESTS")) continue;
            for (int i : w.lines()) {
                readQuest(tab.get(i));
                found++;
            }
        }
        tabListQuestsMissing = found == 0;
    }

    static void readQuest(String line) {
        Matcher m = TAB_QUEST.matcher(line);
        if (!m.matches()) return;
        String name = m.group("name");
        int amount = m.group("amount") == null ? 1 : Integer.parseInt(m.group("amount"));
        boolean green = m.group("status").equals("✔");
        checkQuest(name, green, amount);
    }

    private static void checkQuest(String name, boolean green, int needAmount) {
        Quest old = getQuestByName(name);
        if (old != null) {
            if (green && old.state != QuestState.READY_TO_COLLECT && old.state != QuestState.COLLECTED) {
                old.state = QuestState.READY_TO_COLLECT;
                save();
            }
            return;
        }
        QuestState state = green ? QuestState.READY_TO_COLLECT : QuestState.ACCEPTED;
        addQuest(addQuest(name, state, needAmount));
    }

    static Quest addQuest(String name, QuestState state, int needAmount) {
        for (MiniBoss miniBoss : miniBosses)
            if (name.equals(miniBoss.displayName)) return new Quest(QuestCategory.MINIBOSS, miniBoss.displayName, miniBoss.displayName, miniBoss.displayItem, miniBoss.location, state, needAmount, miniBoss.displayName);
        Matcher k = KUUDRA_QUEST.matcher(name);
        if (k.matches()) {
            KuudraTier tier = KuudraTier.byName(k.group("tier"));
            if (tier != null) return new Quest(QuestCategory.KUUDRA, "Kill Kuudra " + tier.name() + " Tier", tier.getTieredDisplayName(), tier.displayItem, tier.location, state, -1, tier.name());
        }
        String questName = name, dojoGoal = "";
        if (name.contains(" Rank ")) {
            String[] split = name.split(" Rank ");
            questName = split[0];
            dojoGoal = split[1];
        }
        for (String type : new String[]{"FISHING", "RESCUE", "FETCH", "DOJO"}) {
            ReputationData.Quest info = ReputationData.category(type).get(questName);
            if (info == null) continue;
            switch (type) {
                case "FISHING":
                    return new Quest(QuestCategory.FISHING, name, name, info.item(), info.location(), state, needAmount, name);
                case "RESCUE":
                    return new Quest(QuestCategory.RESCUE, "Rescue Mission", "Rescue the NPC", info.item(), info.location(), state, -1, null);
                case "FETCH":
                    return new Quest(QuestCategory.FETCH, name, name, info.item(), info.location(), state, needAmount, name);
                default:
                    return new Quest(QuestCategory.DOJO, questName + " Rank " + dojoGoal, questName + " §7(§e" + getPointsForDojoRank(dojoGoal) + " points§7)", info.item(), info.location(), state, -1, questName);
            }
        }
        return new Quest(QuestCategory.UNKNOWN, name, name, "", null, QuestState.ACCEPTED, -1, null);
    }

    // DojoQuest.getPointsForDojoRank
    static int getPointsForDojoRank(String rank) {
        return switch (rank) {
            case "S" -> 1000;
            case "A" -> 800;
            case "B" -> 600;
            case "C" -> 400;
            case "D" -> 200;
            default -> 0;
        };
    }

    static Quest getQuestByName(String name) {
        // ignore case: a kuudra quest is named after its enum ("Kill Kuudra HOT Tier") but the tab says "Kill Kuudra Hot Tier"
        // exact match never finds it, then every tab update adds it again until the 6th quest resets the list and the
        // row keeps blinking
        for (Quest q : quests) if (q.internalName.equalsIgnoreCase(name)) return q;
        return null;
    }

    public static Quest getQuest(QuestCategory category) {
        for (Quest q : quests) if (q.category == category) return q;
        return null;
    }

    private static void addQuest(Quest q) {
        quests.add(q);
        if (quests.size() > 5) reset();
        save();
    }

    // QuestLoader.checkInventory: a town board category menu (name = category like "Fishing"), slot 22 formatted lore
    public static void onTownBoardMenu(String inventoryName, List<String> slot22Lore) {
        if (slot22Lore == null) return;
        for (Quest quest : new ArrayList<>(quests)) {
            if (!quest.category.name().equalsIgnoreCase(inventoryName)) continue;
            boolean completed = false;
            for (String l : slot22Lore) if (TOWN_BOARD_COMPLETED.matcher(l).matches()) completed = true;
            if (completed && quest.state != QuestState.COLLECTED) {
                quest.state = QuestState.COLLECTED;
                save();
            }
            if (inventoryName.equals("Miniboss")) fixMinibossAmount(quest, slot22Lore);
        }
    }

    private static void fixMinibossAmount(Quest quest, List<String> lore) {
        if (quest.category != QuestCategory.MINIBOSS || quest.needAmount != 1) return;
        for (String line : lore) {
            Matcher m = MINIBOSS_AMOUNT.matcher(line);
            if (!m.matches()) continue;
            int realAmount = Integer.parseInt(m.group("amount"));
            if (realAmount == quest.needAmount) continue;
            Quest fixed = new Quest(QuestCategory.MINIBOSS, quest.internalName, quest.displayName, quest.displayItem, quest.location, quest.state, realAmount, quest.target);
            fixed.haveAmount = quest.haveAmount;
            quests.remove(quest);
            quests.add(fixed);
            CrimsonEnv.world.chat("Fixed wrong miniboss amount from Town Board.");
            save();
            return;
        }
    }

    // --- DailyQuestHelper ---

    // formatted line, true if a reputation line got used
    public static boolean onChat(String formatted) {
        if (!CrimsonEnv.inCrimson()) return false;
        Matcher m = CHAT_COMPLETED.matcher(formatted);
        if (m.matches()) {
            String type = m.group("type").toLowerCase(Locale.ROOT);
            Quest q = type.equals("dojo") ? getQuest(QuestCategory.DOJO) : type.equals("rescue") ? getQuest(QuestCategory.RESCUE) : null;
            if (q != null) {
                q.state = QuestState.READY_TO_COLLECT;
                save();
            }
            return true;
        }
        for (MiniBoss miniBoss : miniBosses)
            if (miniBoss.pattern.matcher(formatted).matches()) {
                finishMiniBoss(miniBoss);
                return true;
            }
        Matcher t = TROPHY_FISH.matcher(formatted);
        if (t.matches()) {
            onTrophyFishCaught(t.group("displayName").replace("§k", ""));
            return false;
        }
        return false;
    }

    // TrophyFishApi.getInternalName
    static String trophyInternalName(String displayName) {
        return GameState.strip(displayName.replace("Obfuscated", "Obfuscated Fish").replaceAll("[- ]", "").toLowerCase(Locale.ROOT));
    }

    static void onTrophyFishCaught(String displayName) {
        Quest fish = getQuest(QuestCategory.FISHING);
        if (fish == null || (fish.state != QuestState.ACCEPTED && fish.state != QuestState.READY_TO_COLLECT)) return;
        if (trophyInternalName(displayName).equals(trophyInternalName(fish.target))) updateProcessQuest(fish, fish.haveAmount + 1);
    }

    // checkInventoryForFetchItem every 3s
    public static void checkInventoryForFetchItem(List<CrimsonEnv.ItemCount> inventory) {
        Quest fetch = getQuest(QuestCategory.FETCH);
        if (fetch == null || (fetch.state != QuestState.ACCEPTED && fetch.state != QuestState.READY_TO_COLLECT)) return;
        int count = 0;
        for (CrimsonEnv.ItemCount it : inventory) if (it.cleanName().equals(fetch.target)) count += it.count();
        updateProcessQuest(fetch, count);
    }

    private static void updateProcessQuest(Quest quest, int newAmount) {
        int count = Math.min(newAmount, quest.needAmount);
        if (quest.haveAmount == count) return;
        CrimsonEnv.world.chat(quest.displayName + " progress: " + count + "/" + quest.needAmount);
        quest.haveAmount = count;
        quest.state = count == quest.needAmount ? QuestState.READY_TO_COLLECT : QuestState.ACCEPTED;
        save();
    }

    static void finishMiniBoss(MiniBoss miniBoss) {
        Quest q = getQuest(QuestCategory.MINIBOSS);
        if (q != null && q.target.equals(miniBoss.displayName) && q.state == QuestState.ACCEPTED) updateProcessQuest(q, q.haveAmount + 1);
        miniBoss.doneToday = true;
        save();
    }

    // DailyKuudraBossHelper.onKuudraDone
    public static void onKuudraComplete(int tier) {
        KuudraTier kuudraTier = null;
        for (KuudraTier t : KuudraTier.values()) if (t.tierNumber == tier) kuudraTier = t;
        if (kuudraTier == null) return;
        Quest q = getQuest(QuestCategory.KUUDRA);
        if (q != null && q.target.equals(kuudraTier.name()) && q.state == QuestState.ACCEPTED) q.state = QuestState.READY_TO_COLLECT;
        kuudraTier.doneToday = true;
        save();
    }

    // --- display ---

    public static List<String> display() {
        List<String> out = new ArrayList<>();
        out.add("§e§lReputation Helper");
        if (ReputationApi.factionType() == null) {
            out.add("§cFaction not found!");
            return out;
        }
        if (tabListQuestsMissing) {
            out.add("§cFaction Quests Widget not found!");
            out.add("§7Open §e/tab §7and enable it!");
            return out;
        }
        int done = 0;
        for (Quest q : quests) if (q.state == QuestState.COLLECTED) done++;
        out.add("");
        out.add("§7Daily Quests (§e" + done + "§8/§e5 collected§7)");
        if (done != 5) for (Quest q : quests) {
            if (hideComplete && q.state == QuestState.COLLECTED) continue;
            out.add(renderQuest(q));
        }
        done = 0;
        for (MiniBoss m : miniBosses) if (m.doneToday) done++;
        out.add("");
        out.add("§7Daily Bosses (§e" + done + "§8/§e5 killed§7)");
        if (done != 5) for (MiniBoss m : miniBosses) {
            if (hideComplete && m.doneToday) continue;
            out.add(" §5" + m.displayName + "§7: " + (m.doneToday ? "§aDone" : "§bTodo"));
        }
        done = 0;
        for (KuudraTier t : KuudraTier.values()) if (t.doneToday) done++;
        out.add("");
        out.add("§7Daily Kuudra (§e" + done + "§8/§e5 killed§7)");
        if (done < 5) for (KuudraTier t : KuudraTier.values()) {
            if (hideComplete && t.doneToday) continue;
            out.add(" " + t.getTieredDisplayName() + ": " + (t.doneToday ? "§aDone" : "§bTodo"));
        }
        return out;
    }

    static String renderQuest(Quest quest) {
        String progressText = quest.isProgress() && quest.state != QuestState.COLLECTED ? " §e" + quest.haveAmount + "§8/§e" + quest.needAmount : "";
        String stateText = quest.category != QuestCategory.UNKNOWN && quest.state != QuestState.ACCEPTED ? quest.state.color + "[" + quest.state.displayName + "] §f" : "";
        return "  " + stateText + quest.category.displayName + ": §f" + quest.displayName + progressText;
    }

    private static boolean needsTownBoardLocation(Quest q) {
        return q.state == QuestState.READY_TO_COLLECT || (q.state == QuestState.ACCEPTED && (q.category == QuestCategory.FETCH || q.category == QuestCategory.RESCUE));
    }

    // onRenderWorld of the 3 helpers (quest spots, minibosses not done, kuudra, town board)
    public static void render(WorldDraw w, Vec3 questBoard, java.util.function.Predicate<Vec3> bossNear) {
        if (!CrimsonEnv.inCrimson() || !showLocations) return;
        for (Quest quest : quests) {
            if (quest.category == QuestCategory.MINIBOSS || quest.state != QuestState.ACCEPTED || quest.location == null) continue;
            mark(w, quest.location, quest.displayName);
        }
        Quest mb = getQuest(QuestCategory.MINIBOSS);
        Vec3 player = CrimsonEnv.world.playerPos();
        for (MiniBoss miniBoss : miniBosses) {
            boolean needed = mb != null && mb.target.equals(miniBoss.displayName) && mb.state == QuestState.ACCEPTED;
            if (miniBoss.doneToday && !needed) continue;
            if (miniBoss.location == null) continue;
            if (player.distanceTo(miniBoss.location) < 40 && bossNear.test(miniBoss.location)) continue;
            mark(w, miniBoss.location, miniBoss.displayName);
        }
        boolean allKuudraDone = true;
        for (KuudraTier t : KuudraTier.values()) if (!t.doneToday) allKuudraDone = false;
        if (!allKuudraDone && kuudraLocation != null) mark(w, kuudraLocation, "Kuudra");
        boolean needBoard = false;
        for (Quest q : quests) if (needsTownBoardLocation(q)) needBoard = true;
        if (needBoard && questBoard != null) mark(w, questBoard, "Town Board");
    }

    private static void mark(WorldDraw w, Vec3 l, String text) {
        w.box(new AABB(l, l.add(1, 1, 1)), 0xFFFFFFFF, true, false);
        w.label(text, l.add(0.5, 1.5, 0.5), true);
    }

    // --- storage ---

    public static void reset() {
        quests.clear();
        for (MiniBoss m : miniBosses) m.doneToday = false;
        for (KuudraTier t : KuudraTier.values()) t.doneToday = false;
        save();
    }

    public static void save() {
        Store.CrimsonProfile s = CrimsonEnv.profile();
        s.quests.clear();
        for (Quest q : quests) s.quests.add(q.internalName + ":" + q.state + ":" + (q.isProgress() ? q.needAmount + ":" + q.haveAmount : "0"));
        s.miniBossesDoneToday.clear();
        for (MiniBoss m : miniBosses) if (m.doneToday) s.miniBossesDoneToday.add(m.displayName);
        s.kuudraTiersDone.clear();
        for (KuudraTier t : KuudraTier.values()) if (t.doneToday) s.kuudraTiersDone.add(t.name());
        Store.markDirty();
    }

    public static void load() {
        loadData();
        Store.CrimsonProfile s = CrimsonEnv.profile();
        quests.clear();
        for (String text : new ArrayList<>(s.quests)) {
            String[] split = text.split(":");
            if (split.length < 3) continue;
            QuestState state;
            try {
                state = split[1].equals("NOT_ACCEPTED") ? QuestState.ACCEPTED : QuestState.valueOf(split[1]);
            } catch (IllegalArgumentException e) {
                continue;
            }
            int needAmount = Integer.parseInt(split[2]);
            Quest q = addQuest(split[0], state, needAmount);
            if (q.category == QuestCategory.UNKNOWN) {
                quests.clear();
                s.quests.clear();
                return;
            }
            if (q.isProgress() && split.length == 4) q.haveAmount = Integer.parseInt(split[3]);
            quests.add(q);
        }
        for (MiniBoss m : miniBosses) m.doneToday = s.miniBossesDoneToday.contains(m.displayName);
        for (KuudraTier t : KuudraTier.values()) t.doneToday = s.kuudraTiersDone.contains(t.name());
    }
}
