// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden;

import dev.zmuzy.sbc.core.CookieBuff;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.regex.Matcher;

// from skyhanni GrowthCycle: when the greenhouse's next growth stage comes, per profile, read from the crop
// diagnostics menu
// its one scoreboard line so its on every island without another floating thing, and the scoreboard's entry
// list can move it
public final class GrowthStage {
    private static final long STALE_MS = 60 * 60_000L;
    // stage chat already got told about
    private static long notified;

    private GrowthStage() {}

    // the "Next Stage: 1h 40m 20s" in these lines if theres one
    static void read(List<String> lore, long now) {
        for (String raw : lore) {
            Matcher m = Patterns.get("SH.garden.greenhouse.growthcycle.nextstage").matcher(GameState.strip(raw).trim());
            if (!m.matches()) continue;
            long left = CookieBuff.parse(m.group("time"));
            if (left < 0) return;
            Store.I.growthStageAt.put(GameState.profileName, now + left);
            Store.markDirty();
            return;
        }
    }

    // skyhanni's timerColor
    private static String colour(long left) {
        return left <= 60_000 ? "§c" : left <= 180_000 ? "§6" : left <= 600_000 ? "§e" : "§a";
    }

    // scoreboard line, "" = none. an hour after ready the time means nothing so the menu has to be opened again
    static String line(long now, boolean onlyReady) {
        Long at = Store.I.growthStageAt.get(GameState.profileName);
        String label = "§aGrowth Stage§f: ";
        if (at != null && at <= now && now - at <= STALE_MS) return label + "§aREADY";
        if (onlyReady) return "";
        if (at == null || at <= now) return label + "§7Open Diagnostics";
        return label + colour(at - now) + SbTime.dur(at - now);
    }

    private static boolean option(String key, boolean def) {
        Feature f = Features.get("garden_growth");
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    public static String line() {
        return Features.on("garden_growth") ? line(System.currentTimeMillis(), option("only_ready", false)) : "";
    }

    // does the stage that came at this moment still need announcing, and its not an old one
    static boolean shouldNotify(long at, long now, long alreadyTold) {
        return at <= now && now - at <= STALE_MS && at != alreadyTold;
    }

    public static void tick(Minecraft mc) {
        if (!Features.on("garden_growth") || mc.player == null) return;
        long now = System.currentTimeMillis();
        var screen = mc.gui.screen();
        if (screen != null && "GARDEN".equals(GameState.island)
                && Patterns.get("SH.garden.greenhouse.growthcycle.inventory").matcher(GameState.strip(screen.getTitle().getString()).trim()).matches()) {
            for (var slot : mc.player.containerMenu.slots) if (!slot.getItem().isEmpty()) read(LegacyText.lore(slot.getItem()), now);
        }
        Long at = Store.I.growthStageAt.get(GameState.profileName);
        if (at == null || !shouldNotify(at, now, notified)) return;
        notified = at;
        if (!option("notify", true)) return;
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.9f, 1.2f);
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§aGreenhouse Growth Stage is ready in the Garden"));
    }

    public static void register() {
        Features.add(new Feature("garden_growth", "Garden", "Next growth stage", "A line on the scoreboard, on every island, with the time to your Greenhouse's next growth stage. Open the Crop Diagnostics menu once to set it. Move it with the scoreboard's entry list.", "SkyHanni: Growth Cycle Timer")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("garden_growth.only_ready", "Only when it is ready", "No line until the stage has come.", false))
                .option(Option.bool("garden_growth.notify", "Sound and chat when it is ready", "", true)));
    }
}
