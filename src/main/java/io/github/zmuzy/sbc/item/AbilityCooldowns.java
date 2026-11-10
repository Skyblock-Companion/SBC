// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.value.ItemNbt;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.SoundHooks;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.Compact;
import io.github.zmuzy.sbc.ui.hud.ElementStyle;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.hud.Looks;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni ItemAbilityCooldown: most abilities are told by the sound right after the item gets clicked,
// a few by the action bar, some by chat
// time left shows on the item itself and on the card above the bars
public final class AbilityCooldowns {
    private static final String WHITE_CASTING = "§lCASTING IN ", CASTING = "§lCASTING", CANCELLED = "§c§lCANCELLED";
    private static final Pattern ALIGNED_OTHERS = Pattern.compile("^You aligned .* other players?!$");
    private static final Pattern BUFFED_YOURSELF = Pattern.compile("^You buffed yourself for \\+\\d+\\D? Strength$");
    private static final long RECENT_MS = 30_000;

    private record ItemText(char color, String text, boolean onCooldown) {}

    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final Map<ItemStack, String> IDENTITY = new IdentityHashMap<>();
    private static long identityAt;
    private static String lastAbility = "";
    private static Map<String, List<ItemAbility>> abilityItems = Map.of();
    private static Map<String, List<ItemText>> items = Map.of();
    private static Object lastLevel;
    private static int ticks;
    private AbilityCooldowns() {}

    // what started one

    private static boolean recentlyHeld(String id) {
        Long at = RECENT.get(id);
        return at != null && System.currentTimeMillis() - at < RECENT_MS;
    }

