// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.events.EventSheet;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.enchants.EnchantParser;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.mc.mixin.ContainerAccess;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// small chocolate factory helpers from skyhanni, each its own part: CFTooltip, CFTooltipCompact,
// CFTooltipStray, CFInventory middle click, CFKeybinds, CFUpgradeWarning, CFCustomReminder, CFBlockOpen,
// HoppityNpc, WarpMenuUniques, HoppityMuteEggSounds, HoppityEggDisplayManager player transparency
public final class CFHelpers {
    public static final String UPGRADE = "cf_upgrade_warning", REMINDER = "cf_reminder", KEYS = "cf_keys", BLOCK = "cf_block_open", SHOP = "hoppity_shop", WARP = "hoppity_warp_uniques", SOUNDS = "hoppity_egg_sounds", SHORTCUT = "cf_shortcut", TIP_MOVE = "cf_tooltip_move";

    private static long lastChocolateClick = HoppityEnv.FAR_PAST, lastKey = HoppityEnv.FAR_PAST;
    private static long lastUpgradeWarning = HoppityEnv.FAR_PAST, lastReminderWarning = HoppityEnv.FAR_PAST, lastBlockedCommand = HoppityEnv.FAR_PAST, lastShopReminder = HoppityEnv.FAR_PAST;
    private static long lastEggMenu = HoppityEnv.FAR_PAST;
    private static int lastUpgradeSlot = -1, lastUpgradeLevel;
    private static boolean inEggMenu;
    private static long lastShortcut = HoppityEnv.FAR_PAST;

    private CFHelpers() {}

    private static boolean on(String id) {
        Feature f = Features.get(id);
        return f != null && f.enabled();
    }

    private static boolean cf(String key, boolean def) {
        return Hoppity.b(Hoppity.CF, key, def);
    }

    // --- tooltips ---

    // CFTooltipCompact.tooltipMove: only the factory's own slots
    private static boolean tipMoved() {
        if (!ChocolateFactory.inChocolateFactory || !on(TIP_MOVE)) return false;
        int slot = hoveredSlot();
        return slot >= 0 && slot <= 44;
    }

    // where the tooltip goes instead of the mouse. the game adds 12 and flips it if it'd leave the screen
    public static int tipX(int mouseX) {
        if (!tipMoved()) return mouseX;
        boolean right = "Right".equals(Features.get(TIP_MOVE).option("cf_tooltip_move.side").choice());
        return right ? Minecraft.getInstance().getWindow().getGuiScaledWidth() : -8;
    }

    public static int tipY(int mouseY) {
        return tipMoved() ? 20 : mouseY;
    }

    private static int hoveredSlot() {
        var mc = Minecraft.getInstance();
        if (mc.gui == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || !(s instanceof ContainerAccess a)) return -1;
        Slot slot = a.sbc$hovered();
        return slot == null ? -1 : slot.index;
    }

    // factory tooltips, the tooltip feature of Hoppity.CF
    public static void tooltip(ItemStack stack, List<Component> lines) {
        if (HoppityCollectionStats.inInventory && cf("descriptiveMilestones", true)) milestoneLore(LegacyText.name(stack), lines);
        if (!ChocolateFactory.inChocolateFactory) return;
        int slot = hoveredSlot();
        if (slot < 0) return;
        List<String> plain = new ArrayList<>();
        for (Component c : lines) plain.add(c.getString());
        tooltipLines(slot, plain, LegacyText.lore(stack), lines);
    }

    // buildDescriptiveMilestoneLore: the two lines that only say "Factory Milestones" become what it takes
    static void milestoneLore(String legacyName, List<Component> lines) {
        for (int i = 2; i < lines.size(); i++) {
            String line = LegacyText.of(lines.get(i));
            boolean factory = HoppityCollectionStats.FACTORY_MILESTONE.matcher(line).matches(), shop = HoppityCollectionStats.SHOP_MILESTONE.matcher(line).matches();
            if (!factory && !shop) continue;
            String name = GameState.strip(legacyName);
            for (HoppityData.Milestone m : factory ? HoppityData.factoryMilestones() : HoppityData.shopMilestones()) {
                if (!GameState.strip(m.rabbit()).equals(name)) continue;
                lines.set(i - 1, Component.literal("§7Obtained by " + (shop ? "spending" : "reaching") + " §6" + EnchantParser.shortFormat(m.amount())));
                lines.set(i, Component.literal("§7all-time §6Chocolate."));
                return;
            }
            return;
        }
    }

