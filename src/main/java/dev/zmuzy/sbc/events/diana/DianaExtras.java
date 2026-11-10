// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.events.diana;

import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;

import java.util.ArrayList;
import java.util.List;

// small diana modules, from skyhanni GriffinPetWarning (spade without a griffin pet -> reminder),
// MuteBuggedSpade (hypixel's spade plays "music.*" sounds that arent real music), DianaFixChat (no guess
// after a spade use -> tell you + suggest /pq extreme), HighlightRareDianaMob (outline on rare mobs)
public final class DianaExtras {
    // --- GriffinPetWarning ---
    private static boolean wasCorrectPetAlready;
    private static long lastWarnTime = DianaEnv.FAR_PAST;
    private static int ticks;

    // --- DianaFixChat ---
    private static boolean hasSetParticleQuality;
    private static long lastParticleQualityPrompt = DianaEnv.FAR_PAST;
    private static int errorCounter, successfulCounter;
    private static long lastShovelSwing = DianaEnv.FAR_PAST, lastErrorTime = DianaEnv.FAR_PAST, lastGuessPoint = DianaEnv.FAR_PAST;
    private static boolean foundGuess;
    private static long lastSecond;

    // --- HighlightRareDianaMob ---
    private static final List<RareMobWaypointShare.Mob> highlighted = new ArrayList<>();

    private DianaExtras() {}

    // every tick
    public static void onTick() {
        ticks++;
        if (ticks % 10 == 0) petWarningTick();
        long now = DianaEnv.now();
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            fixChatSecond();
        }
    }

    // GriffinPetWarning.onTick (every 10 ticks)
    private static void petWarningTick() {
        if (!Diana.b(Diana.PET_WARNING, "on", true)) return;
        if (!DianaApi.isDoingDiana()) return;
        if (!DianaApi.hasSpadeInHand()) return;
        if (DianaApi.hasGriffinPet()) {
            wasCorrectPetAlready = true;
            return;
        }
        if (wasCorrectPetAlready) return;
        warnPet();
    }

    private static void warnPet() {
        DianaEnv.world.chat("§eReminder to use a Griffin pet for Mythological Ritual! §7(/pet opens the pets menu)");
        if (DianaEnv.passedSince(lastWarnTime) > 30_000) {
            lastWarnTime = DianaEnv.now();
            DianaEnv.world.title("§cGriffin Pet!");
        }
    }

    // BurrowDugEvent -> both modules
    public static void onBurrowDug() {
        Delayed.run(2000, () -> wasCorrectPetAlready = false);
    }

    public static void onIslandChange() {
        Delayed.run(5000, () -> wasCorrectPetAlready = false);
    }

    // MuteBuggedSpade.onPlaySound
    public static boolean onSound(SoundHooks.Sound s) {
        if (!Diana.b(Diana.MUTE_SPADE, "on", true) || !DianaApi.isDoingDiana()) return false;
        boolean isRealMusic = s.pitch() == 1f && s.volume() == 1f && s.locationIsZero();
        return s.name().startsWith("music") && !isRealMusic;
    }

    // DianaFixChat.onSecondPassed
    private static void fixChatSecond() {
        if (!fixChatEnabled()) return;
        if (DianaEnv.passedSince(lastShovelSwing) > 60_000) return;
        if (foundGuess) {
            lastErrorTime = DianaEnv.FAR_PAST;
            return;
        }
        // particles dont work if a valid target is close
        if (GriffinBurrowHelper.targetLocation != null) return;
        if (DianaEnv.passedSince(lastShovelSwing) <= 3000) return;
        if (lastErrorTime == lastShovelSwing) return;
        lastErrorTime = lastShovelSwing;
        noGuessFound();
    }

    private static void noGuessFound() {
        errorCounter++;
        if (errorCounter == 1) {
            if (successfulCounter < 5) DianaEnv.world.chat("Could not find Diana guess using particles, please try again.");
            return;
        }
        if (!hasSetParticleQuality) {
            if (DianaEnv.passedSince(lastParticleQualityPrompt) > 30_000) {
                lastParticleQualityPrompt = DianaEnv.now();
                DianaEnv.world.chat("§cError detecting Diana Guess! §eRun §b/pq extreme §eto set the particle quality to extreme, then try again.");
                hasSetParticleQuality = true;
                errorCounter = 0;
            }
        }
    }

    // DianaFixChat.onItemClick (hub)
    public static void onItemClick(boolean rightClick) {
        if (!fixChatEnabled()) return;
        if (!rightClick) return;
        if (!DianaApi.hasSpadeInHand()) return;
        if (DianaEnv.passedSince(lastShovelSwing) > 5000) {
            lastShovelSwing = DianaEnv.now();
            foundGuess = false;
        }
    }

    // DianaFixChat.onBurrowGuess
    public static void onBurrowGuess() {
        foundGuess = true;
        if (hasSetParticleQuality) DianaEnv.world.chat("Changing the particle quality worked, good job!");
        hasSetParticleQuality = false;
        errorCounter = 0;
        // only count successes after new spade clicks, not the repeated moved guesses
        if (lastGuessPoint != lastShovelSwing) {
            lastGuessPoint = DianaEnv.now();
            successfulCounter++;
        }
    }

    public static void onWorldChange() {
        successfulCounter = 0;
        highlighted.clear();
    }

    private static boolean fixChatEnabled() {
        return DianaApi.isDoingDiana() && Diana.guess() && Diana.b("fixChat", true);
    }

    // HighlightRareDianaMob.onRareDianaMobFound
    public static void onRareDianaMobFound(RareMobWaypointShare.Mob mob) {
        if (!Diana.b(Diana.SHARE, "highlight", true)) return;
        highlighted.add(mob);
    }

    // outline around every living rare mob (RenderLivingEntityHelper colour 85,255,255,127)
    public static void render(WorldDraw w) {
        if (!Diana.b(Diana.SHARE, "highlight", true)) return;
        highlighted.removeIf(m -> m.dead() || !m.exists());
        int color = Diana.color(Diana.SHARE, "highlightColor", 0x7F55FFFF);
        for (RareMobWaypointShare.Mob m : highlighted) {
            var p = m.pos();
            w.box(new net.minecraft.world.phys.AABB(p.x - 0.4, p.y, p.z - 0.4, p.x + 0.4, p.y + 2.0, p.z + 0.4), color, true, false);
        }
    }

    public static void reset() {
        wasCorrectPetAlready = false;
        lastWarnTime = DianaEnv.FAR_PAST;
        hasSetParticleQuality = false;
        errorCounter = successfulCounter = 0;
        lastShovelSwing = lastErrorTime = lastGuessPoint = DianaEnv.FAR_PAST;
        foundGuess = false;
        highlighted.clear();
    }
}
