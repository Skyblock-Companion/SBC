// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.events;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.sacks.SackApi;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// witch ingredients card: what your kills put in the sacks during the event, no need to open her sack
public final class WitchIngredients {
    public static final String ID = "witch_ingredients";
    // what the [Sacks] lines added since the game started
    static final Map<String, Long> GAINED = new LinkedHashMap<>();

    private WitchIngredients() {}

    static boolean active() {
        return !YearOfTheWitch.timeLeft(System.currentTimeMillis()).isEmpty();
    }

    static long gainedAt;

    static void onSack(List<SackApi.SackChange> changes) {
        for (SackApi.SackChange c : changes) {
            if (c.delta() <= 0) continue;
            for (YearOfTheWitch.Ingredient i : YearOfTheWitch.INGREDIENTS) if (i.id().equals(c.internalName())) {
                GAINED.merge(i.id(), (long) c.delta(), Long::sum);
                gainedAt = System.currentTimeMillis();
            }
        }
    }

    // combat xp on the action bar or an ingredient coming in, in the last few seconds
    static boolean fighting(long now, String skill, long skillAt, long gainedAt, long window) {
        return ("Combat".equals(skill) && now - skillAt < window) || now - gainedAt < window;
    }

    private static boolean shownNow() {
        if (!opt("witch_ingredients.only_fighting")) return true;
        var f = Features.get(ID);
        var o = f == null ? null : f.option("witch_ingredients.seconds");
        long window = (o == null ? 30 : o.integer()) * 1000L;
        return fighting(System.currentTimeMillis(), io.github.zmuzy.sbc.core.Vitals.skill, io.github.zmuzy.sbc.core.Vitals.skillAt, gainedAt, window);
    }

    // the last word is enough beside its picture, the two dusts keep both
    static String brief(String name) {
        if (name.endsWith(" Dust")) return name;
        int sp = name.lastIndexOf(' ');
        return sp < 0 ? name : name.substring(sp + 1);
    }

    record Count(YearOfTheWitch.Ingredient ingredient, Long have, long gained) {}

    // what to show: everything you hold or got this session, the newest gains first
    static List<Count> counts(Map<String, Long> gained, java.util.function.Function<YearOfTheWitch.Ingredient, Long> have, boolean all) {
        List<Count> out = new ArrayList<>();
        for (YearOfTheWitch.Ingredient i : YearOfTheWitch.INGREDIENTS) {
            Long h = have.apply(i);
            long g = gained.getOrDefault(i.id(), 0L);
            if (all || g > 0 || (h != null && h > 0)) out.add(new Count(i, h, g));
        }
        out.sort((a, b) -> Long.compare(b.gained(), a.gained()));
        return out;
    }

    private static Long have(YearOfTheWitch.Ingredient i) {
        YearOfTheWitch.Need n = new YearOfTheWitch.Need(i.name(), 0, i);
        long h = YearOfTheWitch.have(n);
        return YearOfTheWitch.known(n) || h > 0 ? h : null;
    }

    private static String value(Count c) {
        String own = c.have() == null ? "§e?" : "§f" + String.format(java.util.Locale.ROOT, "%,d", c.have());
        return c.gained() > 0 ? own + " §a+" + String.format(java.util.Locale.ROOT, "%,d", c.gained()) : own;
    }

    static void lines(EventSheet s, List<Count> counts) {
        long total = 0;
        boolean unknown = false;
        for (Count c : counts) {
            total += c.gained();
            if (c.have() == null) unknown = true;
        }
        s.right = total > 0 ? "§a+" + String.format(java.util.Locale.ROOT, "%,d", total) : "";
        if (counts.isEmpty()) {
            s.line("Kill mobs for ingredients", "", Modern.DIM);
            return;
        }
        for (Count c : counts) {
            s.line(c.ingredient().name(), value(c), c.gained() > 0 ? Modern.GOOD : Modern.INK)
                    .icon(io.github.zmuzy.sbc.core.ItemIcons.of(c.ingredient().id())).brief(brief(c.ingredient().name()));
        }
        if (unknown) s.line("Open your Witch's Sack once", "", Modern.WARN).brief("Open Witch's Sack");
    }

    static final Map<String, Long> SAMPLE = Map.of("FESTERING_FLY", 37L, "FUNNY_BONE", 12L, "SPELL_SOOT", 4L);

    private static final HudElement CARD = new EventSheet.Card(ID, "Witch ingredients", "Events", Zone.TOP_LEFT, 52, ID) {
        @Override
        public boolean visible() {
            return (GameState.inSkyBlock || SbcMod.DEV) && active() && shownNow();
        }

        @Override
        protected void live(EventSheet s) {
            lines(s, counts(GAINED, WitchIngredients::have, opt("witch_ingredients.all")));
        }

        @Override
        protected void sample(EventSheet s) {
            Map<String, Long> held = Map.of("FESTERING_FLY", 279L, "FUNNY_BONE", 75L, "SPELL_SOOT", 279L, "SPIDER_EGG", 95L, "BRITTLE_POWDER", 27L, "VOID_OOZE", 2L);
            lines(s, counts(SAMPLE, i -> held.getOrDefault(i.id(), 0L), false));
        }

        @Override
        protected List<Row> plainRows() {
            List<Row> out = new ArrayList<>();
            for (Count c : counts(GAINED, WitchIngredients::have, opt("witch_ingredients.all"))) out.add(new Row(c.ingredient().name(), value(c), 0));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Festering Fly", "279 +37", 0));
        }
    };

    private static boolean opt(String key) {
        var f = Features.get(ID);
        var o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    static void register() {
        // a session is one connection: logging in again starts the + counts from nothing, lobbies and islands dont
        io.github.zmuzy.sbc.core.Safe.join("WitchIngredients.newSession", (handler, sender, client) -> GAINED.clear());
        Features.add(new Feature(ID, "Events", "Witch ingredients", "During the Year of the Witch, a card with the ingredients in your sacks and what your kills added this session, so you don't have to open the Witch's Sack.", "")
                .when(() -> (GameState.inSkyBlock || SbcMod.DEV) && active())
                .option(Option.bool("witch_ingredients.all", "Every ingredient", "Also the ones you have none of.", false))
                .option(Option.bool("witch_ingredients.only_fighting", "Only while you fight", "Shows after Combat XP or an ingredient, and goes when there was none for the seconds below.", true))
                .option(Option.integer("witch_ingredients.seconds", "Seconds after the last fight", "", 30, 10, 300)));
        SackApi.onChange.add(WitchIngredients::onSack);
        HudLayout.add(CARD);
    }
}
