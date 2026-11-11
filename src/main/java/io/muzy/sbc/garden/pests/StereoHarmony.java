// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.pests;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni StereoHarmonyDisplay, StereoHarmonyDiscReplacer, MuteStereoPants + skyblocker
// StereoHarmonyHelper, VacuumCache: the playing vinyl and the pest + crop it boosts, from the stereo harmony
// menu and the vacuum
// in the menu each disc gets its crop in the corner (skyhanni swaps the item for it), the running contest's
// disc is green, yellow if already playing
public final class StereoHarmony {
    public static final String ID = "garden_stereo";
    private static final Pattern PEST_NAME = Pattern.compile("^When playing, (?<name>.+) Pests.*");
    private static final Set<String> PANTS_SOUNDS = Set.of("block.note_block.basedrum", "block.note_block.bass", "block.note_block.harp", "block.note_block.hat", "block.note_block.snare");
    private static long pantsSeen, nextRead;

    private StereoHarmony() {}

    // updateActiveVinyl: what the lore's "Now Playing" says. "" = no such line, "None" = nothing playing
    static String playing(List<String> lore) {
        for (String l : lore) {
            Matcher m = Patterns.get("SH.garden.pests-api.stereo.playing").matcher(l);
            if (m.matches()) return m.group("vinyl").trim();
        }
        return "";
    }

    private static void update(List<String> lore) {
        String vinyl = playing(lore);
        if (vinyl.isEmpty()) return;
        Store.GardenProfile s = GardenMilestones.profile();
        String next = PestData.byVinyl(vinyl) == null ? null : vinyl;
        if (java.util.Objects.equals(next, s.activeVinyl)) return;
        s.activeVinyl = next;
        Store.markDirty();
    }

    public static PestData active() {
        String v = GardenMilestones.profile().activeVinyl;
        return v == null ? null : PestData.byVinyl(v);
    }

    // running contest's crop from the sidebar's "○ Wheat 15m2s", null if no contest. skyblocker CurrentJacobCrop
    public static String contestCrop(List<String> scoreboard) {
        boolean contest = false;
        for (String l : scoreboard) if (l.contains("Jacob's Contest")) contest = true;
        if (!contest) return null;
        for (String l : scoreboard) {
            Matcher m = Patterns.get("SH.garden.farming.contest.sidebarcrop.colorless").matcher(l);
            if (m.matches()) return m.group("crop").trim();
        }
        return null;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.player == null || now < nextRead) return;
        nextRead = now + 500;
        var screen = mc.gui.screen();
        if (screen != null && Patterns.get("SH.garden.pests-api.stereo.inventory").matcher(GameState.strip(screen.getTitle().getString()).trim()).matches()) {
            var slots = mc.player.containerMenu.slots;
            if (slots.size() > 4 && !slots.get(4).getItem().isEmpty()) update(LegacyText.lore(slots.get(4).getItem()));
        }
        if (GardenPack.vacuumInHand()) update(LegacyText.lore(mc.player.getMainHandItem()));
    }

    // the discs in the menu
    static void mark(MenuView v) {
        String contest = contestCrop(GameState.scoreboard);
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            PestData pest = PestData.byVinylId(it.id);
            if (pest == null) {
                // what skyblocker reads when the id says nothing
                Matcher m = it.firstClean(PEST_NAME);
                if (m != null) pest = PestData.byName(m.group("name").trim());
            }
            if (pest == null || pest.crop == null) continue;
            if (MenuMarks.setting(ID, "menu_icons")) v.mark(slot).badge = LegacyItems.stack(pest.crop.icon());
            if (MenuMarks.setting(ID, "contest") && pest.crop.cropName.equals(contest)) v.back(slot, it.cleanHas("PLAYING") ? MenuView.YELLOW : MenuView.GREEN);
        }
    }

    private static boolean isCentered(double c) {
        return Math.abs(c % 1) == 0.5;
    }

    // MuteStereoPants
    private static boolean onSound(SoundHooks.Sound s) {
        if (!GardenPack.on(ID, "mute_pants") || !Features.on(ID) || !GardenPack.skyblock()) return false;
        if (!PANTS_SOUNDS.contains(s.name()) || s.pitch() < 0.4920635f || s.pitch() > 2f) return false;
        if (!isCentered(s.x()) || !isCentered(s.y()) || !isCentered(s.z())) return false;
        long now = System.currentTimeMillis();
        if (now - pantsSeen < 1000) return true;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        for (Player p : mc.level.getEntitiesOfClass(Player.class, new AABB(s.x() - 5, s.y() - 5, s.z() - 5, s.x() + 5, s.y() + 5, s.z() + 5))) {
            if ("MUSIC_PANTS".equals(io.muzy.sbc.core.Gain.sbId(p.getItemBySlot(EquipmentSlot.LEGS)))) {
                pantsSeen = now;
                return true;
            }
        }
        return false;
    }

    private static List<RichCard.Line> lines(PestData pest) {
        List<RichCard.Line> out = new ArrayList<>();
        if (pest == null) {
            out.add(RichCard.Line.row("Playing", "§7Nothing"));
            return out;
        }
        out.add(RichCard.Line.row("Playing", "§a" + pest.vinyl));
        out.add(RichCard.Line.row("Pest", "§c" + pest.pest));
        if (pest.crop != null) out.add(RichCard.Line.row("Crop", "§e" + pest.crop.cropName));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Stereo Harmony", "Garden", Zone.TOP_LEFT, 31, ID) {
        @Override
        public boolean visible() {
            if (!GardenPack.garden()) return false;
            if (!GardenMilestones.isCurrentlyFarming() && !GardenPack.on(ID, "always")) return false;
            return StereoHarmony.active() != null || !GardenPack.on(ID, "hide_none");
        }

        @Override
        protected Content live() {
            return new Content("", lines(StereoHarmony.active()));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(PestData.MOTH));
        }

        @Override
        protected List<Row> classicRows() {
            PestData p = StereoHarmony.active();
            return p == null ? List.of(new Row("Playing", "Nothing", 0)) : List.of(new Row("Playing", p.vinyl, 0), new Row("Pest", p.pest, 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Playing", "Wings of Harmony", 0), new Row("Pest", "Moth", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Stereo Harmony", "The vinyl your vacuum plays and the pest and crop it boosts. In its menu, each disc shows its crop, and the running contest's crop is picked out. Open the menu or hold the vacuum once to set it.", "SkyHanni: Stereo Harmony · Skyblocker: Stereo Harmony Helper")
                .when(GardenPack::skyblock).tick(StereoHarmony::tick)
                .option(Option.bool("garden_stereo.always", "Show when not farming", "", false))
                .option(Option.bool("garden_stereo.hide_none", "Hide without a vinyl", "", true))
                .option(Option.bool("garden_stereo.menu_icons", "Crops on the discs", "In the Stereo Harmony menu, the crop of each disc in its corner.", true))
                .option(Option.bool("garden_stereo.contest", "Pick out the contest's crop", "In the Stereo Harmony menu during Jacob's contest.", true))
                .option(Option.bool("garden_stereo.mute_pants", "Mute Stereo Pants", "The music they play.", false)));
        MenuMarks.add(ID, Patterns.get("SH.garden.pests-api.stereo.inventory"), StereoHarmony::mark);
        SoundHooks.listen(StereoHarmony::onSound);
        HudLayout.add(CARD);
    }
}
