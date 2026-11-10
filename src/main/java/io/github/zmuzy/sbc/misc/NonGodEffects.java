// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.misc;

import io.github.zmuzy.sbc.areas.FishHooks;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni data/effect/EffectApi.kt, NonGodPotEffect.kt + NonGodPotEffectDisplay.kt: effects not in the
// god potion and the time each has, from chat, the tab + the effects menu
public final class NonGodEffects {
    public static final String ID = "non_god_effects";
    private static final Patterns.P TAB_EFFECT = Patterns.p("SH.api.effects.tab.effect"), REPELLENT = Patterns.p("SH.api.effects.tab.repellent"),
            COUNT = Patterns.p("SH.api.effects.footer.effects-count"), REMAINING = Patterns.p("SH.api.effects.inventory.effect-remaining"), HOT_CHOCOLATE = Patterns.p("SH.api.effects.chat.hot-chocolate-mixin-consume"),
            HINT = Patterns.p("SH.api.effects.chat.effects-hint");
    private static final java.util.regex.Pattern MENU = java.util.regex.Pattern.compile("(?:\\(\\d+/\\d+\\) )?Active Effects");

    private enum Change {ADD, SET}

    // from skyhanni NonGodPotEffect: tab name, menu name if different, what chat gives it
    record Effect(String key, String tab, String colour, boolean mixin, long gainMs, Change change, boolean salt, boolean removable) {
        Patterns.P tabPattern() {
            return Patterns.p("SH.misc.nongodpot.effects.tabname-" + key);
        }

        Patterns.P menuPattern() {
            return Patterns.p("SH.misc.nongodpot.effects.inventoryitemname." + key);
        }

        Patterns.P gained() {
            return gainMs == 0 ? null : Patterns.p("SH.misc.nongodpot.effects.gained." + key + ".colorless");
        }

        Patterns.P removed() {
            return removable ? Patterns.p("SH.misc.nongodpot.effects.removed." + key + ".colorless") : null;
        }
    }

    private static Effect e(String key, String tab, String colour) {
        return new Effect(key, tab, colour, false, 0, null, false, false);
    }

    private static Effect mix(String key, String tab, String colour) {
        return new Effect(key, tab, colour, true, 0, null, false, false);
    }

    private static Effect set(String key, String tab, String colour, long ms) {
        return new Effect(key, tab, colour, false, ms, Change.SET, false, false);
    }

    private static Effect salt(String key, String tab) {
        return new Effect(key, tab, "§5", false, 0, null, true, false);
    }

    private static final long MIN = 60_000, HOUR = 3_600_000;
    static final List<Effect> EFFECTS = List.of(
            new Effect("smoldering", "Smoldering Polarization I", "§a", false, HOUR, Change.ADD, false, false),
            set("glowy", "Mushed Glowy Tonic I", "§2", HOUR),
            set("wisp", "Wisp's Ice-Flavored Water I", "§b", 5 * MIN),
            new Effect("goblin", "King's Scent I", "§2", false, 20 * MIN, Change.SET, false, true),
            e("invisibility", "Invisibility I", "§8"),
            mix("rev", "Zombie Brain Mixin", "§c"),
            mix("tara", "Spider Egg Mixin", "§6"),
            mix("sven", "Wolf Fur Mixin", "§b"),
            mix("void", "End Portal Fumes", "§6"),
            mix("blaze", "Gabagoey", "§f"),
            mix("glowing-mush", "Glowing Mush Mixin", "§2"),
            mix("hot-chocolate", "Hot Chocolate Mixin I", "§6"),
            mix("mason-jar", "Celestial Mason Jar I", "§d"),
            mix("hotspot-tonic", "Hotspot Tonic", "§2"),
            mix("melon-juice", "Melon Juice Mixin I", "§f"),
            mix("deep-terror", "Deepterror", "§4"),
            set("great-spook", "Great Spook I", "§f", 24 * HOUR),
            set("douce-pluie-de-stinky-cheese", "Douce Pluie de Stinky Cheese I", "§e", HOUR),
            set("harvest-harbinger", "Harvest Harbinger V", "§6", 25 * MIN),
            set("pest-repellent", "Pest Repellent I", "§6", HOUR),
            set("pest-repellent-max", "Pest Repellent II", "§6", HOUR),
            e("curse-of-greed", "Curse of Greed I", "§4"),
            e("cold-resistance-4", "Cold Resistance IV", "§b"),
            e("powder-pumpkin", "Powder Pumpkin I", "§f"),
            e("filet-o-fortune", "Filet O' Fortune I", "§f"),
            e("chilled-pristine-potato", "Chilled Pristine Potato I", "§f"),
            e("fiesta-flask", "Fiesta Flask I", "§6"),
            e("scatha-in-a-bottle", "Scatha-in-a-Bottle I", "§d"),
            salt("lushlilac-bonbon", "Lushlilac Bonbon"),
            salt("prime-lushlilac-bonbon", "Prime Lushlilac Bonbon"),
            salt("exalted-lushlilac-bonbon", "Exalted Lushlilac Bonbon"),
            salt("oceandy", "Oceandy"),
            salt("candycomb", "Candycomb"),
            salt("brine-tonic", "Brine Tonic"));

