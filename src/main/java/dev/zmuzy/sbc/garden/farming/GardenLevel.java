// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni GardenLevelDisplay + GardenApi level sums: garden level + xp in it, read from the desk and
// skyblock menu and counted up from visitor rewards
// past 15 hypixel stops counting so overflow levels are 10,000 each
public final class GardenLevel {
    public static final String ID = "garden_level";
    private static final int OVERFLOW_EXP = 10_000, MAX_LEVEL = 15;
    private static int[] tiers;
    private static int lastMenu = -1;

    private GardenLevel() {}

    private static int[] tiers() {
        if (tiers != null) return tiers;
        List<Integer> out = new ArrayList<>();
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in != null) for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("garden_exp")) out.add(e.getAsInt());
        } catch (Exception e) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("No garden_exp in Garden.json", e);
        }
        tiers = out.stream().mapToInt(Integer::intValue).toArray();
        return tiers;
    }

    // GardenApi.getExpForLevel
    static long expForLevel(int[] tiers, int level) {
        long total = 0;
        int tier = 0;
        for (int t : tiers) {
            total += t;
            tier++;
            if (tier == level) return total;
        }
        while (tier < level) {
            total += OVERFLOW_EXP;
            tier++;
            if (tier == level) return total;
        }
        return 0;
    }

    // GardenApi.getGardenLevel
    static int level(int[] tiers, long exp, boolean overflow) {
        int tier = 0;
        long total = 0;
        for (int t : tiers) {
            total += t;
            if (total > exp) return tier;
            tier++;
        }
        if (overflow) {
            total += OVERFLOW_EXP;
            while (total < exp) {
                tier++;
                total += OVERFLOW_EXP;
            }
        }
        return tier;
    }

    // onInventoryFullyOpened: xp the desk's or menu's item says, -1 if none
    static long readExp(String cleanName, List<String> lore, int[] tiers) {
        if (!Patterns.get("SH.garden.level.inventory.name").matcher(cleanName).matches()) return -1;
        long next = 0;
        int current = 0;
        for (String line : lore) {
            Matcher m = Patterns.get("SH.garden.level.inventory.levelprogress").matcher(line);
            if (m.matches()) {
                String n = m.group("currentLevel").trim();
                current = (n.matches("\\d+") ? Integer.parseInt(n) : GameState.roman(n)) - 1;
            }
            if (line.equals("§7§8Max level reached!")) current = MAX_LEVEL;
            m = Patterns.get("SH.garden.level.inventory.nextxp").matcher(line);
            if (m.matches()) next = parse(m.group("nextLevelExp"));
            m = Patterns.get("SH.garden.level.inventory.overflow").matcher(line);
            if (m.matches()) return parse(m.group("overflow"));
        }
        return expForLevel(tiers, current) + next;
    }

    private static long parse(String s) {
        String t = GameState.strip(s).replace(",", "").trim().toLowerCase(Locale.ROOT);
        double mul = t.endsWith("k") ? 1e3 : t.endsWith("m") ? 1e6 : 1;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return (long) (Double.parseDouble(t) * mul);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!GardenPack.garden()) return ChatBus.PASS;
        Matcher m = Patterns.get("SH.garden.level.chat.increase").matcher(LegacyText.of(line.component()));
        if (!m.matches()) return ChatBus.PASS;
        Store.GardenProfile s = GardenMilestones.profile();
        if (s.experience < 0) return ChatBus.PASS;
        int old = level(tiers(), s.experience, true);
        s.experience += parse(m.group("exp"));
        Store.markDirty();
        int now = level(tiers(), s.experience, true);
        if (GardenPack.on(ID, "overflow_chat") && now == old + 1 && now > MAX_LEVEL) {
            var mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(" \n§b§lGARDEN LEVEL UP §8" + old + " ➜ §b" + now + "\n "));
        }
        return ChatBus.PASS;
    }

    // tab says "Garden Level: 10 (71%)", use that for the xp when nothing better was read or what we read is stale
    // (level ups, farming + pest xp the chat never mentions). the desk's exact number wins while it agrees with the tab
    // at max level the tab has no percent, chat + desk keep counting
    static long fromTab(int[] tiers, int level, double share, long known) {
        if (level < 0 || level >= MAX_LEVEL) return known;
        long base = expForLevel(tiers, level), step = expForLevel(tiers, level + 1) - base;
        if (step <= 0) return known;
        long guess = base + Math.round(Math.max(0, Math.min(1, share)) * step);
        if (known < 0 || level(tiers, known, false) != level) return guess;
        return Math.abs((double) (known - base) / step - share) > 0.015 ? guess : known;
    }

    private static long tabAt;

    private static void tab() {
        long now = System.currentTimeMillis();
        if (now - tabAt < 2_000) return;
        tabAt = now;
        double[] t = dev.zmuzy.sbc.garden.FarmingCard.gardenLevel();
        if (t == null) return;
        Store.GardenProfile s = GardenMilestones.profile();
        long exp = fromTab(tiers(), (int) t[0], t[1], s.experience);
        if (exp != s.experience) {
            s.experience = exp;
            Store.markDirty();
        }
    }

    private static void tick(Minecraft mc) {
        if (GardenPack.garden()) tab();
        var screen = mc.gui.screen();
        if (mc.player == null || screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            lastMenu = -1;
            return;
        }
        if (!GardenPack.garden() || menu.containerId == lastMenu) return;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        int slot = title.equals("Desk") ? 4 : title.equals("SkyBlock Menu") ? 10 : -1;
        if (slot < 0 || menu.slots.get(slot).getItem().isEmpty()) return;
        lastMenu = menu.containerId;
        ItemStack st = menu.slots.get(slot).getItem();
        long exp = readExp(LegacyText.cleanName(st), LegacyText.lore(st), tiers());
        if (exp < 0) return;
        GardenMilestones.profile().experience = exp;
        Store.markDirty();
    }

    // onToolTip: past the last level the item says Max level reached, we keep counting
    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (!GardenPack.garden() || !GardenPack.on(ID, "overflow")) return;
        var screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        if (!title.equals("Desk") && !title.equals("SkyBlock Menu")) return;
        if (!Patterns.get("SH.garden.level.inventory.name").matcher(LegacyText.cleanName(stack)).matches()) return;
        long exp = GardenMilestones.profile().experience;
        int[] t = tiers();
        int level = level(t, exp, true);
        if (exp < 0 || level < MAX_LEVEL) return;
        long need = expForLevel(t, level), overflow = exp - need, total = exp - expForLevel(t, MAX_LEVEL), step = expForLevel(t, level + 1) - need;
        boolean next = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).getString();
            if (Patterns.get("SH.garden.level.inventory.max.new").matcher(l).matches()) {
                lines.set(i, Component.literal("§7Progress to Level " + (level + 1)));
                next = true;
            } else if (next && l.contains("                    ")) {
                lines.set(i, Component.literal(MilestoneMenu.bar((double) overflow / step, 20) + " §e" + String.format(Locale.ROOT, "%,d", overflow) + "§6/§e" + dev.zmuzy.sbc.core.Prices.coins(step)));
                lines.add(i + 1, Component.empty());
                lines.add(i + 2, Component.literal("§b§lOVERFLOW XP:"));
                lines.add(i + 3, Component.literal("§7▸ " + String.format(Locale.ROOT, "%,d", total)));
                return;
            }
        }
    }

    private static List<RichCard.Line> lines(int level, boolean max, long have, long need) {
        List<RichCard.Line> out = new ArrayList<>();
        if (max) {
            out.add(RichCard.Line.row("Level " + level, "§e" + String.format(Locale.ROOT, "%,d", have)).key("Level"));
            return out;
        }
        double p = need <= 0 ? 0 : (double) have / need;
        out.add(RichCard.Line.row("Level " + level, "§e" + String.format(Locale.ROOT, "%,d", have) + "§7/§e" + String.format(Locale.ROOT, "%,d", need) + "  §7" + RowText.percent(p)).meter(p, 0xFF9CE37D).key("Level"));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Garden level", "Garden", Zone.TOP_LEFT, 30, ID) {
        @Override
        public boolean visible() {
            return GardenPack.garden();
        }

        @Override
        protected Content live() {
            long exp = GardenMilestones.profile().experience;
            if (exp < 0) return new Content("", List.of(RichCard.Line.row("Open the Desk", "§7or turn on the Garden Level tab widget").key("Level")));
            int[] t = tiers();
            boolean overflow = GardenPack.on(ID, "overflow");
            int level = level(t, exp, overflow);
            long need = expForLevel(t, level);
            return new Content("", lines(level, !overflow && level == MAX_LEVEL, exp - need, expForLevel(t, level + 1) - need));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(17, false, 7_891, 10_000));
        }

        @Override
        protected List<Row> classicRows() {
            long exp = GardenMilestones.profile().experience;
            return List.of(new Row("Level", exp < 0 ? "?" : String.valueOf(level(tiers(), exp, true)), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Level", "17", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Garden level", "Your Garden level and how far into it you are, also past the last level. Read from the tab's Garden Level widget, the Desk and the Garden Experience you get.", "SkyHanni: Garden Level")
                .when(GardenPack::garden).tick(GardenLevel::tick).tooltip(GardenLevel::tooltip)
                .option(Option.bool("garden_level.overflow", "Count past the last level", "On the card and in the Desk's tooltip.", true))
                .option(Option.bool("garden_level.overflow_chat", "Say overflow levels in chat", "When you reach one.", true)));
        ChatBus.register(ChatBus.OBSERVE + 19, ID, f, GardenLevel::onChat);
        HudLayout.add(CARD);
    }
}
