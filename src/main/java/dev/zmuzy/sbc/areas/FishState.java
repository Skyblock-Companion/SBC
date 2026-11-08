// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.areas;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.item.menus.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what the fishing helpers know about you + the water. from skyhanni FishingApi (rods from the repo lists,
// bobber, bait in the last hotbar slot, trophy armor), IsFishingDetection, SeaCreatureDetectionApi
public final class FishState {
    // from skyhanni SeaCreatureDetectionApi.DESPAWN_TIME
    public static final long DESPAWN_MS = 6 * 60_000L;
    private static final double MAX_WAIT_DEATH_DISTANCE = 25;
    private static final Vec3 FROSTY_NPC = new Vec3(-1.5, 76, 92.5);
    private static final Pattern HEALTH = Pattern.compile("([\\d.,]+[kMB]?)/([\\d.,]+[kMB]?)❤?");

    public record Kind(String name, String rarity, boolean rare, String chatColor, String chatMessage) {}

    // one alive sea creature, by the armor stand with its name
    public static final class Live {
        public final int stand;
        public final Kind kind;
        public final long spawned;
        public boolean own;
        public boolean announced;
        public long hp = -1, maxHp = -1;
        public Vec3 pos;
        public long lastSeen;

        Live(int stand, Kind kind, long spawned, Vec3 pos) {
            this.stand = stand;
            this.kind = kind;
            this.spawned = spawned;
            this.pos = pos;
            this.lastSeen = spawned;
        }
    }

    public static boolean holdingRod, holdingLava, holdingWater, treasureHook, trophyLine, trophyArmor, emberArmor;
    public static String baitName = "", baitId = "";
    public static int baitAmount;
    public static ItemStack baitStack = ItemStack.EMPTY;
    public static boolean baitKnown;

    public static long castAt, liquidAt;
    private static FishingHook lastHook;
    private static boolean touched;
    public static Vec3 lastBobber;

    private static Map<String, Kind> kinds;
    private static Set<String> lavaRods, waterRods;
    private static final Map<Integer, Live> LIVE = new LinkedHashMap<>();
    private static final Set<Integer> seen = new HashSet<>();

    private static String lastFished;
    private static long lastFishedAt, doubleHookAt;
    private static int mobsToFind;

    public static final List<Consumer<Live>> ON_SPAWN = new ArrayList<>(), ON_DEATH = new ArrayList<>(), ON_CATCH_OWN = new ArrayList<>();
    // whoever casts a line: the bait warning
    public static final List<Runnable> ON_CAST = new ArrayList<>();

    private static int ticks;

    private FishState() {}

