// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.diana;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;

// from skyhanni DianaApi: are we doing diana (hub + ritual active + spade in hotbar), is a spade held, and
// the ritual override
// a rare mob seen or spade used = ritual on, hypixel's "isn't active" line = off
public final class DianaApi {
    // matches: Minos Inquisitor / Sphinx / King Minos / Manticore
    static final Patterns.P RARE_MOB_NAME = Patterns.p("SH.event-diana.rare-mob-name");
    // matches: The mythological ritual isn't active!
    static final Patterns.P RITUAL_NOT_ACTIVE = Patterns.p("SH.event-diana.ritual-not-active");

    private static Boolean ritualActiveOverride;

    private DianaApi() {}

    public static boolean hasSpadeInHand() {
        return DianaEnv.world.spadeInHand();
    }

    public static boolean isRitualActive() {
        if (ritualActiveOverride != null) return ritualActiveOverride;
        return DianaEnv.world.ritualActiveByMayor();
    }

    public static boolean hasGriffinPet() {
        return DianaEnv.world.hasGriffinPet();
    }

    public static boolean isDoingDiana() {
        return DianaEnv.world.onHub() && isRitualActive() && DianaEnv.world.spadeInHotbar();
    }

    public static boolean isDianaSpade(String internalName) {
        return internalName != null && DianaData.spades().contains(internalName);
    }

    // entity name of a rare diana mob (player type entity on the hub)
    public static boolean isRareMobName(String cleanName) {
        return RARE_MOB_NAME.matcher(cleanName.trim()).matches();
    }

    // DianaApi.onJoinWorld: rare mob showed up -> ritual is on, the share module takes it from here
    public static void onRareMobEntity(RareMobWaypointShare.Mob mob) {
        if (!DianaEnv.world.onHub()) return;
        if (!isRareMobName(GameState.strip(mob.name()))) return;
        overrideActiveRitual(true);
        RareMobWaypointShare.onRareDianaMobFound(mob);
        DianaExtras.onRareDianaMobFound(mob);
    }

    // DianaApi.onChat, hub only
    public static void onChat(String plain) {
        if (!DianaEnv.world.onHub()) return;
        if (RITUAL_NOT_ACTIVE.matcher(plain).matches()) overrideActiveRitual(false);
    }

    // DianaApi.onItemClick: right click with a spade
    public static void onItemClick(boolean rightClick) {
        if (!DianaEnv.world.onHub()) return;
        if (!hasSpadeInHand() || !rightClick) return;
        overrideActiveRitual(true);
    }

    public static void overrideActiveRitual(boolean active) {
        if (isRitualActive() != active) ritualActiveOverride = active;
    }

    public static void reset() {
        ritualActiveOverride = null;
    }

    public static DianaData.Creature getCreatureByTrackerName(String name) {
        for (DianaData.Creature c : DianaData.creatures().values()) if (c.trackerId().equals(name)) return c;
        return null;
    }
}
