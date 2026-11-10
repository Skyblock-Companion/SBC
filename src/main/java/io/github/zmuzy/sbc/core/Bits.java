// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.game.GameProfile;
import io.github.zmuzy.sbc.core.game.GameRead;
import io.github.zmuzy.sbc.core.game.Seen;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni data/BitsApi.kt: bits you have, bits left to claim, fame rank, museum milestone, and from
// those what a cookie gives. no switch for reading bc the tooltip, warning etc all need it
public final class Bits {
    public static final String WARNING = "bits_warning", COOKIE = "bits_cookie";
    private static final int DEFAULT_COOKIE_BITS = 4800;
    // from skyhanni PestProfitTracker.KILL_BITS
    public static final int PEST_KILL_BITS = 5;

    private static final Patterns.P RANKUP_BITS = Patterns.p("SH.data.bits.chat.rankup.bits"), RANKUP = Patterns.p("SH.data.bits.chat.rankup.rank"),
            ATE = Patterns.p("SH.data.bits.chat.boostercookieate"), AVAILABLE = Patterns.p("SH.data.bits.gui.availablemenu"), PURSE = Patterns.p("SH.data.bits.gui.bitsmenu"),
            SB_RANK = Patterns.p("SH.data.bits.gui.sbmenufamerank"), SHOP_RANK = Patterns.p("SH.data.bits.gui.communityshopfamerank"), COOKIE_STACK = Patterns.p("SH.data.bits.gui.mainmenustack"),
            BITS_STACK = Patterns.p("SH.data.bits.gui.bitsstack"), RANK_MENU = Patterns.p("SH.data.bits.gui.famerankmenuname"), RANK_STACK = Patterns.p("SH.data.bits.gui.famerankmenustack"),
            MUSEUM_MENU = Patterns.p("SH.data.bits.gui.museumguiname"), MUSEUM_STACK = Patterns.p("SH.data.bits.gui.museumrewardstack"), MILESTONE = Patterns.p("SH.data.bits.gui.museummilestone"),
            WRONG_COOKIE = Patterns.p("SH.cookie.bits.new.wrong"), AMOUNT = Patterns.p("SH.cookie.bits.new.amount"), TIME = Patterns.p("SH.cookie.bits.new.time");

    // FameRank.json: id -> name + bits multiplier
    private record Rank(String id, String name, double multiplier) {}

    private static Map<String, Rank> ranks;
    private static long lastBits = -1;
    private static String lastProfile = "";
    private static int beeps, beepIn;

    private Bits() {}