    // missingRabbitStackNeedsFix: a grey dye in the rabbit's rarity colour
    static void collectionMarks(MenuView v) {
        if (!cf("rarityDyeRecolor", true)) return;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (!it.model.equals("gray_dye") || it.name.isEmpty()) continue;
            String rarity = HoppityApi.rarityByRabbit(it.name);
            String dye = rarity == null ? null : switch (rarity) {
                case "COMMON" -> "light_gray_dye";
                case "UNCOMMON" -> "lime_dye";
                case "RARE" -> "lapis_lazuli";
                case "EPIC" -> "purple_dye";
                case "LEGENDARY" -> "orange_dye";
                case "MYTHIC" -> "magenta_dye";
                case "DIVINE" -> "light_blue_dye";
                default -> null;
            };
            if (dye != null) v.mark(slot).icon = io.muzy.sbc.core.LegacyItems.stack("minecraft:" + dye);
        }
    }

    // CFShortcut: a slot on hypixel's skyblock menu that opens the factory
    static void shortcutMarks(MenuView v) {
        int slot = HoppityData.cfShortcutIndex;
        if (slot >= v.top || disallowedIsland()) return;
        MenuView.Mark m = v.mark(slot);
        m.icon = io.muzy.sbc.core.LegacyItems.stack("minecraft:cocoa_beans");
        m.tip = List.of("§6Open Chocolate Factory", "§7Click here to run", "§e/chocolatefactory");
    }

    private static boolean disallowedIsland() {
        var tags = io.muzy.sbc.core.Repo.constant("IslandTypeTags.json");
        if (tags == null || !tags.has("hoppity_disallowed")) return false;
        for (var e : tags.getAsJsonArray("hoppity_disallowed")) if (e.getAsString().equals(HoppityEnv.world.island())) return true;
        return false;
    }

    // for a factory slot, what the settings add
    static void tooltipLines(int slot, List<String> plain, List<String> lore, List<Component> out) {
        if (cf("compactOnClick", true) && plain.contains("Click to uncover the meaning of life!") && (cf("compactOnClickAlways", false) || HoppityEnv.passedSince(lastChocolateClick) < 1000)) {
            Component name = out.get(0), line = out.size() > 6 ? out.get(6) : null;
            out.clear();
            out.add(name);
            if (line != null) out.add(line);
            return;
        }
        if (cf("showStrayTime", true) && slot <= 26 && slot != HoppityData.infoIndex) {
            for (String l : plain) {
                Matcher m = Patterns.get("SH.misc.chocolatefactory.rabbit.stray.new").matcher(l);
                if (!m.matches()) continue;
                long time = ChocolateFactory.timeUntilNeed(HoppityEnv.parseLong(m.group("amount")) + 1);
                if (time != Long.MAX_VALUE) out.add(Component.literal("§a+§b" + HoppityEnv.format(time, 2) + " §aof production"));
                break;
            }
        }
        if (cf("extraTooltipStats", true) && (HoppityData.otherUpgradeSlots().contains(slot) || HoppityData.rabbitSlots().containsKey(slot))) {
            for (String l : upgradeLines(slot)) out.add(Component.literal(l));
        }
    }

    // CFTooltip
    static List<String> upgradeLines(int slot) {
        List<String> out = new ArrayList<>();
        ChocolateFactory.Upgrade u = null;
        for (ChocolateFactory.Upgrade x : ChocolateFactory.factoryUpgrades) if (x.slotIndex == slot) u = x;
        if (u == null) return out;
        if (slot == HoppityData.timeTowerIndex && u.isMaxed) {
            out.add("§8§m-----------------");
            out.add("§7One charge will give: §6" + HoppityEnv.sep(chocPerTimeTower()));
        }
        if (u.isMaxed) return out;
        out.add("§8§m-----------------");
        out.add("§7Time until upgrade: §e" + u.formattedTimeUntilGoal());
        if (u.effectiveCost == null) return out;
        out.add("§7Extra: §6" + (u.extraPerSecond == null ? "N/A" : String.format(Locale.ROOT, "%.2f", u.extraPerSecond)) + " §7choc/s");
        out.add("§7Effective Cost: §6" + String.format(Locale.ROOT, "%,.2f", u.effectiveCost));
        if (slot == HoppityData.timeTowerIndex) out.add("§7One charge will give: §6" + HoppityEnv.sep(chocPerTimeTower()));
        return out;
    }

    private static long chocPerTimeTower() {
        return (long) (HoppityEnv.profile().rawChocPerSecond * ChocolateFactory.timeTowerMultiplier() * 3600);
    }

    // --- clicks ---

    // every slot click in a chest, true = swallow it
    public static boolean slotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        if (!HoppityEnv.world.inSkyBlock() || !(screen instanceof ContainerScreen)) return false;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        ContainerInput input = io.muzy.sbc.mc.Interactions.clickInput();
        boolean shift = shiftDown();
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
        String name = stack.isEmpty() ? "" : LegacyText.name(stack);
        List<String> lore = stack.isEmpty() ? List.of() : LegacyText.lore(stack);
        if (!stack.isEmpty()) HoppityApi.onSlotClick(title, name, lore);
        if (on(SHORTCUT) && title.equals("SkyBlock Menu") && slotId == HoppityData.cfShortcutIndex && !disallowedIsland()) {
            if (HoppityEnv.passedSince(lastShortcut) > 2000) {
                lastShortcut = HoppityEnv.now();
                HoppityEnv.world.sendCommand("chocolatefactory");
            }
            return true;
        }
        if (on(BLOCK) && Patterns.get("SH.inventory.chocolatefactory.openitem").matcher(name).matches() && !title.equals("Enchanted Clock") && blocked()) return true;
        if (!ChocolateFactory.inChocolateFactory) {
            if (reminderMenu(title) && !stack.isEmpty()) reminderClick(name, lore, button);
            return false;
        }
        if (slotId == HoppityData.infoIndex) lastChocolateClick = HoppityEnv.now();
        if (CFStray.refusesSlot(slotId, shift)) return true;
        if (on(KEYS) && input == ContainerInput.SWAP) return true;
        if (!stack.isEmpty()) reminderClick(name, lore, button);
        return middleClick(slotId, button, input, shift);
    }

    // CFInventory.onSlotClick: your click reaches hypixel as a middle click, its faster
    private static boolean middleClick(int slotId, int button, ContainerInput input, boolean shift) {
        if (!cf("useMiddleClick", true) || slotId < 0 || slotId >= 54 || input == ContainerInput.CLONE || input == ContainerInput.SWAP) return false;
        if (HoppityData.noPickblockSlots().contains(slotId) && (slotId != HoppityData.timeTowerIndex || button == 1)) return false;
        if (shift && HoppityData.rabbitSlots().containsKey(slotId)) return false;
        click(slotId);
        return true;
    }

    private static void click(int slot) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 2, ContainerInput.CLONE, mc.player);
    }

    private static boolean shiftDown() {
        try {
            var window = Minecraft.getInstance().getWindow();
            return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        } catch (Throwable t) {
            return false;
        }
    }

    // key pressed in a chest, false = swallow it
    public static boolean key(Screen s, int key) {
        if (!HoppityEnv.world.inSkyBlock() || !ChocolateFactory.inChocolateFactory || !(s instanceof ContainerScreen)) return true;
        var mc = Minecraft.getInstance();
        boolean closes = key == InputConstants.KEY_ESCAPE || mc.options.keyInventory.matches(new net.minecraft.client.input.KeyEvent(key, 0, 0));
        if (closes && CFStray.refusesClosing(shiftDown())) return false;
        if (!on(KEYS)) return true;
        String keys = Hoppity.text(KEYS, "keys", "1234567").toUpperCase(Locale.ROOT);
        int index = -1;
        for (int i = 0; i < keys.length() && index < 0; i++) if (io.muzy.sbc.mc.KeyHeld.keyOf(keys.charAt(i)) == key) index = i;
        if (index < 0 || index > 6) return true;
        if (HoppityEnv.passedSince(lastKey) >= 200) {
            lastKey = HoppityEnv.now();
            click(28 + index);
        }
        return false;
    }

    // --- CFUpgradeWarning ---

    // once a sec in skyblock
    public static void onSecondPassed() {
        var p = HoppityEnv.profile();
        if (p.bestUpgradeAvailableAt <= HoppityEnv.now() && !HoppityEnv.isFarPast(p.bestUpgradeAvailableAt)) upgradeWarning();
        reminderSecond();
        shopReminder();
        eggPath();
    }

    private static void upgradeWarning() {
        if (!on(UPGRADE) || !Hoppity.cfEnabled() || HoppityEnv.world.isBusy() || reminderActive()) return;
        double minutes = Hoppity.n(UPGRADE, "minutes", 5);
        if (HoppityEnv.passedSince(lastUpgradeWarning) < minutes * 60_000) return;
        lastUpgradeWarning = HoppityEnv.now();
        if (Hoppity.b(UPGRADE, "sound", false)) HoppityEnv.world.playSound("entity.experience_orb.pickup", 1f);
        if (ChocolateFactory.inChocolateFactory) return;
        HoppityEnv.world.clickableChat("You have a Chocolate factory upgrade available to purchase!", "/cf", "§eClick to open the Chocolate Factory!");
    }

    // checkUpgradeChange: a new best upgrade waits its turn
    static void checkUpgradeChange(int slot, int level) {
        if (slot != lastUpgradeSlot || level != lastUpgradeLevel) {
            lastUpgradeWarning = HoppityEnv.now();
            lastUpgradeSlot = slot;
            lastUpgradeLevel = level;
        }
    }

    public static void onProfileJoin() {
        lastUpgradeWarning = HoppityEnv.FAR_PAST;
    }

    // --- CFCustomReminder ---

    static boolean reminderActive() {
        return on(REMINDER) && HoppityEnv.profile().reminderGoal > 0;
    }

    private static boolean reminderMenu(String title) {
        return title.equals("Chocolate Shop") || Patterns.get("SH.misc.chocolatefactory.inventory.name").matcher(title).matches();
    }

    private static boolean inChocolateMenu() {
        return ChocolateFactory.inChocolateFactory || ChocolateFactory.chocolateFactoryPaused || CFPanels.inShop();
    }

    // onSlotClick: what you cant pay yet becomes the goal, click again clears it
    static void reminderClick(String name, List<String> lore, int button) {
        if (!on(REMINDER)) return;
        Matcher tier = ChocolateFactory.UPGRADE_TIER.matcher(GameState.strip(name));
        if (tier.matches() && "Time Tower".equals(tier.group("upgrade")) && button == 1) return;
        Long cost = ChocolateFactory.getChocolateBuyCost(lore);
        String target;
        if (cost != null) {
            String next = ChocolateFactory.getNextLevelName(new ChocolateFactory.Item(name, lore));
            target = next != null ? next : name;
        } else {
            Long missing = null;
            String label = null;
            for (String l : lore) {
                Matcher m = Patterns.get("SH.inventory.chocolate.factory.milestone.cost").matcher(l);
                if (!m.matches()) continue;
                long amount = GameState.parseShort(m.group("amount"));
                missing = amount - ChocolateFactory.Amount.ALL_TIME.chocolate() + ChocolateFactory.Amount.CURRENT.chocolate();
                label = "§6" + EnchantParser.shortFormat(amount) + " Chocolate Milestone";
                break;
            }
            if (missing == null) return;
            cost = missing;
            target = label;
        }
        long duration = ChocolateFactory.Amount.CURRENT.timeUntilGoal(cost);
        var p = HoppityEnv.profile();
        if (duration < 0) {
            p.reminderGoal = 0;
            p.reminderName = "";
            return;
        }
        if (target.equals(p.reminderName)) {
            p.reminderGoal = 0;
            p.reminderName = "";
            return;
        }
        p.reminderGoal = cost;
        p.reminderName = target;
    }

    private static void reminderSecond() {
        if (!reminderActive() || HoppityEnv.world.isBusy() || inChocolateMenu()) return;
        var p = HoppityEnv.profile();
        if (ChocolateFactory.Amount.CURRENT.timeUntilGoal(p.reminderGoal) >= 0) return;
        if (HoppityEnv.passedSince(lastReminderWarning) < Hoppity.n(UPGRADE, "minutes", 5) * 60_000) return;
        lastReminderWarning = HoppityEnv.now();
        if (Hoppity.b(UPGRADE, "sound", false)) HoppityEnv.world.playSound("entity.experience_orb.pickup", 1f);
        HoppityEnv.world.clickableChat("You can now purchase §f" + p.reminderName + " §ein Chocolate factory!", "/cf", "§eClick to open the Chocolate Factory!");
    }

    // onChat: hypixel saying you cant pay for your goal. true = hide the line
    static boolean hidesNoChocolate(String message) {
        if (!on(REMINDER) || !ChocolateFactory.inChocolateFactory || !Hoppity.b(REMINDER, "hide_chat", true)) return false;
        for (int i = 0; i < 3; i++) if (Patterns.get("SH.inventory.chocolate.factory.chat.hide." + i).matcher(message).matches()) return true;
        return false;
    }

    private static void reminderSheet(EventSheet s, String name, long left) {
        if (left < 0) {
            s.line("Goal reached", GameState.strip(name), Modern.GOOD);
            return;
        }
        s.line(GameState.strip(name), HoppityEnv.format(left, 2), Modern.AQUA);
    }

    private static boolean reminderShows() {
        if (!reminderActive() || HoppityEnv.world.isBusy()) return false;
        var mc = Minecraft.getInstance();
        boolean menu = mc != null && mc.gui != null && mc.gui.screen() instanceof ContainerScreen;
        return menu ? inChocolateMenu() : Hoppity.b(REMINDER, "always", false);
    }

    static HudElement reminderElement() {
        return new EventSheet.Card(REMINDER, "Factory goal", "Events", Zone.TOP_RIGHT, 46, REMINDER) {
            @Override public boolean visible() { return reminderShows(); }
            @Override protected void live(EventSheet s) { reminderSheet(s, HoppityEnv.profile().reminderName, ChocolateFactory.Amount.CURRENT.timeUntilGoal(HoppityEnv.profile().reminderGoal)); }
            @Override protected void sample(EventSheet s) { reminderSheet(s, "Rabbit Bro 112", 2 * 3_600_000 + 14 * 60_000); }
            @Override protected List<Row> plainRows() { return List.of(new Row("§f" + HoppityEnv.profile().reminderName + " §ein §b" + HoppityEnv.format(ChocolateFactory.Amount.CURRENT.timeUntilGoal(HoppityEnv.profile().reminderGoal), 2))); }
            @Override protected List<Row> plainDemo() { return List.of(new Row("§fRabbit Bro 112 §ein §b2h 14m")); }
        };
    }

    // --- CFBlockOpen ---

    // tryBlock: true when the factory shouldnt open rn, and we told you why
    private static boolean blocked() {
        if (Hoppity.b(BLOCK, "rabbit", true) && !HoppityEnv.world.hasMythicRabbitPet()) {
            HoppityEnv.world.clickableChat("§cBlocked opening the Chocolate Factory without a §dMythic Rabbit Pet §cequipped!", "/pet", "§eClick to open your pets!");
            HoppityEnv.world.title("§cBlocked opening the Chocolate Factory");
            return true;
        }
        if (Hoppity.b(BLOCK, "cookie", false) && "Not active".equals(io.muzy.sbc.core.CookieBuff.status())) {
            HoppityEnv.world.clickableChat("§cBlocked opening the Chocolate Factory without a §dBooster Cookie §cactive!", "/warp hub", "§eClick to warp to the Hub!");
            HoppityEnv.world.title("§cBlocked opening the Chocolate Factory");
            return true;
        }
        if (Hoppity.b(BLOCK, "mixin", false)) {
            if (io.muzy.sbc.misc.NonGodEffects.hotChocolateLeft() <= 0) {
                HoppityEnv.world.clickableChat("§cBlocked opening the Chocolate Factory without a §dHot Chocolate Mixin §cactive! §7You may need to open §c/effects §7to refresh its time.", "/ahs hot chocolate mixin", "§eClick to look for one in the Auction House!");
                HoppityEnv.world.title("§cBlocked opening the Chocolate Factory");
                return true;
            }
            if (io.muzy.sbc.misc.NonGodEffects.godPotLeft() <= 0) {
                HoppityEnv.world.clickableChat("§cBlocked opening the Chocolate Factory without a §dGod Potion §cactive! §7You may need to open §c/effects §7to refresh its time.", "/ahs god potion", "§eClick to look for one in the Auction House!");
                HoppityEnv.world.title("§cBlocked opening the Chocolate Factory");
                return true;
            }
        }
        return false;
    }

    // onCommandSend: /cf and its aliases. false = keep it from the server
    public static boolean onCommand(String command) {
        HoppityApi.onCommandSend("/" + command);
        if (!on(BLOCK) || !HoppityEnv.world.inSkyBlock()) return true;
        if (!Patterns.get("SH.inventory.chocolatefactory.opencommand").matcher("/" + command).matches()) return true;
        if (HoppityEnv.passedSince(lastBlockedCommand) < 5000 || io.muzy.sbc.events.Events.bingo()) return true;
        if (!blocked()) return true;
        lastBlockedCommand = HoppityEnv.now();
        return false;
    }

    // --- HoppityNpc ---

    // onInventoryFullyOpened: you saw the shop this year
    static void shopOpened() {
        HoppityEnv.profile().hoppityShopYearOpened = HoppityTime.now().year();
    }

    static void shopMarks(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) if (v.item(slot).loreHas("§eClick to trade!") && Hoppity.b(SHOP, "highlight", true)) v.back(slot, MenuView.GREEN);
    }

    private static void shopReminder() {
        if (!on(SHOP) || !Hoppity.b(SHOP, "reminder", true) || HoppityEnv.world.isBusy() || HoppityEnv.world.isStrandedProfile()) return;
        if (HoppityEnv.profile().hoppityShopYearOpened == HoppityTime.now().year() || !HoppityTime.isHoppityEvent()) return;
        if (HoppityEnv.passedSince(lastShopReminder) <= 120_000) return;
        lastShopReminder = HoppityEnv.now();
        HoppityEnv.world.clickableChat("New rabbits are available at §aHoppity's Shop§e!", "/warp hub", "§eClick to warp to the Hub!");
    }

    // --- WarpMenuUniques ---

    // onToolTip in hypixel's fast travel: how many egg spots of that island you got
    public static void warpTooltip(ItemStack stack, List<Component> lines) {
        var mc = Minecraft.getInstance();
        if (!HoppityTime.isHoppityEvent() || mc.gui == null || !(mc.gui.screen() instanceof ContainerScreen s) || !GameState.strip(s.getTitle().getString()).equals("Fast Travel")) return;
        String line = warpLine(LegacyText.name(stack));
        if (line != null && lines.size() >= 2) lines.add(2, Component.literal(line));
    }

    // line for an item's legacy name, null if it says nothing
    static String warpLine(String legacyName) {
        Matcher m = Patterns.get("SH.inventory.warpmenu.island.name").matcher(legacyName);
        if (!m.matches()) return null;
        String island = HoppityData.islandByName(m.group("name"));
        if (island == null) return null;
        int collected = HoppityEggLocations.getEggsIn(island).size();
        if (collected >= 15 && Hoppity.b(WARP, "hide_maxed", true)) return null;
        return "§7Collected Hoppity Eggs: " + (collected == 15 ? "§a" : "") + collected + "/15";
    }

    // --- HoppityMuteEggSounds ---

    // every tick: in a meal egg's menu and for 2s after
    static void eggMenu(String plainTitle) {
        boolean now = plainTitle != null && Patterns.get("SH.misc.chocolatefactory.inventory.mealegg.name").matcher(plainTitle).matches();
        if (inEggMenu && !now) lastEggMenu = HoppityEnv.now();
        inEggMenu = now;
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!on(SOUNDS) || !s.name().equals("block.note_block.bit") || s.volume() != 1f) return false;
        if (!inEggMenu && HoppityEnv.passedSince(lastEggMenu) > 2000) return false;
        if (HoppityEnv.world.playerPos().distanceTo(new net.minecraft.world.phys.Vec3(s.x(), s.y(), s.z())) >= 2) return false;
        if (Hoppity.text(SOUNDS, "mode", "Mute").equals("Old sound")) HoppityEnv.world.playSound("entity.generic.eat", s.pitch());
        return true;
    }

    // --- HoppityEggLocator's path finder ---

    private static net.minecraft.world.phys.Vec3 pathTo;

    // once a sec: a shared egg, or the one guess there is
    static void eggPath() {
        if (!Hoppity.b(Hoppity.EGGS, "showPathFinder", false) || !HoppityEggLocator.isEnabled()) return;
        var guesses = HoppityEggLocator.possibleEggLocations;
        net.minecraft.world.phys.Vec3 egg = HoppityEggLocator.sharedEggLocation != null ? HoppityEggLocator.sharedEggLocation : guesses.size() == 1 ? guesses.get(0) : null;
        if (egg == null || egg.equals(pathTo)) return;
        pathTo = egg;
        io.muzy.sbc.nav.Navigator.navigate(new io.muzy.sbc.nav.Navigator.Target(HoppityEnv.world.island(), "Egg", egg));
    }

    // --- HoppityEggDisplayManager: players near an egg ---

    private static boolean nearEgg(net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof net.minecraft.client.player.RemotePlayer) || e.getUUID().version() == 2) return false;
        net.minecraft.world.phys.Vec3 shared = HoppityEggLocator.sharedEggLocation, at = e.position();
        if (shared != null) return at.distanceTo(shared) < 4;
        for (net.minecraft.world.phys.Vec3 egg : HoppityEggLocator.possibleEggLocations) if (at.distanceTo(egg) < 4) return true;
        return false;
    }

    static void register() {
        Features.add(new Feature(SHOP, "Events", "Hoppity's shop", "In Hoppity's shop, what you have not bought yet is green; during the hunt, a reminder to visit it once a year.", "SkyHanni: Highlight Hoppity Shop, Hoppity Shop Reminder")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(Option.bool("hoppity_shop.highlight", "Highlight what you can buy", "", true))
                .option(Option.bool("hoppity_shop.reminder", "Yearly reminder", "", true)));
        Features.add(new Feature(UPGRADE, "Events", "Chocolate upgrade reminder", "A chat line with a link to /cf when you can pay for the best upgrade of the Chocolate Factory.", "SkyHanni: Upgrade Warning")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(Option.integer("cf_upgrade_warning.minutes", "Every (minutes)", "Also for the goal below.", 5, 1, 60))
                .option(Option.bool("cf_upgrade_warning.sound", "With a sound", "", false)));
        Features.add(new Feature(REMINDER, "Events", "Chocolate factory goal", "Click something in the factory you cannot pay for yet: a card counts down until you can, and chat says when. Click it again to clear it.", "SkyHanni: Chocolate Factory Custom Reminder")
                .when(() -> HoppityEnv.world.inSkyBlock()).defaultOff()
                .option(Option.bool("cf_reminder.always", "Also outside the factory", "", false))
                .option(Option.bool("cf_reminder.hide_chat", "Hide Hypixel's refusal", "That you do not have enough Chocolate.", true)));
        Features.add(new Feature(KEYS, "Events", "Chocolate factory keys", "In the Chocolate Factory, the number row upgrades the seven rabbits, one click to a press.", "SkyHanni: Chocolate Factory Keybinds")
                .when(() -> HoppityEnv.world.inSkyBlock()).defaultOff()
                .option(Option.text("cf_keys.keys", "Keys", "One letter or digit for each of the seven rabbits, in order.", "1234567")));
        Features.add(new Feature(BLOCK, "Events", "Block /cf without buffs", "Keeps the Chocolate Factory from opening while what you want active is not, and says why.", "SkyHanni: Mythic Rabbit, Booster Cookie")
                .when(() -> HoppityEnv.world.inSkyBlock()).defaultOff()
                .option(Option.bool("cf_block_open.rabbit", "Mythic Rabbit pet", "", true))
                .option(Option.bool("cf_block_open.cookie", "Booster Cookie", "", false))
                .option(Option.bool("cf_block_open.mixin", "Hot Chocolate Mixin", "And the God Potion it runs on.", false)));
        Features.add(new Feature(TIP_MOVE, "Events", "Move Chocolate Factory tooltips", "In the Chocolate Factory, the tooltip of what you hover stays at the side of the screen and does not cover the menu.", "SkyHanni: Tooltip Move")
                .when(() -> HoppityEnv.world.inSkyBlock()).defaultOff()
                .option(Option.choice("cf_tooltip_move.side", "Side of the screen", "", "Left", "Left", "Right")));
        Features.add(new Feature(WARP, "Events", "Egg spots in Fast Travel", "During the hunt, on Hypixel's Fast Travel menu: how many egg spots of each island you collected.", "SkyHanni: Show uniques in Warp Menu")
                .when(() -> HoppityEnv.world.inSkyBlock()).tooltip(CFHelpers::warpTooltip)
                .option(Option.bool("hoppity_warp_uniques.hide_maxed", "Not on a finished island", "", true)));
        Features.add(new Feature(SOUNDS, "Events", "Meal egg sounds", "The note block sounds when you open a Chocolate meal egg: gone, or the old eating sound.", "SkyHanni: Modify Egg Sounds")
                .when(() -> HoppityEnv.world.inSkyBlock()).defaultOff()
                .option(Option.choice("hoppity_egg_sounds.mode", "Instead", "", "Mute", "Mute", "Old sound")));
        Features.add(new Feature(SHORTCUT, "Events", "Chocolate Factory in the SkyBlock Menu", "A slot of the SkyBlock Menu opens the Chocolate Factory when you click it.", "SkyHanni: Hoppity Menu Shortcut")
                .when(() -> HoppityEnv.world.inSkyBlock()));
        for (Feature f : CFStray.features()) Features.add(f);
        Features.add(HoppityCompactChat.feature());
        for (Feature f : HoppitySummary.features()) Features.add(f);
        MenuMarks.add(SHOP, Patterns.get("SH.misc.chocolatefactory.hoppity.inventory"), v -> {
            shopOpened();
            shopMarks(v);
        });
        MenuMarks.add(SHORTCUT, "SkyBlock Menu", CFHelpers::shortcutMarks);
        MenuMarks.add(Hoppity.CF, t -> t.contains("Hoppity's Collection"), CFHelpers::collectionMarks);
        SoundHooks.listen(CFHelpers::onSound);
        io.muzy.sbc.combat.CombatMarks.fading(() -> HoppityEggLocator.isEnabled() && Hoppity.n(Hoppity.EGGS, "playerTransparency", 40) < 100, CFHelpers::nearEgg, () -> (int) Hoppity.n(Hoppity.EGGS, "playerTransparency", 40));
        CFPanels.register();
    }
}
