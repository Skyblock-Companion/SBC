// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.track;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.minion.Minions;
import dev.zmuzy.sbc.tasks.Catalog;
import dev.zmuzy.sbc.tasks.Routine;
import dev.zmuzy.sbc.tasks.TaskDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// reads menus as you open them (never clicks): minion storage + upgrades, sb leveling tasks, forge timers,
// marks routine tasks done. also learns where npcs stand from their name tags
public final class Watcher {
    private static final dev.zmuzy.sbc.core.Patterns.P MINION = dev.zmuzy.sbc.core.Patterns.p("Watcher.MINION");
    private static final dev.zmuzy.sbc.core.Patterns.P XP = dev.zmuzy.sbc.core.Patterns.p("Watcher.XP");
    private static final java.util.Set<String> logged = new java.util.HashSet<>();
    private static final dev.zmuzy.sbc.core.Patterns.P PROGRESS = dev.zmuzy.sbc.core.Patterns.p("Watcher.PROGRESS");
    private static final dev.zmuzy.sbc.core.Patterns.P TIME_LEFT = dev.zmuzy.sbc.core.Patterns.p("Watcher.TIME_LEFT");
    private static final dev.zmuzy.sbc.core.Patterns.P NPC_NAME = dev.zmuzy.sbc.core.Patterns.p("Watcher.NPC_NAME");

    private static String lastTitle = "";
    private static int ticksOpen;
    private static int npcScan;
    private Watcher() {}

    private static long lastBits = -1;

    // after joining or switching islands the scoreboard + inventory load in pieces, dont read that as progress
    private static long quietUntil;

    public static void onWorldChange() {
        quietUntil = System.currentTimeMillis() + 20_000;
        lastBits = -1;
        pearls = -1;
        carried.clear();
    }

    public static void tick(Minecraft mc) {
        boolean quiet = System.currentTimeMillis() < quietUntil;
        long bits = GameState.bits;
        if (bits >= 0) {
            if (!quiet && lastBits >= 0 && bits < lastBits) done("bits");
            lastBits = bits;
        }
        var screen = mc.gui.screen();
        net.minecraft.world.inventory.AbstractContainerMenu open = screen instanceof AbstractContainerScreen<?> acs ? acs.getMenu() : null;
        if (open != null) {
            String title = GameState.strip(screen.getTitle().getString()).trim();
            if (!title.equals(lastTitle)) {
                lastTitle = title;
                ticksOpen = 0;
            }
            ticksOpen++;
            if (ticksOpen == 8 || ticksOpen % 40 == 0) read(mc, open, title);
        } else {
            lastTitle = "";
            ticksOpen = 0;
        }
        if (++npcScan % 40 == 0) learnNpcs(mc);
        if (npcScan % 10 == 0) {
            if (quiet) {
                pearls = -1;
                carried.clear();
            } else {
                watchHandIns(mc);
                if (npcScan % 40 == 0) dev.zmuzy.sbc.tasks.FactionQuests.syncProgress();
            }
        }
    }

    private static final Map<String, Integer> carried = new HashMap<>();
    private static int pearls = -1;

    // backup for npc hand in tasks: the item left your inventory while you stood next to that npc
    private static void watchHandIns(Minecraft mc) {
        if (mc.player == null) return;
        int p = dev.zmuzy.sbc.tasks.Mission.inInventory("Heavy Pearl");
        if ("CRIMSON_ISLE".equals(GameState.island) && pearls >= 0 && p > pearls) done("matriarch");
        pearls = p;
        for (Map.Entry<String, List<dev.zmuzy.sbc.tasks.Mission.Need>> e : dev.zmuzy.sbc.tasks.Mission.NEEDS.entrySet()) {
            TaskDef t = Catalog.byId(e.getKey());
            if (t == null || e.getValue().isEmpty()) continue;
            String item = e.getValue().get(0).name();
            int now = dev.zmuzy.sbc.tasks.Mission.inInventory(item);
            Integer before = carried.put(t.id(), now);
            if (before == null || now >= before || !t.island().equals(GameState.island)) continue;
            var loc = dev.zmuzy.sbc.tasks.Guide.locationOf(t);
            if (loc != null && loc.pos().distanceToSqr(mc.player.position()) < 10 * 10) done(t.id());
        }
    }

    private static void read(Minecraft mc, net.minecraft.world.inventory.AbstractContainerMenu menu, String title) {
        List<Slot> slots = new ArrayList<>();
        for (Slot sl : menu.slots) if (sl.container != mc.player.getInventory()) slots.add(sl);

        Matcher m = MINION.matcher(title);
        if (m.find()) {
            readMinion(mc, m.group(1), GameState.roman(m.group(2)), slots);
            return;
        }
        recordMenu(title, slots);
        String lower = title.toLowerCase();
        // any menu whose items mention sb xp is a leveling/task page whatever its title
        readLevelTasks(title, slots);
        readForge(slots, lower.contains("forge"));

    }

