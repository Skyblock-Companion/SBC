// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.pests;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

// from skyblocker GardenGlowAdder: every pest on the garden stands out, green for the running contest's crop,
// blue for the vinyl you play
// skyblocker makes them glow, here they get a box like other mobs. shows through walls so off by default
public final class PestHighlight {
    public static final String ID = "garden_pest_highlight";
    private static final int PEST = 0xFFB62F00, CONTEST = 0xFF55FF55, VINYL = 0xFF00AAAA;
    private static final List<ArmorStand> PESTS = new ArrayList<>();
    private static long nextScan;

    private PestHighlight() {}

    private static String texture(ItemStack head) {
        var profile = head.get(DataComponents.PROFILE);
        if (profile == null) return null;
        try {
            for (var pr : profile.partialProfile().properties().get("textures")) return pr.value();
        } catch (Exception ignored) {
        }
        return null;
    }

    // computeColour: contest first, then vinyl outside a contest
    static int colour(String crop, String contest, String vinylCrop, boolean contestOn, boolean vinylOn) {
        if (contestOn && contest != null) return contest.equals(crop) ? CONTEST : PEST;
        if (vinylOn && vinylCrop != null && vinylCrop.equals(crop)) return VINYL;
        return PEST;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextScan || mc.level == null) return;
        nextScan = now + 500;
        PESTS.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand st)) continue;
            String tex = texture(st.getItemBySlot(EquipmentSlot.HEAD));
            if (tex != null && PestHeads.ALL.contains(tex)) PESTS.add(st);
        }
    }

    private static void draw(WorldDraw w) {
        if (PESTS.isEmpty()) return;
        String contest = StereoHarmony.contestCrop(GameState.scoreboard);
        PestData vinyl = StereoHarmony.active();
        String vinylCrop = vinyl == null || vinyl.crop == null ? null : vinyl.crop.cropName;
        boolean contestOn = GardenPack.on(ID, "contest"), vinylOn = GardenPack.on(ID, "vinyl");
        for (ArmorStand st : PESTS) {
            if (st.isRemoved()) continue;
            String crop = PestHeads.CROP_BY_HEAD.get(texture(st.getItemBySlot(EquipmentSlot.HEAD)));
            int c = colour(crop, contest, vinylCrop, contestOn, vinylOn);
            double x = st.getX(), y = st.getY() + 1.4, z = st.getZ();
            w.box(new AABB(x - 0.4, y, z - 0.4, x + 0.4, y + 0.8, z + 0.4), c, false, true);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Pest highlight", "A box around each pest on the Garden, through walls: green for the running contest's crop, blue for the vinyl you play.", "Skyblocker: Pest Highlighter")
                .when(GardenPack::garden).defaultOff().tick(PestHighlight::tick).world(PestHighlight::draw).left(PESTS::clear)
                .option(Option.bool("garden_pest_highlight.vinyl", "Pick out the vinyl's pests", "Blue, outside of a contest.", true))
                .option(Option.bool("garden_pest_highlight.contest", "Pick out the contest's pests", "Green, during Jacob's contest.", true)));
    }
}