    private static Map<String, Rank> ranks() {
        if (ranks != null && !ranks.isEmpty()) return ranks;
        Map<String, Rank> out = new HashMap<>();
        try {
            JsonObject json = Repo.constant("FameRank.json");
            if (json != null) {
                for (var e : json.getAsJsonObject("fame_rank").entrySet()) {
                    JsonObject r = e.getValue().getAsJsonObject();
                    out.put(e.getKey(), new Rank(e.getKey(), r.get("name").getAsString(), r.get("bits_multiplier").getAsDouble()));
                }
            }
        } catch (Exception e) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("Could not read the fame ranks", e);
        }
        ranks = out;
        return out;
    }

    // from skyhanni FameRanks.getByName, ignores case. null = no such rank
    static String rankIdByName(String name) {
        for (Rank r : ranks().values()) if (r.name().equalsIgnoreCase(name)) return r.id();
        return null;
    }

    private static double fameMultiplier() {
        Rank r = ranks().get(Store.I.fameRank);
        return r == null ? 1.0 : r.multiplier();
    }

    // from skyhanni bitsMultiplier: 1% per museum milestone, times the fame rank
    static double multiplier(int museumMilestone, double fame) {
        return (museumMilestone >= 0 ? 1 + museumMilestone * 0.01 : 1.0) * fame;
    }

    public static double multiplier() {
        Seen s = GameProfile.current();
        return multiplier(s == null ? -1 : s.museumMilestone, fameMultiplier());
    }

    // from skyhanni bitsPerCookie
    public static int perCookie() {
        return (int) (DEFAULT_COOKIE_BITS * multiplier());
    }

    // bits left to claim, -1 till the game showed it
    public static long available() {
        Seen s = GameProfile.current();
        return s == null ? -1 : s.bitsAvailable;
    }

    // from skyhanni updateBits on the scoreboard number. the first read of a session has nothing to diff against
    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (beeps > 0 && --beepIn <= 0) {
            beeps--;
            beepIn = 2;
            Combat.play("minecraft:block.note_block.pling", 1f, 0.6f);
        }
        if (mc.player.tickCount % 20 != 0 || !GameState.inSkyBlock) return;
        long bits = GameState.bits;
        if (bits < 0) return;
        if (!GameState.profileName.equals(lastProfile)) {
            lastProfile = GameState.profileName;
            lastBits = bits;
            return;
        }
        if (lastBits >= 0 && bits != lastBits) gained(GameProfile.current(), bits - lastBits);
        lastBits = bits;
    }

    // pure for tests: whats left to claim after bits came in. going below 0 = we didnt really know it
    static long afterGain(long available, long difference) {
        if (available < 0) return -1;
        long left = available - difference;
        return left < 0 ? -1 : left;
    }

    private static void gained(Seen s, long difference) {
        if (difference <= 0 || s == null) return;
        s.bitsAvailable = afterGain(s.bitsAvailable, difference);
        Store.markDirty();
        Feature f = Features.get(WARNING);
        if (f == null || !f.active()) return;
        if (s.bitsAvailable == 0) noBits(f);
        // skyhanni compares its threshold with the whole purse, but the setting says "the amount of bits you need to
        // get" so we do that
        if (f.option("bits_warning.gain_message").bool() && difference >= f.option("bits_warning.threshold").integer())
            Combat.say("You have gained §b" + String.format(Locale.ROOT, "%,d", difference) + " §fBits.");
    }

    // from skyhanni NoBitsWarning: clickable line, title, repeated sound
    private static void noBits(Feature f) {
        Combat.sayClickable("§bNo Bits Available! §eClick to buy booster cookies on the bazaar.", "/bz booster cookie");
        Combat.title("§bNo Bits Available", 40);
        if (f.option("bits_warning.sound").bool()) {
            beeps = 5;
            beepIn = 0;
        }
    }

    // color coded skyhanni pattern for colorless text, so real chat matches how the player reads it no matter how
    // hypixel colored it
    public static java.util.regex.Pattern colourless(Patterns.P p) {
        String regex = p.pattern().pattern().replace("(?:§.)+", "").replace("(?:§.)*", "").replace("[§\\w\\s]", "[\\w\\s]").replaceAll("§.", "");
        return java.util.regex.Pattern.compile(regex);
    }

    private static java.util.regex.Pattern rankupBits, rankup, ate;

    // from skyhanni BitsApi.onChat, on the colorless line
    static void chat(Seen s, String plain) {
        if (rankupBits == null) {
            rankupBits = colourless(RANKUP_BITS);
            rankup = colourless(RANKUP);
            ate = colourless(ATE);
        }
        String message = plain.trim();
        Matcher m = rankupBits.matcher(message);
        if (m.matches()) {
            long amount = GameRead.number(m.group("amount"));
            if (amount >= 0 && s.bitsAvailable >= 0) s.bitsAvailable += amount;
            Store.markDirty();
            return;
        }
        m = rankup.matcher(message);
        if (m.matches()) {
            setRank(m.group("rank"));
            return;
        }
        if (ate.matcher(message).matches() && s.bitsAvailable >= 0) {
            s.bitsAvailable += perCookie();
            Store.markDirty();
        }
    }

    private static void setRank(String name) {
        String id = rankIdByName(name);
        if (id != null && !id.equals(Store.I.fameRank)) {
            Store.I.fameRank = id;
            Store.markDirty();
        }
    }

    private static GameRead.MenuItem named(List<GameRead.MenuItem> items, Patterns.P name, boolean last) {
        GameRead.MenuItem found = null;
        for (GameRead.MenuItem i : items) {
            if (!name.matcher(i.cleanName()).matches()) continue;
            if (!last) return i;
            found = i;
        }
        return found;
    }

    private static Long firstAmount(List<String> lore, Patterns.P p, String group) {
        for (String l : lore) {
            Matcher m = p.matcher(l);
            if (m.find()) return GameRead.number(m.group(group));
        }
        // resets between the colors break it ("§7Bits Available: §r§b1,038"), try without colors
        java.util.regex.Pattern plain = colourless(p);
        for (String l : lore) {
            Matcher m = plain.matcher(GameState.strip(l));
            if (m.find()) return GameRead.number(m.group(group));
        }
        return null;
    }

    // sb menu not fully there yet = no cookie means nothing
    static final int FULL_MENU = 45;

    // from skyhanni onInventoryFullyOpened, menu lore with colors. called on every menu look
    public static void menu(Seen s, String title, List<GameRead.MenuItem> items) {
        if (title.equals("SkyBlock Menu")) {
            GameRead.MenuItem cookie = named(items, COOKIE_STACK, true);
            // "If the cookie stack is null, then the player should not have any bits to claim" (skyhanni)
            if (cookie == null) {
                if (items.size() >= FULL_MENU) s.bitsAvailable = 0;
            } else {
                Long amount = firstAmount(cookie.lore(), AVAILABLE, "toClaim");
                if (amount != null) s.bitsAvailable = amount;
            }
            Store.markDirty();
        }
        if (RANK_MENU.matcher(title).matches()) {
            GameRead.MenuItem rank = named(items, RANK_STACK, false);
            if (rank != null) {
                for (String l : rank.lore()) {
                    Matcher m = SHOP_RANK.matcher(l);
                    if (!m.matches()) m = SB_RANK.matcher(l);
                    if (m.matches()) {
                        setRank(m.group("rank"));
                        break;
                    }
                }
            }
            GameRead.MenuItem bits = named(items, BITS_STACK, false);
            if (bits != null) {
                Long amount = firstAmount(bits.lore(), AVAILABLE, "toClaim");
                if (amount != null) {
                    s.bitsAvailable = amount;
                    Store.markDirty();
                }
                // purse line of the same stack moved bits without claiming anything
                Long purse = firstAmount(bits.lore(), PURSE, "amount");
                if (purse != null && purse >= 0) {
                    lastBits = purse;
                }
            }
        }
        if (MUSEUM_MENU.matcher(title).matches()) {
            GameRead.MenuItem rewards = named(items, MUSEUM_STACK, false);
            if (rewards != null) {
                Long milestone = firstAmount(rewards.lore(), MILESTONE, "milestone");
                if (milestone != null && milestone >= 0) {
                    s.museumMilestone = milestone.intValue();
                    Store.markDirty();
                }
            }
        }
    }

    // --- BitsPerCookieVisual ---

    // what a cookie stack tooltip gets: the 4 days line goes if it was there and our lines go in its place
    static void cookieTooltip(List<Component> tip, boolean bulk, boolean showBits, boolean showChange, int perCookie, long available) {
        int amount = 1, loreIndex = 0;
        for (int i = 0; i < tip.size(); i++) {
            Matcher m = AMOUNT.matcher(tip.get(i).getString());
            if (m.matches()) {
                amount = Integer.parseInt(m.group("amount"));
                loreIndex = i;
                break;
            }
        }
        int time = -1;
        for (int i = 0; i < tip.size(); i++) {
            if (TIME.matcher(tip.get(i).getString()).matches()) {
                time = i;
                break;
            }
        }
        boolean replaced = time >= 0;
        if (replaced && bulk) tip.remove(time);
        int index = Math.min(replaced ? time : loreIndex + 1, tip.size());
        long gain = (long) perCookie * amount;
        int duration = 4 * amount;
        if (replaced) {
            if (bulk) tip.add(index++, Component.literal("§7§b" + duration + " §7days"));
            tip.add(index++, Component.empty());
        } else {
            tip.add(index++, Component.empty());
            if (bulk) tip.add(index++, Component.literal("§8‣ §7Cookie Buff for §b" + duration + " §7days"));
        }
        if (showBits) tip.add(index++, Component.literal("§8‣ §7Gain §b" + String.format(Locale.ROOT, "%,d", gain) + " Bits"));
        if (showChange && available >= 0)
            tip.add(index, Component.literal("§8‣ §7Available Bits: §3" + String.format(Locale.ROOT, "%,d", available) + " §6→ §3" + String.format(Locale.ROOT, "%,d", available + gain)));
    }

    private static void tooltip(ItemStack stack, List<Component> tip) {
        if (!"BOOSTER_COOKIE".equals(Gain.sbId(stack)) || WRONG_COOKIE.matcher(LegacyText.name(stack)).matches()) return;
        Feature f = Features.get(COOKIE);
        cookieTooltip(tip, f.option("bits_cookie.bulk_time").bool(), f.option("bits_cookie.show_bits").bool(), f.option("bits_cookie.show_change").bool(), perCookie(), available());
    }

    public static void register() {
        Safe.endTick("Bits.tick", Bits::tick);
        ChatBus.register(5, "pet_chat", null, line -> {
            Vitals.petChat(line.component(), line.plain());
            return ChatBus.PASS;
        });
        ChatBus.register(5, "bits", null, line -> {
            Seen s = GameProfile.current();
            if (s != null) chat(s, line.plain());
            return ChatBus.PASS;
        });
        Features.add(new Feature(WARNING, "Misc", "Bits messages", "Tells you when a cookie's bits have run out, with a line to click for the Bazaar, and when you gain bits.", "SkyHanni: Bits Features")
                .when(() -> GameState.inSkyBlock)
                .option(Option.bool("bits_warning.sound", "Sound with the warning", "", true))
                .option(Option.bool("bits_warning.gain_message", "Line when you gain bits", "", true))
                .option(Option.integer("bits_warning.threshold", "Smallest gain to mention", "", 400, 0, 10000)));
        Features.add(new Feature(COOKIE, "Items", "Bits on a cookie", "On a Booster Cookie's tooltip: the bits it gives with your fame rank and museum, how long the buff lasts when you buy several, and what you would have available.", "SkyHanni: Bits on Cookie")
                .when(() -> GameState.inSkyBlock).tooltip(Bits::tooltip)
                .option(Option.bool("bits_cookie.show_bits", "Bits you would gain", "", true))
                .option(Option.bool("bits_cookie.show_change", "Available bits before and after", "", true))
                .option(Option.bool("bits_cookie.bulk_time", "Buff time for several cookies", "", true)));
    }
}
