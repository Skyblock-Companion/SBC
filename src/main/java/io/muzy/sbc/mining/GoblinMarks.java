// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// mobs + small sounds in the mines. from skyhanni GoldenGoblinHighlight (the goblin chat just said is yours,
// spawned within 10 sec of the message)
// GoblinRaidFeatures (superprotectron), HighHeatSound, MuteOrganDonor
// skyhanni finds mobs by name tag, same here from the armor stand that has it. box where skyhanni tints
public final class GoblinMarks {
    static final String GOBLIN = "mining_goblin", RAID = "mining_goblin_raid", MUTES = "mining_mutes";
    private static final long TIME_OUT = 10_000;
    private static final Map<Integer, Long> FIRST_SEEN = new HashMap<>();
    private static final List<ArmorStand> SUPER = new ArrayList<>();
    private static ArmorStand yours;
    private static long lastChat = MiningEnv.FAR_PAST, nextScan;

    private GoblinMarks() {}

    private static AABB box(Entity stand) {
        double x = stand.getX(), y = stand.getY(), z = stand.getZ();
        return new AABB(x - 0.4, y - 2, z - 0.4, x + 0.4, y, z + 0.4);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String m = LegacyText.of(line.component());
        if (MiningNotifications.GOLDEN_GOBLIN.matcher(m).matches() || MiningNotifications.DIAMOND_GOBLIN.matcher(m).matches()) lastChat = MiningEnv.now();
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextScan || mc.level == null) return;
        nextScan = now + 250;
        SUPER.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand st) || !st.hasCustomName()) continue;
            String name = GameState.strip(st.getCustomName().getString());
            if (name.contains("Superprotectron")) SUPER.add(st);
            if (!name.contains("Golden Goblin") && !name.contains("Diamond Goblin")) continue;
            Long first = FIRST_SEEN.get(st.getId());
            if (first == null) {
                first = MiningEnv.now();
                FIRST_SEEN.put(st.getId(), first);
            }
            // skyhanni handle(): chat line and this mob go together when both are fresh
            if (yours == null && MiningEnv.passedSince(lastChat) <= TIME_OUT && MiningEnv.passedSince(first) <= TIME_OUT) {
                yours = st;
                lastChat = MiningEnv.FAR_PAST;
            }
        }
        if (yours != null && yours.isRemoved()) yours = null;
    }

    private static void draw(WorldDraw w) {
        if (yours == null || yours.isRemoved()) return;
        w.box(box(yours), 0xFF55FF55, false, false);
        if (MiningPack.on(GOBLIN, "mining_goblin.line")) w.line(w.eye.add(w.mc.player.getLookAngle()).add(0, -0.2, 0), new Vec3(yours.getX(), yours.getY() - 1, yours.getZ()), 0xFF55FF55, 2f, false);
    }

    private static void drawRaid(WorldDraw w) {
        int color = MiningPack.color(RAID, "mining_goblin_raid.color", 0x58FFFF55);
        for (ArmorStand st : SUPER) if (!st.isRemoved()) w.filledBox(box(st), color & 0xFFFFFF, ((color >>> 24) & 0xFF) / 255f, false);
    }

    private static void leave() {
        FIRST_SEEN.clear();
        SUPER.clear();
        yours = null;
        lastChat = MiningEnv.FAR_PAST;
    }

    // skyhanni HighHeatSound + MuteOrganDonor
    private static boolean onSound(SoundHooks.Sound s) {
        if (MiningApi.inCrystalHollows() && MiningPack.on(MUTES, "mining_mutes.high_heat") && MiningEnv.world.playerPos().y <= 65 && MiningApi.heat >= 90
                && s.name().equals("entity.wolf.pant") && s.pitch() == 0f && s.volume() == 1f) return true;
        return MiningApi.inMineshaft() && MiningPack.on(MUTES, "mining_mutes.organ_donor") && Mineshaft.allCorpsesFound() && s.name().equals("block.note_block.harp");
    }

    public static void register() {
        Feature f = Features.add(new Feature(GOBLIN, "Mining", "Your Golden Goblin", "A box around the Golden or Diamond Goblin you just spawned, where you can see it.", "SkyHanni: Highlight your Golden Goblin, Line to your Golden Goblin")
                .when(MiningPack::mining).tick(GoblinMarks::tick).world(GoblinMarks::draw).left(GoblinMarks::leave)
                .option(Option.bool("mining_goblin.line", "Line to it", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 27, GOBLIN, f, GoblinMarks::onChat);
        Features.add(new Feature(RAID, "Mining", "Superprotectron", "Colours the Superprotectron during a Goblin Raid, where you can see it.", "SkyHanni: Superprotectron Highlight")
                .when(MiningPack::mining).tick(GoblinMarks::tick).world(GoblinMarks::drawRaid).left(SUPER::clear)
                .option(Option.color("mining_goblin_raid.color", "Colour", "", 0x58FFFF55)));
        Features.add(new Feature(MUTES, "Mining", "Quieter mines", "Takes away the panting at high Heat in the Crystal Hollows, and the Organ Donor's note once every corpse in a Mineshaft is found.", "SkyHanni: Mute High Heat, Mute when all Corpses found").defaultOff()
                .when(MiningPack::mining)
                .option(Option.bool("mining_mutes.high_heat", "High Heat", "", true))
                .option(Option.bool("mining_mutes.organ_donor", "Organ Donor Accessory", "", true)));
        SoundHooks.listen(GoblinMarks::onSound);
    }
}
