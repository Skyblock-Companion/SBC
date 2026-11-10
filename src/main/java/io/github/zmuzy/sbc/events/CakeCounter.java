// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni CakeCounterFeatures: the cake counter on your island says cakes eaten and souls found. tells
// you what changed while you were away, and when someone finds a soul while you're there
// they read the stands when names change, here we just read them a few times a sec on the island
public final class CakeCounter {
    public static final String ID = "cake_counter";
    private static final String OFF = "Off", CAKES = "Cakes", SOULS = "Souls", BOTH = "Both", LEFT = "Since last leaving", JOINED = "Since last joining";

    // one island visit, no game needed
    static final class Visit {
        final EventData.ProfileEvents stored;
        final String tracking;
        boolean statsToBeSent = true, cakesSeen, soulsSeen, soulsStandExists = true;
        Integer cakesDifference, soulsDifference;
        long seenAt;
        long soulBySelfAt;

        Visit(EventData.ProfileEvents stored, String tracking) {
            this.stored = stored;
            this.tracking = tracking;
        }

        // updateCakesEaten
        void cakes(int current, boolean onIsland) {
            if (current > stored.cakesEaten && !(onIsland && tracking.equals(JOINED))) {
                cakesDifference = current - stored.cakesEaten;
                stored.cakesEaten = current;
            }
        }

        // updateSoulsFound: true when someone else just found one
        boolean souls(int current, boolean onIsland, long now) {
            if (current <= stored.soulsFound) return false;
            if (!onIsland || !tracking.equals(JOINED)) {
                soulsDifference = current - stored.soulsFound;
                stored.soulsFound = current;
            }
            return onIsland && now - soulBySelfAt > 1_000;
        }

        // sendOfflineStatsMessage: null until theres something to say
        String message(String mode) {
            if (!statsToBeSent || !cakesSeen || soulsStandExists && !soulsSeen) return null;
            boolean quiet = switch (mode) {
                case CAKES -> cakesDifference == null;
                case SOULS -> soulsDifference == null;
                case BOTH -> cakesDifference == null && soulsDifference == null;
                default -> true;
            };
            if (quiet) return null;
            statsToBeSent = false;
            String since = tracking.equals(JOINED) ? "Since you last joined your Private Island, " : "Since you were last on your Private Island, ";
            String cakes = cakesDifference == null ? null : "ate " + number(cakesDifference) + (cakesDifference == 1 ? " Century Cake" : " Century Cakes");
            String souls = soulsDifference == null ? null : "found " + number(soulsDifference) + (soulsDifference == 1 ? " Cake Soul" : " Cake Souls");
            return switch (mode) {
                case CAKES -> since + "players " + cakes + ".";
                case SOULS -> since + "players " + souls + ".";
                default -> since + "players " + (cakes != null && souls != null ? cakes + " and " + souls : cakes != null ? cakes : souls) + ".";
            };
        }
    }

    private static Visit visit;

    private CakeCounter() {}

    private static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static boolean island() {
        return SbcMod.DEV || "PRIVATE_ISLAND".equals(GameState.island);
    }

    private static int parse(String s) {
        return Integer.parseInt(s.replace(",", ""));
    }

    // onEntityChangeName + checkForSoulsStand
    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 5 != 0) return;
        if (visit == null) visit = new Visit(EventData.profile(), Combat.choice(ID, "tracking", LEFT));
        long now = System.currentTimeMillis();
        int cakesBefore = visit.stored.cakesEaten, soulsBefore = visit.stored.soulsFound;
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(128), ArmorStand::hasCustomName)) {
            String name = GameState.strip(a.getCustomName().getString());
            Matcher m = Patterns.get("SH.misc.cakecounter.cakeseaten.colorless").matcher(name);
            if (m.matches()) {
                if (!visit.cakesSeen) {
                    visit.cakesSeen = true;
                    visit.seenAt = now;
                    // never seen on this profile = just placed
                    visit.statsToBeSent = visit.statsToBeSent && visit.stored.cakesEaten != -1;
                    visit.cakes(parse(m.group("cakes")), false);
                } else if (!visit.statsToBeSent) {
                    visit.cakes(parse(m.group("cakes")), true);
                }
                continue;
            }
            m = Patterns.get("SH.misc.cakecounter.soulsfound.colorless").matcher(name);
            if (m.matches()) {
                if (!visit.soulsSeen) {
                    visit.soulsSeen = true;
                    visit.souls(parse(m.group("souls")), false, now);
                } else if (!visit.statsToBeSent && visit.souls(parse(m.group("souls")), true, now) && Combat.opt(ID, "soul_alert")) {
                    Combat.say("Someone just found a Cake Soul on your Island!");
                }
            }
        }
        // 2s after the cakes, a stand that didnt show up doesnt exist
        if (visit.cakesSeen && !visit.soulsSeen && now - visit.seenAt > 2_000) visit.soulsStandExists = false;
        String text = visit.message(Combat.choice(ID, "offline", OFF));
        if (text != null) Combat.say(text);
        if (visit.stored.cakesEaten != cakesBefore || visit.stored.soulsFound != soulsBefore) Store.markDirty();
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Events", "Cake Counter", "With a Cake Counter on your Private Island: a message when someone finds a Cake Soul while you are there, and if you want what happened while you were away.", "SkyHanni: Cake Counter Features")
                .when(CakeCounter::island)
                .tick(CakeCounter::tick)
                .left(() -> visit = null)
                .option(Option.choice("cake_counter.offline", "While you were away", "What to tell you when you come back.", OFF, OFF, CAKES, SOULS, BOTH))
                .option(Option.choice("cake_counter.tracking", "Counted", "From when you last left, or from when you last came.", LEFT, LEFT, JOINED))
                .option(Option.bool("cake_counter.soul_alert", "Soul found alert", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 31, ID, f, line -> {
            String clean = GameState.strip(line.plain());
            if (visit != null && Patterns.get("SH.misc.cakesoul.found.colorless").matcher(clean).matches()) visit.soulBySelfAt = System.currentTimeMillis();
            return ChatBus.PASS;
        });
    }
}
