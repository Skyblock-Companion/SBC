// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
//? if >=26.3 {
import net.minecraft.world.entity.monster.Enderman;
//?} else {
/*import net.minecraft.world.entity.monster.EnderMan;
*///?}
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// slayer quest as the helpers ask for it. from skyhanni SlayerApi + SlayerType
// quest + state from the sidebar, which slayer goes with this area, are you in the right one
// card and kill timer keep their own reading in nav/Slayers
public final class SlayerApi {
    public enum State {GRINDING, BOSS_FIGHT, FAILED, SLAIN, COCOONED, NONE}

    // from skyhanni SlayerType
    public enum Type {
        REVENANT("Revenant Horror", "REVENANT", Zombie.class, Set.of("Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant"), "Atoned Horror"),
        TARANTULA("Tarantula Broodfather", "TARANTULA", Spider.class, Set.of("Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula", "Primordial Jockey", "Primordial Viscount"), "Conjoined Brood"),
        SVEN("Sven Packmaster", "SVEN", Wolf.class, Set.of("Pack Enforcer", "Sven Follower", "Sven Alpha")),
        //? if >=26.3 {
        VOID("Voidgloom Seraph", "VOID", Enderman.class, Set.of("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")),
        //?} else {
        /*VOID("Voidgloom Seraph", "VOID", EnderMan.class, Set.of("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")),
        *///?}
        INFERNO("Inferno Demonlord", "INFERNO", Blaze.class, Set.of("Flare Demon", "Kindleheart Demon", "Burningsoul Demon")),
        VAMPIRE("Bloodfiend", "VAMPIRE", Zombie.class, Set.of());

        public final String displayName, dataKey;
        public final Class<?> mobClass;
        public final Set<String> minibosses;
        private final String[] other;

        Type(String displayName, String dataKey, Class<?> mobClass, Set<String> minibosses, String... other) {
            this.displayName = displayName;
            this.dataKey = dataKey;
            this.mobClass = mobClass;
            this.minibosses = minibosses;
            this.other = other;
        }

        public static Type byName(String name) {
            for (Type t : values()) {
                if (name.contains(t.displayName)) return t;
                for (String o : t.other) if (name.contains(o)) return t;
            }
            return null;
        }
    }

    private static final Patterns.P START = Patterns.p("SH.slayer.api.quest.start"), COMPLETE = Patterns.p("SH.slayer.api.quest.complete"), FAILED = Patterns.p("SH.slayer.api.quest.failed"),
            COCOON = Patterns.p("SH.slayer.api.cocooned.colorless");
    // cocoons take 5 sec to burst + 1 sec for the sidebar to catch up
    private static final long GRACE_COCOON = 6000;

    private static String category = "", progress = "", stateRaw;
    private static int tier;
    private static Type active, areaType;
    private static State state = State.NONE;
    private static long questStart, cocoonAt;
    private static final List<Consumer<State>> STATE = new ArrayList<>();
    private static final List<BiConsumer<String, String>> PROGRESS = new ArrayList<>();
    private static final List<Runnable> QUEST_DONE = new ArrayList<>();
    private static final List<Consumer<Entity>> ATTACK = new ArrayList<>();

    private SlayerApi() {}

    public static void onState(Consumer<State> l) {
        STATE.add(l);
    }

    public static void onProgress(BiConsumer<String, String> l) {
        PROGRESS.add(l);
    }

    public static void onQuestComplete(Runnable l) {
        QUEST_DONE.add(l);
    }

    // skyhanni EntityClickEvent, attack only
    public static void onAttack(Consumer<Entity> l) {
        ATTACK.add(l);
    }

    public static Type active() {
        return active;
    }

    public static Type areaType() {
        return areaType;
    }

    public static int tier() {
        return tier;
    }

    // quest line from the sidebar like "Revenant Horror IV", empty for none
    public static String category() {
        return category;
    }

    // latestCategory without the tier, thats what the rng meters are called
    public static String slayerName() {
        int s = category.lastIndexOf(' ');
        return s < 0 ? category : category.substring(0, s);
    }

    public static State state() {
        return state;
    }

    public static boolean inBossFight() {
        return state == State.BOSS_FIGHT;
    }

    public static boolean hasQuest() {
        return !category.isEmpty();
    }

    public static boolean inCorrectArea() {
        return areaType != null && areaType == active;
    }

    public static boolean inAnyArea() {
        return areaType != null;
    }

    public static long questStart() {
        return questStart;
    }