    // skyhanni onPlaySound. held = id in hand, scrolls = what it carries. both only matter for a few of these
    static void sound(String name, float volume, float pitch, String held, List<String> scrolls) {
        switch (name) {
            // wither shield solo + wither impact
            case "entity.zombie_villager.cure" -> {
                if (pitch == 0.6984127f && volume == 1f) {
                    Set<ItemAbility> all = ItemAbility.ofScrolls(scrolls);
                    if (all.size() == 1 && all.contains(ItemAbility.WITHER_IMPACT)) ItemAbility.WITHER_IMPACT.sound();
                    else for (ItemAbility a : all) {
                        if (a == ItemAbility.WITHER_SHIELD_SCROLL) a.activate((char) 0, 5_000);
                        a.sound();
                    }
                }
                if (pitch == 1.8888888f && volume == 0.7f) ItemAbility.TACTICAL_INSERTION.activate((char) 0, 17_000);
            }
            case "block.lava.extinguish" -> {
                if (pitch == 0.4920635f && volume == 1f && ItemAbility.ofScrolls(scrolls).contains(ItemAbility.SHADOW_WARP_SCROLL)) ItemAbility.SHADOW_WARP_SCROLL.sound();
            }
            case "block.lava.pop" -> {
                if (pitch == 1f && volume == 1f) ItemAbility.FIRE_FURY_STAFF.sound();
                if (pitch == 0.7619048f && volume == 0.15f) ItemAbility.WAND_OF_ATONEMENT.sound();
            }
            case "entity.ender_dragon.growl" -> {
                if (pitch == 1f && volume == 1f) ItemAbility.ICE_SPRAY_WAND.sound();
                if (pitch == 0.4920635f && volume == 2f) ItemAbility.ENRAGER.sound();
            }
            case "entity.enderman.teleport" -> {
                if (pitch == 0.61904764f && volume == 1f) ItemAbility.GYROKINETIC_WAND_LEFT.sound();
                if (held.equals("SHADOW_FURY") || held.equals("STARRED_SHADOW_FURY")) ItemAbility.SHADOW_FURY.sound();
            }
            case "block.anvil.land" -> {
                if (pitch == 0.4920635f && volume == 0.5f) ItemAbility.GIANTS_SWORD.sound();
            }
            case "entity.ghast.ambient" -> {
                if (pitch == 0.4920635f && volume == 0.15f) ItemAbility.ATOMSPLIT_KATANA.sound();
            }
            case "entity.bat.hurt" -> {
                if (volume == 0.1f) ItemAbility.STARLIGHT_WAND.sound();
            }
            case "entity.ghast.hurt" -> {
                if (volume == 1f && pitch >= 1.6 && pitch <= 1.7) {
                    if (recentlyHeld("VOODOO_DOLL")) ItemAbility.VOODOO_DOLL.sound();
                    else if (recentlyHeld("VOODOO_DOLL_WILTED")) ItemAbility.VOODOO_DOLL_WILTED.sound();
                }
            }
            case "entity.generic.explode" -> {
                if (pitch == 1f && volume == 1f && ItemAbility.ofScrolls(scrolls).contains(ItemAbility.IMPLOSION_SCROLL)) ItemAbility.IMPLOSION_SCROLL.sound();
                if (pitch == 4.047619f && volume == 0.2f) ItemAbility.GOLEM_SWORD.sound();
                if (pitch == 0.4920635f && volume == 0.5f) ItemAbility.STAFF_OF_THE_VOLCANO.sound();
            }
            case "entity.wolf.death" -> {
                if (volume == 0.5f) {
                    if (recentlyHeld("WEIRD_TUBA")) ItemAbility.WEIRD_TUBA.sound();
                    if (recentlyHeld("WEIRDER_TUBA")) ItemAbility.WEIRDER_TUBA.sound();
                }
            }
            case "entity.zombie_villager.converted" -> {
                if (pitch == 2f && volume == 0.3f) ItemAbility.END_STONE_SWORD.sound();
            }
            case "entity.wolf.pant" -> {
                if (pitch == 1.3968254f && volume == 0.4f) ItemAbility.SOUL_ESOWARD.sound();
            }
            case "entity.piglin.angry" -> {
                if (pitch == 2f && volume == 0.3f) ItemAbility.PIGMAN_SWORD.sound();
            }
            case "entity.ghast.shoot" -> {
                if (pitch == 1f && volume == 0.3f) ItemAbility.EMBER_ROD.sound();
            }
            case "entity.elder_guardian.ambient" -> {
                if (pitch == 2f && volume == 0.2f) ItemAbility.FIRE_FREEZE_STAFF.sound();
            }
            case "entity.generic.eat" -> {
                if (pitch == 1f && volume == 1f) ItemAbility.STAFF_OF_THE_VOLCANO.sound();
                if (pitch == 0.4920635f && volume == 1f) ItemAbility.WAND_OF_STRENGTH.sound();
            }
            case "entity.generic.drink" -> {
                if (Math.round(pitch * 10) == 18 && volume == 1f) ItemAbility.HOLY_ICE.sound();
            }
            case "entity.bat.ambient" -> {
                if (pitch == 0.4920635f && volume == 1f) ItemAbility.ROYAL_PIGEON.sound();
            }
            case "item.flintandsteel.use" -> {
                if (pitch == 0.74603176f && volume == 1f) ItemAbility.TACTICAL_INSERTION.activate('5', 3_000);
            }
            case "block.lever.click" -> {
                if (pitch == 0.84126985f && volume == 0.5f && recentlyHeld("TOTEM_OF_CORRUPTION")) ItemAbility.TOTEM_OF_CORRUPTION.sound();
            }
            case "entity.firework_rocket.launch" -> {
                if (pitch == 1f && volume == 3f) {
                    if (recentlyHeld("WARNING_FLARE") || recentlyHeld("ALERT_FLARE")) ItemAbility.ALERT_FLARE.sound();
                    if (recentlyHeld("SOS_FLARE")) ItemAbility.SOS_FLARE.sound();
                }
            }
            default -> {
            }
        }
    }

    private static List<String> scrollsOf(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? List.of() : ItemNbt.getAbilityScrolls(data.copyTag());
    }

    // skyhanni onItemClick: item in hand was just used, so a sound now is its ability
    static void clicked(String id, List<String> scrolls) {
        ItemAbility a = ItemAbility.byInternalName(id);
        if (a != null) a.setItemClick();
        // gyrokinetic has one id and 2 abilities, each with its own sound + chat line
        if (a == ItemAbility.GYROKINETIC_WAND_RIGHT || a == ItemAbility.GYROKINETIC_WAND_LEFT) {
            ItemAbility.GYROKINETIC_WAND_LEFT.setItemClick();
            ItemAbility.GYROKINETIC_WAND_RIGHT.setItemClick();
        }
        for (ItemAbility s : ItemAbility.ofScrolls(scrolls)) s.setItemClick();
    }

    // skyhanni onActionBarUpdate, action bar with color codes
    public static void onActionBar(String message) {
        if (Features.on("ability_cooldowns")) actionBar(message);
    }

    static void actionBar(String message) {
        oldAbilities(message);
        if (message.contains(WHITE_CASTING)) {
            if (!ItemAbility.RAGNAROCK_AXE.onCooldown()) ItemAbility.RAGNAROCK_AXE.activate('f', 3_000);
        } else if (message.contains(CASTING)) {
            if (ItemAbility.RAGNAROCK_AXE.specialColor != '5') ItemAbility.RAGNAROCK_AXE.activate('5', 10_000);
        } else if (message.contains(CANCELLED)) {
            ItemAbility.RAGNAROCK_AXE.activate((char) 0, 17_000);
        }
    }

