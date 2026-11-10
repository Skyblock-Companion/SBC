// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.panels.EquipmentCache;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the 3 slayer cards. from skyhanni SlayerRngMeterDisplay + RemainingSlayerKills, skyblocker SlayerBossBar as a card
// skipped their click to open, cards take no clicks
// skipped the pet term in remaining kills, every pets "scaling" in Slayer.json is 0 so it never changes anything lol
public final class SlayerCards {
    public static final String RNG = "slayer_rng_display", KILLS = "slayer_remaining_kills", BAR = "slayer_bossbar";
    private static final Patterns.P UPDATE = Patterns.p("SH.slayer.rngmeter.update"), CHANGED = Patterns.p("SH.slayer.rngmeter.changeditem"), BOOK = Patterns.p("SH.slayer.rngmeter.book.format"),
            KILL_PROGRESS = Patterns.p("SH.slayer.remaining-kills.progress"), COMBO_OVER = Patterns.p("SH.slayer.remaining-kills.combo.expired"), COMBO_WISDOM = Patterns.p("SH.slayer.remaining-kills.kill-combat-wisdom");
    // from skyblocker SlayerBossBar.HEALTH_PATTERN
    private static final Pattern HEALTH = Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?[kM]?)(?=❤)");
    private static final int PINK = 0xFFFF55FF;

    private static long droppedAt;
    private static Double missing, maxXp;
    private static int comboWisdom;

    private SlayerCards() {}

    // rng meter

    private static CombatData.Rng storage() {
        return storage(SlayerApi.slayerName());
    }

    private static CombatData.Rng storage(String slayer) {
        return slayer.isEmpty() ? null : CombatData.get().rng.computeIfAbsent(slayer, k -> new CombatData.Rng());
    }

    private static JsonObject scores;

    // xp the meter needs for a drop, -1 = not known. neu's file, not the repo's: the repo has none and a null
    // here threw, which turned the whole menu reader off for the session
    static long score(String slayer, String id) {
        if (scores == null) {
            JsonObject all = io.muzy.sbc.core.data.DataFiles.object(io.muzy.sbc.core.data.DataFiles.Source.NEU, "rngscore.json");
            scores = all != null && all.has("slayer") ? all.getAsJsonObject("slayer") : new JsonObject();
        }
        if (id == null || !scores.has(slayer)) return -1;
        JsonObject items = scores.getAsJsonObject(slayer);
        return items.has(id) ? items.get(id).getAsLong() : -1;
    }

    // neu name for a book: ENCHANTMENT_SMITE_6 -> SMITE;6 like rngscore.json has it
    static String neuId(String id) {
        if (id == null) return null;
        if (id.startsWith("ENCHANTMENT_")) {
            int last = id.lastIndexOf('_');
            return id.substring(12, last) + ";" + id.substring(last + 1);
        }
        return id;
    }

    // what the slayer menu calls a book: "Duplex I"
    private static String idFromName(String clean) {
        String id = ItemRepo.idForName(clean);
        if (id != null) return neuId(id);
        int space = clean.lastIndexOf(' ');
        if (space <= 0) return null;
        String word = clean.substring(0, space).toUpperCase(Locale.ROOT).replace(' ', '_');
        return word + ";" + GameState.roman(clean.substring(space + 1));
    }

    // the menus say which slayer theyre for, no quest needed
    private static void setGoal(String slayer, String id, String name) {
        CombatData.Rng s = storage(slayer);
        if (s == null) return;
        // an old build saved a lore line as a slayer name
        CombatData.get().rng.keySet().removeIf(k -> k.startsWith("Your RNG Meter"));
        if (id == null) {
            if (s.itemGoal.isEmpty() && s.goalNeeded == -1) return;
            s.itemGoal = "";
            s.goalNeeded = -1;
        } else {
            long need = score(slayer, id);
            if (s.itemGoal.equals(name) && s.goalNeeded == need) return;
            s.itemGoal = name;
            s.goalNeeded = need;
        }
        CombatData.changed();
    }

    // "Selected Drop" and the line under it, null if the lore has none
    static String selectedDrop(List<String> lore) {
        int at = lore.indexOf("§7Selected Drop");
        if (at < 0 || at + 1 >= lore.size()) return null;
        String raw = lore.get(at + 1);
        Matcher m = BOOK.matcher(raw);
        return GameState.strip(m.matches() ? m.group("name") : raw).trim();
    }

    private static void setFromLore(String slayer, List<String> lore) {
        String clean = selectedDrop(lore);
        if (clean == null) return;
        if (clean.isEmpty() || clean.equals("None")) setGoal(slayer, null, "");
        else setGoal(slayer, idFromName(clean), clean);
    }

    // from skyhanni readRngMeterInventory. the top item says the chosen drop too, for when the drop is on another page
    private static void rngMenu(MenuView v) {
        String name = v.title.substring(0, Math.max(0, v.title.length() - " RNG Meter".length()));
        if (name.isEmpty()) return;
        for (int i = 0; i < v.top; i++) {
            if (v.items[i].empty() || !v.items[i].loreHas("§a§lSELECTED")) continue;
            setGoal(name, neuId(v.items[i].id), v.items[i].clean);
            return;
        }
        for (int i = 0; i < v.top; i++) {
            if (v.items[i].empty() || selectedDrop(v.items[i].lore) == null) continue;
            setFromLore(name, v.items[i].lore);
            return;
        }
        setGoal(name, null, "");
    }

    // every slayer's meter in one menu
    private static void metersMenu(MenuView v) {
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it.empty() || !it.clean.endsWith(" RNG Meter")) continue;
            setFromLore(it.clean.substring(0, it.clean.length() - " RNG Meter".length()), it.lore);
        }
    }

    // skyhanni readSlayerInventory: slot 35 has the meter of the slayer youre on. its name says which,
    // "Revenant Horror RNG Meter", the first lore line is just text
    private static void slayerMenu(MenuView v) {
        if (v.top <= 35 || v.items[35].empty() || !v.items[35].clean.endsWith(" RNG Meter")) return;
        String clean = v.items[35].clean;
        setFromLore(clean.substring(0, clean.length() - " RNG Meter".length()), v.items[35].lore);
    }

    private static ChatBus.Result rngChat(ChatBus.Line line) {
        String message = LegacyText.of(line.component());
        boolean hide = Combat.opt(RNG, "hide_chat");
        if (hide && SlayerApi.inCorrectArea() && CHANGED.matcher(message).matches()) return ChatBus.CANCEL;
        Matcher m = UPDATE.matcher(message);
        if (!m.matches()) return ChatBus.PASS;
        CombatData.Rng s = storage();
        if (s == null) return ChatBus.PASS;
        long now = (long) SlayerFeatures.shortNumber(m.group("exp"));
        long old = s.currentMeter;
        s.currentMeter = now;
        boolean block = false;
        if (old != -1) {
            boolean selected = !s.itemGoal.isEmpty() && !s.itemGoal.equals("?");
            if (!selected && Combat.opt(RNG, "warn_empty")) {
                Combat.say("§cNo Slayer RNG Meter item selected!");
                Combat.title("§cNo RNG Meter Item!", 40);
            }
            block = hide && selected;
            long diff = now - old;
            if (diff > 0) s.gainPerBoss = diff;
            else {
                s.currentMeter = 0;
                block = false;
                double raw = s.goalNeeded > 0 ? Math.min(1, old / (double) s.goalNeeded) : 0;
                Combat.say(String.format(Locale.ROOT, "§dRNG Meter §7dropped at §e%.1f%% §7XP (%,d/%,d)", raw * 100, old, s.goalNeeded));
                droppedAt = System.currentTimeMillis();
            }
        }
        CombatData.changed();
        return block ? ChatBus.CANCEL : ChatBus.PASS;
    }

    // skyhanni drawDisplay sum: bosses still needed, at least 1
    static int bossesLeft(long goal, long current, long gain) {
        long missing = goal - current + gain;
        return (int) Math.max(1, Math.ceil(missing / (double) gain));
    }

    private static RichCard.Content rngCard(String item, long current, long goal, long gain, boolean dropped) {
        List<RichCard.Line> out = new ArrayList<>();
        if ("?".equals(item)) out.add(RichCard.Line.row("Selected", "open the RNG Meter menu", Modern.BAD).raw());
        else if (item.isEmpty()) out.add(dropped ? RichCard.Line.row("RNG item", "dropped!", Modern.GOOD).raw() : RichCard.Line.row("Selected", "none", Modern.WARN).raw());
        else {
            out.add(RichCard.Line.row("Selected", item).raw());
            if (current < 0 || gain < 0) out.add(RichCard.Line.row("Bosses", "kill two to know", Modern.WARN).raw());
            else if (goal > 0) {
                out.add(RichCard.Line.row("Bosses", "§e" + String.format(Locale.ROOT, "%,d", bossesLeft(goal, current, gain))).raw());
                out.add(RichCard.Line.row("Stored XP", Combat.shortNumber(current) + "/" + Combat.shortNumber(goal)).meter(Math.min(1, current / (double) goal), PINK));
            } else out.add(RichCard.Line.row("Stored XP", Combat.shortNumber(current)).raw());
        }
        return new RichCard.Content("", out);
    }

    private static boolean rngShown() {
        if (!SlayerApi.hasQuest() || !SlayerApi.inCorrectArea()) return false;
        String cat = SlayerApi.category();
        return !cat.endsWith(" I") && !cat.endsWith(" II") && storage() != null;
    }

    // the same, as rows under the slayer card's own
    static List<HudElement.Row> rngRows(String item, long current, long goal, long gain, boolean dropped) {
        List<HudElement.Row> out = new ArrayList<>();
        if ("?".equals(item)) out.add(new HudElement.Row("RNG Meter", "open its menu once", Modern.BAD));
        else if (item.isEmpty()) out.add(dropped ? new HudElement.Row("RNG drop", "dropped!", Modern.GOOD) : new HudElement.Row("RNG Meter", "no drop chosen", Modern.WARN));
        else {
            out.add(new HudElement.Row("RNG drop", item, PINK));
            if (current < 0 || gain < 0) out.add(new HudElement.Row("RNG bosses", "kill two to know", Modern.WARN));
            else if (goal > 0) {
                out.add(new HudElement.Row("RNG bosses", String.format(Locale.ROOT, "%,d", bossesLeft(goal, current, gain)), Modern.WARN));
                out.add(HudElement.Row.meter("RNG XP", Combat.shortNumber(current) + "/" + Combat.shortNumber(goal), 0, Math.min(1, current / (double) goal), PINK));
            } else out.add(new HudElement.Row("RNG XP", Combat.shortNumber(current), 0));
        }
        return out;
    }

    public static List<HudElement.Row> rngRows(boolean demo) {
        if (demo) return rngRows("Beheaded Horror", 186_400, 310_925, 12_600, false);
        if (!Features.on(RNG) || !rngShown()) return List.of();
        CombatData.Rng s = storage();
        return rngRows(s.itemGoal, s.currentMeter, s.goalNeeded, s.gainPerBoss, System.currentTimeMillis() - droppedAt < 4000);
    }

    private static final HudElement RNG_CARD = new RichCard(RNG, "RNG Meter", "Slayer", Zone.TOP_LEFT, 41, RNG) {
        // the slayer card carries it, shows alone only when that card is off
        @Override public boolean visible() {
            HudElement slayer = io.muzy.sbc.areas.SlayerCombatCrimson.SLAYER;
            return rngShown() && !(slayer.active() && slayer.visible());
        }

        @Override protected Content live() {
            CombatData.Rng s = storage();
            if (s == null) return new Content("", List.of());
            return rngCard(s.itemGoal, s.currentMeter, s.goalNeeded, s.gainPerBoss, System.currentTimeMillis() - droppedAt < 4000);
        }

        @Override protected Content sample() {
            return rngCard("Beheaded Horror", 186_400, 310_925, 12_600, false);
        }

        @Override public List<Content> states() {
            return List.of(rngCard("?", -1, -1, -1, false), rngCard("", 0, -1, 12_600, true));
        }

        @Override protected List<Row> classicRows() {
            CombatData.Rng s = storage();
            if (s == null) return List.of();
            return List.of(new Row("RNG", s.itemGoal.isEmpty() ? "none" : s.itemGoal, Theme.current().accent()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("RNG", "Beheaded Horror", Theme.current().accent()), new Row("Bosses", "10", Theme.current().text()));
        }
    };

    // remaining kills

    // one mob a quest counts, from Slayer.json
    record KillMob(String name, int level, int maxHealth, double xp) {}

    // skyhanni getMobs: what lives here + this tiers minibosses
    private static List<KillMob> mobs() {
        JsonObject data = Repo.constant("Slayer.json");
        List<KillMob> out = new ArrayList<>();
        SlayerApi.Type area = SlayerApi.areaType(), active = SlayerApi.active();
        if (data == null || area == null) return out;
        JsonObject areas = data.getAsJsonObject("normal_mobs").getAsJsonObject(area.dataKey);
        if (areas != null && areas.has(GameState.area)) addMobs(out, areas.getAsJsonArray(GameState.area));
        if (active != null) {
            JsonObject minis = data.getAsJsonObject("mini_bosses").getAsJsonObject(active.dataKey);
            String tier = String.valueOf(SlayerApi.tier());
            if (minis != null && minis.has(tier)) addMobs(out, minis.getAsJsonArray(tier));
        }
        return out;
    }

    private static void addMobs(List<KillMob> out, com.google.gson.JsonArray list) {
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            out.add(new KillMob(o.get("name").getAsString(), o.get("level").getAsInt(), o.get("max_health").getAsInt(), o.get("xp").getAsDouble()));
        }
    }

    // skyhanni getCombatWisdomMultiplier: 1 + wisdom/100, with what weapon + gear add for this slayer
    private static double wisdomMultiplier(JsonObject data) {
        double wisdom = 1 + Vitals.stat("Combat Wisdom") + comboWisdom;
        var p = Minecraft.getInstance().player;
        SlayerApi.Type active = SlayerApi.active();
        if (p == null || active == null) return 1 + 0.01 * wisdom;
        JsonObject weapons = data.getAsJsonObject("weapons").getAsJsonObject(active.dataKey);
        String held = Gain.sbId(p.getMainHandItem());
        if (weapons != null && held != null && weapons.has(held)) {
            wisdom += weapons.get(held).getAsInt();
            int count = 0;
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                int level = MenuView.Item.of(p.getItemBySlot(s)).tag.getCompoundOrEmpty("enchantments").getIntOr("ultimate_habanero_tactics", 0);
                if (level == 4) count++;
                else if (level == 5) count += 2;
            }
            wisdom += data.get("habanero_wisdom_per_level").getAsDouble() * count;
        }
        JsonObject gear = data.getAsJsonObject("equipments").getAsJsonObject(active.dataKey);
        if (gear != null) {
            for (ItemStack st : EquipmentCache.current()) {
                String id = Gain.sbId(st);
                if (id != null && gear.has(id)) wisdom += gear.get(id).getAsInt();
            }
        }
        if (active == SlayerApi.Type.INFERNO && SlayerFeatures.smoldering()) wisdom += 10;
        return 1 + 0.01 * wisdom;
    }

    // skyhanni getMultiplicativeMultiplier with champion: mayor perks + the weapons enchant
    private static double perkMultiplier(JsonObject data) {
        double m = 1;
        JsonObject perks = data.getAsJsonObject("multiplicative_mayor_perks");
        for (var perk : Mayor.perks) {
            String key = perk.name().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (perks.has(key)) m *= perks.get(key).getAsDouble();
        }
        m *= data.get("arbitrary_multiplier").getAsDouble();
        var p = Minecraft.getInstance().player;
        if (p != null) {
            int champion = MenuView.Item.of(p.getMainHandItem()).tag.getCompoundOrEmpty("enchantments").getIntOr("champion", 0);
            var list = data.getAsJsonArray("champion");
            if (champion > 0 && champion <= list.size()) m *= 1 + list.get(champion - 1).getAsDouble();
        }
        return m;
    }

    // skyhanni getMobNames: how many of each, xp capped at 3/4 of the quest. most kills first
    static List<RichCard.Line> killLines(List<KillMob> mobs, double missing, double questXp, double multiplier, boolean overkill, boolean showXp, boolean showLevel, boolean showHealth) {
        record Row(KillMob mob, double xp, double times) {}
        List<Row> rows = new ArrayList<>();
        for (KillMob m : mobs) {
            double xp = Math.min(m.xp() * multiplier, questXp * 0.75);
            rows.add(new Row(m, xp, missing / xp));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.times()).reversed());
        List<RichCard.Line> out = new ArrayList<>();
        for (Row r : rows) {
            StringBuilder name = new StringBuilder();
            if (showLevel) name.append("§8[§7Lv").append(r.mob().level()).append("§8] ");
            name.append(r.mob().name().startsWith("§") ? r.mob().name() : "§c" + r.mob().name());
            StringBuilder value = new StringBuilder();
            if (overkill && r.xp() > questXp) value.append("§4Overkill ");
            if (showXp) value.append(String.format(Locale.ROOT, "§3%.1f xp ", r.xp()));
            if (showHealth) value.append("§a").append(Combat.shortNumber(r.mob().maxHealth())).append("§c❤ ");
            value.append("§e").append(String.format(Locale.ROOT, "%,d", (long) Math.ceil(r.times()))).append("x");
            out.add(RichCard.Line.row(name.toString(), value.toString()).key(GameState.strip(r.mob().name())).raw());
        }
        return out;
    }

    private static void killProgress(String old, String now) {
        Matcher m = KILL_PROGRESS.matcher(now);
        if (m.matches()) {
            double cur = SlayerFeatures.shortNumber(m.group("current"));
            maxXp = SlayerFeatures.shortNumber(m.group("max"));
            missing = maxXp - cur;
        } else {
            missing = null;
        }
    }

    private static ChatBus.Result killChat(ChatBus.Line line) {
        String plain = line.plain();
        if (COMBO_OVER.matcher(plain).matches()) comboWisdom = 0;
        Matcher m = COMBO_WISDOM.matcher(plain);
        if (m.matches()) comboWisdom += Integer.parseInt(m.group("wisdom"));
        return ChatBus.PASS;
    }

    private static final List<KillMob> DEMO_MOBS = List.of(new KillMob("Crypt Ghoul", 33, 2000, 49), new KillMob("§6Golden Ghoul", 65, 45000, 97), new KillMob("§c§lRevenant Sycophant", 114, 50000, 855));

    private static final HudElement KILLS_CARD = new RichCard(KILLS, "Remaining kills", "Slayer", Zone.TOP_LEFT, 41, KILLS) {
        @Override public boolean visible() {
            return missing != null && maxXp != null && SlayerApi.inCorrectArea();
        }

        @Override protected Content live() {
            JsonObject data = Repo.constant("Slayer.json");
            if (data == null || missing == null || maxXp == null) return new Content("", List.of());
            List<Line> lines = new ArrayList<>(killLines(mobs(), missing, maxXp, wisdomMultiplier(data) * perkMultiplier(data), Combat.opt(KILLS, "overkill"), Combat.opt(KILLS, "xp"), Combat.opt(KILLS, "level"), Combat.opt(KILLS, "health")));
            if (Vitals.stat("Combat Wisdom") <= 0) lines.add(Line.row("Wisdom", "open /stats", Modern.WARN).raw());
            return new Content("§7" + SlayerApi.tier(), lines);
        }

        @Override protected Content sample() {
            return new Content("§74", killLines(DEMO_MOBS, 1450, 4800, 2.1, true, false, false, false));
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("XP left", missing == null ? "-" : Combat.shortNumber(missing), Theme.current().accent()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("XP left", "1.5k", Theme.current().accent()));
        }
    };

    // boss bar

    // skyhanni SlayerType.health + getHealth: what a boss of this tier starts at
    static int bossMax(SlayerApi.Type type, String name, int tier) {
        if (name.contains("Conjoined Brood")) return 20_000_000;
        int[] h = switch (type) {
            case REVENANT -> new int[]{500, 20_000, 400_000, 1_500_000, 10_000_000};
            case TARANTULA -> new int[]{750, 30_000, 900_000, 2_400_000, 10_000_000};
            case SVEN -> new int[]{2_000, 40_000, 750_000, 2_000_000};
            case VOID -> new int[]{300_000, 12_000_000, 50_000_000, 210_000_000};
            case INFERNO -> new int[]{2_500_000, 10_000_000, 45_000_000, 150_000_000};
            case VAMPIRE -> new int[]{625, 1_100, 1_800, 2_400, 3_000};
        };
        return tier >= 1 && tier <= h.length ? h[tier - 1] : 0;
    }

    private static Mobs.Mob ownBoss() {
        for (Mobs.Mob m : Mobs.all()) if (m.category == Mobs.Category.SLAYER && m.stand != null && m.stand.isAlive() && m.mine()) return m;
        return null;
    }

    private static RichCard.Content barCard(String name, String health, double fraction) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(fraction >= 0 ? RichCard.Line.row(name, health).meter(Math.min(0.999, fraction), Modern.BAD) : RichCard.Line.row(name, health, Modern.BAD).raw());
        return new RichCard.Content("", out);
    }

    private static final HudElement BAR_CARD = new RichCard(BAR, "Slayer boss bar", "Slayer", Zone.TOP_LEFT, 41, BAR) {
        @Override public boolean visible() {
            return ownBoss() != null;
        }

        @Override public String heading() {
            return "";
        }

        @Override protected Content live() {
            Mobs.Mob m = ownBoss();
            if (m == null) return new Content("", List.of());
            String text = GameState.strip(m.stand.getName().getString());
            Matcher h = HEALTH.matcher(text);
            SlayerApi.Type t = SlayerApi.Type.byName(m.name);
            if (!h.find()) return barCard(m.name, text.substring(text.lastIndexOf(m.name) + m.name.length()).trim(), -1);
            double now = SlayerFeatures.shortNumber(h.group(1)), max = t == null ? 0 : bossMax(t, m.name, m.levelOrTier);
            return barCard(m.name, h.group(1) + "❤", max > 0 ? now / Math.max(max, now) : -1);
        }

        @Override protected Content sample() {
            return barCard("Voidgloom Seraph", "143.2M❤", 143.2 / 210);
        }

        @Override public List<Content> states() {
            return List.of(barCard("Voidgloom Seraph", "94 Hits", -1));
        }

        @Override protected List<Row> classicRows() {
            Mobs.Mob m = ownBoss();
            return m == null ? List.of() : List.of(new Row(m.name, GameState.strip(m.stand.getName().getString()), Theme.current().bad()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Voidgloom Seraph", "143.2M", Theme.current().bad()));
        }
    };

    static void register() {
        Feature rng = Features.add(new Feature(RNG, "Slayer", "Slayer RNG Meter", "The drop you chose and how many bosses it is away, with the meter. Open the slayer's RNG Meter menu once to set it.", "SkyHanni: RNG Meter Display").when(Combat::sb)
                .option(Option.bool("slayer_rng_display.hide_chat", "Hide the meter's chat lines", "While a drop is chosen; the drop itself still shows.", false))
                .option(Option.bool("slayer_rng_display.warn_empty", "Warn with no drop chosen", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 19, RNG, rng, SlayerCards::rngChat);
        MenuMarks.add(RNG, (java.util.function.Predicate<String>) t -> t.endsWith(" RNG Meter"), SlayerCards::rngMenu);
        MenuMarks.add(RNG, "Slayer", SlayerCards::slayerMenu);
        MenuMarks.add(RNG, "Slayer RNG Meters", SlayerCards::metersMenu);
        Feature kills = Features.add(new Feature(KILLS, "Slayer", "Remaining slayer kills", "How many of each mob around you until the boss spawns, with your Combat Wisdom, weapon, gear and the mayors counted.", "SkyHanni: Remaining Kills").when(Combat::sb)
                .option(Option.bool("slayer_remaining_kills.overkill", "Say overkill", "When one kill gives more than the quest needs.", true))
                .option(Option.bool("slayer_remaining_kills.xp", "XP per kill", "", false))
                .option(Option.bool("slayer_remaining_kills.health", "Mob's health", "", false))
                .option(Option.bool("slayer_remaining_kills.level", "Mob's level", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 19, KILLS, kills, SlayerCards::killChat);
        SlayerApi.onProgress(SlayerCards::killProgress);
        Features.add(new Feature(BAR, "Slayer", "Slayer boss bar", "Your slayer boss's health as a bar.", "Skyblocker: Slayer Bossbar").when(Combat::sb).defaultOff());
        HudLayout.add(RNG_CARD);
        HudLayout.add(KILLS_CARD);
        HudLayout.add(BAR_CARD);
    }
}
