// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.minion;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.combat.Combat;
import io.muzy.sbc.combat.CombatMarks;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.core.game.GameProfile;
import io.muzy.sbc.core.game.GameRead;
import io.muzy.sbc.core.game.Seen;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.sacks.Sacks;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.render.WorldDraw;
import io.muzy.sbc.tasks.Mission;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni features/minion: MinionFeatures.kt (your minions, when a hopper was emptied, last one
// clicked, coins a day), MinionXP.kt (what collecting gives), MinionUpgradeHelper.kt
public final class MinionFeatures {
    public static final String NAMES = "minion_names", LAST = "minion_last_clicked", PANEL = "minion_panel", XP = "minion_xp", HIDE_TAGS = "minion_hide_nametags", INFERNO = "inferno_fuel_block";
    private static final int FUEL_SLOT = 19, PICKUP_SLOT = 53, HOPPER_SLOT = 28, COLLECT_SLOT = 48, UPGRADE_SLOT = 50;

    private static final Patterns.P UPGRADE = Patterns.p("SH.minion.chat.upgrade-colorless"), COINS = Patterns.p("SH.minion.chat.coin-colorless"), PICKUP = Patterns.p("SH.minion.chat.pickup"),
            PLACE = Patterns.p("SH.minion.chat.place"), TITLE = Patterns.p("SH.minion.title"), COLLECT = Patterns.p("SH.minion.item.collect.colorless"), NEEDS = Patterns.p("SH.minion.items.upgrade.colorless"),
            INFERNO_TITLE = Patterns.p("SH.minion.infernominiontitle");

    private static Vec3 lastClickedEntity, lastStorage, newMinion, lastMinion;
    private static String newMinionName;
    private static long lastMinionOpened, lastInventoryClosed;
    private static boolean minionOpen, storageOpen, inferno;
    private static String openTitle = "";
    private static net.minecraft.client.multiplayer.ClientLevel level;

    // upgrade helper: what the upgrade slot says is missing
    record Upgrade(String id, String name, int needed, int inSacks, double price) {
        int remaining() {
            return needed - inSacks;
        }

        // from skyhanni MinionUpgradeHelper.createLore, its 2 sums
        double remainingCost() {
            return Math.max(0, remaining()) * price;
        }

        double totalCost() {
            return needed * price;
        }
    }

    private static Upgrade upgrade;
    private static String coinsPerDay = "";

    // from skyhanni MinionXP
    private static final Map<String, Object[]> XP_INFO = new HashMap<>();
    private static boolean xpRead;
    private static final Map<String, String> xpItems = new HashMap<>();
    private static final List<String> collectYap = new ArrayList<>();
    private static String collectItem = "";
    private record Storage(Vec3 position, Map<String, Double> xp, long at) {}
    private static final List<Storage> storages = new ArrayList<>();

    private static Set<String> fuels;
    private static final Map<String, Vec3> PLACES = new HashMap<>();

    private MinionFeatures() {}

    static String key(Vec3 v) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", v.x, v.y, v.z);
    }

    private static Vec3 place(String key) {
        return PLACES.computeIfAbsent(key, k -> {
            String[] p = k.split(",");
            return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        });
    }

    private static boolean island(String... ids) {
        for (String id : ids) if (id.equals(GameState.island)) return true;
        return false;
    }

    private static boolean ours() {
        return island("PRIVATE_ISLAND", "HUB");
    }

    private static boolean opt(String feature, String key) {
        return Combat.opt(feature, key);
    }

    // from skyhanni getMinionName: "Cobblestone Minion XI" -> "Cobblestone Minion 11", or another tier
    static String minionName(String oldName, int newTier) {
        String[] words = oldName.trim().split(" ");
        String last = words[words.length - 1];
        int number = newTier != 0 ? newTier : last.chars().allMatch(Character::isDigit) ? Integer.parseInt(last) : GameState.roman(last);
        return String.join(" ", java.util.Arrays.copyOf(words, words.length - 1)) + " " + number;
    }

    // --- what the player does ---

    private static InteractionResult useEntity(Entity entity) {
        if (ours()) lastClickedEntity = entity.position();
        return InteractionResult.PASS;
    }

    // from skyhanni onPlayerInteraction: minion item against a block with air on that side
    private static void useBlock(BlockPos pos, net.minecraft.core.Direction face) {
        if (ours()) lastStorage = Vec3.atLowerCornerOf(pos);
        Minecraft mc = Minecraft.getInstance();
        if (!island("PRIVATE_ISLAND") || mc.player == null || mc.level == null) return;
        BlockPos at = pos.relative(face);
        ItemStack held = mc.player.getMainHandItem();
        if (held.getHoverName().getString().contains(" Minion ") && mc.level.getBlockState(at).is(Blocks.AIR)) {
            newMinion = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            newMinionName = minionName(GameState.strip(held.getHoverName().getString()), 0);
        } else {
            newMinion = null;
            newMinionName = null;
        }
    }

    // --- menus ---

    // from skyhanni onInventoryFullyOpened, onInventoryUpdated, onMinionOpen + what the other 3 do with MinionOpenEvent
    public static void menu(Seen s, String title, List<GameRead.MenuItem> items) {
        inferno = INFERNO_TITLE.matcher(title).matches();
        if (!ours() || !TITLE.matcher(title).find()) return;
        GameRead.MenuItem collect = slot(items, COLLECT_SLOT);
        if (collect != null && COLLECT.matcher(collect.cleanName()).find()) minionOpened(s, title, items, collect);
        else if (!minionOpen) {
            storageOpen = true;
            openTitle = title;
            storageOpened(items);
        }
    }

    private static GameRead.MenuItem slot(List<GameRead.MenuItem> items, int slot) {
        for (GameRead.MenuItem i : items) if (i.slot() == slot) return i;
        return null;
    }

    private static void minionOpened(Seen s, String title, List<GameRead.MenuItem> items, GameRead.MenuItem collect) {
        removeBugged(s, false);
        if (lastClickedEntity != null) {
            String name = minionName(title, 0);
            String at = key(lastClickedEntity);
            Seen.MinionSpot spot = s.minionSpots.get(at);
            if (spot == null && !"HUB".equals(GameState.island)) {
                spot = new Seen.MinionSpot();
                s.minionSpots.put(at, spot);
            }
            if (spot != null) spot.displayName = name;
            Store.markDirty();
            lastMinion = lastClickedEntity;
            lastClickedEntity = null;
            lastMinionOpened = 0;
        }
        minionOpen = true;
        openTitle = title;
        coinsPerDay = coinsPerDay(s, slot(items, HOPPER_SLOT));
        upgrade = readUpgrade(slot(items, UPGRADE_SLOT));
        minionXp(items, collect);
    }

    // from skyhanni onInventoryClose
    private static void tick(Minecraft mc) {
        if (mc.level != level) {
            level = mc.level;
            worldChanged();
        }
        if (!minionOpen && !storageOpen && !inferno) return;
        Screen screen = mc.gui.screen();
        String title = screen instanceof AbstractContainerScreen<?> ? GameState.strip(screen.getTitle().getString()).trim() : "";
        if ((minionOpen || storageOpen) && !title.equals(openTitle)) {
            if (minionOpen) {
                lastMinionOpened = System.currentTimeMillis();
                lastInventoryClosed = lastMinionOpened;
            }
            minionOpen = storageOpen = false;
            upgrade = null;
            coinsPerDay = "";
            xpItems.clear();
            collectYap.clear();
            collectItem = "";
        }
        if (inferno && !INFERNO_TITLE.matcher(title).matches()) inferno = false;
    }

    // from skyhanni onWorldChange + onIslandChange: where you were stops mattering, what you own stays
    private static void worldChanged() {
        lastClickedEntity = lastMinion = lastStorage = null;
        lastMinionOpened = 0;
        minionOpen = storageOpen = inferno = false;
        upgrade = null;
        coinsPerDay = "";
        storages.clear();
        xpItems.clear();
        collectYap.clear();
    }

    // from skyhanni removeBuggedMinions: a spot within 30 blocks with no armor stand isnt a minion
    private static void removeBugged(Seen s, boolean command) {
        Minecraft mc = Minecraft.getInstance();
        if (!island("PRIVATE_ISLAND") || mc.level == null || mc.player == null) return;
        List<String> gone = new ArrayList<>();
        for (String at : s.minionSpots.keySet()) {
            Vec3 v = place(at);
            if (v.distanceTo(mc.player.position()) > 30) continue;
            boolean found = false;
            for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, new AABB(v, v).inflate(5))) found |= a.position().distanceTo(v) == 0;
            if (!found) gone.add(at);
        }
        if (gone.isEmpty()) {
            if (command) Combat.say("No bugged minions found nearby.");
            return;
        }
        gone.forEach(s.minionSpots::remove);
        Store.markDirty();
        Combat.say("Removed " + gone.size() + " wrong/bugged minion locations from your island.");
    }

    // from skyhanni updateCoinsPerDay
    private static String coinsPerDay(Seen s, GameRead.MenuItem hopper) {
        if (hopper == null || lastMinion == null) return "";
        for (String line : hopper.lore()) {
            if (!line.contains("Held Coins")) continue;
            Seen.MinionSpot spot = s.minionSpots.get(key(lastMinion));
            if (spot == null || spot.lastClicked <= 0) return "§cNo time data yet";
            String[] parts = line.split(": §b");
            if (parts.length < 2) return "";
            long coins = GameRead.number(parts[1]);
            if (coins < 0) return "";
            return String.format(Locale.ROOT, "%,d", perDay(coins, System.currentTimeMillis() - spot.lastClicked));
        }
        return "";
    }

    static long perDay(long coins, long millis) {
        return millis <= 0 ? 0 : (long) (coins / (double) millis * 86_400_000L);
    }

    // from skyhanni MinionUpgradeHelper.onMinionOpen: lore.withWrappedLines so a line + the next can be one sentence
    static Matcher upgradeLine(List<String> cleanLore) {
        for (int i = 0; i < cleanLore.size(); i++) {
            Matcher m = NEEDS.matcher(cleanLore.get(i));
            if (m.matches()) return m;
            if (i + 1 < cleanLore.size()) {
                m = NEEDS.matcher(cleanLore.get(i) + " " + cleanLore.get(i + 1).trim());
                if (m.matches()) return m;
            }
        }
        return null;
    }

    private static Upgrade readUpgrade(GameRead.MenuItem item) {
        if (item == null) return null;
        Matcher m = upgradeLine(item.cleanLore());
        if (m == null) return null;
        String name = m.group("itemName");
        String id = ItemRepo.idForName(name);
        if (id == null) id = Prices.idFromName(name);
        int needed = Integer.parseInt(m.group("amount"));
        if (needed <= 0) return null;
        Integer sacks = Sacks.amountInSacks(id);
        Prices.Product p = Prices.get(id);
        return new Upgrade(id, name, needed, sacks == null ? 0 : sacks, p == null ? 0 : p.buy());
    }

    // --- skyhanni MinionXP ---

    private static void readXp() {
        if (xpRead) return;
        xpRead = true;
        try {
            JsonObject json = Repo.constant("MinionXP.json");
            if (json == null) return;
            for (var skill : json.getAsJsonObject("minion_xp").entrySet())
                for (var e : skill.getValue().getAsJsonObject().entrySet())
                    XP_INFO.put(e.getKey(), new Object[]{skill.getKey(), e.getValue().getAsDouble()});
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("Could not read the minion XP", e);
        }
    }

    private static boolean storageSlot(int slot) {
        return slot >= 21 && slot <= 26 || slot >= 30 && slot <= 35 || slot >= 39 && slot <= 44;
    }

    // from skyhanni handleItems. "add wisdom and temporary skill exp (Events) to calculation" is still their TODO
    private static Map<String, Double> handleItems(List<GameRead.MenuItem> items, boolean isMinion) {
        readXp();
        boolean moar = false;
        for (Mayor.Perk p : Mayor.perks) moar |= p.name().equals("Moar Skillz");
        Map<String, Double> total = new java.util.TreeMap<>();
        for (GameRead.MenuItem item : items) {
            if (item.lore().isEmpty() || isMinion && !storageSlot(item.slot())) continue;
            Object[] xp = XP_INFO.get(item.id());
            if (xp == null) continue;
            double amount = (double) xp[1] * item.count() * (moar ? 1.5 : 1);
            xpItems.put(item.id() + "|" + item.count(), collectMessage((String) xp[0], amount));
            total.merge((String) xp[0], amount, Double::sum);
        }
        return total;
    }

    private static String collectMessage(String skill, double amount) {
        return "§7Collect to get: §b" + String.format(Locale.ROOT, "%,.1f", amount) + " §e" + skill + " XP";
    }

    private static void minionXp(List<GameRead.MenuItem> items, GameRead.MenuItem collect) {
        collectItem = collect.vanilla();
        collectYap.clear();
        Map<String, Double> total = handleItems(items, true);
        boolean misses = false;
        if (lastMinion != null && hasStorage(lastMinion)) {
            Storage found = null;
            for (Storage st : storages)
                if (st.position().distanceToSqr(lastMinion) <= 2.5 && System.currentTimeMillis() - st.at() < 20 * 60_000L) found = st;
            if (found == null) misses = true;
            else found.xp().forEach((k, v) -> total.merge(k, v, Double::sum));
        }
        total.forEach((k, v) -> collectYap.add(collectMessage(k, v)));
        if (misses) {
            collectYap.add("");
            collectYap.add("§cNo Minion Storage data");
            collectYap.add("§eOpen the storage to get the right value");
        }
        collectYap.add("");
    }

    private static void storageOpened(List<GameRead.MenuItem> items) {
        Map<String, Double> total = handleItems(items, false);
        if (lastStorage == null) return;
        storages.removeIf(s -> s.position().equals(lastStorage));
        storages.add(new Storage(lastStorage, total, System.currentTimeMillis()));
    }

    private static boolean hasStorage(Vec3 minion) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockPos at = BlockPos.containing(minion);
        for (BlockPos p : new BlockPos[]{at.east(), at.south(), at.west(), at.north()})
            if (mc.level.getBlockState(p).getBlock() instanceof ChestBlock) return true;
        return false;
    }

    private static void xpTooltip(ItemStack stack, List<Component> tip) {
        if (!minionOpen && !storageOpen) return;
        String line = xpItems.get(Gain.sbId(stack) + "|" + stack.getCount());
        if (line != null) {
            tip.add(Component.empty());
            tip.add(Component.literal(line));
        }
        if (minionOpen && !collectItem.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(collectItem)) {
            int at = 1;
            for (String l : collectYap) if (at <= tip.size()) tip.add(at++, Component.literal(l));
        }
    }

    // --- chat ---

    // from skyhanni onChat on the private island. the names are your minions, whatever feature shows them
    static void chat(Seen s, String message) {
        if (!island("PRIVATE_ISLAND")) return;
        if (COINS.matcher(message).matches()) {
            if (System.currentTimeMillis() - lastInventoryClosed < 2000 && lastMinion != null) {
                Seen.MinionSpot spot = s.minionSpots.get(key(lastMinion));
                if (spot != null) {
                    spot.lastClicked = System.currentTimeMillis();
                    Store.markDirty();
                }
            }
        }
        if (PICKUP.matcher(message).find() && lastMinion != null) {
            s.minionSpots.remove(key(lastMinion));
            Minions.pickedUp(GameState.island, lastMinion);
            lastClickedEntity = null;
            lastMinion = null;
            lastMinionOpened = 0;
            Store.markDirty();
        }
        if (PLACE.matcher(message).find() && newMinion != null) {
            Seen.MinionSpot spot = new Seen.MinionSpot();
            spot.displayName = newMinionName == null ? "" : newMinionName;
            s.minionSpots.put(key(newMinion), spot);
            newMinion = null;
            newMinionName = null;
            Store.markDirty();
        }
        Matcher up = UPGRADE.matcher(message);
        if (up.matches() && lastMinion != null) {
            Seen.MinionSpot spot = s.minionSpots.get(key(lastMinion));
            if (spot != null) {
                String tier = up.group("tier");
                spot.displayName = minionName(spot.displayName, tier.chars().allMatch(Character::isDigit) ? Integer.parseInt(tier) : GameState.roman(tier));
                Store.markDirty();
            }
        }
    }

    // --- the world ---

    // from skyhanni onRenderLastClickedMinion: as long as the setting says after the menu closed
    private static void drawLast(WorldDraw w) {
        Feature f = Features.get(LAST);
        if (lastMinion == null || lastMinionOpened + f.option("minion_last_clicked.time").integer() * 1000L <= System.currentTimeMillis()) return;
        w.waypointBox(lastMinion.add(-0.5, 0, -0.5), f.option("minion_last_clicked.color").color(), false, true, -0.25);
    }

    // from skyhanni onRenderLastEmptied
    private static void drawNames(WorldDraw w) {
        Seen s = GameProfile.current();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || !island("PRIVATE_ISLAND")) return;
        Feature f = Features.get(NAMES);
        double distance = f.option("minion_names.distance").number();
        boolean onlyTier = f.option("minion_names.only_tier").bool(), emptied = f.option("minion_names.emptied").bool();
        for (var e : s.minionSpots.entrySet()) {
            Vec3 at = place(e.getKey()).add(0, 1, 0);
            if (mc.player.position().distanceTo(at) >= distance) continue;
            String name = e.getValue().displayName;
            String shown = "§6" + (onlyTier ? name.substring(name.lastIndexOf(' ') + 1) : name);
            w.label(shown, at.add(0, 0.65, 0), true);
            if (emptied && e.getValue().lastClicked > 0)
                w.label("§eHopper Emptied: " + SbTime.dur(System.currentTimeMillis() - e.getValue().lastClicked) + " ago", at.add(0, 1.15, 0), true);
        }
    }

    // from skyhanni onRenderLiving: a mob name tag near a minion
    private static boolean hiddenTag(Entity e) {
        if (!(e instanceof ArmorStand) || !e.hasCustomName() || e.getCustomName() == null || !e.getCustomName().getString().contains("❤")) return false;
        Seen s = GameProfile.current();
        if (s == null) return false;
        for (String at : s.minionSpots.keySet()) if (place(at).distanceTo(e.position()) < 5) return true;
        return false;
    }

    // --- skyhanni InfernoMinionFeatures ---

    private static Set<String> fuels() {
        if (fuels != null) return fuels;
        fuels = new HashSet<>();
        try {
            JsonObject json = Repo.constant("InfernoMinionFuels.json");
            if (json != null) json.getAsJsonArray("inferno_minion_fuels").forEach(e -> fuels.add(e.getAsString()));
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("Could not read the inferno fuels", e);
        }
        return fuels;
    }

    private static boolean isFuel(ItemStack st) {
        String id = Gain.sbId(st);
        if (fuels().contains(id)) return true;
        String named = ItemRepo.idForName(GameState.strip(st.getHoverName().getString()).trim());
        return named != null && fuels().contains(named);
    }

    private static boolean ctrl() {
        Minecraft mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    private static boolean infernoClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        if (!inferno || !Features.on(INFERNO) || slotId != FUEL_SLOT && slotId != PICKUP_SLOT || ctrl()) return false;
        if (FUEL_SLOT >= screen.getMenu().slots.size()) return false;
        return isFuel(screen.getMenu().slots.get(FUEL_SLOT).getItem());
    }

    private static void infernoTooltip(ItemStack stack, List<Component> tip) {
        if (!inferno) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || PICKUP_SLOT >= screen.getMenu().slots.size()) return;
        var menu = screen.getMenu();
        if (stack == menu.slots.get(FUEL_SLOT).getItem() && isFuel(stack)) {
            tip.add(Component.empty());
            tip.add(Component.literal("§cBlocking you from taking this out!"));
            tip.add(Component.literal("  §7(Bypass by holding Ctrl)"));
        }
        if (stack == menu.slots.get(PICKUP_SLOT).getItem() && isFuel(menu.slots.get(FUEL_SLOT).getItem())) {
            tip.add(Component.empty());
            tip.add(Component.literal("§cBlocking you from picking this minion up!"));
            tip.add(Component.literal("  §7(Bypass by holding Ctrl)"));
        }
    }

    // --- the panel ---

    private static final class Panel implements SideHelper.Content {
        @Override public String title() {
            return "Minion";
        }

        @Override public ItemStack icon() {
            return LegacyItems.stack("minecraft:hopper");
        }

        @Override public int accent() {
            return 0xFF55FFFF;
        }

        @Override public String[] tabs() {
            return new String[]{"Upgrade"};
        }

        @Override public void refresh(AbstractContainerScreen<?> screen) {}

        @Override public int summary(Ui ui, int x, int y, int w) {
            if (coinsPerDay.isEmpty() || !Combat.opt(PANEL, "coins")) return 0;
            SideHelper.tile(ui, x, y, w, "Coins a day", coinsPerDay, "", Modern.GOLD);
            return 26;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            Upgrade u = Combat.opt(PANEL, "upgrade") ? upgrade : null;
            int y0 = y;
            if (u == null) return SideHelper.emptyText(ui, "Nothing missing for the next tier.", x, y, w);
            for (String line : lines(u)) {
                ui.textShadow(ui.ellipsize(line, w), x + 2, y + 2, Modern.INK);
                y += 12;
            }
            boolean hv = ui.hover(x, y, w, 20);
            ui.rect(x, y, w, 20, hv ? 0x44FFFFFF : 0x22FFFFFF);
            String click = u.remaining() > 0 ? "§eOpen the Bazaar" : "§eTake them from your sacks";
            ui.textShadow(click, x + 4, y + 6, Modern.INK);
            ui.click(x, y, w, 20, () -> {
                if (u.remaining() > 0) Mission.run("bz " + u.name());
                else Mission.run("gfs " + u.id() + " " + u.needed());
            });
            y += 22;
            return y - y0;
        }
    }

    // from skyhanni createLore
    static List<String> lines(Upgrade u) {
        List<String> out = new ArrayList<>();
        if (u.inSacks() > 0) out.add("§7In sacks: §a" + String.format(Locale.ROOT, "%,d", u.inSacks()) + "§7x §b" + u.name());
        if (u.remaining() > 0) {
            out.add("§7From Bazaar: §a" + u.remaining() + "§7x §b" + u.name());
            out.add("§7Cost: §6" + Prices.coins(u.remainingCost()) + " coins");
        } else out.add("§7All needed items are already in sacks!");
        out.add("§7Total price: §6" + Prices.coins(u.totalCost()) + " coins");
        return out;
    }

    private static final Panel PANEL_CONTENT = new Panel();

    private static SideHelper.Content panel(Screen s) {
        return minionOpen && Features.on(PANEL) && (upgrade != null || !coinsPerDay.isEmpty()) ? PANEL_CONTENT : null;
    }

    public static void register() {
        Safe.endTick("MinionFeatures.tick", MinionFeatures::tick);
        Safe.useEntity("MinionFeatures.useEntity", (player, level, hand, entity, hit) -> level.isClientSide() ? useEntity(entity) : InteractionResult.PASS);
        Safe.useBlock("MinionFeatures.useBlock", (player, level, hand, hit) -> {
            if (level.isClientSide()) useBlock(hit.getBlockPos(), hit.getDirection());
            return InteractionResult.PASS;
        });
        ChatBus.register(5, "minions", null, line -> {
            Seen s = GameProfile.current();
            if (s != null) chat(s, line.plain());
            return ChatBus.PASS;
        });
        Features.add(new Feature(NAMES, "Minions", "Minion names", "Over your minions when you stand close: name and tier, and how long ago the hopper was emptied.", "SkyHanni: Minion features")
                .when(() -> island("PRIVATE_ISLAND")).world(MinionFeatures::drawNames)
                .option(Option.bool("minion_names.only_tier", "Only the tier", "Useful for Bingo.", false))
                .option(Option.bool("minion_names.emptied", "Hopper emptied", "", true))
                .option(Option.integer("minion_names.distance", "Distance", "How close you need to stand.", 10, 1, 50)));
        Features.add(new Feature(LAST, "Minions", "Last clicked minion", "Marks the minion you last opened for a little while.", "SkyHanni: Last Minion Display")
                .when(MinionFeatures::ours).defaultOff().world(MinionFeatures::drawLast)
                .option(Option.integer("minion_last_clicked.time", "Seconds", "", 20, 1, 120))
                .option(Option.color("minion_last_clicked.color", "Colour", "", 0xF555FF55)));
        Features.add(new Feature(PANEL, "Minions", "Minion panel", "Beside a minion's menu: coins a day from its hopper, and where the next upgrade's items come from, with a click to the Bazaar or your sacks.", "SkyHanni: Minion Upgrade Helper")
                .when(MinionFeatures::ours)
                .option(Option.bool("minion_panel.coins", "Coins a day", "From the hopper's held coins and when it was last emptied.", true))
                .option(Option.bool("minion_panel.upgrade", "Upgrade items", "", true)));
        SideHelper.add(MinionFeatures::panel);
        Features.add(new Feature(XP, "Minions", "XP from a minion", "On the items in a minion and on Collect All: the skill XP you get for taking them.", "SkyHanni: Show XP")
                .when(MinionFeatures::ours).tooltip(MinionFeatures::xpTooltip));
        Features.add(new Feature(HIDE_TAGS, "Minions", "Hide mob names near minions", "Mob name tags within 5 blocks of your minions.", "SkyHanni: Hide Mob Nametag")
                .when(() -> island("PRIVATE_ISLAND")).defaultOff());
        CombatMarks.hiding(() -> Features.on(HIDE_TAGS) && island("PRIVATE_ISLAND"), MinionFeatures::hiddenTag);
        Features.add(new Feature(INFERNO, "Minions", "Inferno fuel blocker", "Stops you taking out the fuel of an Inferno Minion, or picking it up, while it has fuel. Ctrl lets it through.", "SkyHanni: Inferno Fuel Blocker")
                .when(() -> GameState.inSkyBlock).defaultOff().tooltip(MinionFeatures::infernoTooltip));
        Interactions.onSlotClick(MinionFeatures::infernoClick);
        Safe.commands("MinionFeatures.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("fixminions").executes(c -> {
                    Seen s = GameProfile.current();
                    if (s != null) removeBugged(s, true);
                    return 1;
                }))));
    }
}