    private static void oldAbilities(String message) {
        Matcher m = Patterns.get("SH.item.abilities.cooldown.abilityuse").matcher(message);
        if (!m.matches()) {
            lastAbility = "";
            return;
        }
        String name = m.group("type");
        if (name.equals(lastAbility)) return;
        lastAbility = name;
        for (ItemAbility a : ItemAbility.values()) {
            if (!a.newVariant && a.abilityName.equals(name)) {
                a.activate();
                return;
            }
        }
    }

    // skyhanni onChat, no color codes, same lines skyhanni reads
    static void chat(String line) {
        if (line.equals("Creeper Veil Activated!")) ItemAbility.WITHER_CLOAK.activate('d', 10_000);
        if (line.equals("Creeper Veil De-activated! (Expired)") || line.equals("Not enough mana! Creeper Veil De-activated!")) ItemAbility.WITHER_CLOAK.activate();
        if (line.equals("Creeper Veil De-activated!")) ItemAbility.WITHER_CLOAK.activate((char) 0, 5_000);
        if (ALIGNED_OTHERS.matcher(line).matches() || line.equals("You aligned yourself!")) ItemAbility.GYROKINETIC_WAND_RIGHT.activate('9', 6_000);
        if (line.equals("Ragnarock was cancelled due to being hit!")) ItemAbility.RAGNAROCK_AXE.activate((char) 0, 17_000);
        if (BUFFED_YOURSELF.matcher(line).matches()) ItemAbility.SWORD_OF_BAD_HEALTH.activate();
    }

    // every tick

    // from skyhanni hasAbility
    private static List<ItemAbility> abilitiesOf(ItemStack stack) {
        String name = GameState.strip(stack.getHoverName().getString()), id = Gain.sbId(stack);
        Set<ItemAbility> out = EnumSet.noneOf(ItemAbility.class);
        out.addAll(ItemAbility.ofScrolls(scrollsOf(stack)));
        for (ItemAbility a : ItemAbility.values()) {
            if (a.newVariant) {
                if (a.internalNames.contains(id)) out.add(a);
            } else {
                for (String n : a.itemNames) if (name.contains(n)) out.add(a);
            }
        }
        return new ArrayList<>(out);
    }

    // skyhanni createItemText + what comes after a phase: the ability keeps going with its next stretch
    private static ItemText textOf(ItemAbility a) {
        if (a.onCooldown()) {
            char color = a.specialColor != 0 ? a.specialColor : a.leftMs() < 600 ? 'c' : 'e';
            return new ItemText(color, a.durationText(), true);
        }
        if (a.specialColor != 0) {
            char was = a.specialColor;
            a.specialColor = 0;
            nextPhase(a, was);
            if (a.onCooldown()) return textOf(a);
        }
        return new ItemText('a', option("ability_cooldowns.showReady") ? "R" : "", false);
    }

    private static void nextPhase(ItemAbility a, char was) {
        switch (a) {
            case GYROKINETIC_WAND_RIGHT -> {
                if (was == '9') a.activate((char) 0, 4_000);
            }
            case RAGNAROCK_AXE -> {
                if (was == '5') a.activate((char) 0, (int) Math.max(20_000 * a.multiplier() - 13_000, 0));
            }
            case WITHER_SHIELD_SCROLL -> {
                if (was == '5') a.activate((char) 0, (int) Math.max(10_000 * a.multiplier() - 5_000, 0));
            }
            default -> {
            }
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            for (ItemAbility a : ItemAbility.values()) a.reset();
        }
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        String held = Gain.sbId(mc.player.getMainHandItem());
        if (!held.isEmpty()) RECENT.put(held, now);
        RECENT.values().removeIf(at -> now - at >= RECENT_MS);
        if (ticks++ % 10 == 0 || abilityItems.isEmpty()) {
            Map<String, List<ItemAbility>> found = new HashMap<>();
            for (Slot slot : mc.player.containerMenu.slots) {
                if (!slot.hasItem()) continue;
                String key = SlotLocks.key(slot.getItem());
                if (key.isEmpty() || found.containsKey(key)) continue;
                List<ItemAbility> list = abilitiesOf(slot.getItem());
                if (!list.isEmpty()) found.put(key, list);
            }
            abilityItems = found;
        }
        Map<String, List<ItemText>> texts = new HashMap<>();
        for (Map.Entry<String, List<ItemAbility>> e : abilityItems.entrySet()) {
            List<ItemText> list = new ArrayList<>(e.getValue().size());
            for (ItemAbility a : e.getValue()) list.add(textOf(a));
            texts.put(e.getKey(), list);
        }
        items = texts;
    }

