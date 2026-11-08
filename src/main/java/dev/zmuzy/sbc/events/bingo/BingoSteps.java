// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.bingo;

import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.events.EventData;
import dev.zmuzy.sbc.events.EventSheet;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// what to do next for your bingo goals. from skyhanni BingoNextStepHelper + its steps (NextStep,
// ProgressionStep, ChatMessageStep, CollectionStep, CraftStep, IslandVisitStep, ItemsStep, SkillLevelStep)
// squashed into one class
// every goal it can read becomes a step with requirements, steps with nothing left before them get shown
public final class BingoSteps {
    private static final String RHYS_TASK_NAME = "30x Enchanted Minerals (Redstone, Lapis Lazuli, Coal) (for Rhys)";

    // what the steps read from the game, tests give their own
    interface World {
        long itemCount(String cleanName);

        long skillExp(String skill);

        long collection(String name);

        String island();
    }

    enum Kind { PLAIN, CRAFT, CRYSTAL, ISLAND, ITEMS, RHYS, SKILL, COLLECTION }

    static final class Step {
        final Kind kind;
        final String displayName;
        final List<Step> requirements = new ArrayList<>();
        boolean done;
        long amountNeeded, amountHaving, amountHiddenHaving;
        String name = "";
        Map<String, Integer> variants = Map.of();

        Step(Kind kind, String displayName) {
            this.kind = kind;
            this.displayName = displayName;
        }

        Step requires(Step other) {
            requirements.add(other);
            return this;
        }

        boolean progression() {
            return kind == Kind.ITEMS || kind == Kind.RHYS || kind == Kind.SKILL || kind == Kind.COLLECTION;
        }

        long needed() {
            return amountNeeded - amountHiddenHaving;
        }
    }

    // one walk of the helper state, tests drive this too
    static final class Helper {
        final Map<String, Step> islands = new HashMap<>();
        final Map<String, Step> itemIslandRequired = new HashMap<>();
        final Map<String, Step> itemPreconditions = new HashMap<>();
        final List<Step> finalSteps = new ArrayList<>();
        List<Step> current = new ArrayList<>();
        boolean dirty = true, nextMessageIsCrystal;

        Helper() {
            reset();
        }

        private Step island(String id) {
            return islands.computeIfAbsent(id, k -> {
                Step s = new Step(Kind.ISLAND, "Visit " + GameState.pretty(k));
                s.name = k;
                return s;
            });
        }

        void reset() {
            islands.clear();
            finalSteps.clear();
            itemIslandRequired.clear();
            itemPreconditions.clear();
            itemIslandRequired.put("Acacia Wood", island("THE_PARK"));
            for (String item : List.of("Redstone", "Lapis Lazuli", "Coal", "Slimeball", "Emerald", "Mithril")) itemIslandRequired.put(item, island("DEEP_CAVERNS"));
            island("GOLD_MINES").requires(island("HUB")).requires(skill("Mining", 1));
            island("DEEP_CAVERNS").requires(island("GOLD_MINES")).requires(skill("Mining", 5));
            rhys();
            island("DWARVEN_MINES").requires(skill("Mining", 12).requires(island("THE_FARMING_ISLANDS")));
            island("CRYSTAL_HOLLOWS").requires(island("DWARVEN_MINES"));
            island("GARDEN").requires(island("HUB"));
            Step farmingContest = new Step(Kind.PLAIN, "Farming Contest").requires(skill("Farming", 10));
            itemPreconditions.put("Jacob's Ticket", farmingContest);
            finalSteps.add(island("DWARVEN_MINES"));
            Step ender = new Step(Kind.PLAIN, "Get Ender Armor").requires(island("THE_END"));
            finalSteps.add(ender);
            island("THE_END").requires(skill("Combat", 12).requires(island("DEEP_CAVERNS")));
        }

