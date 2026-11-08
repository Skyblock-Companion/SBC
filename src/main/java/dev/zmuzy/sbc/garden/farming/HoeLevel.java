// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.CropType;
import dev.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni HoeLevelDisplay + MuteHoeLevelUp: level and xp of the tool in your hand, including levels
// past 50 that hypixel only shows by dropping a capsule
// levels + overflow from the repo's Garden.json
public final class HoeLevel {
    public static final String ID = "garden_hoe_level";
    private static final int OVERCLOCK_THRESHOLD = 40, MAX_LEVEL = 50;
    private static int[] levels;
    private static int overflowStep = 200_000;

    private HoeLevel() {}

    private static int[] levels() {
        if (levels != null) return levels;
        List<Integer> out = new ArrayList<>();
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : o.getAsJsonArray("hoe_exp_levels")) out.add(e.getAsInt());
                overflowStep = o.get("hoe_exp_overflow").getAsInt();
            }
        } catch (Exception e) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("No hoe_exp_levels in Garden.json", e);
        }
        levels = out.stream().mapToInt(Integer::intValue).toArray();
        return levels;
    }

    private static CompoundTag held() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        var data = mc.player.getMainHandItem().get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static int overflowOf(String uuid) {
        return GardenMilestones.profile().overflowHoeLevels.getOrDefault(uuid, 0);
    }

    // card text from the tool's own numbers
    private static RichCard.Content content(long exp, int level, int overflow, boolean wrongCrop) {
        int[] all = levels();
        long next = level >= 1 && level <= all.length ? all[level - 1] : overflowStep;
        int shown = level > all.length ? level + overflow : level;
        List<RichCard.Line> out = new ArrayList<>();
        double p = next <= 0 ? 0 : Math.min(1, (double) exp / next);
        boolean over = exp > next;
        out.add(RichCard.Line.row("Level", "§8" + shown + "➜§3" + (shown + 1)));
        out.add(RichCard.Line.row("Experience", (over ? "§c§l" : "§e") + String.format(Locale.ROOT, "%,d", exp) + "§8/§e" + String.format(Locale.ROOT, "%,d", next) + "  §7" + RowText.percent(p)).meter(p, Modern.AQUA).brief("XP"));
        if (over) out.add(RichCard.Line.row(shown >= OVERCLOCK_THRESHOLD ? "§3§lOVERCLOCK REQUIRED!" : "§c§lUPGRADE REQUIRED!", "").key("Upgrade"));
        if (wrongCrop) out.add(RichCard.Line.row("§cNot gaining XP! (Wrong crop)", "").key("Wrong crop"));
        return new RichCard.Content("", out);
    }

    private static final RichCard CARD = new RichCard(ID, "Hoe level", "Garden", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            CompoundTag t = held();
            return GardenPack.garden() && t != null && t.contains("levelable_exp") && t.contains("levelable_lvl");
        }

        @Override
        protected Content live() {
            CompoundTag t = held();
            if (t == null) return new Content("", List.of());
            CropType broken = GardenCropSpeed.lastBrokenCrop, hand = GardenMilestones.cropInHand();
            int overflow = GardenPack.on(ID, "overflow") ? overflowOf(t.getStringOr("uuid", "")) : 0;
            return content((long) t.getDoubleOr("levelable_exp", 0), t.getIntOr("levelable_lvl", 1), overflow, broken != null && hand != null && broken != hand);
        }

        @Override
        protected Content sample() {
            return content(98_400, 16, 0, false);
        }

        @Override
        protected List<Row> classicRows() {
            CompoundTag t = held();
            return t == null ? List.of() : List.of(new Row("Level", String.valueOf(t.getIntOr("levelable_lvl", 1)), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Level", "16", 0));
        }
    };

    // onChat: a capsule dropped so the tool went one level past
    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!GardenPack.garden()) return ChatBus.PASS;
        Matcher m = Patterns.get("SH.hoe.levels.levelup-nocolor").matcher(GameState.strip(line.plain()));
        if (!m.matches()) return ChatBus.PASS;
        var mc = Minecraft.getInstance();
        CompoundTag t = held();
        if (t == null || mc.player == null) return ChatBus.PASS;
        ItemStack st = mc.player.getMainHandItem();
        if (!GameState.strip(st.getHoverName().getString()).contains(m.group("tool"))) return ChatBus.PASS;
        String uuid = t.getStringOr("uuid", "");
        if (uuid.isEmpty()) return ChatBus.PASS;
        GardenMilestones.profile().overflowHoeLevels.put(uuid, overflowOf(uuid) + 1);
        Store.markDirty();
        return ChatBus.PASS;
    }

    private static int setLevel(int level) {
        CompoundTag t = held();
        String uuid = t == null ? "" : t.getStringOr("uuid", "");
        if (uuid.isEmpty() || !t.contains("levelable_lvl")) {
            GardenPack.chat("§cHold a specialized farming tool to use this.");
            return 1;
        }
        if (t.getIntOr("levelable_lvl", 0) < MAX_LEVEL) {
            GardenPack.chat("§cTools below level " + MAX_LEVEL + " cannot have overflow levels.");
            return 1;
        }
        if (level < MAX_LEVEL) {
            GardenPack.chat("§cAn overflow level cannot be below " + MAX_LEVEL + ".");
            return 1;
        }
        GardenMilestones.profile().overflowHoeLevels.put(uuid, level - MAX_LEVEL);
        Store.markDirty();
        GardenPack.chat("§7Set the overflow level of this tool to §b" + level + "§7.");
        return 1;
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Hoe level", "The level of the tool in your hand and the experience to the next one, also past level 50.", "SkyHanni: Hoe Levels Display")
                .when(GardenPack::garden).defaultOff()
                .option(Option.bool("garden_hoe_level.overflow", "Count past level 50", "From the capsules it drops. /sbc sethoelevel sets it by hand.", true))
                .option(Option.bool("garden_hoe_level.mute", "Mute the level up sound", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 20, ID, f, HoeLevel::onChat);
        SoundHooks.listen(s -> Features.on(ID) && GardenPack.garden() && GardenPack.on(ID, "mute") && s.pitch() == 1.4920635f && s.name().equals("block.portal.travel"));
        Safe.commands("HoeLevel.commands", (d, r) -> d.register(literal("sbc").then(literal("sethoelevel").then(argument("level", IntegerArgumentType.integer()).executes(c -> setLevel(IntegerArgumentType.getInteger(c, "level")))))));
        dev.zmuzy.sbc.core.Commands.describe("sethoelevel", "Set the overflow level of the tool in your hand");
        HudLayout.add(CARD);
    }
}
