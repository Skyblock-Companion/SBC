// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.events.EventSheet;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// wires skyhanni's hoppity hunt + chocolate factory into the game: features/options, chat + particle hooks,
// egglocator click, menu reading (factory, hoppity's collection, meal egg menus), hud (unclaimed eggs, event
// counts, factory stats, collection summary), slot highlights, /sbc shareegg
public final class Hoppity {
    public static final String PANEL = "hoppity_panel", EGGS = "hoppity_eggs", CALL = "hoppity_call", CF = "hoppity_cf";

    private static Object lastLevel;
    private static String lastIsland = "", lastProfile = "";
    private static int lastMenuId = -1;
    private static String lastMenuTitle = "";
    private static int lastMenuHash;
    private static long lastSecond;
    private static final List<long[]> DELAYS = new ArrayList<>();
    private static final List<Runnable> DELAY_RUNS = new ArrayList<>();
    private static Integer fishyRabbitSlot;
    private Hoppity() {}

    // --- options ---

    static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static double n(String feature, String key, double def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.number();
    }

    static String text(String feature, String key, String def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.text();
    }

    static int color(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.color();
    }

    private static boolean on(String id) {
        Feature f = Features.get(id);
        return f == null || f.enabled();
    }

    static boolean eggsEnabled() {
        return on(EGGS);
    }

    static boolean callEnabled() {
        return on(CALL);
    }

    static boolean cfEnabled() {
        return on(CF);
    }

    // DelayedRun.runDelayed
    static synchronized void later(long ms, Runnable r) {
        DELAYS.add(new long[]{HoppityEnv.now() + ms});
        DELAY_RUNS.add(r);
    }

    static void runDelayed() {
        List<Runnable> due = new ArrayList<>();
        synchronized (Hoppity.class) {
            long now = HoppityEnv.now();
            for (int i = DELAYS.size() - 1; i >= 0; i--) {
                if (DELAYS.get(i)[0] <= now) {
                    due.add(DELAY_RUNS.remove(i));
                    DELAYS.remove(i);
                }
            }
        }
        for (Runnable r : due) r.run();
    }

    // --- the game ---

    private static final class McWorld implements HoppityEnv.World {
        private Minecraft mc() {
            return Minecraft.getInstance();
        }

        @Override public boolean inSkyBlock() { return GameState.inSkyBlock || SbcMod.DEV; }
        @Override public String island() { return GameState.island; }

        @Override public Vec3 playerPos() {
            var p = mc().player;
            return p == null ? Vec3.ZERO : p.position();
        }

        @Override public boolean locatorInHand() {
            var p = mc().player;
            return p != null && HoppityEggLocator.LOCATOR_ITEM.equals(Gain.sbId(p.getMainHandItem()));
        }

        @Override public boolean locatorInHotbar() {
            var p = mc().player;
            if (p == null) return false;
            for (int i = 0; i < 9; i++) if (HoppityEggLocator.LOCATOR_ITEM.equals(Gain.sbId(p.getInventory().getItem(i)))) return true;
            return false;
        }

        @Override public boolean hasMythicRabbitPet() {
            if (!Vitals.petName.equalsIgnoreCase("Rabbit")) return false;
            Profile.Data d = Profile.current;
            if (d != null) for (Profile.Pet pp : d.pets) if (pp.active() && "RABBIT".equalsIgnoreCase(pp.type())) return "MYTHIC".equalsIgnoreCase(pp.tier());
            return true;
        }

        @Override public boolean isBusy() {
            return "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island);
        }

        @Override public boolean isStrandedProfile() {
            return GameState.stranded;
        }

        @Override public String playerName() { return mc().getUser().getName(); }

        @Override public void sendCommand(String command) {
            var c = mc().getConnection();
            if (c != null) c.sendCommand(command);
        }

        @Override public void chat(String message) {
            var p = mc().player;
            if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §r" + message));
        }

        @Override public void clickableChat(String message, String command, String hover) {
            var p = mc().player;
            if (p == null) return;
            Style st = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
            p.sendSystemMessage(Component.literal("§b[SBC] §r" + message).withStyle(st));
        }

        @Override public void richChat(String message, String hover, String command) {
            var p = mc().player;
            if (p == null) return;
            Style st = Style.EMPTY;
            if (hover != null) st = st.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
            if (command != null) st = st.withClickEvent(new ClickEvent.RunCommand(command));
            p.sendSystemMessage(Component.literal(message).withStyle(st));
        }