        private void rhys() {
            Step mines = island("DWARVEN_MINES");
            mines.requires(partial("Redstone", "Enchanted Redstone"));
            mines.requires(partial("Lapis Lazuli", "Enchanted Lapis Lazuli"));
            mines.requires(partial("Coal", "Enchanted Coal"));
        }

        private Step partial(String item, String enchanted) {
            Step s = items(Kind.RHYS, RHYS_TASK_NAME, item, 160 * 10, Map.of(item, 1, enchanted, 160));
            return s.requires(island("DEEP_CAVERNS"));
        }

        private static Step items(Kind kind, String display, String item, long needed, Map<String, Integer> variants) {
            Step s = new Step(kind, display);
            s.name = item;
            s.amountNeeded = needed;
            s.variants = variants;
            return s;
        }

        private static Step skill(String skill, int level) {
            Step s = new Step(Kind.SKILL, skill + " " + level);
            s.name = skill;
            s.amountNeeded = level < Profile.SKILL_XP.length ? Profile.SKILL_XP[level] : Long.MAX_VALUE;
            return s;
        }

        // readDescription
        Step read(String description) {
            Matcher m = Patterns.get("SH.bingo.steps.collection").matcher(description);
            if (m.matches()) {
                int amount = Integer.parseInt(m.group("amount").replace(",", ""));
                String name = m.group("name");
                Step s = new Step(Kind.COLLECTION, Combat.shortNumber(amount) + " " + name + " Collection");
                s.name = name;
                s.amountNeeded = amount;
                Step island = itemIslandRequired.get(name);
                if (island != null) s.requires(island);
                return s;
            }
            if (description.equals("Craft an Emerald Ring.")) {
                return new Step(Kind.CRAFT, "Craft Emerald Ring").requires(
                        items(Kind.ITEMS, "32x Enchanted Emerald", "Emerald", 160 * 32, Map.of("Emerald", 1, "Enchanted Emerald", 160)).requires(island("DWARVEN_MINES")));
            }
            if (description.equals("Obtain a Mathematical Hoe Blueprint.")) {
                Step tickets = items(Kind.ITEMS, "32x Jacob's Ticket", "Jacob's Ticket", 32, Map.of("Jacob's Ticket", 1));
                Step pre = itemPreconditions.get("Jacob's Ticket");
                if (pre != null) tickets.requires(pre);
                return new Step(Kind.CRAFT, "Craft Mathematical Hoe Blueprint").requires(tickets.requires(island("GARDEN")));
            }
            m = Patterns.get("SH.bingo.steps.crystal.obtain").matcher(description);
            if (m.matches()) {
                Step s = new Step(Kind.CRYSTAL, "Obtain a " + m.group("name") + " Crystal");
                s.name = m.group("name");
                return s.requires(island("CRYSTAL_HOLLOWS"));
            }
            m = Patterns.get("SH.bingo.steps.skill").matcher(description);
            if (m.matches()) return skill(m.group("skill"), Integer.parseInt(m.group("level")));
            return null;
        }

        // update: goals still to do become steps, once
        void update(List<EventData.Goal> personal) {
            List<EventData.Goal> todo = new ArrayList<>();
            for (EventData.Goal g : personal) if (!g.done) todo.add(g);
            if (todo.isEmpty()) {
                if (!dirty) {
                    reset();
                    dirty = true;
                }
                return;
            }
            if (!dirty) return;
            dirty = false;
            for (EventData.Goal g : todo) {
                Step s = read(GameState.strip(g.description));
                if (s != null) finalSteps.add(s);
            }
            result();
        }

        // updateResult with printRequirements
        void result() {
            current = new ArrayList<>();
            for (Step s : finalSteps) walk(s, false);
        }

        private void walk(Step step, boolean parentDone) {
            int todo = 0;
            for (Step r : step.requirements) {
                walk(r, step.done || parentDone);
                if (!r.done) todo++;
            }
            if (!step.done && !parentDone && todo == 0 && !current.contains(step)) current.add(step);
        }