    // effect -> when it ends
    private static final Map<Effect, Long> ENDS = new HashMap<>();
    // from skyhanni setRecently: a menu read beats a chat line that comes within 5 sec
    private static final Map<Effect, Long> SET_AT = new HashMap<>();
    private static int total;
    private static long lastCheck;
    private static long lastTabRead;
    private static String profile = "";

    private NonGodEffects() {}

    // from skyhanni TimeUtils.getDuration for "1d 5h" and the menus 105:01:34
    static long duration(String text) {
        String t = text.trim();
        if (t.contains(":")) {
            long ms = 0;
            for (String part : t.split(":")) ms = ms * 60 + Long.parseLong(part);
            return ms * 1000;
        }
        return io.github.zmuzy.sbc.core.CookieBuff.parse(t);
    }

    // from skyhanni clampUsingPartialSet: hypixel cuts the smaller units so 20m means 20m to 21m. what we counted
    // stays when it fits inside that
    static long clamp(long existing, long duration) {
        if (existing == 0) return duration;
        long upper;
        if (duration % MIN != 0) upper = duration + 1000;
        else if (duration % HOUR != 0) upper = duration + MIN;
        else if (duration % (24 * HOUR) != 0) upper = duration + HOUR;
        else if (duration % (7 * 24 * HOUR) != 0) upper = duration + 24 * HOUR;
        else if (duration > 0) upper = duration + 7 * 24 * HOUR;
        else upper = duration + 1000;
        if (existing < duration) return duration;
        if (existing >= upper) return upper - 1000;
        return existing;
    }

    // skyhanni godPotExpiry, as far as this session was told
    private static long godPotEnd;

    // from skyhanni CFBlockOpen.hotChocolateMixinRequirement: how long each still lasts, 0 = not known active
    public static long hotChocolateLeft() {
        long now = System.currentTimeMillis();
        for (Effect e : EFFECTS) if (e.key().equals("hot-chocolate")) return left(e, now);
        return 0;
    }

    public static long godPotLeft() {
        return Math.max(0, godPotEnd - System.currentTimeMillis());
    }

    // from skyhanni EffectApi onWidgetUpdate + onChat, the god potion part
    static void readGodPot(List<String> tab, long now) {
        for (String raw : tab) {
            Matcher m = Patterns.get("SH.api.effects.tab.god-pot").matcher(GameState.strip(raw).trim());
            if (!m.matches()) continue;
            long d = duration(m.group("time"));
            if (d >= 0) godPotEnd = now + d;
            return;
        }
    }

    static void godPotChat(String clean, long now) {
        Matcher m = Patterns.get("SH.api.effects.chat.god-pot-consume").matcher(clean);
        if (m.matches()) godPotEnd = Math.max(godPotEnd, now) + Math.max(0, duration(m.group("time")));
    }

    private static long left(Effect e, long now) {
        Long end = ENDS.get(e);
        return end == null ? 0 : Math.max(0, end - now);
    }