    // repo creatures by name, what skyhannis SeaCreatureManager.allFishingMobs is
    public static Map<String, Kind> kinds() {
        if (kinds != null) return kinds;
        Map<String, Kind> out = new HashMap<>();
        JsonObject root = Repo.constant("SeaCreatures.json");
        if (root != null) {
            for (Map.Entry<String, JsonElement> area : root.entrySet()) {
                if (!area.getValue().isJsonObject() || !area.getValue().getAsJsonObject().has("sea_creatures")) continue;
                JsonObject a = area.getValue().getAsJsonObject();
                String color = a.has("chat_color") ? a.get("chat_color").getAsString() : "§9";
                for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("sea_creatures").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    out.put(e.getKey(), new Kind(e.getKey(), o.has("rarity") ? o.get("rarity").getAsString() : "COMMON", o.has("rare") && o.get("rare").getAsBoolean(), color, o.has("chat_message") ? o.get("chat_message").getAsString() : ""));
                }
            }
        }
        kinds = out;
        return out;
    }

    public static Kind byMessage(String plain) {
        for (Kind k : kinds().values()) if (k.chatMessage().equals(plain)) return k;
        return null;
    }

    private static void rods() {
        if (lavaRods != null) return;
        Set<String> lava = new HashSet<>(), water = new HashSet<>();
        JsonObject o = Repo.constant("Items.json");
        if (o != null) {
            for (JsonElement e : o.getAsJsonArray("lava_fishing_rods")) lava.add(e.getAsString());
            for (JsonElement e : o.getAsJsonArray("water_fishing_rods")) water.add(e.getAsString());
        }
        lavaRods = lava;
        waterRods = water;
    }

    public static boolean isLavaRod(String id) {
        rods();
        return lavaRods.contains(id);
    }

    public static boolean isWaterRod(String id) {
        rods();
        return waterRods.contains(id);
    }

    // from skyhanni FishingApi.isFishing
    public static boolean fishing() {
        Minecraft mc = Minecraft.getInstance();
        return holdingRod || (mc != null && mc.player != null && mc.player.fishing != null);
    }

    public static boolean stranded() {
        return GameState.stranded;
    }

    // from skyhanni FishingApi.isTrophyFishing minus the skill level check, that one only guesses for players
    // without the gear
    public static boolean trophyFishing() {
        if (treasureHook) return false;
        return trophyArmor || emberArmor || "HOT_BAIT".equals(baitId) || trophyLine || holdingLava;
    }

    // from skyhanni FishingApi.isBait: items category (last word of the last line), or one of the obfuscated fish
    static boolean isBait(MenuView.Item it) {
        return it.lastClean().endsWith(" BAIT") || it.id.matches("OBFUSCATED_FISH_[12]_(BRONZE|SILVER|GOLD|DIAMOND)");
    }

    // what the stand says, or null: skyhannis mob name filter on an invisible stand with a visible name (skyblockers check)
    static Kind kindOf(ArmorStand st, String plain) {
        if (!st.isInvisible() || !st.hasCustomName() || !st.isCustomNameVisible()) return null;
        String name = labelName(plain);
        if (name == null) return null;
        // from skyhanni FishingApi.countIsZero: a dragon thats always fought, a shop, a summoned soul
        if (name.equals("Reindrake") || name.contains("'s ")) return null;
        if (name.equals("Frosty") && st.position().distanceTo(FROSTY_NPC) < 2) return null;
        return kinds().get(name);
    }

    // mob name in its label like skyhannis MobFactories reads it: a bosses "﴾ [Lv400] Thunder 29M/35M ﴿" first
    // (bossMobNameFilter), bc the basic filter would read its bracket wrong
    static String labelName(String plain) {
        Matcher boss = Patterns.get("SH.mob.detection.filter.boss").matcher(plain);
        if (boss.matches() && boss.group("name") != null) return boss.group("name").trim();
        Matcher m = Patterns.get("SH.mob.detection.filter.basic").matcher(plain);
        return m.find() && m.group("name") != null ? m.group("name").trim() : null;
    }

    static long health(String text) {
        double mult = 1;
        String s = text.replace(",", "");
        if (s.endsWith("k")) mult = 1_000;
        else if (s.endsWith("M")) mult = 1_000_000;
        else if (s.endsWith("B")) mult = 1_000_000_000;
        try {
            return (long) (Double.parseDouble(s.replaceAll("[kMB]$", "")) * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void entity(Entity e) {
        if (!(e instanceof ArmorStand st) || !st.hasCustomName()) return;
        String plain = GameState.strip(st.getCustomName().getString());
        Kind k = kindOf(st, plain);
        if (k == null) return;
        long now = System.currentTimeMillis();
        seen.add(st.getId());
        Live l = LIVE.get(st.getId());
        if (l == null) {
            l = new Live(st.getId(), k, now, st.position());
            LIVE.put(st.getId(), l);
            own(l);
        }
        l.pos = st.position();
        l.lastSeen = now;
        Matcher h = HEALTH.matcher(plain);
        if (h.find()) {
            l.hp = health(h.group(1));
            l.maxHp = health(h.group(2));
        }
    }

    // from skyhanni handleOwnMob times: catch max 1 sec ago + creature came up within 1.5 sec, so an older one by
    // the bobber isnt yours
    static boolean justCaught(long now, long fishedAt, long spawnedAt) {
        return now - fishedAt <= 1000 && now - spawnedAt < 1500;
    }

    // from skyhanni handleOwnMob: the name you just caught, near where the bobber was, within a moment
    private static void own(Live l) {
        if (l.own || mobsToFind <= 0 || lastFished == null || !lastFished.equals(l.kind.name())) return;
        if (!justCaught(System.currentTimeMillis(), lastFishedAt, l.spawned) || lastBobber == null) return;
        double dx = l.pos.x - lastBobber.x, dz = l.pos.z - lastBobber.z;
        if (dx * dx + dz * dz > 9) return;
        l.own = true;
        mobsToFind--;
    }

    // whos alive, in order they came
    public static List<Live> live() {
        return new ArrayList<>(LIVE.values());
    }

    public static int ownCount() {
        int n = 0;
        for (Live l : LIVE.values()) if (l.own) n++;
        return n;
    }

    public static int liveCount() {
        return LIVE.size();
    }

    // the first one in, whose 6 min end first
    public static long oldestAge() {
        long oldest = Long.MAX_VALUE;
        for (Live l : LIVE.values()) oldest = Math.min(oldest, l.spawned);
        return oldest == Long.MAX_VALUE ? -1 : System.currentTimeMillis() - oldest;
    }

    // is this stand id a sea creatures
    public static Live byStand(int id) {
        return LIVE.get(id);
    }

    static final FishHooks.Scan SCAN = new FishHooks.Scan() {
        // from skyhanni SeaCreatureDetectionApi.isActive: no sea creatures in dungeons so all those labels skip the regex
        private long fishedAt;

        @Override
        public boolean wanted() {
            if ("CATACOMBS".equals(GameState.island)) return false;
            long now = System.currentTimeMillis();
            if (fishing()) fishedAt = now;
            return now - fishedAt < 60_000 || !LIVE.isEmpty();
        }

        @Override
        public void begin() {
            seen.clear();
        }

        @Override
        public void entity(Entity e) {
            FishState.entity(e);
        }

        @Override
        public void end() {
            long now = System.currentTimeMillis();
            Minecraft mc = Minecraft.getInstance();
            for (Iterator<Live> it = LIVE.values().iterator(); it.hasNext(); ) {
                Live l = it.next();
                if (!l.announced && now - l.spawned > 1200) {
                    l.announced = true;
                    for (Consumer<Live> c : ON_SPAWN) c.accept(l);
                }
                if (seen.contains(l.stand)) {
                    if (now - l.spawned > DESPAWN_MS) it.remove();
                    continue;
                }
                // from skyhanni assumeDeathIfAreaLeft: gone from where you can see it = dead, gone from far away = out of sight
                boolean near = mc.player != null && l.pos != null && l.pos.distanceTo(mc.player.position()) <= MAX_WAIT_DEATH_DISTANCE;
                if (near) for (Consumer<Live> c : ON_DEATH) c.accept(l);
                if (near || now - l.lastSeen > DESPAWN_MS) it.remove();
            }
        }
    };

    // Feature.left: new world or left skyblock, nobody is alive for us anymore
    static void reset() {
        LIVE.clear();
        seen.clear();
        lastHook = null;
        touched = false;
        lastFished = null;
        mobsToFind = 0;
        lastBobber = null;
        castAt = liquidAt = 0;
    }

    static void tick(Minecraft mc) {
        if (mc.player == null) return;
        FishingHook hook = mc.player.fishing;
        if (hook != lastHook) {
            lastHook = hook;
            touched = false;
            liquidAt = 0;
            if (hook != null) {
                castAt = System.currentTimeMillis();
                for (Runnable r : ON_CAST) r.run();
            }
        }
        if (hook != null) {
            lastBobber = hook.position();
            if (!touched && ((hook.isInLava() && holdingLava) || (hook.isInWater() && holdingWater))) {
                touched = true;
                liquidAt = System.currentTimeMillis();
            }
        }
        if (++ticks % 5 != 0) return;
        ItemStack hand = mc.player.getMainHandItem();
        String id = Gain.sbId(hand);
        boolean was = holdingRod;
        holdingLava = isLavaRod(id);
        holdingWater = isWaterRod(id);
        holdingRod = holdingLava || holdingWater;
        if (holdingRod) {
            var ea = hand.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            var tag = ea == null ? null : ea.copyTag();
            treasureHook = tag != null && "TREASURE_HOOK".equals(tag.getCompoundOrEmpty("hook").getStringOr("part", ""));
            trophyLine = tag != null && "TROPHY_LINE".equals(tag.getCompoundOrEmpty("line").getStringOr("part", ""));
            readBait(mc);
        } else if (was) {
            setBait(ItemStack.EMPTY, "", "", 0);
        }
        int[] tiers = new int[4];
        boolean ember = true;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String armor = Gain.sbId(mc.player.getItemBySlot(slot));
            Matcher m = Patterns.get("SH.fishing.trophyfishing.armor").matcher(armor);
            if (m.matches()) tiers[switch (m.group("tier")) {
                case "BRONZE" -> 0;
                case "SILVER" -> 1;
                case "GOLD" -> 2;
                default -> 3;
            }]++;
            if (!Patterns.get("SH.fishing.trophyfishing.emberarmor").matcher(armor).matches()) ember = false;
        }
        trophyArmor = tiers[0] >= 2 || tiers[1] >= 2 || tiers[2] >= 2 || tiers[3] >= 2;
        emberArmor = ember;
    }

    // from skyhanni checkAndUpdateBaitFromInventory: hypixel shows the bait in the last hotbar slot while you hold a rod
    private static void readBait(Minecraft mc) {
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
        ItemStack stack = mc.player.getInventory().getItem(8);
        MenuView.Item it = MenuView.Item.of(stack);
        if (stack.isEmpty() || !isBait(it)) {
            setBait(ItemStack.EMPTY, "", "", 0);
            return;
        }
        Matcher m = it.firstClean(Patterns.get("SH.fishing.bait.inventory"));
        if (m == null) return;
        setBait(stack, stack.getHoverName().getString(), it.id, Integer.parseInt(m.group("amount").replace(",", "")));
    }

    private static void setBait(ItemStack stack, String name, String id, int amount) {
        baitKnown = true;
        if (id.equals(baitId) && amount == baitAmount) return;
        String before = baitName;
        boolean wasBait = !baitId.isEmpty(), change = !id.equals(baitId);
        baitId = id;
        baitName = name;
        baitAmount = amount;
        baitStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (change) FishCards.baitChanged(wasBait, before, name);
    }

    // from skyhanni SeaCreatureManager.onChat: the double hook line comes before the creature line
    public static boolean doubleHook() {
        return System.currentTimeMillis() - doubleHookAt < 2000;
    }

    static ChatBus.Result chat(ChatBus.Line line) {
        String plain = line.plain().trim();
        if (Patterns.get("SH.fishing.seacreature.doublehook.colorless").matcher(plain).matches()) {
            doubleHookAt = System.currentTimeMillis();
            return ChatBus.PASS;
        }
        Kind k = byMessage(plain);
        if (k == null) return ChatBus.PASS;
        boolean twice = doubleHook();
        lastFished = k.name();
        lastFishedAt = System.currentTimeMillis();
        mobsToFind = twice ? 2 : 1;
        for (Live l : LIVE.values()) own(l);
        for (Consumer<Live> c : ON_CATCH_OWN) c.accept(new Live(-1, k, lastFishedAt, lastBobber == null ? Vec3.ZERO : lastBobber));
        return ChatBus.PASS;
    }
}