    // on the item

    private static boolean option(String key) {
        Feature f = Features.get("ability_cooldowns");
        Option o = f == null ? null : f.option(key);
        return o == null ? key.endsWith("onItem") || key.endsWith("background") : o.bool();
    }

    private static List<ItemText> textsOn(ItemStack stack) {
        if (items.isEmpty() || stack == null || stack.isEmpty() || !Features.on("ability_cooldowns") || !option("ability_cooldowns.onItem")) return null;
        long now = System.currentTimeMillis();
        if (now - identityAt > 2000) {
            IDENTITY.clear();
            identityAt = now;
        }
        return items.get(IDENTITY.computeIfAbsent(stack, SlotLocks::key));
    }

    // time left text in place of the stack count. with more than one ability, whatever runs out first
    public static String textOn(ItemStack stack) {
        List<ItemText> list = textsOn(stack);
        if (list == null) return null;
        boolean guiOpen = Minecraft.getInstance().gui != null && Minecraft.getInstance().gui.screen() != null;
        ItemText pick = null;
        for (ItemText t : list) {
            if (t.text().isEmpty() || (guiOpen && !t.onCooldown())) continue;
            if (pick == null || (t.onCooldown() && !pick.onCooldown())) pick = t;
        }
        return pick == null ? null : "§" + pick.color() + pick.text();
    }

    // color behind an item while its waiting, 0 = none
    public static int backgroundOn(ItemStack stack) {
        List<ItemText> list = textsOn(stack);
        if (list == null || !option("ability_cooldowns.background")) return 0;
        boolean guiOpen = Minecraft.getInstance().gui != null && Minecraft.getInstance().gui.screen() != null;
        for (ItemText t : list) {
            if (t.onCooldown()) return rgb(t.color()) | 130 << 24;
        }
        if (!guiOpen && option("ability_cooldowns.showReady") && !list.isEmpty()) return rgb('a') | 80 << 24;
        return 0;
    }

    private static int rgb(char code) {
        return switch (code) {
            case 'c' -> 0xFF5555;
            case 'e' -> 0xFFFF55;
            case 'a' -> 0x55FF55;
            case '5' -> 0xAA00AA;
            case 'd' -> 0xFF55FF;
            case '9' -> 0x5555FF;
            default -> 0xFFFFFF;
        };
    }

    // the card

    // mage cooldown reduction, dungeon only. from class + level like skyhanni works it out, the players setting
    // while those arent known
    static double mageMultiplier() {
        if (!"CATACOMBS".equals(GameState.island)) return 1;
        try {
            double known = io.github.zmuzy.sbc.dungeon.DungeonRun.mageMultiplier();
            if (known >= 0) return known;
            Object o = Config.I.options.get("ability_cd_reduction");
            return o instanceof Number n ? (100 - Math.max(0, Math.min(90, n.intValue()))) / 100.0 : 1;
        } catch (Throwable t) {
            return 1; // no config outside the game (tests)
        }
    }

    private static String seconds(double s) {
        return Math.abs(s - Math.rint(s)) < 0.05 ? Math.round(s) + "s" : String.format(Locale.ROOT, "%.1fs", s);
    }

    // time left, full time, and a meter running down to ready, goes green near the end
    private static HudElement.Row cooldownRow(String ability, double left, double full) {
        String time = String.format(Locale.ROOT, left < 10 ? "%.1fs" : "%.0fs", left);
        double share = full > 0 ? Math.min(1, left / full) : -1;
        int color = Looks.of("ability_cooldowns").accent();
        int bar = share >= 0 && share < 0.34 ? Ui.lerpColor(Modern.GOOD, color, (float) (share / 0.34)) : color;
        return new HudElement.Row(null, ability, full > 0 ? time + "§7/" + seconds(full) : time, left < 1 ? Modern.GOOD : Modern.AQUA, share, bar);
    }

