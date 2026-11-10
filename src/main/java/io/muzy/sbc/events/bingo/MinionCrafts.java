// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.bingo;

import io.muzy.sbc.combat.Combat;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.events.EventSheet;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenItems;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// how much more you need for a minion's next tier. from skyhanni MinionCraftHelper + FirstMinionTier, on our recipes
// their text rows become card lines, the percent gets a meter
public final class MinionCrafts {
    public static final String ID = "bingo_minion_craft";
    private static final Set<String> NOT_TIER_ONE = Set.of("REVENANT_GENERATOR_1", "TARANTULA_GENERATOR_1", "VOIDLING_GENERATOR_1", "INFERNO_GENERATOR_1", "VAMPIRE_GENERATOR_1");

    // one row: minion heading or an ingredient
    record Row(String minion, String item, long have, long need, boolean tool) {
        boolean done() {
            return need > 0 && have >= need;
        }
    }

    // what the recipes answer, tests give their own
    interface Recipes {
        // ingredient -> count for each recipe that makes this id, one map per recipe
        List<Map<String, Integer>> of(String id);

        // NeuItems.getPrimitiveMultiplier
        GardenItems.Primitive primitive(String id);
    }

    private static final Recipes GAME = new Recipes() {
        private final Map<String, List<Map<String, Integer>>> cache = new java.util.HashMap<>();

        @Override
        public List<Map<String, Integer>> of(String id) {
            return cache.computeIfAbsent(id, k -> {
                List<Map<String, Integer>> out = new ArrayList<>();
                for (ItemInfo.Recipe r : ItemInfo.recipes(k)) {
                    if (!r.type().equals("crafting")) continue;
                    Map<String, Integer> map = new LinkedHashMap<>();
                    for (ItemInfo.Part p : r.parts()) map.merge(p.id(), p.count(), Integer::sum);
                    out.add(map);
                }
                return out;
            });
        }

        @Override
        public GardenItems.Primitive primitive(String id) {
            return GardenItems.primitive(id);
        }
    };

    private static final List<String> TIER_ONE = new ArrayList<>();
    private static final Set<String> ALL_INGREDIENTS = new HashSet<>();
    private static final Set<String> NOTIFIED = new HashSet<>();
    private static List<Row> display = List.of();
    private static boolean hasMinion, hasItems;
    private static int ticks;

    private MinionCrafts() {}

    // isMinionName
    static boolean isMinionName(String name) {
        return name.contains(" Minion ") && !name.contains(" Minion Skin");
    }

    // addOneToId
    static String addOne(String id) {
        int at = id.lastIndexOf('_');
        return id.substring(0, at + 1) + (Integer.parseInt(id.substring(at + 1)) + 1);
    }

    // init: tier 1 minions + what any minion is made of
    private static void init() {
        if (!TIER_ONE.isEmpty()) return;
        for (String id : ItemRepo.ids()) {
            if (id.endsWith("_GENERATOR_1") && !NOT_TIER_ONE.contains(id)) TIER_ONE.add(id);
            if (id.contains("_GENERATOR_")) for (Map<String, Integer> r : GAME.of(id)) for (String ing : r.keySet()) if (!ing.contains("_GENERATOR_")) ALL_INGREDIENTS.add(ing);
        }
    }

    // isAllowed: this item, or something it crafts into, goes into one of these minions
    static boolean allowed(List<String> minions, String id, Recipes recipes) {
        GardenItems.Primitive stack = recipes.primitive(id);
        for (String minion : minions) {
            for (Map<String, Integer> r : recipes.of(minion)) {
                for (String ingredient : r.keySet()) {
                    if (ingredient.equals(id)) return true;
                    GardenItems.Primitive p = recipes.primitive(ingredient);
                    if (stack.id().equals(p.id()) && stack.amount() < p.amount()) return true;
                }
            }
        }
        return false;
    }