    private static final java.util.Set<String> recorded = new java.util.HashSet<>();

    // saves each menus layout the first time you open it (slot, item, first lore lines) to
    // config/sbcompanion/menus/, so game style menus can be built from what hypixel really shows
    private static void recordMenu(String title, List<Slot> slots) {
        if (!recorded.add(title) || slots.isEmpty()) return;
        try {
            java.nio.file.Path dir = dev.zmuzy.sbc.core.Config.DIR.resolve("menus");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder b = new StringBuilder("# " + title + "  (" + slots.size() + " slots)" + System.lineSeparator());
            for (int i = 0; i < slots.size(); i++) {
                ItemStack st = slots.get(i).getItem();
                if (st.isEmpty()) continue;
                String name = GameState.strip(st.getHoverName().getString()).trim();
                if (st.getItem().toString().contains("glass_pane") && name.isBlank()) continue;
                b.append(i).append(" | ").append(st.getItem()).append(" x").append(st.getCount()).append(" | ").append(name).append(System.lineSeparator());
                int n = 0;
                var raw = st.get(DataComponents.LORE);
                // colors on a second line since the patterns read those, blanks kept so the line count shows
                if (raw != null) for (Component c : raw.lines()) {
                    String plain = GameState.strip(c.getString()), colored = dev.zmuzy.sbc.core.LegacyText.of(c);
                    b.append("      ").append(plain.isBlank() ? "(blank)" : plain).append(System.lineSeparator());
                    if (!colored.equals(plain) && !plain.isBlank()) b.append("        = ").append(colored).append(System.lineSeparator());
                    if (++n >= 40) break;
                }
            }
            java.nio.file.Files.writeString(dir.resolve(title.replaceAll("[^A-Za-z0-9 ()_-]+", "_").trim() + ".txt"), b.toString());
        } catch (Exception ignored) {
        }
    }

    private static void done(String id) {
        TaskDef t = Catalog.byId(id);
        if (t != null && Routine.progress(t, System.currentTimeMillis()) < t.target()) Routine.add(t, t.target());
    }

    private static void readMinion(Minecraft mc, String type, int tier, List<Slot> slots) {
        Map<String, Integer> storage = new HashMap<>();
        List<String> upgrades = new ArrayList<>();
        String fuel = "";
        int storageSlots = 0;
        for (Slot sl : slots) {
            ItemStack st = sl.getItem();
            if (st.isEmpty()) {
                continue;
            }
            String name = GameState.strip(st.getHoverName().getString());
            String id = st.getItem().toString();
            if (id.contains("glass_pane") || name.isBlank()) continue;
            if (Minions.isFuel(name)) {
                fuel = name;
                continue;
            }
            if (Minions.isUpgrade(name)) {
                upgrades.add(name);
                continue;
            }
            if (isUi(name)) continue;
            storage.merge(name, st.getCount(), Integer::sum);
            storageSlots++;
        }
        Entity looked = mc.crosshairPickEntity;
        String key = looked instanceof ArmorStand a ? GameState.island + "@" + a.blockPosition().toShortString() : null;
        if (key == null) key = Minions.nearestKey(GameState.island, type, tier, mc.player.position());
        if (key == null) key = GameState.island + "@" + type + tier;
        Minions.observe(key, type, tier, GameState.island, upgrades, fuel, storage, Math.max(storageSlots, tierSlots(tier)));
    }

    private static int tierSlots(int tier) {
        return tier <= 2 ? 1 : tier <= 4 ? 3 : tier <= 6 ? 6 : tier <= 8 ? 9 : tier <= 10 ? 12 : 15;
    }

    private static boolean isUi(String n) {
        String l = n.toLowerCase();
        return l.contains("minion") || l.contains("collect all") || l.contains("close") || l.contains("fuel") || l.contains("upgrade slot")
                || l.contains("pickup") || l.contains("next tier") || l.contains("ideal layout") || l.contains("skin") || l.contains("shipping")
                || l.contains("quick-upgrade") || l.contains("storage unlocked") || l.contains("required items") || l.contains("collect");
    }