        @Override public void title(String text) {
            var mc = mc();
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(Component.literal(text));
            mc.gui.hud.setSubtitle(Component.empty());
        }

        @Override public void playSound(String id, float pitch) {
            var p = mc().player;
            if (p == null) return;
            var ev = switch (id) {
                case "block.note_block.pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
                case "entity.experience_orb.pickup" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                default -> {
                    net.minecraft.resources.Identifier named = net.minecraft.resources.Identifier.tryParse(id);
                    net.minecraft.sounds.SoundEvent found = named == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(named);
                    yield found != null ? found : SoundEvents.NOTE_BLOCK_PLING.value();
                }
            };
            p.playSound(ev, 1f, pitch);
        }

        @Override public Store.HoppityProfile profile() {
            String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
            return Store.I.hoppity.computeIfAbsent(key, k -> new Store.HoppityProfile());
        }
    }

    // --- hooks ---

    private static ChatBus.Result onChat(ChatBus.Line line) {
        try {
            if (!HoppityEnv.world.inSkyBlock()) return ChatBus.PASS;
            // with colour codes, skyhanni's formatted chat patterns have them
            String message = LegacyText.of(line.component());
            String plain = line.plain();
            boolean compacted = HoppityApi.onChat(message, plain);
            boolean hide = HoppityEggsManager.onChat(message);
            HoppityEggsShared.onChat(plain);
            HoppityCallWarning.onChat(plain);
            HoppitySummary.onChat(message);
            CFPanels.onChat(message);
            if (hide || compacted || CFHelpers.hidesNoChocolate(message)) return ChatBus.CANCEL;
            String changed = HoppityApi.changedDuplicate(message);
            if (changed != null) return new ChatBus.Replace(Component.literal(changed));
        } catch (Throwable t) {
            SbcMod.LOG.warn("Hoppity chat failed", t);
        }
        return ChatBus.PASS;
    }

