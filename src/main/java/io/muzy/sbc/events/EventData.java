// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// what the seasonal events remember between sessions, in the mod's data file
// skyhanni's PlayerSpecificStorage bingoSessions + winter, ProfileSpecificStorage carnival + cakeCounterData
public final class EventData {
    // BingoGoal with HiddenGoalData
    public static final class Goal {
        public boolean community;
        public String displayName = "", description = "";
        public List<String> guide = new ArrayList<>();
        public boolean done, highlight, unknownTip;
        // time to next hint as the card said when last open, -1 = none
        public long nextHintIn = -1;
        // of the whole, 0.05 = top 5%, -1 = none
        public double communityPercentage = -1;
    }

    // BingoSession: one month's card by goal slot
    public static final class BingoSession {
        public Map<Integer, Goal> goals = new LinkedHashMap<>();
        public Set<String> tierOneMinionsDone = new HashSet<>();
        public long lastCardOpen;
    }

    public static final class ProfileEvents {
        public int carnivalYear = -1;
        // CarnivalGoal names reached this year
        public Set<String> carnivalGoals = new HashSet<>();
        // day tickets were claimed, utc date
        public String ticketsClaimedDay = "";
        // shop -> upgrade -> level
        public Map<String, Map<String, Integer>> carnivalShops = new HashMap<>();
        public int cakesEaten = -1, soulsFound;
    }

    // month start in seconds -> that month's card
    public Map<Long, BingoSession> bingo = new HashMap<>();
    public int amountGifted;
    public Set<String> gifted = new HashSet<>();
    public int cakeCollectedYear = -1;
    public Map<String, ProfileEvents> profiles = new HashMap<>();

    public static EventData get() {
        if (Store.I.events == null) Store.I.events = new EventData();
        return Store.I.events;
    }

    public static ProfileEvents profile() {
        String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
        return get().profiles.computeIfAbsent(key, k -> new ProfileEvents());
    }
}
