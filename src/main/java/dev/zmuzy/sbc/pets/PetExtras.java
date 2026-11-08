// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.pets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Pets;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.core.game.GameProfile;
import dev.zmuzy.sbc.core.game.GameRead;
import dev.zmuzy.sbc.core.game.Seen;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni features/pets: HighlightCurrentPet, PetCandyUsedDisplay, PetExpTooltip, PetItemDisplay,
// PetNametag + what CurrentPetDisplay and PetDisplayTextRenderables say on the card
public final class PetExtras {
    public static final String HIGHLIGHT = "pet_highlight", CANDY = "pet_candy_used", TOOLTIP = "pet_xp_tooltip", ITEM_ICONS = "pet_item_icons", NAMETAG = "pet_nametag", DETAILS = "pet_details";
    private static final Patterns.P PET_MENU = Patterns.p("SH.misc.pet.storage.menu.gui.name"), TAG = Patterns.p("SH.pet.nametag");
    private static final int[] EXP_SHARE_SLOTS = {30, 31, 32};
    private static final long LEVEL_100_COMMON = 5_624_785, LEVEL_100_LEGENDARY = 25_353_230, LEVEL_200_LEGENDARY = 210_255_385;

    private PetExtras() {}

    static JsonObject info(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    // from skyhanni PetInfo.ownedUuid
    static String uuid(JsonObject info) {
        String id = text(info, "uniqueId");
        return id.isEmpty() ? text(info, "uuid") : id;
    }

    // from skyhanni PetStorageExpShare.readInventory, on every menu look
    public static void menu(Seen s, String title, List<GameRead.MenuItem> items) {
        if (!title.equals("Exp Sharing")) return;
        List<String> read = new ArrayList<>();
        for (int slot : EXP_SHARE_SLOTS) {
            String uuid = "";
            for (GameRead.MenuItem i : items) {
                if (i.slot() == slot && !i.cleanName().equals("No pet in slot")) uuid = uuid(info(i.petInfo()));
            }
            read.add(uuid);
        }
        s.expSharePets = read;
        Store.markDirty();
    }

    // from skyhanni activeSlotCount: hypixels perk opens the other 2 slots
    static int activeSlots() {
        for (Mayor.Perk p : Mayor.perks) if (p.name().equals("Sharing is Caring")) return EXP_SHARE_SLOTS.length;
        return 1;
    }

    // --- slots ---

    private static JsonObject stackInfo(MenuView.Item it) {
        return info(it.tag.getStringOr("petInfo", ""));
    }

    // from skyhanni HighlightCurrentPet. the menus own "Click to despawn" for a pet with no uuid
    private static void highlight(MenuView v) {
        Seen s = GameProfile.current();
        String active = "";
        if (s != null) for (Seen.Pet p : s.allPets()) if (p.active) active = p.uuid;
        int color = Features.get(HIGHLIGHT).option("pet_highlight.color").color();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            String uuid = uuid(stackInfo(it));
            if (!uuid.isEmpty() ? uuid.equals(active) : it.cleanHas("Click to despawn")) v.back(slot, color);
        }
    }