        // updateCurrentSteps + updateIslandsVisited
        void refresh(World w) {
            boolean changed = false;
            for (Step step : islands.values()) {
                if (!step.done && step.name.equals(w.island())) {
                    step.done = true;
                    changed = true;
                }
            }
            for (Step step : new ArrayList<>(current)) {
                long having = step.amountHaving;
                switch (step.kind) {
                    case ITEMS, RHYS -> {
                        having = 0;
                        for (Map.Entry<String, Integer> v : step.variants.entrySet()) having += w.itemCount(v.getKey()) * v.getValue();
                    }
                    case SKILL -> having = w.skillExp(step.name);
                    case COLLECTION -> having = w.collection(step.name);
                    default -> {
                    }
                }
                if (having != step.amountHaving) {
                    step.amountHaving = having;
                    if (having >= step.needed()) step.done = true;
                    changed = true;
                }
            }
            if (changed) result();
        }

        // onChat: a crystal you found and the items rhys took
        void chat(String clean) {
            for (Step step : current) {
                if (step.kind == Kind.CRYSTAL) {
                    if (dev.zmuzy.sbc.events.EventPack.plain("SH.bingo.steps.crystal.found").matcher(clean).matches()) {
                        nextMessageIsCrystal = true;
                        return;
                    }
                    if (nextMessageIsCrystal) {
                        nextMessageIsCrystal = false;
                        Matcher m = dev.zmuzy.sbc.events.EventPack.plain("SH.bingo.steps.crystal.obtained").matcher(clean);
                        if (m.matches() && m.group("crystalName").equals(step.name)) {
                            step.done = true;
                            result();
                            return;
                        }
                    }
                }
                if (step.kind == Kind.RHYS && clean.equals("[NPC] Rhys: Thank you for the items!")) step.amountHiddenHaving -= 10;
            }
        }
    }

    private static final Helper HELPER = new Helper();
    private static final World GAME = new World() {
        @Override
        public long itemCount(String cleanName) {
            var p = Minecraft.getInstance().player;
            if (p == null) return 0;
            long n = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.isEmpty() && GameState.strip(st.getHoverName().getString()).trim().equals(cleanName)) n += st.getCount();
            }
            return n;
        }

        @Override
        public long skillExp(String skill) {
            Profile.Data d = Profile.current;
            Profile.Level l = d == null ? null : d.skills.get(skill);
            return l == null ? 0 : (long) l.xp();
        }

        @Override
        public long collection(String name) {
            Profile.Data d = Profile.current;
            String id = ItemRepo.idForName(name.equals("Mushroom") ? "Red Mushroom" : name);
            Long n = d == null || id == null ? null : d.collections.get(id);
            return n == null ? 0 : n;
        }

        @Override
        public String island() {
            return GameState.island;
        }
    };
    private static long refreshedAt;

    private BingoSteps() {}

    static void dirty() {
        HELPER.dirty = true;
        HELPER.reset();
    }

    static void chat(String message) {
        HELPER.chat(message);
    }

    private static String line(Step step) {
        if (!step.progression() || step.amountHaving <= 0) return "";
        double p = step.amountHaving / (double) step.needed();
        return String.format(Locale.ROOT, "%,d/%,d", step.amountHaving, step.needed()) + " " + Math.min(100, Math.round(p * 100)) + "%";
    }

    // drawDisplay
    static void fill(EventSheet s) {
        long now = System.currentTimeMillis();
        if (now - refreshedAt > 250) {
            refreshedAt = now;
            List<EventData.Goal> personal = new ArrayList<>();
            for (EventData.Goal g : BingoCard.session().goals.values()) if (!g.community) personal.add(g);
            HELPER.update(personal);
            HELPER.refresh(GAME);
        }
        s.section("Next steps");
        if (HELPER.current.isEmpty()) s.line("Open the card with /bingo", "", Modern.WARN);
        for (Step step : HELPER.current) s.line(step.displayName, line(step), Modern.AQUA).labelColor(Modern.LABEL);
    }
}
