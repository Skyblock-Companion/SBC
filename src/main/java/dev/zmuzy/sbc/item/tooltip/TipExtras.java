// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.tooltip;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.mixin.ContainerAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// tooltip lines from skyblocker tooltip/adders (each method names its class) + skyhanni EvolvingItems
// exotic armor kind, calendar event date, evolving item age, motes, essence cost in coins, a dyes real hex
// color, whole separators
// prices + details that were already on the tooltip live in item/Tips
public final class TipExtras {
    public static final String ID = "tip_more";

    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("E MMM d yyyy HH:mm", Locale.US).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter OBTAINED_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter OLD_OBTAINED_DATE = DateTimeFormatter.ofPattern("M/d/yy h:m a", Locale.ENGLISH).withZone(ZoneId.of("UTC"));

    private TipExtras() {}

    private static boolean b(String key, boolean def) {
        return dev.zmuzy.sbc.item.market.Market.b(ID, key, def);
    }

    private static CompoundTag data(ItemStack st) {
        var cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static Slot focused() {
        var mc = Minecraft.getInstance();
        return mc.gui.screen() instanceof AbstractContainerScreen<?> cs && cs instanceof ContainerAccess a ? a.sbc$hovered() : null;
    }

    private static String screenTitle() {
        var s = Minecraft.getInstance().gui.screen();
        return s == null ? "" : s.getTitle().getString();
    }

    // from skyblocker ObtainedDateTooltip

    private static TemporalAccessor timestamp(CompoundTag customData) {
        if (customData.get("timestamp") instanceof LongTag(long value)) return Instant.ofEpochMilli(value);
        if (customData.get("timestamp") instanceof StringTag(String value)) {
            try {
                return OLD_OBTAINED_DATE.parse(value);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    // both forms a timestamp has had: epoch ms since jan 2024, "M/d/yy h:m a" before that. empty if none
    public static String obtained(CompoundTag customData) {
        TemporalAccessor t = timestamp(customData);
        return t == null ? "" : OBTAINED_DATE.format(t);
    }

    static long obtainedMillis(CompoundTag customData) {
        TemporalAccessor t = timestamp(customData);
        return t == null ? 0 : Instant.from(t).toEpochMilli();
    }

    // from skyblocker ColorTooltip

    private static final long WITHER_GLITCHED_AFTER_DATE = 1605830400000L;
    private static final Set<String> SEYMOUR_IDS = Set.of("VELVET_TOP_HAT", "CASHMERE_JACKET", "SATIN_TROUSERS", "OXFORD_SHOES");
    private static final Set<String> CRYSTAL_HEXES = Set.of("1F0030", "46085E", "54146E", "5D1C78", "63237D", "6A2C82", "7E4196", "8E51A6", "9C64B3", "A875BD", "B88BC9", "C6A3D4", "D9C1E3", "E5D1ED", "EFE1F5", "FCF3FF");
    private static final Set<String> FAIRY_HEXES = Set.of("330066", "4C0099", "660033", "660066", "6600CC", "7F00FF", "99004C", "990099", "9933FF", "B266FF",
            "CC0066", "CC00CC", "CC99FF", "E5CCFF", "FF007F", "FF00FF", "FF3399", "FF33FF", "FF66B2", "FF66FF", "FF99CC", "FF99FF", "FFCCE5", "FFCCFF");
    private static final Set<String> OG_FAIRY_HEXES = Set.of("FF99FF", "FFCCFF", "E5CCFF", "CC99FF", "CC00CC", "FF00FF", "FF33FF", "FF66FF",
            "B266FF", "9933FF", "7F00FF", "660066", "6600CC", "4C0099", "330066", "990099", "660033", "99004C", "CC0066", "FFCCE5", "FF99CC", "FF66B2");
    private static final Set<String> SPOOK = Set.of("000000", "070008", "0E000F", "150017", "1B001F", "220027", "29002E", "300036", "37003E", "3E0046",
            "45004D", "4C0055", "52005D", "590065", "60006C", "670074", "6E007C", "750084", "7C008B", "830093", "89009B", "9000A3", "9700AA", "993399", "9E00B2");
    private static final Map<String, String> MISC_GLITCHED_HEXES = Map.of("FFDC51", "SHARK_SCALE", "F7DA33", "FROZEN_BLAZE", "606060", "BAT_PERSON");
    private static final Map<String, String> WITHER_CHESTPLATE_HEXES = Map.of("E7413C", "POWER_WITHER_CHESTPLATE", "45413C", "TANK_WITHER_CHESTPLATE", "4A14B7", "SPEED_WITHER_CHESTPLATE", "1793C4", "WISE_WITHER_CHESTPLATE", "000000", "WITHER_CHESTPLATE");
    private static final Map<String, String> WITHER_LEGGINGS_HEXES = Map.of("E75C3C", "POWER_WITHER_LEGGINGS", "65605A", "TANK_WITHER_LEGGINGS", "5D2FB9", "SPEED_WITHER_LEGGINGS", "17A8C4", "WISE_WITHER_LEGGINGS", "000000", "WITHER_LEGGINGS");
    private static final Map<String, String> WITHER_BOOTS_HEXES = Map.of("E76E3C", "POWER_WITHER_BOOTS", "88837E", "TANK_WITHER_BOOTS", "8969C8", "SPEED_WITHER_BOOTS", "1CD4E4", "WISE_WITHER_BOOTS", "000000", "WITHER_BOOTS");
    private static final Set<String> GLITCHED = Set.of("FFDC51", "F7DA33", "606060", "E7413C", "45413C", "4A14B7", "1793C4", "000000", "E75C3C", "65605A", "5D2FB9", "17A8C4", "E76E3C", "88837E", "8969C8", "1CD4E4");
    private static final Set<String> RANCHERS = Set.of("CC5500", "000000", "0");
    private static final Set<String> REAPER = Set.of("1B1B1B", "FF0000");
    private static final Set<String> ADAPTIVE_CHEST = Set.of("3ABE78", "82E3D8", "BFBCB2", "D579FF", "FF4242", "FFC234");
    private static final Set<String> ADAPTIVE = Set.of("169F57", "2AB5A5", "6E00A0", "BB0000", "BFBCB2", "FFF7E6");

    // from skyblocker ColorTooltip.isException
    static boolean colorException(String id, String hex) {
        if (id.startsWith("LEATHER") || id.equals("GHOST_BOOTS") || SEYMOUR_IDS.contains(id)) return true;
        if (id.startsWith("RANCHER")) return RANCHERS.contains(hex);
        if (id.contains("ADAPTIVE_CHESTPLATE")) return ADAPTIVE_CHEST.contains(hex);
        if (id.contains("ADAPTIVE")) return ADAPTIVE.contains(hex);
        if (id.contains("REAPER")) return REAPER.contains(hex);
        if (id.contains("FAIRY")) return FAIRY_HEXES.contains(hex);
        if (id.contains("CRYSTAL")) return CRYSTAL_HEXES.contains(hex);
        if (id.contains("SPOOK")) return SPOOK.contains(hex);
        return false;
    }

    private static boolean isWitherGlitched(String id, String hex, long obtained) {
        if (hex.equals("000000") && obtained < WITHER_GLITCHED_AFTER_DATE) return false; // too old to be glitched
        if (id.contains("CHESTPLATE")) return differs(WITHER_CHESTPLATE_HEXES, hex, id);
        if (id.contains("LEGGINGS")) return differs(WITHER_LEGGINGS_HEXES, hex, id);
        if (id.contains("BOOTS")) return differs(WITHER_BOOTS_HEXES, hex, id);
        return false;
    }

    private static boolean differs(Map<String, String> hexes, String hex, String id) {
        return hexes.containsKey(hex) && hexes.containsValue(id) && !hexes.get(hex).equals(id);
    }

    // skyblocker ColorTooltip.checkDyeType: name + its color code
    static String dyeType(String id, String hex, long obtained) {
        if (CRYSTAL_HEXES.contains(hex)) return "§bCRYSTAL";
        if (FAIRY_HEXES.contains(hex)) return "§dFAIRY";
        if (OG_FAIRY_HEXES.contains(hex)) return "§5OG_FAIRY";
        if (SPOOK.contains(hex) && id.startsWith("FAIRY_")) return "§cSPOOK";
        if (GLITCHED.contains(hex)) {
            boolean glitched;
            if (id.contains("WITHER")) glitched = isWitherGlitched(id, hex, obtained);
            else {
                String misc = MISC_GLITCHED_HEXES.get(hex);
                glitched = misc != null && id.startsWith(misc);
            }
            if (glitched) return "§9GLITCHED";
        }
        return "§6EXOTIC";
    }

    // skyblocker ColorTooltip.getExpectedHex: items color on hypixels list, "r,g,b"
    static String expectedHex(String color) {
        if (color == null) return null;
        String[] rgb = color.split(",");
        return String.format("%02X%02X%02X", Integer.parseInt(rgb[0].trim()), Integer.parseInt(rgb[1].trim()), Integer.parseInt(rgb[2].trim()));
    }

    private static void exotic(ItemStack stack, List<Component> lines) {
        if (!b("tip_more.exotic", true) || !stack.has(DataComponents.DYED_COLOR)) return;
        String id = Gain.sbId(stack), expected = expectedHex(ItemRepo.color(id));
        if (expected == null) return;
        String hex = String.format("%06X", stack.get(DataComponents.DYED_COLOR).rgb() & 0x00FFFFFF);
        CompoundTag custom = data(stack);
        if (hex.equalsIgnoreCase(expected) || colorException(id, hex) || custom.contains("dye_item")) return;
        String existing = "";
        for (Component c : lines) {
            String text = c.getString() + " ";
            if (text.startsWith("Color: ")) {
                existing = text;
                break;
            }
        }
        lines.add(Math.min(1, lines.size()), Component.literal(existing + "§8(" + dyeType(id, hex, obtainedMillis(custom)) + " §r- ")
                .append(Component.literal("#" + hex).withColor(Integer.parseInt(hex, 16)))
                .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
    }

    // skyblocker EvolvingItemProgressTooltip, skyhanni EvolvingItems

    private static final Set<String> EVOLUTION_ITEMS = Set.of("DARK_CACAO_TRUFFLE", "MOBY_DUCK", "ROSEWATER_FLASK", "NOT_VERY_MOLDY_BREAD", "DISCRITE", "NEW_BOTTLE_OF_JYRRE", "TRAINING_WEIGHTS");

    // secs an evolving item has been held, from whatever tag it uses. -1 if its not one
    static int secondsHeld(String id, CompoundTag custom) {
        if (!EVOLUTION_ITEMS.contains(id)) return -1;
        return switch (id) {
            case "DISCRITE" -> custom.getIntOr("rift_discrite_seconds", 0);
            case "NEW_BOTTLE_OF_JYRRE" -> custom.getIntOr("bottle_of_jyrre_seconds", 0);
            case "TRAINING_WEIGHTS" -> custom.getIntOr("trainingWeightsHeldTime", 0);
            default -> custom.getIntOr("seconds_held", 0);
        };
    }

    static int maxSeconds(String id) {
        return id.equals("TRAINING_WEIGHTS") ? 5500 * 60 : 3600 * 300;
    }

    // age line: hours held of hours needed + percent
    static String ageLine(int held, int max) {
        return String.format(Locale.ROOT, "§7Age: §c%3.1f§7/§c%3.1fh §7(§c%2.1f%%§7)", held / 3600f, max / 3600f, 100.0 * held / max);
    }

    // skyhanni SimpleTimeMark.format, max 3 units
    static String duration(long seconds) {
        long d = seconds / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        int units = 0;
        if (d > 0) {
            sb.append(d).append("d ");
            units++;
        }
        if (h > 0) {
            sb.append(h).append("h ");
            units++;
        }
        if (m > 0 && units < 3) {
            sb.append(m).append("m ");
            units++;
        }
        if ((s > 0 || units == 0) && units < 3) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private static void evolving(ItemStack stack, List<Component> lines) {
        String id = Gain.sbId(stack);
        int held = secondsHeld(id, data(stack));
        if (held < 0) return;
        int max = maxSeconds(id);
        if (b("tip_more.evolving", true)) lines.add(Component.literal(ageLine(held, max)));
        // skyhanni EvolvingItems: "all the current ones take 300 hours", on line 10 if the tooltip is that long
        int at = Math.min(10, lines.size());
        if (b("tip_more.timeLeft", false)) lines.add(at, Component.literal("§7Time Left: §b" + duration(Math.max(0, 300 * 60 * 60 - held))));
        if (b("tip_more.timeHeld", false)) lines.add(at, Component.literal("§7Time Held: §b" + duration(held)));
    }

    // from skyblocker MotesTooltip

    // skyblocker getMotesMessage: mcgrubber stacks add 5% each
    static String motesLine(int price, int count, int grubber) {
        float multiplier = grubber * 0.05f + 1;
        String total = String.format(Locale.ENGLISH, "%1$,.1f", price * count * multiplier).replace(".0", "");
        if (count == 1) return "§dMotes Price: §3" + total + " Motes";
        return "§dMotes Price: §3" + total + " Motes §7(" + String.format(Locale.ENGLISH, "%1$,.1f", price * multiplier).replace(".0", "") + " each)";
    }

    private static void motes(ItemStack stack, List<Component> lines) {
        if (!b("tip_more.motes", true)) return;
        int price = ItemRepo.motesPrice(Gain.sbId(stack));
        if (price <= 0 || dev.zmuzy.sbc.rift.RiftMotes.price(stack) <= 0) return;
        Option o = Features.get(ID).option("tip_more.mcGrubber");
        lines.add(Component.literal(motesLine(price, stack.getCount(), o == null ? 0 : o.integer())));
    }

    // from skyblocker EssenceShopPrice

    private static final Pattern ESSENCE_SHOP = Pattern.compile("\\S+ Essence Shop");
    private static final Pattern ESSENCE_PATTERN = Pattern.compile("Cost (?<amount>[\\d,]+) (?<type>[A-Za-z]+) Essence");
    private static final Set<String> ESSENCE_TYPES = Set.of("WITHER", "SPIDER", "UNDEAD", "DRAGON", "GOLD", "DIAMOND", "ICE", "CRIMSON", "FOREST", "FOSSIL", "SAFARI");

    // the line or null
    static String essenceLine(String lore, java.util.function.ToLongFunction<String> pricePerEssence) {
        Matcher m = ESSENCE_PATTERN.matcher(lore);
        if (!m.find()) return null;
        String type = m.group("type").toUpperCase(Locale.ROOT);
        long each = ESSENCE_TYPES.contains(type) ? pricePerEssence.applyAsLong(type) : 0;
        if (each == 0) return null;
        long cost = Long.parseLong(m.group("amount").replace(",", ""));
        return String.format(Locale.ROOT, "§bEssence Cost: §3%,d coins §7(%,d each)", each * cost, each);
    }

    private static void essence(List<Component> lines) {
        if (!b("tip_more.essenceCost", true) || !ESSENCE_SHOP.matcher(GameState.strip(screenTitle())).matches()) return;
        StringBuilder lore = new StringBuilder();
        for (Component c : lines) lore.append(lore.isEmpty() ? "" : " ").append(GameState.strip(c.getString()));
        String line = essenceLine(lore.toString(), type -> (long) Prices.sell("ESSENCE_" + type));
        if (line != null) lines.add(Component.literal(line));
    }

    // from skyblocker DateCalculatorTooltip

    private static final Pattern TIMER_PATTERN = Pattern.compile("((?<days>\\d+)d)? ?((?<hours>\\d+)h)? ?((?<minutes>\\d+)m)? ?((?<seconds>\\d+)s)?");
    private static final Pattern CALENDAR_MENU = Pattern.compile("(?<month>.+), Year (?<year>\\d+)");
    private static final List<String> MONTHS = List.of("Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter");
    // skyblocker SkyblockTime: first second of year 1 + how long the units are
    private static final long SKYBLOCK_EPOCH = 1560275700000L, DAY_MS = 20 * 60_000L, MONTH_MS = DAY_MS * 31, YEAR_MS = MONTH_MS * 12;

    private static int group(MatchResult r, String name) {
        String g = r.group(name);
        return g == null ? 0 : Integer.parseInt(g);
    }

    // skyblocker Events.getStartTime: "Starts in: 1d 2h" from now, rounded to the min. null if the line has no time
    static Instant eventStart(String line, Instant now) {
        MatchResult result = TIMER_PATTERN.matcher(line).results()
                .filter(r -> r.group("days") != null || r.group("hours") != null || r.group("minutes") != null || r.group("seconds") != null)
                .findFirst().orElse(null);
        if (result == null) return null;
        return now.plus(group(result, "days"), ChronoUnit.DAYS).plus(group(result, "hours"), ChronoUnit.HOURS).plus(group(result, "minutes"), ChronoUnit.MINUTES)
                .plusSeconds(group(result, "seconds")).plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
    }

    // skyblocker Calendar.getStartTime: the day a calendar slot stands for, null if its not a day
    static Instant calendarDay(String title, int stackCount) {
        Matcher m = CALENDAR_MENU.matcher(title);
        if (!m.matches() || stackCount > 31) return null;
        int month = MONTHS.indexOf(m.group("month")), year;
        try {
            year = Integer.parseInt(m.group("year"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (month < 0 || year == 0) return null;
        return Instant.ofEpochMilli(SKYBLOCK_EPOCH + (year - 1) * YEAR_MS + month * MONTH_MS + (stackCount - 1) * DAY_MS);
    }

    private static void dates(ItemStack stack, List<Component> lines) {
        if (!b("tip_more.dates", true)) return;
        String title = GameState.strip(screenTitle());
        boolean events = title.equals("Calendar and Events"), calendar = !events && CALENDAR_MENU.matcher(title).matches();
        if (!events && !calendar) return;
        Slot slot = focused();
        if (slot == null || slot.container instanceof Inventory) return;
        for (int i = 1; i < lines.size(); i++) {
            String text = lines.get(i).getString();
            if (!text.contains("Starts in:") && !text.contains(" (")) continue;
            Instant at = events ? eventStart(text, Instant.now()) : calendarDay(title, stack.getCount());
            if (at != null) lines.add(++i, Component.literal("§8§o" + EVENT_DATE.format(at)));
        }
    }

    // skyblocker TrueHexDisplay, TrueHexDyeScreenDisplay

    private static final Pattern HEX_PATTERN = Pattern.compile("#[A-Fa-f0-9]{6}");
    private static final Pattern DYE_SCREEN_NAMES = Pattern.compile("Dye Compendium|Dyes");
    private static final Pattern DYE_HEX_LINE = Pattern.compile("Hex (?<hex>#[A-Fa-f0-9]{6})");

    private static void hex(ItemStack stack, List<Component> lines) {
        if (!b("tip_more.hexColours", true)) return;
        String id = Gain.sbId(stack);
        if (!id.isEmpty() && (id.startsWith("DYE_") || id.endsWith("_DYE"))) {
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i).copy();
                List<Component> siblings = line.getSiblings();
                for (int j = 0; j < siblings.size(); j++) {
                    String text = siblings.get(j).getString();
                    if (HEX_PATTERN.matcher(text).matches()) {
                        siblings.set(j, siblings.get(j).copy().withColor(Integer.parseInt(text.substring(1), 16)));
                        lines.set(i, line);
                    }
                }
            }
        }
        if (stack.is(Items.PLAYER_HEAD) && stack.getHoverName().getString().endsWith("Dye") && DYE_SCREEN_NAMES.matcher(GameState.strip(screenTitle())).matches()) {
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = DYE_HEX_LINE.matcher(lines.get(i).getString());
                if (m.matches()) {
                    String hex = m.group("hex");
                    lines.set(i, Component.literal("Hex ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(hex).withColor(Integer.parseInt(hex.substring(1), 16))));
                    return;
                }
            }
        }
    }

    // from skyblocker LineSmoothener

    private static final Component BUMPY_LINE = Component.literal("-----------------").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);

    public static MutableComponent smoothLine() {
        return Component.literal("                    ").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD);
    }

    private static void smooth(List<Component> lines) {
        if (!b("tip_more.smoothLines", true)) return;
        for (int i = 0; i < lines.size(); i++) {
            List<Component> siblings = lines.get(i).getSiblings();
            if (siblings.size() == 1 && siblings.getFirst().equals(BUMPY_LINE)) lines.set(i, smoothLine());
        }
    }

    // from skyblocker HuntingBoxPriceTooltip

    private static final Pattern HUNTING_BOX = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Hunting Box$");
    private static final Pattern HUNTING_BOX_COUNT = Pattern.compile("Owned: (?<shards>[\\d,]+) Shards?");

    // skyblocker gets the bz id from the repos attribute file which we dont have, so the id is built from the shard name
    // a name that doesnt fit just gets no line
    static String shardProduct(String name) {
        int index = name.indexOf("Shard");
        if (index > 0) name = name.substring(0, index - 1);
        name = name.replace("BUY ", "").replace("SELL ", "");
        return "SHARD_" + name.toUpperCase(Locale.ROOT).replace(" ", "_");
    }

    private static void huntingBox(ItemStack stack, List<Component> lines) {
        if (!b("tip_more.huntingBox", true) || !stack.has(DataComponents.CUSTOM_NAME) || !HUNTING_BOX.matcher(GameState.strip(screenTitle())).matches()) return;
        Slot slot = focused();
        if (slot == null || slot.index > 53) return;
        Prices.Product product = Prices.get(shardProduct(GameState.strip(stack.getHoverName().getString())));
        if (product == null) return;
        int count = 1;
        for (String l : LegacyText.lore(stack)) {
            Matcher m = HUNTING_BOX_COUNT.matcher(GameState.strip(l));
            if (m.find()) count = Integer.parseInt(m.group("shards").replace(",", ""));
        }
        lines.add(Component.literal("§6" + (count > 1 ? "Shards" : "Shard") + " Sell Price: §3" + Prices.coins(product.sell() * count) + (count > 1 ? " §7(" + Prices.coins(product.sell()) + " each)" : "")));
    }

    // all of it

    private static void tooltip(ItemStack stack, List<Component> lines) {
        smooth(lines);
        hex(stack, lines);
        evolving(stack, lines);
        motes(stack, lines);
        essence(lines);
        exotic(stack, lines);
        dates(stack, lines);
        huntingBox(stack, lines);
    }

    private static void refreshPrices() {
        Prices.refresh();
        AuctionPrices.refresh();
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §7Refreshing item prices."));
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "More tooltip lines", "Exotic type on armor, dates in the calendar, the age of evolving items, motes, essence cost in coins, the real colour of a dye, whole separator lines and shard prices in the Hunting Box.", "Skyblocker: exotic, date calculator, evolving items, motes, essence cost, hex colours · SkyHanni: evolving items")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tooltip(TipExtras::tooltip)
                .option(Option.bool("tip_more.exotic", "Exotic type", "Crystal, fairy, spook, glitched or exotic under the name of dyed armor.", true))
                .option(Option.bool("tip_more.dates", "Dates in the calendar", "When an event starts, as a date, in the calendar menus.", true))
                .option(Option.bool("tip_more.evolving", "Evolving item age", "How long a Bottle of Jyrre, Dark Cacao Truffle, Discrite and the like has been held, of what it takes.", true))
                .option(Option.bool("tip_more.timeHeld", "Time held", "The time an evolving item has been held, as days and hours.", false))
                .option(Option.bool("tip_more.timeLeft", "Time left", "The time an evolving item still needs, as days and hours.", false))
                .option(Option.bool("tip_more.motes", "Motes price", "What a Rift item sells for.", true))
                .option(Option.integer("tip_more.mcGrubber", "McGrubber's Burger stacks", "Each adds 5% to the motes price.", 0, 0, 5))
                .option(Option.bool("tip_more.essenceCost", "Essence cost", "In an Essence Shop: what the essence for an upgrade costs in coins.", true))
                .option(Option.bool("tip_more.hexColours", "Real dye colours", "The hex code of a dye in its own colour.", true))
                .option(Option.bool("tip_more.smoothLines", "Whole separator lines", "Hypixel's dashed lines drawn as one line.", true))
                .option(Option.bool("tip_more.huntingBox", "Shard prices in the Hunting Box", "What a shard sells for, for all you own of it.", true)));
        BitsHelper.register();
        Safe.commands("TipExtras.commands", (dispatcher, registry) -> dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("sbc")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("refreshprices").executes(c -> {
                    refreshPrices();
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("refreshprices", "Fetch the Bazaar and Auction House prices again now");
    }
}
