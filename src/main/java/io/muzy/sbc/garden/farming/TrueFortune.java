// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import io.muzy.sbc.core.CookieBuff;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenCropSpeed;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni FarmingFortuneDisplay: true fortune for the crop (tab's Farming Fortune + that crop's), what
// pests take off it, the pesthunter bonus + warning when it runs out
// their tool tooltip breakdown is turned off in their own code, so nothing for it here
public final class TrueFortune {
    public static final String ID = "garden_true_fortune";
    private static double universal, crop;
    private static CropType tabCrop;
    private static boolean foundUniversal, foundCrop, warnedBonus = true;
    private static int bonusFortune;
    private static long bonusEnd, nextRead, lastToolSwitch, lastMissing;
    private static CropType lastTool;
    private static boolean chanceDisabled;

    private TrueFortune() {}

    // getPestFFReduction: pests past what bonus pest chance lets through take a share, every 100 chance forgives one
    static int pestReduction(int pests, double bonusChance) {
        int count = Math.max(0, pests - (int) Math.floor(bonusChance / 100));
        return switch (count) {
            case 0, 1, 2, 3 -> 0;
            case 4 -> 5;
            case 5 -> 15;
            case 6 -> 30;
            case 7 -> 50;
            default -> 75;
        };
    }

    private static int pestsAlive() {
        for (String l : GameState.scoreboardFormatted) {
            Matcher m = Patterns.get("SH.garden.pests-api.scoreboard.pests").matcher(l);
            if (m.matches()) try {
                return Integer.parseInt(GameState.strip(m.group("pests")).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static void readTab(long now) {
        for (String l : GameState.tab) {
            Matcher m = Patterns.get("SH.garden.fortunedisplay.tablist.universal-no-color").matcher(l);
            if (m.matches()) {
                universal = Double.parseDouble(m.group("fortune"));
                foundUniversal = true;
            }
            m = Patterns.get("SH.garden.fortunedisplay.tablist.cropspecific-no-color").matcher(l);
            if (m.matches()) {
                tabCrop = CropType.getByNameOrNull(m.group("crop"));
                crop = Double.parseDouble(m.group("fortune"));
                foundCrop = true;
            }
            m = Patterns.get("SH.garden.fortunedisplay.pestfortunebuff-no-color").matcher(l);
            if (m.matches()) {
                if (m.group("inactive") != null) {
                    bonusEnd = 0;
                    bonusFortune = 0;
                    if (!warnedBonus) expired();
                } else if (m.group("time") != null && m.group("fortune") != null) {
                    warnedBonus = false;
                    bonusFortune = Integer.parseInt(m.group("fortune"));
                    long left = CookieBuff.parse(m.group("time"));
                    if (left >= 0) bonusEnd = now + left;
                }
            }
        }
        CropType inHand = GardenMilestones.cropInHand();
        if (inHand != null && inHand == tabCrop && foundUniversal) {
            GardenMilestones.profile().latestTrueFortune.put(inHand.name(), universal + crop);
            Store.markDirty();
        }
    }

    // BonusPestChanceDisplay: hypixel strikes the stat through while it does nothing
    private static void readChance(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn == null) return;
        var pattern = Patterns.get("SH.garden.bonuspestchance.widget-no-color");
        for (var info : conn.getListedOnlinePlayers()) {
            Component name = info.getTabListDisplayName();
            if (name == null || !pattern.matcher(GameState.strip(name.getString())).matches()) continue;
            boolean struck = false;
            for (Component part : name.toFlatList()) if (part.getStyle().isStrikethrough()) struck = true;
            chanceDisabled = struck;
            return;
        }
    }

    // what the cards write for bonus pest chance
    public static String chanceText(String value) {
        return chanceDisabled && !value.isEmpty() ? value + " DISABLED" : value;
    }

    // pestBuffExpireWarning
    private static void expired() {
        warnedBonus = true;
        var mc = Minecraft.getInstance();
        if (GardenPack.on(ID, "expire_chat") && mc.player != null) {
            boolean phillip = GardenPack.on(ID, "call_phillip");
            String command = phillip ? "/call Phillip" : "/plottp barn";
            Style st = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to " + (phillip ? "call Phillip" : "teleport to the barn"))));
            mc.player.sendSystemMessage(Component.literal("§b[SBC] §cPest fortune buff has expired!").withStyle(st));
        }
        if (GardenPack.on(ID, "expire_title")) {
            GardenPack.title("§cPest Fortune Buff Has Expired!", 3);
            GardenPack.sound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f);
        }
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextRead || mc.player == null) return;
        nextRead = now + 500;
        CropType tool = GardenMilestones.cropInHand();
        if (tool != lastTool) {
            lastTool = tool;
            lastToolSwitch = now;
        }
        readTab(now);
        readChance(mc);
        // onTick: say it once in a while when the widget it reads is missing
        if (GardenPack.on(ID, "missing") && !foundUniversal && GardenMilestones.isCurrentlyFarming() && now - lastMissing > 20_000) {
            lastMissing = now;
            GardenPack.chat("§cNo Farming Fortune in the tab list. Open /widget, enable the Stats widget and show the Farming Fortune stat.");
        }
    }

    private static List<RichCard.Line> lines(CropType shown, String fortune, boolean known, int reduction, int bonus, long bonusLeft) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Farming Fortune", (reduction > 0 ? "§c" : "§e") + fortune).icon(shown.icon()).brief("FF"));
        if (!known) out.add(RichCard.Line.row("Break " + shown.cropName, "§7to update").key("Crop").brief("Break it"));
        if (GardenPack.on(ID, "show_bonus")) out.add(RichCard.Line.row("Bonus", bonusLeft > 0 ? "§6+" + bonus + "☘ §b" + SbTime.dur(bonusLeft) : "§cInactive"));
        if (reduction > 0) out.add(RichCard.Line.row("Pests", "§7-§e" + reduction + "%"));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "True fortune", "Garden", Zone.TOP_LEFT, 29, ID) {
        @Override
        public boolean visible() {
            return GardenPack.garden() && GardenMilestones.hasToolInHand() && (GardenMilestones.cropInHand() != null || tabCrop != null);
        }

        @Override
        protected Content live() {
            CropType inHand = GardenMilestones.cropInHand();
            CropType shown = inHand != null ? inHand : tabCrop != null ? tabCrop : CropType.WHEAT;
            boolean wrongTab = inHand != null && inHand != tabCrop;
            Double latest = GardenMilestones.profile().latestTrueFortune.get(shown.name());
            boolean switched = System.currentTimeMillis() - lastToolSwitch < 1500;
            String fortune = wrongTab || switched ? "§7" + (latest == null ? "?" : String.format(Locale.ROOT, "%,.0f", latest)) : String.format(Locale.ROOT, "%,.0f", universal + crop);
            long now = System.currentTimeMillis();
            return new Content("", lines(shown, fortune, !wrongTab, pestReduction(pestsAlive(), Vitals.stat("Bonus Pest Chance")), bonusFortune, bonusEnd - now));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(CropType.WHEAT, "1,482", true, 15, 200, 29 * 60_000L));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Farming Fortune", String.format(Locale.ROOT, "%,.0f", universal + crop), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Farming Fortune", "1,482", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "True fortune", "Your whole fortune for the crop of the tool in your hand, what pests take off it, and the Pesthunter's bonus. Needs the Stats widget in the tab list.", "SkyHanni: Farming Fortune Display")
                .when(GardenPack::garden).tick(TrueFortune::tick).left(() -> foundUniversal = foundCrop = false)
                .option(Option.bool("garden_true_fortune.show_bonus", "Show the Pesthunter's bonus", "How much and for how long.", true))
                .option(Option.bool("garden_true_fortune.missing", "Say when the tab list lacks it", "A chat line each 20 seconds while you farm.", true))
                .option(Option.bool("garden_true_fortune.expire_chat", "Warn in chat when the bonus ends", "You can click the line to go to the barn.", true))
                .option(Option.bool("garden_true_fortune.expire_title", "Title when the bonus ends", "With a sound.", false))
                .option(Option.bool("garden_true_fortune.call_phillip", "The warning calls Phillip", "Clicking the chat line calls him instead of going to the barn.", false)));
        HudLayout.add(CARD);
    }
}
