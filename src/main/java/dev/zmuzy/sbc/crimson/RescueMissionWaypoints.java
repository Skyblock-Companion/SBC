// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.crimson;

import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.render.ParkourHelper;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;
import java.util.regex.Matcher;

// from skyhanni RescueMissionWaypoints: tier from the town board "Rescue" menu (slot 22 "§aⒸ §eRescue
// Mission"), the parkour for your faction + tier shown from the undercover agent's briefing
// reset when caught, stopped when cancelled. nav hints go through our navigator (agent / quest board graph nodes)
public final class RescueMissionWaypoints {
    static final Patterns.P AGENT_DIALOGUE = Patterns.p("SH.crimson.rescue.start");
    static final Patterns.P RECRUITER = Patterns.p("SH.crimson.rescue.recruiter");
    static final Patterns.P CAUGHT = Patterns.p("SH.crimson.rescue.caught");
    static final Patterns.P CANCEL_TIMEOUT = Patterns.p("SH.crimson.rescue.cancel.timeout");
    static final Patterns.P CANCEL_AFRAID = Patterns.p("SH.crimson.rescue.cancel.afraid");
    static final Patterns.P CANCEL_RUN_AWAY = Patterns.p("SH.crimson.rescue.cancel.run-away");
    static final Patterns.P QUEST_TIER = Patterns.p("SH.crimson.rescue.tier");
    static final Patterns.P MENU = Patterns.p("SH.crimson.rescue.menu");

    static ParkourHelper parkourHelper;
    static String tier;
    static boolean tierWasUnknown;

    // config.variant for S rank (1 or 2), hostagePath, agentPath
    public static int variant = 1;
    public static boolean hostagePath = true, agentPath = true;
    // nav callback: (graph node or null, pos or null) -> pathfind there
    public static BiConsumer<String, Vec3> navigate = (n, p) -> {};

    private RescueMissionWaypoints() {}

    public static void onProfileJoin() {
        tier = null;
    }

    public static void onIslandChange() {
        stopParkour();
    }

    private static void stopParkour() {
        parkourHelper = null;
    }

    // onInventoryFullyOpened on crimson: the Rescue menu's slot 22 formatted name (leading white, fewer resets)
    public static void onInventory(String inventoryName, String slot22Name) {
        if (!MENU.matcher(inventoryName).matches() || slot22Name == null) return;
        Matcher m = QUEST_TIER.matcher(slot22Name);
        tier = m.matches() ? toLetter(m.group("tier")) : null;
        if (tier != null && tierWasUnknown) {
            tierWasUnknown = false;
            CrimsonEnv.world.chat("Now the tier is known!");
            navigateToUndercoverAgent();
        }
    }

    static String toLetter(String s) {
        return switch (s) {
            case "Ⓢ" -> "S";
            case "Ⓐ" -> "A";
            case "Ⓑ" -> "B";
            case "Ⓒ" -> "C";
            case "Ⓓ" -> "D";
            default -> null;
        };
    }

    private static void initParkour() {
        String t = tier;
        if (t == null) return;
        ReputationApi.FactionType faction = ReputationApi.factionType();
        if (t.equals("S")) {
            if (faction == ReputationApi.FactionType.MAGE) {
                CrimsonEnv.world.chat("§cNo data present for Mage S-rank Rescue Mission");
                return;
            }
            ParkourHelper.Data d = ReputationData.rescueParkour("barb", "S" + variant);
            parkourHelper = d == null ? null : createParkour(d);
            return;
        }
        if (faction == null) return;
        ParkourHelper.Data d = ReputationData.rescueParkour(faction == ReputationApi.FactionType.MAGE ? "mage" : "barb", t);
        parkourHelper = d == null ? null : createParkour(d);
    }

    private static ParkourHelper createParkour(ParkourHelper.Data d) {
        ParkourHelper p = new ParkourHelper(d.locations(), d.shortCuts(), 1.0, 3.5, true, RescueMissionWaypoints::stopParkour, false);
        p.rainbowColor = true;
        p.lookAhead = 3;
        p.playerPos = () -> CrimsonEnv.world.playerPos();
        return p;
    }

    // clean + formatted line, true if a rescue line got used
    public static boolean onChat(String clean, String formatted) {
        if (!CrimsonEnv.inCrimson()) return false;
        boolean used = false;
        if (hostagePath) {
            if (AGENT_DIALOGUE.matcher(clean).matches()) {
                used = true;
                if (tier != null) startParkour();
                else {
                    CrimsonEnv.world.chat("§cRescue mission tier still not known! Check out the Quest Board first as I told you earlier!");
                    navigateToQuestBoard("forgot to check out tier");
                }
            }
            if (RECRUITER.matcher(formatted).matches()) {
                used = true;
                if (tier == null) {
                    CrimsonEnv.world.chat("§cRescue mission tier not known! Check out the Quest Board first!");
                    tierWasUnknown = true;
                    return true;
                }
                navigateToUndercoverAgent();
            }
            if (CAUGHT.matcher(formatted).matches()) {
                used = true;
                if (parkourHelper != null) parkourHelper.reset();
                navigateToParkourStart();
            }
        }
        if (parkourHelper != null) {
            if (CANCEL_AFRAID.matcher(formatted).matches() || CANCEL_RUN_AWAY.matcher(formatted).matches()) {
                used = true;
                stopParkour();
                if (hostagePath) CrimsonEnv.world.clickableChat("Wanna retry the rescue mission quest? Click here!", "/sbc rescueboard", "Head back to the Quest Board");
            }
            if (CANCEL_TIMEOUT.matcher(formatted).matches()) {
                used = true;
                stopParkour();
                if (hostagePath) navigateToQuestBoard("run out of time");
            }
        }
        return used;
    }

    private static void startParkour() {
        if (parkourHelper != null) parkourHelper.reset();
        initParkour();
        navigateToParkourStart();
    }

    private static void navigateToParkourStart() {
        if (parkourHelper == null) return;
        navigate.accept(null, parkourHelper.getStartLocation());
    }

    private static void navigateToUndercoverAgent() {
        if (!agentPath) return;
        ReputationApi.FactionType f = ReputationApi.factionType();
        if (f == null) return;
        navigate.accept(f.undercoverAgentNode(), null);
    }

    public static void navigateToQuestBoard(String reason) {
        ReputationApi.FactionType f = ReputationApi.factionType();
        if (f == null) return;
        CrimsonEnv.world.chat("Head back to Quest board, " + reason);
        navigate.accept(f.questBoardNode(), null);
    }

    public static boolean inParkour() {
        return parkourHelper != null;
    }

    public static String tier() {
        return tier;
    }

    // parkour look, ConditionalUtils.onToggle in skyhanni
    public static void style(boolean rainbow, int color, int lookAhead) {
        if (parkourHelper == null) return;
        parkourHelper.rainbowColor = rainbow;
        parkourHelper.monochromeColor = color;
        parkourHelper.lookAhead = lookAhead + 1;
    }

    public static void render(WorldDraw w) {
        if (!hostagePath || parkourHelper == null) return;
        parkourHelper.render(w);
    }
}