    // from skyhanni PetCandyUsedDisplay, PetItemDisplay in the corner under it
    private static void candyAndItems(MenuView v) {
        boolean candy = Features.on(CANDY), hideMaxed = candy && MenuMarks.setting(CANDY, "pet_candy_used.hide_maxed");
        boolean icons = Features.on(ITEM_ICONS);
        for (int slot = 0; slot < v.itemsLength(); slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty() || it.count != 1) continue;
            JsonObject info = stackInfo(it);
            if (info == null) continue;
            if (candy && info.has("candyUsed") && !(hideMaxed && atMaxLevel(it.clean, text(info, "type")))) {
                int used = info.get("candyUsed").getAsInt();
                if (used != 0) v.corner(slot, MenuView.TOP_RIGHT, "§c" + used);
            }
            if (icons) {
                String held = text(info, "heldItem");
                if (held.equals("PET_ITEM_EXP_SHARE") && MenuMarks.setting(ITEM_ICONS, "pet_item_icons.exp_share")) v.corner(slot, MenuView.BOTTOM_RIGHT, "§5⚘");
                if (held.equals("PET_ITEM_TIER_BOOST") && MenuMarks.setting(ITEM_ICONS, "pet_item_icons.tier_boost")) v.corner(slot, MenuView.BOTTOM_RIGHT, "§c●");
            }
        }
    }

    private static boolean atMaxLevel(String name, String type) {
        return name.contains("[Lvl " + Pets.maxLevel(type) + "]");
    }

    // --- skyhanni PetExpTooltip ---

    // from skyhanni findIndex
    static int tooltipIndex(List<Component> tip) {
        for (int i = 0; i < tip.size(); i++) if (tip.get(i).getString().contains("MAX LEVEL")) return i + 2;
        for (int i = 0; i < tip.size(); i++) if (tip.get(i).getString().contains("Progress to Level")) return i + 3;
        return -1;
    }

    // from skyhanni getMaxValues: level, xp
    static long[] maxValues(String name, double exp, String type, boolean dragonEgg) {
        boolean level200 = Pets.maxLevel(type) == 200;
        boolean use200 = level200 && (!dragonEgg || exp >= LEVEL_100_LEGENDARY);
        long xp = use200 ? LEVEL_200_LEGENDARY : name.contains("Bingo") ? LEVEL_100_COMMON : LEVEL_100_LEGENDARY;
        return new long[]{use200 ? 200 : 100, xp};
    }

    private static String progressBar(double done) {
        StringBuilder b = new StringBuilder("§2");
        boolean missing = false;
        for (int i = 0; i <= 24; i++) {
            if (!missing && i / 24.0 > done) {
                b.append("§f");
                missing = true;
            }
            b.append("§l§m ");
        }
        return b.append("§r").toString();
    }

    private static void expTooltip(ItemStack stack, List<Component> tip) {
        Feature f = Features.get(TOOLTIP);
        Minecraft mc = Minecraft.getInstance();
        boolean shift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (!shift && !f.option("pet_xp_tooltip.always").bool()) return;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        JsonObject info = info(tag.getStringOr("petInfo", ""));
        if (info == null || !info.has("exp")) return;
        int index = tooltipIndex(tip);
        if (index < 0) return;
        double exp = info.get("exp").getAsDouble();
        String name = LegacyText.name(stack);
        long[] max = maxValues(name, exp, text(info, "type"), f.option("pet_xp_tooltip.dragon_egg").bool());
        double percent = exp / max[1];
        if (percent >= 1) return;
        boolean belowLegendary = switch (text(info, "tier")) {
            case "LEGENDARY", "MYTHIC" -> false;
            default -> true;
        };
        index = Math.min(index, tip.size());
        tip.add(index, Component.literal(progressBar(percent) + " §e" + String.format(Locale.ROOT, "%,.1f", exp) + "§6/§e" + Combat.shortNumber(max[1])));
        tip.add(index, Component.literal("§7Progress to " + (belowLegendary ? "§6" : "") + "Level " + max[0] + ": §e" + String.format(Locale.ROOT, "%.2f%%", percent * 100)));
        tip.add(index + 2, Component.literal(" "));
    }

    // --- skyhanni PetNametag ---

    // from skyhanni onNameTagRender. what the game wouldve written when nothing gets hidden
    public static Component nameTag(Component tag) {
        if (!Features.on(NAMETAG)) return tag;
        String shown = withoutLevel(LegacyText.of(tag, true, false), MenuMarks.setting(NAMETAG, "pet_nametag.level"), MenuMarks.setting(NAMETAG, "pet_nametag.max_level"));
        return shown == null ? tag : Component.literal(shown);
    }

    // name tag without its level, null = not a pet or the level stays
    static String withoutLevel(String tag, boolean level, boolean max) {
        Matcher m = TAG.matcher(tag);
        if (!m.matches()) return null;
        String lvl = m.group("lvl");
        if (!level && !(max && (lvl.equals("100") || lvl.equals("200")))) return null;
        String skin = m.group("skin");
        return m.group("rarity") + m.group("pet") + (skin == null ? "" : skin);
    }

    // --- the card ---

    private static Seen.Pet activePet(Seen s) {
        if (s != null) for (Seen.Pet p : s.allPets()) if (p.active) return p;
        return null;
    }

    private static String held(Seen.Pet p) {
        if (p.held.isEmpty()) return p.heldName;
        String name = ItemRepo.name(p.held);
        return name == null ? p.heldName : name;
    }

    // from skyhanni PetData.overflowXp: whats past the last level, under 1,000 isnt worth saying
    static double overflow(double exp, String tier, String type) {
        double over = exp - Pets.xpForMax(tier, type);
        return over > 1000 ? over : 0;
    }

    private static List<RichCard.Line> lines(Seen s) {
        List<RichCard.Line> out = new ArrayList<>();
        Seen.Pet p = activePet(s);
        if (p != null && p.exp > 0) {
            out.add(RichCard.Line.row("Total XP", "§b" + Combat.shortNumber((long) p.exp)).raw().key("Total XP").brief("Total"));
            double over = overflow(p.exp, p.tier, p.type);
            if (over > 0) out.add(RichCard.Line.row("Overflow XP", "§7+§b" + Combat.shortNumber((long) over)).raw().key("Overflow XP").brief("Overflow"));
        }
        if (p != null && !held(p).isEmpty()) out.add(RichCard.Line.row("Held Item", "§b" + held(p)).raw().key("Held Item").brief("Held"));
        if (s != null) {
            int open = activeSlots();
            for (int i = 0; i < s.expSharePets.size(); i++) {
                String uuid = s.expSharePets.get(i);
                if (uuid.isEmpty()) continue;
                boolean off = i >= open;
                if (off && Combat.opt(DETAILS, "active_only")) continue;
                for (Seen.Pet shared : s.allPets()) {
                    if (!uuid.equals(shared.uuid)) continue;
                    out.add(RichCard.Line.row((off ? "§8" : "§b") + "Exp Share", (off ? "§8" : "§f") + "[Lvl " + shared.level + "] " + shared.name).raw().key("Exp Share").brief("Share"));
                }
            }
        }
        return out;
    }

    private static final HudElement CARD = new RichCard(DETAILS, "Pet details", "HUD", Zone.TOP_LEFT, 59, DETAILS) {
        @Override public boolean visible() {
            return !Vitals.petName.isEmpty() && !lines(GameProfile.current()).isEmpty();
        }

        @Override protected Content live() {
            return new Content("", lines(GameProfile.current()));
        }

        @Override protected Content sample() {
            return new Content("", List.of(Line.row("Total XP", "§b18.4M").raw().key("Total XP").brief("Total"), Line.row("Overflow XP", "§7+§b2.1M").raw().key("Overflow XP").brief("Overflow"),
                    Line.row("Held Item", "§bTier Boost").raw().key("Held Item").brief("Held"), Line.row("§bExp Share", "§f[Lvl 100] Tiger").raw().key("Exp Share").brief("Share")));
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            Seen s = GameProfile.current();
            Seen.Pet p = activePet(s);
            if (p != null && !held(p).isEmpty()) out.add(new Row("Held Item", held(p), Theme.current().text()));
            if (p != null && p.exp > 0) out.add(new Row("Total XP", Combat.shortNumber((long) p.exp), Theme.current().text()));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Held Item", "Tier Boost", Theme.current().text()), new Row("Total XP", "18.4M", Theme.current().text()));
        }
    };

    public static void register() {
        Features.add(new Feature(HIGHLIGHT, "Pets", "Highlight current pet", "Marks the pet you have out in the Pets menu.", "SkyHanni: Highlight Current Pet")
                .when(() -> GameState.inSkyBlock)
                .option(Option.color("pet_highlight.color", "Colour", "", 0x8055FF55)));
        MenuMarks.add(HIGHLIGHT, t -> PET_MENU.matcher(t).matches(), PetExtras::highlight);
        Features.add(new Feature(CANDY, "Pets", "Pet Candy used", "How many Pet Candies went into a pet, on its slot.", "SkyHanni: Pet Candy Used")
                .when(() -> GameState.inSkyBlock)
                .option(Option.bool("pet_candy_used.hide_maxed", "Not on a pet at its top level", "", false)));
        Features.add(new Feature(ITEM_ICONS, "Pets", "Pet item icons", "A small icon on pets holding an Exp Share or a Tier Boost.", "SkyHanni: Show Pet Item")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .option(Option.bool("pet_item_icons.exp_share", "Exp Share", "", true))
                .option(Option.bool("pet_item_icons.tier_boost", "Tier Boost", "", true)));
        MenuMarks.add(CANDY, t -> true, PetExtras::candyAndItems);
        MenuMarks.add(ITEM_ICONS, t -> !Features.on(CANDY), PetExtras::candyAndItems);
        Features.add(new Feature(TOOLTIP, "Pets", "Pet progress to the top level", "On a pet's tooltip while holding Shift: how far it is to level 100 or 200, whatever its rarity.", "SkyHanni: Pet Experience Tooltip")
                .when(() -> GameState.inSkyBlock).tooltip(PetExtras::expTooltip)
                .option(Option.bool("pet_xp_tooltip.always", "Without Shift", "", false))
                .option(Option.bool("pet_xp_tooltip.dragon_egg", "Dragons from the egg", "Progress to level 100 first, then 200.", true)));
        Features.add(new Feature(NAMETAG, "Pets", "Pet name tags", "Takes the level off the name above pets.", "SkyHanni: Pet Nametag")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .option(Option.bool("pet_nametag.level", "Hide the level", "", false))
                .option(Option.bool("pet_nametag.max_level", "Hide it at the top level only", "", true)));
        // off by default, the hud already has the pet plate. one card too many
        Features.add(new Feature(DETAILS, "Pets", "Pet details", "What the Equipped pet plate has no room for: total XP, XP past the top level, the held item and your Exp Share pets. Numbers as last seen in the Pets and Exp Sharing menus.", "SkyHanni: Pet Display")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .option(Option.bool("pet_details.active_only", "Only slots in use", "Leaves out Exp Share slots that are not open now.", false)));
        HudLayout.add(CARD);
    }
}
