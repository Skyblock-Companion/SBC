// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.tasks;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.plan.MaxPlan;
import dev.zmuzy.sbc.plan.MoneyMethods;
import dev.zmuzy.sbc.plan.Planner;

import java.util.ArrayList;
import java.util.List;

// routines for what you wanna do today. each picks its own tasks in its own order, then fills the rest of
// your hours with timed grind blocks (the guide walks you through both)
public final class RoutinePreset {
    // a grind block: planner activity id, or "plan" (max plan focus), "money1/2" (best money methods), "level1"
    public record Slot(String what, double share) {}

    public record Preset(String id, String name, String icon, String about, List<String> tasks, List<Slot> slots) {}

    public static final List<Preset> ALL = List.of(
            new Preset("smart", "Smart day", "nether_star", "Every daily by importance, then your plan-to-max focus.", null,
                    List.of(new Slot("plan", 1))),
            new Preset("quick", "Quick 30 min", "clock", "Only the fast, high-value dailies. Done in half an hour.",
                    List.of("visitors", "experiments", "fetchur", "puzzler", "pomtair", "sirih", "matriarch", "bits", "bazaar_orders", "daily_reward"),
                    List.of()),
            new Preset("money", "Money day", "gold_ingot", "Dailies that pay, then your two best money methods for your gear.",
                    List.of("visitors", "experiments", "matriarch", "kuudra", "forge_collect", "bazaar_orders", "bits", "slayer"),
                    List.of(new Slot("money1", 0.6), new Slot("money2", 0.4))),
            new Preset("level", "SkyBlock XP rush", "experience_bottle", "Everything that gives SkyBlock XP and reputation, then the fastest XP grind.",
                    List.of("commissions", "crimson_quests", "pomtair", "sirih", "suus", "dojo", "experiments", "visitors", "fetchur", "puzzler",
                            "museum", "bestiary", "collections"),
                    List.of(new Slot("level1", 1))),
            new Preset("mining", "Mining day", "iron_pickaxe", "Commissions, Fetchur, Puzzler, Forge, Nucleus, then gemstones and Mineshafts.",
                    List.of("commissions", "fetchur", "puzzler", "forge_collect", "nucleus"),
                    List.of(new Slot("gemstones", 0.6), new Slot("glacite", 0.4))),
            new Preset("crimson", "Crimson Isle", "blaze_powder", "Faction quests and every Barbarian rep task, Dojo, Matriarch, then Kuudra.",
                    List.of("crimson_quests", "pomtair", "sirih", "suus", "dojo", "matriarch", "kuudra"),
                    List.of(new Slot("kuudra", 0.6), new Slot("trophy", 0.4))),
            new Preset("garden", "Garden day", "wheat", "Visitors, composter, pests and Jacob's contest, then farming.",
                    List.of("visitors", "composter", "pests", "jacob"),
                    List.of(new Slot("farming", 0.7), new Slot("visitors_loop", 0.3))),
            new Preset("combat", "Combat day", "iron_sword", "Slayers, dungeons and Kuudra, then your best combat grind.",
                    List.of("slayer", "dungeons", "kuudra", "bestiary"),
                    List.of(new Slot("slayer", 0.5), new Slot("dungeons", 0.5))),
            new Preset("galatea", "Hunting & Foraging", "lead", "Hunt shards and chop on Galatea (Starlyn's contest when it runs).",
                    List.of("hunting_shards", "starlyn"),
                    List.of(new Slot("hunting", 0.5), new Slot("foraging", 0.5))));

    private RoutinePreset() {}

    public static Preset current() {
        for (Preset p : ALL) if (p.id().equals(Config.I.routinePreset)) return p;
        return ALL.get(0);
    }

    // todays tasks for the chosen routine in its order (smart day keeps the importance order of all dailies)
    public static List<Routine.Entry> tasks() {
        Preset p = current();
        List<Routine.Entry> all = Routine.today();
        List<Routine.Entry> out = new ArrayList<>();
        if (p.tasks() == null) {
            for (Routine.Entry e : all) if (e.def().period() == dev.zmuzy.sbc.core.SbTime.Period.DAILY) out.add(e);
            return out;
        }
        for (String id : p.tasks()) for (Routine.Entry e : all) if (e.def().id().equals(id)) out.add(e);
        return out;
    }

    // grind blocks after the tasks, sized to your hours minus the tasks still open
    public static List<MaxPlan.Block> blocks() {
        Preset p = current();
        List<MaxPlan.Block> out = new ArrayList<>();
        int taskMin = 0;
        for (Routine.Entry e : tasks()) if (!e.complete()) taskMin += e.def().minutes();
        int free = Math.max(20, (int) Math.round(Config.I.planHours * 60) - taskMin);
        for (Slot s : p.slots()) {
            int minutes = (int) Math.max(10, Math.round(free * s.share()));
            switch (s.what()) {
                case "plan" -> {
                    for (MaxPlan.Block b : MaxPlan.get().today)
                        if (!b.island().isEmpty()) out.add(new MaxPlan.Block(b.name(), b.icon(), Math.max(10, Math.round(minutes * b.minutes()
                                / (float) Math.max(1, MaxPlan.get().today.stream().filter(x -> !x.island().isEmpty()).mapToInt(MaxPlan.Block::minutes).sum()))),
                                b.island(), b.node(), b.why(), ""));
                }
                case "money1", "money2" -> {
                    List<MoneyMethods.Rated> r = MoneyMethods.ranked();
                    int i = s.what().equals("money1") ? 0 : 1;
                    if (i < r.size()) {
                        MoneyMethods.Method m = r.get(i).m();
                        out.add(new MaxPlan.Block(m.name() + " (" + r.get(i).tier().label() + ")", m.icon(), minutes, m.island(), m.node(), m.how(), ""));
                    }
                }
                case "level1" -> {
                    List<Planner.Scored> r = Planner.rank(0);
                    if (!r.isEmpty()) out.add(block(r.get(0).a(), minutes));
                }
                default -> {
                    Planner.Activity a = MaxPlan.activity(s.what());
                    if (a != null) out.add(block(a, minutes));
                }
            }
        }
        return out;
    }

    private static MaxPlan.Block block(Planner.Activity a, int minutes) {
        return new MaxPlan.Block(a.name(), a.icon(), minutes, a.island(), a.node(), a.how(), "");
    }

    // minutes the whole routine takes: open tasks + blocks
    public static int totalMinutes() {
        int m = 0;
        for (Routine.Entry e : tasks()) if (!e.complete()) m += e.def().minutes();
        for (MaxPlan.Block b : blocks()) m += b.minutes();
        return m;
    }
}
