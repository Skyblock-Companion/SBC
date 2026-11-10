// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.feature;

import dev.zmuzy.sbc.core.Config;

// settings that live as plain Config fields, attached to their features as options so they show in the
// settings screen too. each option is bound to its field
public final class Options {
    private Options() {}

    private static Feature f(String id) {
        return Features.get(id);
    }

    private static void add(String featureId, Option o) {
        Feature f = f(featureId);
        if (f != null) f.option(o);
    }

    public static void register() {
        // appearance + layout (always on, these are the knobs, the hud itself is other features)
        Feature look = Features.add(new Feature("appearance", "HUD", "Appearance and layout",
                "Theme, size and position of the whole HUD. The HUD editor (H) moves and styles each element.", "").alwaysOn());
        look.option(Option.bool("appearance.hide_in_debug", "Hide the HUD while the debug screen (F3) is open", "", true));
        look.option(Option.bool("appearance.hud_texture", "HUD as a picture", "Draws the HUD into a picture 20 times a second and shows the picture in between, which is nearly free. Numbers move up to 50 ms later. Turn off if the HUD ever looks wrong.", true));
        look.option(Option.bool("appearance.hud_replay", "Faster HUD", "Works the HUD out 20 times a second and shows that picture in between, for a lot more FPS. Numbers move up to 50 ms later. Turn off if a card ever looks stuck.", true));
        look.option(Option.bool("appearance.hudInInventory", "Keep the HUD while an inventory is open", "Off: only elements that belong inside menus stay.", true)
                .bind(() -> Config.I.hudInInventory, v -> Config.I.hudInInventory = (Boolean) v));
        look.option(Option.number("appearance.classicAlpha", "Classic background opacity", "How dark the box behind each Classic element is.", Config.DEFAULT_ALPHA, 0, 1, 0.05)
                .bind(() -> (double) Config.I.classicAlpha, v -> Config.I.classicAlpha = ((Number) v).floatValue()));
        look.option(Option.bool("appearance.hudAnimations", "Smooth HUD", "Panels ease into place and fade in instead of jumping.", true)
                .bind(() -> Config.I.hudAnimations, v -> Config.I.hudAnimations = (Boolean) v));
        look.option(Option.number("appearance.hudAll", "Size of all elements", "Multiplies every HUD element's size.", 1.0, 0.5, 2.0, 0.05)
                .bind(() -> (double) Config.I.hudAll, v -> Config.I.hudAll = ((Number) v).floatValue()));
        look.option(Option.number("appearance.hudScale", "Size of the left column", "", 1.0, 0.5, 1.5, 0.05)
                .bind(() -> (double) Config.I.hudScale, v -> Config.I.hudScale = ((Number) v).floatValue()));
        look.option(Option.integer("appearance.hudX", "Left column X", "Pixels from the left edge.", 6, 0, 400)
                .bind(() -> Config.I.hudX, v -> Config.I.hudX = ((Number) v).intValue()));
        look.option(Option.bool("appearance.perfOverlay", "Performance overlay", "Milliseconds per element and feature, top centre.", false)
                .bind(() -> Config.I.perfOverlay, v -> Config.I.perfOverlay = (Boolean) v));

        // routine card
        add("routine", Option.integer("routine.maxTasks", "Tasks shown", "", 7, 3, 12).bind(() -> Config.I.hudMaxTasks, v -> Config.I.hudMaxTasks = ((Number) v).intValue()));
        add("routine", Option.bool("routine.showEvents", "Show the next event", "", true).bind(() -> Config.I.hudShowEvents, v -> Config.I.hudShowEvents = (Boolean) v));
        add("routine", Option.choice("routine.preset", "Routine preset", "", "smart", "smart", "money", "level", "minimal")
                .bind(() -> Config.I.routinePreset, v -> Config.I.routinePreset = String.valueOf(v)));

        // minimap
        add("minimap", Option.integer("minimap.size", "Size (px)", "", 128, 64, 240).bind(() -> Config.I.minimapSize, v -> Config.I.minimapSize = ((Number) v).intValue()));
        add("minimap", Option.integer("minimap.radius", "Zoom (blocks to the edge)", "Numpad + / - also zoom.", 64, 16, 256).bind(() -> Config.I.minimapRadius, v -> Config.I.minimapRadius = ((Number) v).intValue()));
        add("minimap", Option.bool("minimap.round", "Round minimap", "", true).bind(() -> Config.I.minimapRound, v -> Config.I.minimapRound = (Boolean) v));
        add("minimap", Option.bool("minimap.rotate", "Turn with where you look", "Round map only. Off: north stays up and the pointer turns, like the square map.", false).bind(() -> Config.I.minimapRotate, v -> Config.I.minimapRotate = (Boolean) v));
        add("minimap", Option.bool("minimap.labels", "Place labels", "", true).bind(() -> Config.I.minimapLabels, v -> Config.I.minimapLabels = (Boolean) v));
        add("minimap", Option.bool("minimap.hideInDungeon", "Hide inside dungeons", "", true).bind(() -> Config.I.minimapHideInDungeon, v -> Config.I.minimapHideInDungeon = (Boolean) v));

        // search + path line
        add("inv_search", Option.bool("inv_search.ctrlK", "Ctrl+K instead of Ctrl+F", "", false));
        add("inv_search", Option.bool("inv_search.skyblockOnly", "Only on SkyBlock", "", false));
        add("tablist", Option.bool("tablist.reverseSort", "Reverse the order of the players", "", false));
        add("tablist", Option.bool("tablist.toggle", "The TAB key toggles", "One press opens it and the next closes it, instead of holding.", false));
        add("pathline", Option.color("pathline.color", "Colour", "", 0xFF39D0FF).bind(() -> Config.I.pathColor, v -> Config.I.pathColor = ((Number) v).intValue()));

        // chat
        add("npcdialogue", Option.bool("npcdialogue.hideChat", "Keep NPC lines out of chat", "", true).bind(() -> Config.I.hideNpcChat, v -> Config.I.hideNpcChat = (Boolean) v));

        // alerts + routine behaviour
        Feature alerts = Features.add(new Feature("alerts", "Misc", "Alerts", "Pop-up alerts for resets, events and timers.", "")
                .bind(() -> Config.I.alerts, v -> Config.I.alerts = v));
        alerts.option(Option.bool("alerts.sounds", "Sound with alerts", "", true).bind(() -> Config.I.sounds, v -> Config.I.sounds = (Boolean) v));
        alerts.option(Option.bool("alerts.briefing", "Welcome briefing when you join SkyBlock", "", true).bind(() -> Config.I.loginBriefing, v -> Config.I.loginBriefing = (Boolean) v));
        alerts.option(Option.bool("alerts.autoGuide", "Start the auto routine when you join", "", true).bind(() -> Config.I.autoGuide, v -> Config.I.autoGuide = (Boolean) v));
        alerts.option(Option.integer("alerts.minutesBeforeReset", "Warn before the daily reset (minutes)", "", 60, 15, 240).bind(() -> Config.I.alertMinutesBeforeReset, v -> Config.I.alertMinutesBeforeReset = ((Number) v).intValue()));
        alerts.option(Option.choice("alerts.weeklyResetDay", "Weekly reset day", "", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .bind(() -> new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}[Math.max(0, Math.min(6, Config.I.weeklyResetDay - 1))],
                        v -> Config.I.weeklyResetDay = java.util.Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").indexOf(String.valueOf(v)) + 1));

        // browser
        Features.add(new Feature("browser", "Misc", "In-game browser", "Wiki and chat links open inside the game instead of your desktop browser.", "")
                .bind(() -> Config.I.inGameBrowser, v -> Config.I.inGameBrowser = v));

        // planner
        Feature plan = Features.add(new Feature("planner", "Misc", "Planner", "How the planner weighs money against levels and how long a session is.", "").alwaysOn());
        plan.option(Option.integer("planner.moneyWeight", "Money vs. level (%)", "0 = all level, 100 = all money.", 50, 0, 100).bind(() -> Config.I.moneyWeight, v -> Config.I.moneyWeight = ((Number) v).intValue()));
        plan.option(Option.number("planner.hours", "Session length (hours)", "", 2.0, 0.5, 12, 0.5).bind(() -> Config.I.planHours, v -> Config.I.planHours = ((Number) v).doubleValue()));
        plan.option(Option.number("planner.flipCapital", "Flip capital (0 = purse + bank)", "", 0, 0, 1_000_000_000, 1_000_000).bind(() -> Config.I.flipCapital, v -> Config.I.flipCapital = ((Number) v).doubleValue()));
    }
}
