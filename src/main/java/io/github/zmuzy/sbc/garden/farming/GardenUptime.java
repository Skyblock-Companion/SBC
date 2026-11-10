// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import io.github.zmuzy.sbc.garden.milestones.GardenMilestones;
import net.minecraft.client.Minecraft;

import java.time.LocalDate;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni GardenUptimeCommand + GardenUptimeManager: how long you farmed each day
// a second counts if you broke a crop within the time you set before it (5 min like theirs). their
// crop/pest/visitor split is for their trackers, not this list
public final class GardenUptime {
    public static final String ID = "garden_uptime";
    private static long nextSecond, lastSave;

    private GardenUptime() {}

    // the command's chat lines, today backwards
    static String[] report(Map<String, Long> days, LocalDate today, int count) {
        String[] out = new String[count + 3];
        out[0] = "§eGarden uptime for the past §a" + count + " §edays:";
        long total = 0;
        for (int i = 0; i < count; i++) {
            LocalDate day = today.minusDays(i);
            long seconds = days.getOrDefault(day.toString(), 0L);
            total += seconds;
            out[i + 1] = "    §e" + (i == 0 ? "Today" : day.toString()) + ":    §b" + SbTime.dur(seconds * 1000);
        }
        out[count + 1] = "§bTotal Uptime: §e" + SbTime.dur(total * 1000);
        out[count + 2] = "§bAverage Uptime: §e" + SbTime.dur(total * 1000 / count);
        return out;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextSecond || mc.player == null) return;
        nextSecond = now + 1000;
        if (!GardenPack.garden() || now - GardenCropSpeed.lastBrokenTime > GardenPack.number(ID, "afk", 300) * 1000L) return;
        GardenMilestones.profile().uptime.merge(LocalDate.now().toString(), 1L, Long::sum);
        if (now - lastSave > 60_000) {
            lastSave = now;
            Store.markDirty();
        }
    }

    private static int command(int days) {
        if (!Features.on(ID)) {
            GardenPack.chat("§cTurn on Garden uptime in the settings first.");
            return 1;
        }
        for (String line : report(GardenMilestones.profile().uptime, LocalDate.now(), days)) GardenPack.chat(line);
        return 1;
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Garden uptime", "Counts how long you farm each day. /sbc gardenuptime lists the days.", "SkyHanni: Garden Uptime")
                .when(GardenPack::garden).defaultOff().tick(GardenUptime::tick)
                .option(Option.integer("garden_uptime.afk", "Away after (s)", "Time stops counting this long after the last crop you broke.", 300, 10, 3600)));
        Safe.commands("GardenUptime.commands", (d, r) -> d.register(literal("sbc").then(literal("gardenuptime").executes(c -> command(7))
                .then(argument("days", IntegerArgumentType.integer(1, 30)).executes(c -> command(IntegerArgumentType.getInteger(c, "days")))))));
        io.github.zmuzy.sbc.core.Commands.describe("gardenuptime", "How long you farmed each of the past days: [days]");
    }
}