    private static void partial(Effect e, long duration, long now) {
        ENDS.put(e, now + clamp(left(e, now), duration));
    }

    // from skyhanni readNonGodPotEffects + the salts and pests widgets. pure on its lines, for the test
    static void readTab(List<String> lines, long now) {
        for (String raw : lines) {
            String l = GameState.strip(raw);
            if (l.isBlank()) continue;
            Matcher m = TAB_EFFECT.matcher(l);
            if ("dhms0123456789 ".indexOf(l.charAt(l.length() - 1)) >= 0 && m.matches()) {
                for (Effect e : EFFECTS) {
                    if (!e.tabPattern().matcher(m.group("effect").trim()).matches()) continue;
                    long d = duration(m.group("time"));
                    if (d >= 0) partial(e, d, now);
                    break;
                }
                continue;
            }
            // skyhannis tab.salt for the salts it names. its pattern backtracks badly on a long line without a colon, so
            // look up the names first
            int colon = l.indexOf(':');
            if (colon > 0) {
                String name = l.substring(0, colon).trim();
                for (Effect e : EFFECTS) {
                    if (!e.salt() || !e.tab().equals(name)) continue;
                    long d = duration(l.substring(colon + 1));
                    if (d >= 0) partial(e, d, now);
                    break;
                }
            }
            m = REPELLENT.matcher(l);
            if (m.matches() && m.group("time") != null && m.group("tier") != null) {
                String tier = m.group("tier").toUpperCase(java.util.Locale.ROOT);
                String key = tier.equals("MAX") ? "pest-repellent-max" : tier.equals("REGULAR") ? "pest-repellent" : "";
                for (Effect e : EFFECTS) if (e.key().equals(key)) partial(e, Math.max(0, duration(m.group("time"))), now);
            }
        }
    }

    static int footerCount(String footer) {
        for (String l : footer.split("\n")) {
            Matcher m = COUNT.matcher(GameState.strip(l).trim());
            if (m.matches()) return Integer.parseInt(m.group("count"));
        }
        return 0;
    }

    // from skyhanni onChat
    static void chat(String clean, long now) {
        godPotChat(clean, now);
        Matcher m = HOT_CHOCOLATE.matcher(clean);
        if (m.matches()) for (Effect e : EFFECTS) if (e.key().equals("hot-chocolate") && !recentlySet(e, now)) ENDS.put(e, now + left(e, now) + Math.max(0, duration(m.group("time"))));
        String msg = HINT.matcher(clean).replaceAll("").trim();
        for (Effect e : EFFECTS) {
            if (e.removed() != null && e.removed().matcher(msg).matches()) {
                ENDS.remove(e);
                return;
            }
            if (e.gained() == null || !e.gained().matcher(msg).matches()) continue;
            if (e.change() == Change.SET) {
                ENDS.put(e, now + e.gainMs());
                SET_AT.put(e, now);
            } else if (!recentlySet(e, now)) ENDS.put(e, now + left(e, now) + e.gainMs());
            return;
        }
    }

    private static boolean recentlySet(Effect e, long now) {
        Long at = SET_AT.get(e);
        return at != null && now - at < 5000;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String clean = line.plain();
        if (clean.trim().equals("You cleared all of your active effects!")) {
            ENDS.clear();
            return ChatBus.PASS;
        }
        chat(clean, System.currentTimeMillis());
        return ChatBus.PASS;
    }