    // skyhanni checkTypeForCurrentArea with the trackers 3 settings at default: graveyard counts, nest doesnt,
    // end with no area does
    static Type areaType(String island, String area) {
        return switch (area) {
            case "Graveyard" -> "HUB".equals(island) ? Type.REVENANT : null;
            case "Revenant Cave", "Crypts" -> Type.REVENANT;
            case "Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert" -> Type.TARANTULA;
            case "Ruins", "Howling Cave", "Soul Cave", "Spirit Cave" -> Type.SVEN;
            case "Void Sepulture", "Zealot Bruiser Hideout" -> Type.VOID;
            case "", "no_area" -> "THE_END".equals(island) ? Type.VOID : null;
            case "Stronghold", "The Wasteland", "Smoldering Tomb" -> Type.INFERNO;
            case "Stillgore Château", "Oubliette" -> Type.VAMPIRE;
            default -> null;
        };
    }

    // from skyhanni detectState
    static State detect(String line) {
        if (line.contains("Combat") || line.contains("Kills")) return State.GRINDING;
        if (line.equals("Slay the boss!")) return State.BOSS_FIGHT;
        if (line.equals("Boss slain!")) return State.SLAIN;
        return State.NONE;
    }

    private static void set(State s) {
        if (s == state) return;
        state = s;
        for (Consumer<State> l : STATE) l.accept(s);
    }

    private static void tell(State s) {
        for (Consumer<State> l : STATE) l.accept(s);
    }

    // skyhanni updateSlayerState: from "Slayer Quest" down the sidebar
    private static void update() {
        List<String> lines = new ArrayList<>();
        boolean in = false;
        for (String l : GameState.scoreboard) {
            String t = l.trim();
            if (t.equals("Slayer Quest")) in = true;
            if (in) lines.add(t);
        }
        String cat = "", prog = "no slayer";
        Type type = null;
        int newTier = 0;
        for (int i = 0; i < lines.size(); i++) {
            type = Type.byName(lines.get(i));
            if (type == null) continue;
            cat = lines.get(i);
            prog = i + 1 < lines.size() ? lines.get(i + 1) : "no slayer";
            String last = cat.substring(cat.lastIndexOf(' ') + 1);
            newTier = last.matches("[IVX]+") ? GameState.roman(last) : 0;
            break;
        }
        category = cat;
        tier = newTier;
        active = type;
        if (!prog.equals(progress)) {
            String old = progress;
            progress = prog;
            for (BiConsumer<String, String> l : PROGRESS) l.accept(old, prog);
        }
        if (!prog.equals(stateRaw)) {
            stateRaw = prog;
            State next = detect(prog);
            boolean cocooned = state == State.COCOONED && System.currentTimeMillis() - cocoonAt <= GRACE_COCOON;
            if (cocooned && (next == State.NONE || next == State.SLAIN)) next = State.COCOONED;
            if ((state == State.BOSS_FIGHT || state == State.COCOONED) && next == State.GRINDING) tell(State.SLAIN);
            if (state == State.GRINDING && next == State.SLAIN) tell(State.BOSS_FIGHT);
            set(next);
        }
        areaType = areaType(GameState.island, GameState.area);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (mc.player.tickCount % 5 == 0) update();
    }

    // cocoons dont follow you to another world
    private static void left() {
        if (state != State.COCOONED) return;
        stateRaw = null;
        set(State.NONE);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String m = line.plain();
        if (START.matcher(m).matches()) questStart = System.currentTimeMillis();
        else if (COMPLETE.matcher(m).matches()) {
            for (Runnable r : QUEST_DONE) r.run();
        } else if (FAILED.matcher(m).matches()) {
            stateRaw = "no slayer";
            set(State.FAILED);
        } else if (COCOON.matcher(m).matches()) {
            stateRaw = "cocooned";
            cocoonAt = System.currentTimeMillis();
            set(State.COCOONED);
        }
        return ChatBus.PASS;
    }

    static void register() {
        Feature f = Features.add(new Feature("slayer_quest_state", "Slayer", "Slayer quest", "Knows your slayer quest, its state and which slayer this area is for; the slayer helpers ask it.", "SkyHanni: Slayer API")
                .alwaysOn().when(Combat::sb).tick(SlayerApi::tick).left(SlayerApi::left));
        ChatBus.register(ChatBus.OBSERVE + 13, "slayerapi", f, SlayerApi::onChat);
        Safe.attackEntity("SlayerApi.attackEntity", (player, world, hand, entity, hit) -> {
            if (world.isClientSide()) for (Consumer<Entity> l : ATTACK) l.accept(entity);
            return InteractionResult.PASS;
        });
    }
}
