// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.milestones;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// glue for the skyhanni garden milestone port (GardenApi tool handling, GardenCropBreakTracker,
// GardenCropSpeed, WrongDataFix, GardenCropMilestoneDisplay, GardenBestCropTime, CropMoneyDisplay)
// held tool + crop, crop clicks, per sec speed check, tab widget + menu reading, 3 hud elements, commands
public final class GardenMilestones {
    public static final String MILESTONES = "garden_crop_milestones", BEST = "garden_best_crop_time", MONEY = "garden_money_per_hour";

    private static final java.util.Set<String> EXTRA_FARMING_TOOLS = java.util.Set.of("BASIC_GARDENING_HOE", "ADVANCED_GARDENING_HOE", "BASIC_GARDENING_AXE", "ADVANCED_GARDENING_AXE", "ROOKIE_HOE", "BINGHOE");

    static String toolInHand, itemUuid = "";
    static CropType cropInHand, lastBrokenCropType;
    static boolean itemHasCounter;
    private static long lastSecond, lastMoney;
    private static Vec3 lastLocation;
    private static List<String> progressDisplay = List.of(), moneyDisplay = List.of();
    private static int lastMenuId = -1;
    private static String lastProfile = "";

    private GardenMilestones() {}

    // both cards shared this key, so the saved value is what both showed. it gets copied to each
    static void splitShowOnlyBest(java.util.Map<String, Object> options) {
        Object shared = options.remove("showOnlyBest");
        if (shared == null) return;
        options.putIfAbsent(BEST + ".showOnlyBest", shared);
        options.putIfAbsent(MONEY + ".showOnlyBest", shared);
    }

    static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static int i(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : (int) o.number();
    }

    private static boolean garden() {
        return "GARDEN".equals(GameState.island) || SbcMod.DEV;
    }

    public static Store.GardenProfile profile() {
        String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
        return Store.I.garden.computeIfAbsent(key, k -> new Store.GardenProfile());
    }

    private static boolean night(Minecraft mc) {
        return mc.level != null && mc.level.getOverworldClockTime() % 24000 >= 12000;
    }

    // GardenApi.readCounter: cultivating, hoe xp, old counter
    static Long readCounter(CompoundTag ea) {
        long c = ea.getLongOr("farmed_cultivating", 0);
        if (c != 0) return c;
        double exp = ea.getDoubleOr("levelable_exp", 0);
        if (exp != 0) return (long) exp;
        long old = ea.getLongOr("mined_crops", 0);
        return old != 0 ? old : null;
    }

    // GardenApi.hasFarmingToolInHand, read every half sec like the rest of the held tool stuff
    public static boolean hasToolInHand() {
        return toolInHand != null;
    }

    // GardenApi.cropInHand: the tool's own crop
    public static CropType cropInHand() {
        return cropInHand;
    }

    // GardenApi.isCurrentlyFarming
    public static boolean isCurrentlyFarming() {
        return garden() && GardenCropSpeed.averageBlocksPerSecond > 0 && toolInHand != null;
    }

    // GardenApi.getCurrentlyFarmedCrop
    public static CropType getCurrentlyFarmedCrop() {
        if (toolInHand == null) return null;
        if (lastBrokenCropType != null) return lastBrokenCropType;
        if (cropInHand != null) return cropInHand;
        return GardenCropSpeed.lastBrokenCrop;
    }

    // GardenApi.checkItemInHand
    private static void checkItemInHand(Minecraft mc) {
        ItemStack st = mc.player.getMainHandItem();
        String id = Gain.sbId(st);
        CropType crop = id.isEmpty() ? null : CropType.fromToolId(id, night(mc));
        String newTool = crop != null ? crop.cropName : (EXTRA_FARMING_TOOLS.contains(id) ? id : null);
        var cd = st.get(DataComponents.CUSTOM_DATA);
        String uuid = cd == null ? "" : cd.copyTag().getStringOr("uuid", "");
        if (!uuid.equals(itemUuid) || (crop != cropInHand && !(toolInHand == null && newTool == null))) {
            toolInHand = newTool;
            cropInHand = crop;
            itemUuid = uuid;
            lastBrokenCropType = null;
            itemHasCounter = cd != null && readCounter(cd.copyTag()) != null;
            // GardenToolChangeEvent
            GardenCropSpeed.onGardenToolChange();
            update();
        }
    }

    // BlockState.isBabyCrop: age property 0
    private static boolean isBabyCrop(BlockState state) {
        for (Property<?> p : state.getProperties())
            if (p.getName().equals("age") && p instanceof IntegerProperty ip) return state.getValue(ip) == 0;
        return false;
    }