    private static void menu(MenuView v) {
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            for (Effect e : EFFECTS) {
                if (!e.menuPattern().matcher(it.clean).matches()) continue;
                Matcher m = it.firstClean(REMAINING.pattern());
                if (m != null) {
                    ENDS.put(e, now + duration(m.group("time")));
                    SET_AT.put(e, now);
                }
                break;
            }
        }
    }

    private static boolean enabledHere() {
        return GameState.inSkyBlock && !"CATACOMBS".equals(GameState.island) && !"KUUDRA".equals(GameState.island);
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        if (!GameState.profileName.equals(profile)) {
            profile = GameState.profileName;
            ENDS.clear();
            total = 0;
        }
        ENDS.values().removeIf(end -> end < now);
        if (now - lastTabRead >= 2000) {
            lastTabRead = now;
            readTab(GameState.tab, now);
            readGodPot(GameState.tab, now);
            if (GameState.tabFooter.contains("Active Effects")) {
                readTab(List.of(GameState.tabFooter.split("\n")), now);
                total = footerCount(GameState.tabFooter);
            }
        }
        Feature f = Features.get(ID);
        boolean warn = f.option("non_god_effects.expireWarning").bool(), sound = f.option("non_god_effects.expireSound").bool();
        if (!warn && !sound) return;
        long at = f.option("non_god_effects.expireWarnTime").integer();
        for (Effect e : EFFECTS) {
            if (left(e, now) / 1000 != at) continue;
            if (warn) {
                FishHooks.title(e.colour() + e.tab());
                SmallMisc.say(e.colour() + e.tab() + " §eis running out soon!");
            }
            if (sound) FishHooks.pling();
        }
    }

    private static String timeColour(long ms) {
        return ms < 5 * MIN ? "§c" : ms < 30 * MIN ? "§e" : "§a";
    }

    private static RichCard.Content card(List<String[]> rows, int unknown) {
        List<RichCard.Line> out = new ArrayList<>();
        for (String[] r : rows) out.add(RichCard.Line.row(r[0], r[1]).raw());
        if (unknown > 0) out.add(RichCard.Line.row("Open /effects", "§e" + unknown + " more").raw());
        return new RichCard.Content("", out);
    }

    private static final HudElement CARD = new RichCard(ID, "Other effects", "Misc", Zone.TOP_RIGHT, 31, ID) {
        private List<String[]> effectRows() {
            long now = System.currentTimeMillis();
            boolean mixins = Features.get(ID).option("non_god_effects.showMixins").bool();
            List<Object[]> sorted = new ArrayList<>();
            for (Effect e : EFFECTS) {
                long left = left(e, now);
                if (left <= 0 || e.key().equals("invisibility") || (e.mixin() && !mixins)) continue;
                sorted.add(new Object[]{e, left});
            }
            sorted.sort((a, b) -> Long.compare((Long) a[1], (Long) b[1]));
            List<String[]> out = new ArrayList<>();
            for (Object[] o : sorted) out.add(new String[]{((Effect) o[0]).colour() + ((Effect) o[0]).tab(), timeColour((Long) o[1]) + SmallMisc.time((Long) o[1])});
            return out;
        }

        @Override public boolean visible() {
            if (!enabledHere() || "THE_RIFT".equals(GameState.island)) return false;
            return !effectRows().isEmpty() || total > ENDS.size();
        }

        @Override protected Content live() {
            return card(effectRows(), Math.max(0, total - ENDS.size()));
        }

        @Override protected Content sample() {
            return card(List.of(new String[]{"§6Harvest Harbinger V", "§e14m 20s"}, new String[]{"§2Mushed Glowy Tonic I", "§a43m"}), 2);
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            for (String[] r : effectRows()) out.add(new Row(GameState.strip(r[0]), GameState.strip(r[1]), Theme.current().text()));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Harvest Harbinger V", "14m 20s", Theme.current().text()));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Misc", "Other potion effects", "The effects that are not part of the God Potion, each with its time, and a warning before one runs out. Open /effects once to get them all.", "SkyHanni: Non-God Pot Effects")
                .when(NonGodEffects::enabledHere).tick(NonGodEffects::tick)
                .option(Option.bool("non_god_effects.showMixins", "Show God Potion mixins", "", false))
                .option(Option.bool("non_god_effects.expireWarning", "Warn before one runs out", "A title and a chat line.", true))
                .option(Option.bool("non_god_effects.expireSound", "With a sound", "", false))
                .option(Option.integer("non_god_effects.expireWarnTime", "Warn this long before (seconds)", "", 30, 5, 600)));
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, NonGodEffects::onChat);
        MenuMarks.add(ID, MENU, NonGodEffects::menu);
        HudLayout.add(CARD);
    }
}