    private static void onParticle(ParticleHooks.Particle p) {
        if (!p.is("minecraft:happy_villager")) return;
        boolean hook = false;
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 at = new Vec3(p.x(), p.y(), p.z());
            hook = !mc.level.getEntitiesOfClass(FishingHook.class, new AABB(at.add(-0.3, -0.3, -0.3), at.add(0.3, 0.3, 0.3))).isEmpty();
        }
        HoppityEggLocator.onParticle(p.type(), p.x(), p.y(), p.z(), p.count(), p.speed(), hook);
    }

    private static void tick(Minecraft mc) {
        try {
            if (mc.level == null || mc.player == null) return;
            if (mc.level != lastLevel) {
                lastLevel = mc.level;
                onWorldChange();
            }
            if (!GameState.island.equals(lastIsland)) {
                lastIsland = GameState.island;
                HoppityEggLocations.onIslandChange();
                CFStray.onIslandChange();
            }
            String prof = GameState.profileName == null ? "" : GameState.profileName;
            if (!prof.equals(lastProfile)) {
                lastProfile = prof;
                HoppityEggsManager.onProfileJoin();
                HoppitySummary.onProfileJoin();
                CFHelpers.onProfileJoin();
            }
            runDelayed();
            readMenu(mc);
            HoppityCallWarning.onTick();
            CFStray.onTick();
            var open = mc.gui.screen();
            CFHelpers.eggMenu(open == null ? null : GameState.strip(open.getTitle().getString()).trim());
            long now = HoppityEnv.now();
            if (now - lastSecond >= 1000) {
                lastSecond = now;
                HoppityEggsManager.onSecondPassed();
                if (HoppityEnv.world.inSkyBlock()) {
                    ChocolateFactory.timeTowerSecond();
                    HoppitySummary.onSecondPassed();
                    CFHelpers.onSecondPassed();
                }
                if (now - lastChangeLook >= CHANGE_LOOK_MS) {
                    lastChangeLook = now;
                    if (changedSinceLastLook()) Store.markDirty();
                }
            }
        } catch (Throwable t) {
            io.github.zmuzy.sbc.core.Safe.caught("Hoppity.tick", t);
        }
    }

    // 2x as often as the store saves. its read and written all over this package so only the data itself can say it changed
    private static final long CHANGE_LOOK_MS = 30_000;
    private static long lastChangeLook;
    private static int lastHash;

    // does what the store would save of hoppity differ from the last look
    static boolean changedSinceLastLook() {
        int hash = Store.savedHash(Store.I.hoppity);
        boolean changed = hash != lastHash;
        lastHash = hash;
        return changed;
    }

    private static void onWorldChange() {
        HoppityEggLocator.onWorldChange();
        HoppityEggsManager.onWorldChange();
        HoppityCallWarning.onWorldChange();
        ChocolateFactory.onWorldChange();
        fishyRabbitSlot = null;
    }

    // InventoryFullyOpenedEvent / InventoryUpdatedEvent / InventoryCloseEvent from the open chest
    private static void readMenu(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            if (lastMenuId != -1) {
                lastMenuId = -1;
                lastMenuTitle = "";
                lastMenuHash = 0;
                ChocolateFactory.onInventoryClose();
                CFStray.onInventoryClose();
                HoppityCollectionStats.onInventoryClose();
                HoppityApi.onInventoryClose();
                fishyRabbitSlot = null;
            }
            return;
        }
        String title = screen.getTitle().getString();
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int hash = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (!st.isEmpty()) hash = hash * 31 + st.getHoverName().getString().hashCode() + st.getCount();
        }
        boolean opened = menu.containerId != lastMenuId || !title.equals(lastMenuTitle);
        boolean changed = hash != lastMenuHash;
        if (!opened && !changed) return;
        if (mc.player.tickCount % 2 != 0 && !opened) return; // let the server finish filling the slots
        lastMenuId = menu.containerId;
        lastMenuTitle = title;
        lastMenuHash = hash;
        Map<Integer, ChocolateFactory.Item> items = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            items.put(i, new ChocolateFactory.Item(LegacyText.name(st), LegacyText.lore(st)));
        }
        String plain = GameState.strip(title).trim();
        HoppityApi.onInventoryUpdated(items);
        if (opened) {
            HoppityApi.onInventoryOpened(title);
            ChocolateFactory.onInventoryOpened(title, items);
            HoppityCollectionStats.onInventoryOpened(plain, items);
            fishyRabbitSlot = null;
            if (HoppityTime.isHoppityEvent() && b(EGGS, "preventMissingRabbitTheFish", true)
                    && io.github.zmuzy.sbc.core.Patterns.get("SH.misc.chocolatefactory.inventory.mealegg.name").matcher(plain).matches()) {
                for (Map.Entry<Integer, ChocolateFactory.Item> e : items.entrySet()) {
                    if (e.getKey() == 22) continue;
                    if (io.github.zmuzy.sbc.core.Patterns.get("SH.misc.chocolatefactory.item.rabbitthefish").matcher(e.getValue().name()).matches()) {
                        fishyRabbitSlot = e.getKey();
                        HoppityEnv.world.title("§cRabbit the Fish is here!");
                        HoppityEnv.world.chat("§cRabbit the Fish §eis in this egg menu - click him before opening the Chocolate Factory!");
                    }
                }
            }
        } else if (ChocolateFactory.inChocolateFactory) ChocolateFactory.updateInventoryItems(items);
        if (ChocolateFactory.inChocolateFactory) {
            List<CFStray.Stray> strays = new ArrayList<>();
            for (Map.Entry<Integer, ChocolateFactory.Item> e : items.entrySet()) {
                if (e.getKey() > 26) continue;
                ItemStack st = menu.slots.get(e.getKey()).getItem();
                if (st.is(net.minecraft.world.item.Items.PLAYER_HEAD)) strays.add(new CFStray.Stray(e.getKey(), e.getValue().name(), e.getValue().lore(), io.github.zmuzy.sbc.dungeon.DungeonMarks.headTexture(st)));
            }
            CFStray.onInventoryUpdated(strays);
        }
    }

    // slot overlays in the open menu (SlotOverlayMixin): collection highlights, best upgrade, rabbit the fish
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof ChestMenu)) return;
        if (slot.container == mc.player.getInventory()) return;
        int idx = slot.getContainerSlot();
        if (HoppityCollectionStats.inInventory) {
            Integer c = HoppityCollectionStats.highlightFor(LegacyText.name(st));
            if (c != null) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (0x80 << 24) | (c & 0xFFFFFF));
            return;
        }
        if (ChocolateFactory.inChocolateFactory && b(CF, "highlightUpgrades", true)) {
            if (idx == ChocolateFactory.bestAffordableSlot) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xC855FF55);
            else if (idx == ChocolateFactory.bestPossibleSlot) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFAA00);
            else if (idx == HoppityData.barnIndex && ChocolateFactory.isBarnFull()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFFFF5555);
            else if (idx == HoppityData.timeTowerIndex && ChocolateFactory.timeTowerActive()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xC8FF55FF);
            else if (idx == HoppityData.timeTowerIndex && ChocolateFactory.timeTowerFull()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFFFF5555);
            else if (idx == HoppityData.milestoneIndex && unclaimedRewards(st)) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFFFF5555);
            for (ChocolateFactory.Upgrade u : ChocolateFactory.factoryUpgrades) {
                if (u.slotIndex != idx) continue;
                if (idx != ChocolateFactory.bestAffordableSlot && idx != ChocolateFactory.bestPossibleSlot && u.canAfford()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x4B55FF55);
                String tip = u.stackTip();
                if (b(CF, "showStackSizes", true) && !tip.isEmpty()) g.text(mc.font, tip, slot.x + 17 - mc.font.width(GameState.strip(tip)), slot.y + 9, 0xFFFFFFFF, true);
            }
            if (idx == ChocolateFactory.bestPossibleSlot) g.text(mc.font, "§6✦", slot.x + 11, slot.y, 0xFFFFFFFF, true);
        }
        if (ChocolateFactory.inChocolateFactory && CFStray.highlighted(idx)) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, CFStray.highlightColor());
        if (fishyRabbitSlot != null && fishyRabbitSlot == idx) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FF5555);
    }

    // screen flash for hoppity's call and strays, over the world and over the factory
    public static void flash(GuiGraphicsExtractor g, int w, int h) {
        long now = HoppityEnv.now();
        int color = HoppityCallWarning.flashColor(now);
        if (color == 0) color = CFStray.flashColor(now);
        if (color != 0) g.fill(0, 0, w, h, color);
    }

    // CFInventory.unclaimedRewardsPattern
    private static boolean unclaimedRewards(ItemStack st) {
        for (String l : LegacyText.lore(st)) if (io.github.zmuzy.sbc.core.Patterns.get("SH.misc.chocolatefactory.unclaimedrewards").matcher(l).matches()) return true;
        return false;
    }

    // --- hud ---

    private static List<HudElement.Row> textRows(List<String> lines, int from) {
        List<HudElement.Row> out = new ArrayList<>();
        for (int i = from; i < lines.size(); i++) out.add(new HudElement.Row(lines.get(i)));
        return out;
    }

    public static HudElement UNCLAIMED, PANEL_EL, CF_STATS, COLLECTION;

    private static void elements() {
        UNCLAIMED = new EventSheet.Card("hoppity_unclaimed", "Unclaimed Eggs", "Events", Zone.TOP_LEFT, 46, PANEL) {
            @Override public boolean visible() { return !HoppityEggsManager.displayLines().isEmpty(); }
            @Override public String heading() { return compact() ? "Unclaimed" : "Unclaimed Eggs"; }
            @Override protected void live(EventSheet s) { HoppityCards.unclaimedLive(s); }
            @Override protected void sample(EventSheet s) { HoppityCards.unclaimedSample(s); }
            @Override protected List<Row> plainRows() { return textRows(HoppityEggsManager.displayLines(), 1); }
            @Override protected List<Row> plainDemo() { return List.of(new Row("§7 - §6Breakfast:§6 2h 10m"), new Row("§7 - §7§mLunch:§9 9h 3m"), new Row("§7Locations: §e9§7/§a15")); }
        };
        PANEL_EL = new EventSheet.Card("hoppity_panel", "Hoppity's Hunt", "Events", Zone.TOP_LEFT, 47, PANEL) {
            @Override public boolean visible() {
                return HoppityEnv.world.inSkyBlock() && (!HoppityApi.foundByType.isEmpty() || !HoppityApi.rabbitsByRarity.isEmpty());
            }

            @Override protected void live(EventSheet s) {
                HoppityCards.hunt(s, HoppityApi.foundByType, HoppityApi.rabbitsByRarity, HoppityApi.newRabbits, HoppityApi.duplicateRabbits, HoppityApi.duplicateChocolate);
            }

            @Override protected void sample(EventSheet s) { HoppityCards.huntSample(s); }

            @Override protected List<Row> plainRows() {
                List<Row> out = new ArrayList<>();
                int total = 0;
                StringBuilder sb = new StringBuilder();
                for (var e : HoppityApi.foundByType.entrySet()) {
                    total += e.getValue();
                    sb.append(e.getKey().mealColor).append(e.getKey().mealName.charAt(0)).append(e.getValue()).append(' ');
                }
                if (total > 0) out.add(new Row("Eggs", total + "  " + sb.toString().trim(), Theme.current().text()));
                for (String r : HoppityApi.RARITIES) {
                    Integer n = HoppityApi.rabbitsByRarity.get(r);
                    if (n != null) out.add(new Row(r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT), String.valueOf(n), rarityColor(r)));
                }
                if (HoppityApi.newRabbits > 0) out.add(new Row("New rabbits", String.valueOf(HoppityApi.newRabbits), Theme.current().good()));
                if (HoppityApi.duplicateRabbits > 0) out.add(new Row("Duplicates", HoppityApi.duplicateRabbits + "  §6+" + HoppityEnv.sep(HoppityApi.duplicateChocolate), 0xFFAA5500));
                return out;
            }

            @Override protected List<Row> plainDemo() { return List.of(new Row("Eggs", "9  §6B3 §9L3 §aD3", 0), new Row("Rare", "2", 0xFF5555FF), new Row("Duplicates", "4  §6+1,200,000", 0xFFAA5500)); }
        };
        CF_STATS = new EventSheet.Card("hoppity_cf_stats", "Chocolate Factory", "Events", Zone.TOP_RIGHT, 48, CF) {
            @Override public boolean visible() { return ChocolateFactory.inChocolateFactory || ChocolateFactory.chocolateFactoryPaused; }
            @Override public String heading() { return compact() ? "Choc Factory" : "Chocolate Factory"; }
            @Override protected void live(EventSheet s) { HoppityCards.factory(s, HoppityCards.Factory.live()); }
            @Override protected void sample(EventSheet s) { HoppityCards.factory(s, HoppityCards.Factory.sample()); }
            @Override protected List<Row> plainRows() { return textRows(ChocolateFactory.statsLines(), 0); }
            @Override protected List<Row> plainDemo() { return List.of(new Row("§6§lChocolate Factory III"), new Row("§eCurrent Chocolate: §6330,382,389"), new Row("§eBarn: §6160/450 Rabbits")); }
        };
        COLLECTION = new EventSheet.Card("hoppity_collection", "Hoppity's Collection", "Events", Zone.TOP_RIGHT, 49, CF) {
            @Override public boolean visible() { return HoppityCollectionStats.inInventory; }
            @Override protected void live(EventSheet s) { HoppityCards.collection(s, HoppityCards.Album.live()); }
            @Override protected void sample(EventSheet s) { HoppityCards.collection(s, HoppityCards.Album.sample()); }
            @Override protected List<Row> plainRows() { return textRows(HoppityCollectionStats.summaryLines(), 0); }
            @Override protected List<Row> plainDemo() { return List.of(new Row("§eRabbits found: §a395§7/§a457"), new Row("§bResidents here: §a3§7/§a5")); }
        };
    }

    private static int rarityColor(String r) {
        return switch (r) {
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            default -> 0xFFFFFFFF;
        };
    }

    public static void register() {
        HoppityEnv.world = new McWorld();
        Features.add(new Feature(PANEL, "Events", "Hoppity's Hunt card", "Unclaimed meal eggs with their respawn timers and the collected-locations count; eggs and rabbits found this session; a warning (chat, title, sound) when all eggs are ready; the next egg time in chat.", "")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(Option.bool("warningsEnabled", "Warn when all eggs are ready", "", true))
                .option(Option.bool("warpClickEnabled", "Clickable warp in the warning", "", true))
                .option(Option.text("warpClickDestination", "Warp destination", "Empty = /warp (spawn).", ""))
                .option(Option.bool("showWhileBusy", "Show while busy", "In dungeons / Kuudra.", false))
                .option(Option.bool("warnWhileBusy", "Warn while busy", "", false))
                .option(Option.bool("showCollectedLocationCount", "Collected locations count", "", true))
                .option(Option.choice("displayOrder", "Order", "", "Soonest first", "Soonest first", "Meal order"))
                .option(Option.bool("eggLocatorTimeInChat", "Next egg time in chat", "Replaces Hypixel's 'already collected' / 'none left' lines with the time to the next egg.", true)));
        Features.add(new Feature(EGGS, "Events", "Egg waypoints", "The Egglocator's particle curve is followed to the nearest known egg spot; eggs other players share in chat; optionally every known spot of the island; duplicate spots marked; Mythic Rabbit pet reminder; Rabbit the Fish highlight.", "")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(Option.color("hoppity_eggs.color", "Waypoint colour", "", 0xFF00FF00))
                .option(Option.bool("showLine", "Line to the guess", "", true))
                .option(Option.bool("showAll", "Show all egg spots", "When no Egglocator is in the hotbar and eggs are unclaimed.", false))
                .option(Option.bool("hideDuplicates", "Hide collected spots in 'show all'", "", false))
                .option(Option.bool("shared", "Shared eggs", "Read and send the lines that tell other players where a meal egg is.", true))
                .option(Option.bool("highlightDuplicates", "Mark duplicate spots", "", true))
                .option(Option.bool("showNearbyDuplicates", "Show nearby duplicates", "Collected spots within 10 blocks.", true))
                .option(Option.bool("petWarning", "Mythic Rabbit pet reminder", "Only of use once you own one.", false))
                .option(Option.bool("preventMissingRabbitTheFish", "Rabbit the Fish warning", "", true))
                .option(Option.integer("playerTransparency", "Players near an egg (%)", "How much you see of players standing on a guessed or shared egg; 100 leaves them as they are.", 40, 0, 100))
                .option(Option.bool("showPathFinder", "Path to the egg", "Walks you to a guessed or shared egg, along the island's paths.", false))
                .world(HoppityEggLocator::render));
        Features.add(new Feature(CALL, "Events", "Hoppity call warning", "A repeating sound, a title and the screen flashing while Hoppity rings your Abiphone, until you pick up.", "SkyHanni: Hoppity Call Warning")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(Option.text("hoppity_call.sound", "Sound", "The game's name of a sound.", "block.note_block.pling"))
                .option(Option.color("hoppity_call.flash_color", "Flash colour", "", 0x7F00EEFF)));
        Features.add(new Feature(CF, "Events", "Chocolate Factory", "Reads the factory menu: chocolate (live), per second, multiplier, barn, leaderboard, time tower, prestige and the best upgrade (highlighted; stack sizes = level). Barn-full and Time Tower warnings; Hoppity's Collection highlights and summary.", "")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .tooltip(CFHelpers::tooltip)
                .option(Option.bool("highlightUpgrades", "Highlight the best upgrade", "Bright green = best you can pay, gold star = best of all, faint green = what you can pay. Also the barn or Time Tower in red when full, and milestones to claim.", true))
                .option(Option.bool("extraTooltipStats", "Numbers on the upgrades' tooltips", "Time until you can pay, chocolate per second it adds, effective cost, what a Time Tower charge gives.", true))
                .option(Option.bool("compactOnClick", "Short tooltip on the chocolate", "For a second after clicking it.", true))
                .option(Option.bool("compactOnClickAlways", "Always short", "", false))
                .option(Option.bool("useMiddleClick", "Clicks as middle clicks", "Your clicks in the menu reach Hypixel as middle clicks, which go through faster.", true))
                .option(Option.bool("showStrayTime", "Production time of a stray", "On the tooltip of a caught stray.", true))
                .option(Option.bool("showDuplicateTime", "Production time of a duplicate", "In chat, after the chocolate of a duplicate rabbit.", true))
                .option(Option.bool("leaderboardChange", "Leaderboard change in chat", "When you open /cf on a new server.", false))
                .option(Option.bool("showStackSizes", "Levels on the upgrade items", "", true))
                .option(Option.integer("barnCapacityThreshold", "Barn full threshold", "Warn when this few spaces are left (0 = off).", 6, 0, 50))
                .option(Option.bool("rabbitCrushOnlyDuringHoppity", "Barn warning only during the hunt", "", false))
                .option(Option.bool("timeTowerWarning", "Time Tower full warning", "", true))
                .option(Option.bool("timeTowerReminder", "Time Tower expired reminder", "", true))
                .option(Option.bool("upgradeWarningTimeTower", "Count the Time Tower as an upgrade", "", false))
                .option(Option.bool("highlightFoundRabbits", "Collection: highlight found rabbits too", "", false))
                .option(Option.bool("rarityDyeRecolor", "Collection: missing rabbits by rarity", "The grey dye of a rabbit you lack gets the colour of its rarity.", true))
                .option(Option.bool("descriptiveMilestones", "Collection: what a milestone takes", "On a milestone rabbit's tooltip, the chocolate it asks for.", true))
                .option(Option.bool("highlight.met", "Collection: requirement met", "", true))
                .option(Option.bool("highlight.not_met", "Collection: requirement not met", "", false))
                .option(Option.bool("highlight.factory", "Collection: factory milestones", "", false))
                .option(Option.bool("highlight.shop", "Collection: shop milestones", "", false))
                .option(Option.bool("highlight.strays", "Collection: stray rabbits", "", false))
                .option(Option.bool("highlight.abi", "Collection: Abi", "", false))
                .option(Option.bool("highlight.residents", "Collection: residents of this island", "", true))
                .option(Option.bool("highlight.hotspots", "Collection: hotspots of this island", "", true)));
        elements();
        io.github.zmuzy.sbc.events.SharedOptionKeys.split(io.github.zmuzy.sbc.core.Config.I.options);
        CFHelpers.register();
        HudLayout.add(CFStray.element());
        HudLayout.add(CFHelpers.reminderElement());
        HudLayout.add(HoppitySummary.element());
        HudLayout.add(UNCLAIMED);
        HudLayout.add(PANEL_EL);
        HudLayout.add(CF_STATS);
        HudLayout.add(COLLECTION);

        ChatBus.register(ChatBus.OBSERVE + 19, "hoppity", null, Hoppity::onChat);
        ParticleHooks.listen(Hoppity::onParticle);
        Interactions.onItemClick(right -> {
            HoppityEggLocator.onItemClick(right);
            return false;
        });
        Interactions.onSlotClick(CFHelpers::slotClick);
        Safe.allowCommand("CFHelpers.onCommand", CFHelpers::onCommand);
        Safe.commands("Hoppity.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("hoppitystats").executes(c -> {
                    HoppitySummary.command(null);
                    return 1;
                }).then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("year", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    HoppitySummary.command(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "year"));
                    return 1;
                })))
                .then(literal("resethoppitystats").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §cThis forgets every Hoppity's Hunt on record. §fTo go on: /sbc resethoppitystats confirm"));
                    return 1;
                }).then(literal("confirm").executes(c -> {
                    HoppityEnv.profile().eventStats.clear();
                    Store.markDirty();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fHoppity's Hunt stats have been reset."));
                    return 1;
                })))
                .then(literal("resetsavedrabbits").executes(c -> {
                    HoppityEnv.profile().rabbitCounts.clear();
                    Store.markDirty();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fThe rabbits on record were forgotten; open Hoppity's Collection to read them again."));
                    return 1;
                }))));
        io.github.zmuzy.sbc.core.Commands.describe("hoppitystats", "The summary of a Hoppity's Hunt: /sbc hoppitystats [year|all]");
        io.github.zmuzy.sbc.core.Commands.describe("resethoppitystats", "Forget every Hoppity's Hunt on record");
        io.github.zmuzy.sbc.core.Commands.describe("resetsavedrabbits", "Forget which rabbits you found until you open Hoppity's Collection again");
        lastHash = Store.savedHash(Store.I.hoppity);
        Safe.endTick("Hoppity.tick", Hoppity::tick);
        Safe.commands("Hoppity.commands#2", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("shareegg").executes(c -> {
                    boolean ok = HoppityEggsShared.shareRemembered();
                    if (!ok) c.getSource().sendFeedback(Component.literal("§b[SBC] §cNo egg to share right now (find one first)."));
                    return 1;
                }))));
        io.github.zmuzy.sbc.core.Commands.describe("shareegg", "Share the Hoppity egg you just found with the server");
    }
}