    private static void readLevelTasks(String title, List<Slot> slots) {
        List<Store.LevelTask> found = new ArrayList<>();
        for (Slot sl : slots) {
            ItemStack st = sl.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = lore(st);
            int xp = 0;
            boolean done = false, notDone = false;
            String progress = "";
            for (String line : lore) {
                Matcher x = XP.matcher(line);
                if (x.find()) xp = Math.max(xp, (int) GameState.num(x.group(1) != null ? x.group(1) : x.group(2)));
                if (line.contains("XP") && logged.add(title)) dev.zmuzy.sbc.SbcMod.LOG.info("[sbc scan] menu '{}' lore sample: {}", title, line);
                if (line.contains("✔") || line.toUpperCase().contains("COMPLETED")) done = true;
                if (line.contains("✖") || line.toUpperCase().contains("NOT COMPLETED") || line.toUpperCase().contains("INCOMPLETE")) notDone = true;
                Matcher p = PROGRESS.matcher(line);
                if (p.find() && progress.isEmpty()) progress = p.group(1) + "/" + p.group(2);
            }
            if (xp <= 0) continue;
            Store.LevelTask t = new Store.LevelTask();
            t.category = title;
            t.name = GameState.strip(st.getHoverName().getString());
            t.xp = xp;
            t.done = done && !notDone;
            t.progress = progress;
            found.add(t);
        }
        if (found.isEmpty()) return;
        Store.I.levelTasks.removeIf(t -> title.equals(t.category));
        Store.I.levelTasks.addAll(found);
        Store.I.levelTasksScannedAt = System.currentTimeMillis();
        Store.markDirty();
    }

    private static final dev.zmuzy.sbc.core.Patterns.P FORGE_TIER = dev.zmuzy.sbc.core.Patterns.p("Watcher.FORGE_TIER");

    private static void readForge(List<Slot> slots, boolean isForge) {
        long soonest = Long.MAX_VALUE;
        for (Slot sl : slots) {
            // recipe pages say which hotm tier they need, remember it for the forge profit page
            if (!sl.getItem().isEmpty()) {
                String name = GameState.strip(sl.getItem().getHoverName().getString()).trim();
                for (String line : lore(sl.getItem())) {
                    Matcher t = FORGE_TIER.matcher(line);
                    if (t.find() && (line.toLowerCase().contains("require") || line.contains("✖") || line.contains("✔"))) {
                        String v = t.group(1);
                        int tier = v.chars().allMatch(Character::isDigit) ? Integer.parseInt(v) : GameState.roman(v);
                        if (!name.isEmpty() && !Integer.valueOf(tier).equals(Store.I.forgeTiers.put(name, tier))) Store.markDirty();
                    }
                }
            }
            for (String line : lore(sl.getItem())) {
                int i = line.indexOf("Time Remaining:");
                if (i < 0) continue;
                long ms = parseDuration(line.substring(i + "Time Remaining:".length()).trim());
                if (ms > 0) soonest = Math.min(soonest, ms);
            }
        }
        if (isForge && soonest != Long.MAX_VALUE) {
            Store.I.timers.put("forge", System.currentTimeMillis() + soonest);
            Store.markDirty();
        }
    }

    public static long parseDuration(String s) {
        Matcher m = TIME_LEFT.matcher(s.trim());
        if (!m.find()) return -1;
        long d = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long h = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        long mi = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        long se = m.group(4) == null ? 0 : Long.parseLong(m.group(4));
        return ((d * 24 + h) * 60 + mi) * 60_000 + se * 1000;
    }

    public static List<String> lore(ItemStack st) {
        List<String> out = new ArrayList<>();
        if (st == null || st.isEmpty()) return out;
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return out;
        for (Component c : lore.lines()) out.add(GameState.strip(c.getString()));
        return out;
    }

    private static void learnNpcs(Minecraft mc) {
        if (mc.level == null || mc.player == null || !GameState.inSkyBlock || GameState.island.isEmpty()) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand a) || !a.hasCustomName() || a.distanceToSqr(mc.player) > 48 * 48) continue;
            String name = GameState.strip(a.getCustomName().getString()).trim();
            if (!NPC_NAME.matcher(name).matches() || name.equals("CLICK") || name.contains("CLICK")) continue;
            String key = GameState.island + "|" + name;
            if (!Store.I.npcTextures.containsKey(key) && Store.I.npcTextures.size() < 1500) {
                // the npc is a player model under its name tag, keep its skin for map icons
                for (Entity o : mc.level.entitiesForRendering()) {
                    if (!(o instanceof net.minecraft.world.entity.player.Player pl) || pl == mc.player || o.distanceToSqr(a) > 9) continue;
                    for (com.mojang.authlib.properties.Property pr : pl.getGameProfile().properties().get("textures")) {
                        Store.I.npcTextures.put(key, pr.value());
                        Store.markDirty();
                    }
                    break;
                }
            }
            if (!Store.I.npcs.containsKey(key) && Store.I.npcs.size() < 800) {
                Store.I.npcs.put(key, new double[]{a.getX(), a.getY() - 1.5, a.getZ()});
                Store.markDirty();
            }
        }
    }
}