    public static final HudElement COOLDOWNS = new HudElement("ability_cooldowns", "Ability cooldowns", "Items", Zone.BOTTOM_CENTER, 20, "ability_cooldowns") {
        private static final int GAP = 4, SIDE = 3;

        @Override
        public boolean visible() {
            for (ItemAbility a : ItemAbility.values()) if (a.onCooldown()) return true;
            return false;
        }

        @Override
        public String heading() {
            return "";
        }

        // smaller than the bars under it, its one strip of a stack you read in passing
        @Override
        public float defaultScale() {
            return HudLayout.infoScale();
        }

        @Override
        public boolean stretchable() {
            return false;
        }

        @Override
        public List<Row> rows() {
            List<Row> out = new ArrayList<>();
            for (ItemAbility a : ItemAbility.values()) if (a.onCooldown()) out.add(cooldownRow(a.title(), a.leftMs() / 1000.0, a.cooldownMs() / 1000.0));
            return out;
        }

        @Override
        public List<Row> demo() {
            return List.of(cooldownRow("Wand of Atonement", 3.2, 7), cooldownRow("Wither Impact", 1.8, 5), cooldownRow("Fire Freeze Staff", 0.6, 10));
        }

        // two abilities per line where both fit so the strip stays low, it sits right above the bars
        private int columns(List<Row> rows, int inner) {
            if (rows.size() < 2) return 1;
            int need = 0;
            for (Row r : rows) need = Math.max(need, Ui.MEASURE.width(r.label()) + Ui.MEASURE.width(r.value()) + 6);
            return 2 * need + GAP <= inner ? 2 : 1;
        }

        // hotbar width at default size, lines up with everything else on the hotbar
        @Override
        public int width() {
            return Math.round(182f / defaultScale() / 2) * 2;
        }

        @Override
        public int height() {
            Theme t = Theme.current();
            ElementStyle st = style();
            List<Row> rows = visibleRows(st);
            int cols = columns(rows, width() - 2 * SIDE);
            return 5 + (rows.size() + cols - 1) / cols * (Math.min(rowH(t, st), Compact.ROW_H) + Compact.METER_BLOCK);
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            Theme t = Theme.current();
            ElementStyle st = style();
            int w = drawWidth(), h = height(), rh = Math.min(rowH(t, st), Compact.ROW_H), accent = st.titleColor() != null ? st.titleColor() : accent();
            t.panel(ui, x, y, w, h, st);
            Modern.edge(ui, x, y, h, accent);
            List<Row> rows = visibleRows(st);
            int inner = w - 2 * SIDE, cols = columns(rows, inner), cx = x + SIDE;
            // a line of one has room for the card icon, a line of two needs that room for the 2nd ability
            if (cols == 1) {
                ui.icon(icon(), x + 4, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
                cx += 13;
                inner -= 13;
            }
            int cell = (inner - (cols - 1) * GAP) / cols, labelColor = st.textColor() != null ? st.textColor() : Modern.LABEL;
            if (cols == 2) ui.rect(cx + cell + GAP / 2 - 1, y + 3, 1, h - 6, 0x26FFFFFF);
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int rx = cx + i % cols * (cell + GAP), ry = y + 3 + i / cols * (rh + Compact.METER_BLOCK);
                // percent only where a line of one has room for it
                String value = cols == 1 && r.progress() >= 0 ? r.value() + "  §7" + RowText.percent(r.progress()) : r.value();
                Modern.row(ui, r.label(), value, rx, ry, cell, labelColor, st.valueColor() != null ? st.valueColor() : r.color());
                if (r.progress() >= 0) Modern.meter(ui, rx, ry + rh, cell, Modern.METER, r.progress(), r.barColor());
            }
        }
    };

    public static void register() {
        Feature f = new Feature("ability_cooldowns", "Items", "Ability cooldowns", "Time left on the abilities you use: on the item itself and on a card above the bars.", "SkyHanni: Ability Cooldown")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("ability_cooldowns.onItem", "Time on the item", "The seconds left, where the item's count goes.", true))
                .option(Option.bool("ability_cooldowns.background", "Colour behind the item", "A colour behind the item while it waits.", true))
                .option(Option.bool("ability_cooldowns.showReady", "Mark what is ready", "An R and a green tint on items whose ability is ready.", false))
                .option(Option.integer("ability_cd_reduction", "Cooldown reduction %", "Used in dungeons until your class and level are known; after that they decide.", 0, 0, 90))
                .tick(AbilityCooldowns::tick);
        Features.add(f);
        HudLayout.add(COOLDOWNS);
        SoundHooks.listen(s -> {
            if (!Features.on("ability_cooldowns")) return false;
            var player = Minecraft.getInstance().player;
            ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
            sound(s.name(), s.volume(), s.pitch(), Gain.sbId(held), scrollsOf(held));
            return false;
        });
        Interactions.onItemClick(right -> {
            var player = Minecraft.getInstance().player;
            if (player != null && Features.on("ability_cooldowns")) clicked(Gain.sbId(player.getMainHandItem()), scrollsOf(player.getMainHandItem()));
            return false;
        });
        ChatBus.register(ChatBus.OBSERVE, "ability_cooldowns", f, line -> {
            chat(line.plain().trim());
            return ChatBus.PASS;
        });
    }
}
