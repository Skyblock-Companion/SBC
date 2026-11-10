// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Worth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Set;

// more tooltip lines: whats in an items value (hold shift), is an accessory worth owning, and the museum
public final class Extras {
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY_SPECIAL"};
    private static final int[] MP = {3, 5, 8, 12, 16, 22, 3, 5};
    private static final Set<String> TIER_WORDS = Set.of("talisman", "ring", "artifact", "relic", "of", "the");
    private Extras() {}

    private static void line(List<Component> out, String s) {
        out.add(Component.literal(s));
    }

    private static CompoundTag data(ItemStack st) {
        var cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    // hold shift on a modified item to see what its estimated value is made of (skyhanni EstimatedItemValue lines)
    public static void breakdown(ItemStack st, List<Component> lines) {
        String id = Gain.sbId(st);
        if (id.isEmpty()) return;
        List<String> lore = io.github.zmuzy.sbc.core.LegacyText.lore(st);
        boolean dungeon = lore.stream().anyMatch(l -> l.contains("DUNGEON "));
        valueOptions();
        List<String> parts;
        try {
            parts = io.github.zmuzy.sbc.item.value.EstimatedItemValue.draw(data(st), id, dungeon, lore);
        } catch (Exception e) {
            return;
        }
        if (parts == null) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean shift = io.github.zmuzy.sbc.item.market.Market.b("tip_breakdown", "tip_breakdown.always", false) || InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        if (!shift) {
            line(lines, "§8Hold Shift for the value breakdown");
            return;
        }
        line(lines, "");
        for (String l : parts) line(lines, l);
    }

    private static int rarity(String tier) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equalsIgnoreCase(tier)) return i;
        return 0;
    }

    private static List<io.github.zmuzy.sbc.plan.AccessoryHelper.Family> families = List.of();
    private static long familiesAt;

    // every family with what you own of it. recomputed once in a while, not on every tooltip
    private static List<io.github.zmuzy.sbc.plan.AccessoryHelper.Family> families() {
        long now = System.currentTimeMillis();
        if (now - familiesAt < 5000 && !families.isEmpty()) return families;
        familiesAt = now;
        Set<String> have = io.github.zmuzy.sbc.core.game.GameProfile.ownedAccessories(Profile.current);
        haveAny = !have.isEmpty();
        families = io.github.zmuzy.sbc.plan.AccessoryHelper.families(ItemRepo.accessories(), have);
        return families;
    }

    private static boolean haveAny;

    // skyhanni calculateReport4Accessory with AccessoryTooltip words: collected, upgrade, upgradable, downgrade or missing
    // where it sits in its family (from 1), and what it adds in magical power, which is more than the source says
    static String accessoryLine(int tier, int highest, int owned, int gain) {
        String title = "§6Accessory: ";
        String more = gain > 0 ? " §b+" + gain + " MP" : "";
        if (highest == 0) return owned == 0 ? title + "§a✔ Collected" : title + "§c✖ Missing" + more;
        int t = tier + 1, h = highest + 1, o = owned + 1;
        if (owned < 0) return title + "§c✖ Missing §f(" + t + "/" + h + ")" + more;
        if (tier == highest && owned == highest) return title + "§a✔ Collected";
        if (tier > owned) return title + "§9✦ Upgrade §f(" + o + "→" + t + "/" + h + ")" + more;
        if (tier < owned) return title + "§7↓ Downgrade §f(" + o + "→" + t + "/" + h + ")";
        return title + "§e↑ Upgradable §f(" + o + "/" + h + ")";
    }

    // is this accessory new to your bag, an upgrade, or a copy of a better one you already have
    public static void accessory(ItemStack st, List<Component> lines) {
        String id = Gain.sbId(st);
        if (!ItemRepo.accessories().containsKey(id)) return;
        for (io.github.zmuzy.sbc.plan.AccessoryHelper.Family f : families()) {
            if (!haveAny) return;
            for (int i = 0; i < f.tiers().size(); i++) {
                if (!f.tiers().get(i).id().equals(id)) continue;
                int gain = io.github.zmuzy.sbc.plan.AccessoryHelper.MP[f.tiers().get(i).rarity()] - f.ownedMp();
                line(lines, "");
                line(lines, accessoryLine(i, f.tiers().size() - 1, f.owned(), Math.max(0, gain)));
                return;
            }
        }
    }

    // the value breakdown settings

    private static io.github.zmuzy.sbc.feature.Option valueOption(String key) {
        var f = io.github.zmuzy.sbc.feature.Features.get("tip_breakdown");
        return f == null ? null : f.option(key);
    }

    // skyhanni EstimatedItemValueConfig onto the calculator fields, before it runs
    private static void valueOptions() {
        var v = io.github.zmuzy.sbc.item.value.EstimatedItemValue.class;
        var caps = valueOption("tip_breakdown.enchantmentsCap");
        if (caps == null) return;
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.enchantmentsCap = caps.integer();
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.starMaterialCap = valueOption("tip_breakdown.starMaterialCap").integer();
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.exactPrice = valueOption("tip_breakdown.exactPrice").bool();
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.ignoreHelmetSkins = valueOption("tip_breakdown.ignoreHelmetSkins").bool();
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.ignoreArmorDyes = valueOption("tip_breakdown.ignoreArmorDyes").bool();
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.ignoreRunes = valueOption("tip_breakdown.ignoreRunes").bool();
        String source = valueOption("tip_breakdown.priceSource").choice();
        io.github.zmuzy.sbc.item.value.EstimatedItemValue.price = source.equals("BEST_KNOWN") ? id -> Math.max(0, io.github.zmuzy.sbc.core.AuctionPrices.value(id)) : id -> io.github.zmuzy.sbc.item.market.PriceSource.of(source, id);
    }

    private static final java.util.regex.Pattern ARMOR_SETS = java.util.regex.Pattern.compile("\\(\\d+/\\d+\\) Armor Sets"), EQUIPMENT_SETS = java.util.regex.Pattern.compile("\\(\\d+/\\d+\\) Equipment Sets");

    // skyhanni EstimatedWardrobePrice + createPriceLore: on a wardrobe slot button, what the set above it is worth
    private static void wardrobe(ItemStack st, List<Component> lines) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) || !(cs instanceof io.github.zmuzy.sbc.mc.mixin.ContainerAccess acc)) return;
        String title = io.github.zmuzy.sbc.core.GameState.strip(cs.getTitle().getString());
        boolean armor = io.github.zmuzy.sbc.item.market.Market.b("tip_breakdown", "tip_breakdown.armor", true) && ARMOR_SETS.matcher(title).matches();
        boolean equipment = io.github.zmuzy.sbc.item.market.Market.b("tip_breakdown", "tip_breakdown.equipment", true) && EQUIPMENT_SETS.matcher(title).matches();
        if (!armor && !equipment) return;
        var slot = acc.sbc$hovered();
        if (slot == null || slot.getItem() != st || slot.index < 36 || slot.index > 44) return;
        int column = slot.index - 36;
        valueOptions();
        List<String> out = new java.util.ArrayList<>();
        double total = 0;
        for (int row = 0; row < 4; row++) {
            ItemStack piece = cs.getMenu().slots.get(column + row * 9).getItem();
            String id = Gain.sbId(piece);
            var cd = piece.get(DataComponents.CUSTOM_DATA);
            if (id.isEmpty() || cd == null) continue;
            List<String> lore = io.github.zmuzy.sbc.core.LegacyText.lore(piece);
            boolean dungeon = lore.stream().anyMatch(l -> l.contains("DUNGEON "));
            double price = io.github.zmuzy.sbc.item.value.EstimatedItemValue.calculate(cd.copyTag(), id, dungeon, lore).totalPrice();
            out.add("  §7- " + io.github.zmuzy.sbc.core.LegacyText.name(piece) + ": §6" + Prices.coins(price));
            total += price;
        }
        if (out.isEmpty()) return;
        line(lines, "");
        line(lines, "§aEstimated " + (armor ? "Armor" : "Equipment") + " Value:");
        for (String l : out) line(lines, l);
        if (total != 0) line(lines, " §aTotal Value: §6§l" + Prices.coins(total) + " coins");
    }

    // settings for features declared in Builtin
    public static void registerOptions() {
        var f = io.github.zmuzy.sbc.feature.Features.get("tip_breakdown");
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.always", "Show without Shift", "The breakdown is always on the tooltip.", false));
        f.option(io.github.zmuzy.sbc.feature.Option.integer("tip_breakdown.enchantmentsCap", "Enchantments shown", "Only the most expensive ones.", 7, 1, 30));
        f.option(io.github.zmuzy.sbc.feature.Option.integer("tip_breakdown.starMaterialCap", "Star materials shown", "Only the most expensive ones.", 3, 1, 15));
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.exactPrice", "Exact total", "The total with every digit.", false));
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.ignoreHelmetSkins", "Leave helmet skins out", "", false));
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.ignoreArmorDyes", "Leave armor dyes out", "", false));
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.ignoreRunes", "Leave runes out", "", false));
        f.option(io.github.zmuzy.sbc.feature.Option.choice("tip_breakdown.priceSource", "Price source", "Best known is what the market search shows.", "BEST_KNOWN", "BEST_KNOWN", io.github.zmuzy.sbc.item.market.PriceSource.INSTANT_BUY, io.github.zmuzy.sbc.item.market.PriceSource.INSTANT_SELL, io.github.zmuzy.sbc.item.market.PriceSource.NPC));
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.armor", "Armor set value", "On a Wardrobe slot's button: what the set above it is worth.", true));
        f.option(io.github.zmuzy.sbc.feature.Option.bool("tip_breakdown.equipment", "Equipment set value", "The same in the Equipment Wardrobe.", true));
        var breakdown = f.onTooltip;
        f.onTooltip = (st, lines) -> {
            breakdown.accept(st, lines);
            wardrobe(st, lines);
        };
    }

    // does the museum take it, and does it already have one
    public static void museum(ItemStack st, List<Component> lines) {
        String id = Gain.sbId(st);
        Profile.Data d = Profile.current;
        if (id.isEmpty() || d == null || !d.knows(io.github.zmuzy.sbc.core.game.GameProfile.MUSEUM) || !ItemRepo.museumEligible(id)) return;
        line(lines, "");
        // skyhanni MuseumTooltip: the category when hypixel names one
        String type = ItemRepo.museumType(id);
        String head = type.isEmpty() ? "§dMuseum: " : "§dMuseum (" + io.github.zmuzy.sbc.core.GameState.pretty(type) + "): ";
        if (d.museum.contains(id)) line(lines, head + "§a§l✔§a Donated");
        else {
            int xp = ItemRepo.museumXp(id);
            line(lines, head + "§c§l✖§c Not Donated" + (xp > 0 ? " §8(+" + xp + " museum XP)" : ""));
        }
    }
}