    // GardenApi.onBlockClick -> CropClickEvent
    private static void onBlockClick(Vec3 pos, boolean right) {
        if (right || !garden()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos bp = BlockPos.containing(pos);
        BlockState state = mc.level.getBlockState(bp);
        CropType crop = CropType.fromBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), night(mc));
        if (crop == null) return;
        if (crop.multiplier() == 1 && isBabyCrop(state)) return;
        if (pos.equals(lastLocation)) return;
        lastLocation = pos;
        GardenCropSpeed.onCropClick(crop, System.currentTimeMillis());
        lastBrokenCropType = crop;
    }

    static void update() {
        CropType crop = getCurrentlyFarmedCrop();
        // getDefaultCrop: no tool = what you last farmed
        if (crop == null && b(MILESTONES, "showWithoutTool", false)) crop = GardenCropSpeed.lastBrokenCrop;
        if (crop == null) {
            progressDisplay = List.of();
            return;
        }
        GardenCropMilestoneDisplay.showMaxTier = b(MILESTONES, "showMaxTier", false);
        GardenCropMilestoneDisplay.blocksBrokenPrecision = i(MILESTONES, "blocksBrokenPrecision", 2);
        GardenCropMilestoneDisplay.overflowDisplay = b(MILESTONES, "overflow", false);
        progressDisplay = GardenCropMilestoneDisplay.drawProgressDisplay(crop, true, Vitals.stat("Farming Fortune"), GardenCropSpeed.averageBlocksPerSecond, itemHasCounter);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!GameState.profileName.equals(lastProfile)) {
            lastProfile = GameState.profileName;
            GardenCropSpeed.onProfileJoin();
            CropMilestonesApi.clearMilestoneCache();
            CropMilestonesApi.inaccurateMilestone = profile().cropMilestoneCounter.isEmpty();
            WrongDataFix.reset();
            CropBreakCounter.reset();
        }
        if (!garden()) {
            progressDisplay = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        if (mc.player.tickCount % 10 == 1) checkItemInHand(mc);
        if (mc.player.tickCount % 5 == 0) {
            // OwnInventoryItemUpdateEvent for the held tool + GardenCropBreakTracker's 5 tick flush
            ItemStack st = mc.player.getMainHandItem();
            var cd = st.get(DataComponents.CUSTOM_DATA);
            if (itemHasCounter && cd != null && !itemUuid.isEmpty()) {
                CropType crop = lastBrokenCropType != null ? lastBrokenCropType : cropInHand;
                CropBreakCounter.onToolCounter(itemUuid, readCounter(cd.copyTag()), crop);
            }
            CropBreakCounter.flush();
            int[] lvl = CropMilestonesApi.overflowLevelUp;
            if (lvl != null) {
                CropMilestonesApi.overflowLevelUp = null;
                CropType c = CropType.values()[lvl[0]];
                if (b(MILESTONES, "overflow_chat", true)) mc.player.sendSystemMessage(Component.literal("§r§b§lGARDEN MILESTONE §3" + c.cropName + " §8" + lvl[1] + "➜§3" + lvl[2]));
            }
        }
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            GardenCropSpeed.blocksBrokenResetTime = i(MILESTONES, "blocksBrokenResetTime", 5);
            GardenCropSpeed.checkSpeed();
            WrongDataFix.onTab(GameState.tab);
            update();
            if (Features.on(MONEY) && now - lastMoney >= 5000 && (getCurrentlyFarmedCrop() != null || b(MONEY, "alwaysOn", false))) {
                lastMoney = now;
                ItemStack st = mc.player.getMainHandItem();
                var cd = st.get(DataComponents.CUSTOM_DATA);
                boolean bountiful = cd != null && "bountiful".equals(cd.copyTag().getStringOr("modifier", ""));
                CropMoneyDisplay.compactPrice = b(MONEY, "compactPrice", true);
                CropMoneyDisplay.bountiful = b(MONEY, "bountiful", true);
                CropMoneyDisplay.mergeSeeds = b(MONEY, "mergeSeeds", true);
                CropMoneyDisplay.showCurrent = b(MONEY, "showCurrent", true);
                CropMoneyDisplay.showOnlyBest = i(MONEY, "showOnlyBest", 5);
                moneyDisplay = CropMoneyDisplay.build(getCurrentlyFarmedCrop(), bountiful);
            }
        }
        readMenu(mc);
    }

    // InventoryFullyOpenedEvent("Crop Milestones") -> WrongDataFix
    private static void readMenu(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            lastMenuId = -1;
            return;
        }
        if (menu.containerId == lastMenuId) return;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        if (!title.equals("Crop Milestones")) return;
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        List<List<String>> lores = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            any = true;
            lores.add(LegacyText.lore(st));
        }
        if (!any) return;
        lastMenuId = menu.containerId;
        WrongDataFix.onCropMilestonesMenu(lores);
        update();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (garden()) WrongDataFix.onChat(LegacyText.of(line.component()));
        return ChatBus.PASS;
    }

    private static List<HudElement.Row> rows(List<String> lines) {
        List<HudElement.Row> out = new ArrayList<>();
        for (String l : lines) out.add(new HudElement.Row(l));
        return out;
    }

    // farmed crop's total vs the last tier
    private static MilestoneCards.Road road() {
        CropType crop = getCurrentlyFarmedCrop();
        Long total = crop == null ? null : CropMilestonesApi.getMilestoneCounter(crop);
        return total == null ? null : new MilestoneCards.Road(total, CropMilestonesApi.getMaxedMilestoneAmount(crop), CropMilestonesApi.getMaxTier());
    }

    // what the hud editor shows: real lines of a mushroom 21 at 56.6% (numbers from Garden.json) farmed at 20 blocks/s
    private static final List<String> DEMO_PROGRESS = List.of("§6Crop Milestones", "§7Mushroom §821➜§322", "§e226,400§8/§e400,000", "§7In §b49m 2s", "§7Crops/Minute§8: §e3,540", "§7Blocks/Second§8: §e20.00");
    private static final MilestoneCards.Road DEMO_ROAD = new MilestoneCards.Road(1_294_810, 20_218_410, 46);
    private static final List<String> DEMO_BEST = List.of("§eBest Crop Time §7(§bSkyBlock Level§7)", "§71# §eMushroom 21➜22§r §b49m 2s", "§72# §7Wheat 30➜31§r §b3h 40m", "§73# §7Cocoa Beans 28➜29§r §b9h 12m", "§74# §7Nether Wart 33➜34§r §b1d 6h");
    private static final List<String> DEMO_MONEY = List.of("§7Money per Hour when selling: §7(§eSell Offer§7)", "§7#1 §eEnchanted Brown Mushroom§7: §61.4M", "§7#2 §eEnchanted Red Mushroom§7: §61.3M", "§7#3 §7Enchanted Golden Carrot§7: §6987k", "§7#4 §7Enchanted Nether Wart§7: §6842k", "§7#5 §7Enchanted Hay Bale§7: §696.4k");

    public static void register() {
        CropMilestonesApi.load();
        CropMilestonesApi.storage = GardenMilestones::profile;
        GardenCropSpeed.storage = GardenMilestones::profile;
        GardenCropSpeed.currentlyFarmedCrop = GardenMilestones::getCurrentlyFarmedCrop;
        CropMilestonesApi.onUpdate = GardenMilestones::update;
        CropMoneyDisplay.bazaar = id -> {
            Prices.Product p = Prices.get(id.replace(":", "-"));
            if (p == null) p = Prices.get(id);
            return p == null ? null : new double[]{p.buy(), p.sell()};
        };
        CropMoneyDisplay.npcPrice = ItemRepo::npcPrice;
        CropMoneyDisplay.itemName = id -> GameState.strip(ItemRepo.name(id));

        Features.add(new Feature(MILESTONES, "Garden", "Crop milestones", "For the crop you farm: milestone tier, progress x/y and %, time to the next tier, crops per second/minute/hour and blocks per second. The counter comes from the tool (Cultivating / hoe counter), the Crop Milestones menu and the tab widget.", "SkyHanni: Crop Milestone Display")
                .when(GardenMilestones::garden)
                .option(Option.bool("showMaxTier", "Show max tier", "Count towards tier 46 instead of the next tier.", false))
                .option(Option.bool("overflow", "Overflow", "Keep counting past the last tier.", false))
                .option(Option.integer("blocksBrokenResetTime", "Speed reset after (s)", "Seconds without breaking a crop before the speed resets.", 5, 1, 60))
                .option(Option.integer("garden_crop_milestones.blocksBrokenPrecision", "Blocks per second decimals", "", 2, 0, 6))
                .option(Option.bool("garden_crop_milestones.showWithoutTool", "Show without a tool", "Keeps showing the crop you farmed last.", false))
                .option(Option.bool("garden_crop_milestones.overflow_chat", "Say overflow tiers in chat", "When a crop goes past the last tier.", true)));
        Features.add(new Feature(BEST, "Garden", "Best crop time", "Which crop reaches its next milestone soonest at your measured speeds.", "SkyHanni: Best Crop Time")
                .when(GardenMilestones::garden)
                .option(Option.integer("garden_best_crop_time.showOnlyBest", "Show best", "", 3, 1, 13))
                .option(Option.bool("garden_best_crop_time.alwaysOn", "Always on", "In the Garden, also without a tool.", false))
                .option(Option.bool("garden_best_crop_time.showCurrent", "Keep the crop you farm", "Adds it to the list if it ranks below the best ones.", true))
                .option(Option.choice("garden_best_crop_time.sort", "Rank by", "SkyBlock level looks at the time alone, Garden Experience at what the next tier gives for it.", "Garden Experience", "Garden Experience", "SkyBlock Level")));
        Features.add(new Feature(MONEY, "Garden", "Money per hour", "Coins per hour for the Bazaar forms of your crops at your measured speed (sell offer price).", "SkyHanni: Crop Money Display")
                .when(GardenMilestones::garden)
                .option(Option.bool("alwaysOn", "Always on", "", false))
                .option(Option.bool("compactPrice", "Compact price", "", true))
                .option(Option.integer("garden_money_per_hour.showOnlyBest", "Show best", "", 5, 1, 20))
                .option(Option.bool("garden_money_per_hour.showCurrent", "Keep the crop you farm", "Adds it to the list if it ranks below the best ones.", true))
                .option(Option.bool("garden_money_per_hour.bountiful", "Include Bountiful", "The coins from the Bountiful reforge.", true))
                .option(Option.bool("garden_money_per_hour.mergeSeeds", "Merge seeds with wheat", "", true))
                .option(Option.bool("garden_money_per_hour.mooshroom", "Include Mooshroom Cow", "The mushrooms your pet finds for you.", true))
                .option(Option.bool("garden_money_per_hour.rare_crops", "Include rare crops", "What the rare crops of your armour are worth on average.", true)));
        splitShowOnlyBest(dev.zmuzy.sbc.core.Config.I.options);
        ChatBus.register(ChatBus.OBSERVE + 25, "garden_milestones", null, GardenMilestones::onChat);
        Safe.endTick("GardenMilestones.tick", GardenMilestones::tick);
        Interactions.onBlockClick(GardenMilestones::onBlockClick);

        HudLayout.add(new GardenCard("garden_crop_milestones", "Crop milestones", Zone.TOP_LEFT, 36, MILESTONES) {
            private List<String> lines() {
                return demo ? DEMO_PROGRESS : progressDisplay;
            }

            @Override public boolean visible() {
                return garden() && !progressDisplay.isEmpty();
            }

            @Override public List<Row> rows() {
                return GardenMilestones.rows(progressDisplay);
            }

            @Override public List<Row> demo() {
                return GardenMilestones.rows(DEMO_PROGRESS);
            }

            @Override protected GardenCard.Chip chip() {
                return MilestoneCards.state(lines());
            }

            @Override protected List<GardenCard.Block> blocks() {
                return MilestoneCards.progress(shownLines(lines()), demo ? DEMO_ROAD : road(), compact());
            }
        });
        HudLayout.add(new GardenCard("garden_best_crop_time", "Best crop time", Zone.TOP_LEFT, 37, BEST) {
            private List<String> lines() {
                GardenCropMilestoneDisplay.bestByGardenExp = "Garden Experience".equals(String.valueOf(Features.get(BEST).option("sort").raw()));
                return demo ? DEMO_BEST : GardenCropMilestoneDisplay.drawBestDisplay(getCurrentlyFarmedCrop(), i(BEST, "showOnlyBest", 3), b(BEST, "showCurrent", true));
            }

            @Override public boolean visible() {
                return garden() && (getCurrentlyFarmedCrop() != null || b(BEST, "alwaysOn", false));
            }

            @Override public List<Row> rows() {
                return GardenMilestones.rows(GardenCropMilestoneDisplay.drawBestDisplay(getCurrentlyFarmedCrop(), i(BEST, "showOnlyBest", 3), b(BEST, "showCurrent", true)));
            }

            @Override public List<Row> demo() {
                return GardenMilestones.rows(DEMO_BEST);
            }

            @Override protected String bandValue() {
                return MilestoneCards.bestKind(lines());
            }

            @Override protected List<GardenCard.Block> blocks() {
                return MilestoneCards.best(shownLines(lines()), compact());
            }
        });
        HudLayout.add(new GardenCard("garden_money_per_hour", "Money per hour", Zone.TOP_LEFT, 38, MONEY) {
            private List<String> lines() {
                return demo ? DEMO_MONEY : moneyDisplay;
            }

            @Override public boolean visible() {
                return garden() && !moneyDisplay.isEmpty() && (getCurrentlyFarmedCrop() != null || b(MONEY, "alwaysOn", false));
            }

            @Override public List<Row> rows() {
                return GardenMilestones.rows(moneyDisplay);
            }

            @Override public List<Row> demo() {
                return GardenMilestones.rows(DEMO_MONEY);
            }

            @Override protected String bandValue() {
                return MilestoneCards.moneyKind(lines());
            }

            @Override protected List<GardenCard.Block> blocks() {
                return MilestoneCards.money(shownLines(lines()), compact());
            }
        });
        Safe.commands("GardenMilestones.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("resetcropmilestones").executes(c -> {
                    CropMilestonesApi.resetMilestones();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §cReset Crop Milestones!"));
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("resetcropmilestones", "Reset the crop milestone counters of this profile");
    }
}