    // addMinion: next tier ingredients vs whats left of the items, then the tier after if this one can be made
    static void addMinion(String name, int tier, String minionId, Map<String, Integer> items, boolean progressFirst, List<Row> out, Recipes recipes, List<String> craftable) {
        int next = tier + 1;
        String minionName = name + " Minion " + next;
        String nextId = addOne(minionId);
        for (Map<String, Integer> recipe : recipes.of(nextId)) {
            Row heading = new Row(minionName, "", 0, 0, false);
            int start = out.size();
            out.add(heading);
            Map<String, Integer> need = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : recipe.entrySet()) {
                GardenItems.Primitive p = recipes.primitive(e.getKey());
                if (!minionId.equals(p.id())) need.merge(p.id(), e.getValue() * p.amount(), Integer::sum);
            }
            boolean allDone = true, dropped = false;
            for (Map.Entry<String, Integer> e : need.entrySet()) {
                GardenItems.Primitive p = recipes.primitive(e.getKey());
                long amount = (long) e.getValue() * p.amount();
                int have = items.getOrDefault(p.id(), 0);
                boolean tool = p.id().startsWith("WOOD_");
                if (have >= amount) {
                    items.put(p.id(), (int) (have - amount));
                    out.add(new Row(minionName, e.getKey(), amount, amount, tool));
                } else {
                    if (!progressFirst && !tool && minionId.endsWith("_0")) {
                        while (out.size() > start) out.removeLast();
                        dropped = true;
                        break;
                    }
                    out.add(new Row(minionName, e.getKey(), have, amount, tool));
                    allDone = false;
                }
            }
            if (dropped) return;
            if (allDone) {
                craftable.add(minionName);
                addMinion(name, next, nextId, items, progressFirst, out, recipes, craftable);
            }
        }
    }

    // loadFromInventory with FirstMinionTier: minions you hold + a tier 0 for each first tier you have items for
    // and never crafted
    private static void update(Minecraft mc, boolean draw) {
        init();
        Map<String, String> minions = new LinkedHashMap<>();
        Map<String, Integer> items = new LinkedHashMap<>();
        var inv = mc.player.getInventory();
        List<String> allMinions = new ArrayList<>(TIER_ONE);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String name = GameState.strip(st.getHoverName().getString()).trim();
            if (isMinionName(name)) {
                String id = Gain.sbId(st);
                minions.put(name, id);
                if (id != null && !id.isEmpty()) allMinions.add(addOne(id));
            }
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || isMinionName(GameState.strip(st.getHoverName().getString()).trim()) || hypixelEnchanted(st)) continue;
            String id = Gain.sbId(st);
            if (id == null || id.isEmpty() || !ALL_INGREDIENTS.contains(id) || !allowed(allMinions, id, GAME)) continue;
            GardenItems.Primitive p = GAME.primitive(id);
            items.merge(p.id(), st.getCount() * p.amount(), Integer::sum);
        }
        Set<String> done = BingoCard.session().tierOneMinionsDone;
        firstTier(items, minions, TIER_ONE, done, GAME);
        hasItems = !minions.isEmpty();
        if (!draw) return;
        List<Row> rows = new ArrayList<>();
        List<String> craftable = new ArrayList<>();
        boolean progressFirst = Combat.opt(ID, "progress_first");
        for (Map.Entry<String, String> e : minions.entrySet()) {
            Matcher m = Patterns.get("SH.bingo.minion.name").matcher(e.getKey());
            if (!m.matches()) continue;
            String number = m.group("number");
            int tier = number.chars().allMatch(Character::isDigit) ? Integer.parseInt(number) : GameState.roman(number);
            addMinion(GameState.strip(m.group("name")), tier, e.getValue(), items, progressFirst, rows, GAME, craftable);
        }
        display = rows;
        for (String c : craftable) {
            if (NOTIFIED.add(c)) Combat.title("§eCan craft §9" + c, 40);
        }
    }

    // ItemUtils.hasHypixelEnchantments: tools you upgraded arent material
    private static boolean hypixelEnchanted(ItemStack st) {
        var data = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("enchantments");
    }

    // FirstMinionTier.firstMinionTier
    static void firstTier(Map<String, Integer> items, Map<String, String> minions, List<String> tierOne, Set<String> done, Recipes recipes) {
        List<String> open = new ArrayList<>();
        for (String id : tierOne) if (!done.contains(id)) open.add(id);
        for (String id : open) {
            String prefix = id.substring(0, id.length() - 1);
            for (String have : minions.values()) {
                if (have != null && have.startsWith(prefix)) {
                    if (done.add(id)) Store.markDirty();
                    break;
                }
            }
        }
        Set<String> help = new HashSet<>();
        for (String id : items.keySet()) if (!id.startsWith("WOOD_")) help.add(id);
        for (String id : open) {
            for (Map<String, Integer> r : recipes.of(id)) {
                boolean any = false;
                for (String ing : r.keySet()) if (help.contains(ing)) any = true;
                if (!any) continue;
                String name = ItemRepo.name(id).replace(" I", " 0");
                minions.put(name, id.replace("_1", "_0"));
            }
        }
    }

    // package-private so the test sees the same lines the card has
    static void fill(EventSheet s, List<Row> rows) {
        String section = null;
        for (Row r : rows) {
            if (r.item().isEmpty()) {
                if (!r.minion().equals(section)) s.section(r.minion());
                section = r.minion();
                continue;
            }
            String name = ItemRepo.name(r.item());
            if (r.done()) {
                s.line(name, "Done", r.tool() ? Modern.DIM : Modern.GOOD);
            } else {
                double p = r.have() / (double) r.need();
                s.line(name, String.format(Locale.ROOT, "%,d/%,d", r.have(), r.need()), 0xFFFFFF55).tail(Math.round(p * 100) + "%").meter(p);
            }
        }
    }

    private static final HudElement CARD = new EventSheet.Card(ID, "Minion crafts", "Events", Zone.TOP_LEFT, 67, ID) {
        @Override
        public boolean visible() {
            return BingoCard.onBingo() && !display.isEmpty();
        }

        @Override
        protected void live(EventSheet s) {
            fill(s, display);
        }

        @Override
        protected void sample(EventSheet s) {
            fill(s, List.of(new MinionCrafts.Row("Cobblestone Minion 2", "", 0, 0, false), new MinionCrafts.Row("Cobblestone Minion 2", "COBBLESTONE", 180, 320, false), new MinionCrafts.Row("Wheat Minion 3", "", 0, 0, false), new MinionCrafts.Row("Wheat Minion 3", "WHEAT", 64, 64, false)));
        }

        @Override
        protected List<HudElement.Row> plainRows() {
            List<HudElement.Row> out = new ArrayList<>();
            for (MinionCrafts.Row r : display) {
                if (r.item().isEmpty()) out.add(new HudElement.Row(r.minion()));
                else out.add(new HudElement.Row(ItemRepo.name(r.item()), r.done() ? "Done" : String.format(Locale.ROOT, "%,d/%,d", r.have(), r.need()), Theme.current().text()));
            }
            return out;
        }

        @Override
        protected List<HudElement.Row> plainDemo() {
            return List.of(new HudElement.Row("Cobblestone", "180/320", Theme.current().text()));
        }
    };

    private static void tick(Minecraft mc) {
        if (mc.player == null || !BingoCard.onBingo()) return;
        ticks++;
        if (ticks % 10 == 0) {
            hasMinion = false;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (isMinionName(GameState.strip(inv.getItem(i).getHoverName().getString()))) hasMinion = true;
        }
        if (ticks % 40 == 0) update(mc, false);
        if (!hasMinion && !hasItems) {
            display = List.of();
            return;
        }
        if (ticks % 3 == 0) update(mc, true);
    }

    // onInventoryFullyOpened: first tiers you crafted
    private static void craftedMenu(MenuView v) {
        if (!BingoCard.onBingo()) return;
        Set<String> done = BingoCard.session().tierOneMinionsDone;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (!it.name.startsWith("§e")) continue;
            String id = ItemRepo.idForName(it.clean + " I");
            if (id == null) id = (it.clean.replace(" ", "_").toUpperCase(Locale.ROOT) + "_1").replace("MINION", "GENERATOR").replace("CAVE_SPIDER", "CAVESPIDER");
            if (done.add(id)) Store.markDirty();
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Events", "Minion crafts", "On a Bingo profile, how much more you need for the next tier of the minions in your inventory, and a title when you can craft one.", "SkyHanni: Minion Craft Helper")
                .when(() -> GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV)
                .tick(MinionCrafts::tick)
                .left(() -> {
                    display = List.of();
                    NOTIFIED.clear();
                })
                .option(Option.bool("bingo_minion_craft.progress_first", "Progress to first tiers", "Also show first tiers you do not have all the items for.", false)));
        MenuMarks.add(ID, Patterns.get("SH.bingo.minion.inventory"), MinionCrafts::craftedMenu);
        HudLayout.add(CARD);
    }
}
