// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.milestones;

// from skyhanni CropType, just what the milestone and speed code needs
public enum CropType {
    WHEAT("Wheat", "THEORETICAL_HOE_WHEAT", "CROPIE", 1.0, "wheat", false, "wheat", "§6"),
    CARROT("Carrot", "THEORETICAL_HOE_CARROT", "CROPIE", 3.0, "carrot", true, "carrot", "§6"),
    POTATO("Potato", "THEORETICAL_HOE_POTATO", "CROPIE", 3.0, "potato", true, "potato", "§6"),
    NETHER_WART("Nether Wart", "THEORETICAL_HOE_WARTS", "FERMENTO", 2.5, "wart", true, "warts", "§c"),
    PUMPKIN("Pumpkin", "PUMPKIN_DICER", "SQUASH", 1.0, "pumpkin", false, "pumpkin", "§6"),
    MELON("Melon Slice", "MELON_DICER", "SQUASH", 5.0, "melon", false, "melon slice", "§a"),
    COCOA_BEANS("Cocoa Beans", "COCO_CHOPPER", "SQUASH", 3.0, "cocoa", true, "coco", "§6"),
    SUGAR_CANE("Sugar Cane", "THEORETICAL_HOE_CANE", "FERMENTO", 2.0, "cane", false, "cane", "§a"),
    CACTUS("Cactus", "CACTUS_KNIFE", "FERMENTO", 2.0, "cactus", false, "cactus", "§a"),
    MUSHROOM("Mushroom", "FUNGI_CUTTER", "FERMENTO", 1.0, "mushroom", false, "mushrooms", "§c"),
    SUNFLOWER("Sunflower", "THEORETICAL_HOE_SUNFLOWER", "HELIANTHUS", 2.0, "sunflower", true, "sunflower", "§6"),
    MOONFLOWER("Moonflower", "THEORETICAL_HOE_SUNFLOWER", "HELIANTHUS", 2.0, "moonflower", true, "moonflower", "§b"),
    WILD_ROSE("Wild Rose", "THEORETICAL_HOE_WILD_ROSE", "HELIANTHUS", 2.0, "rose", true, "wild rose", "§c");

    public final String cropName, toolName, specialDropType, simpleName, enchantName, color;
    public final double baseDrops;
    public final boolean replenish;

    CropType(String cropName, String toolName, String specialDropType, double baseDrops, String simpleName, boolean replenish, String enchantName, String color) {
        this.cropName = cropName;
        this.toolName = toolName;
        this.specialDropType = specialDropType;
        this.baseDrops = baseDrops;
        this.simpleName = simpleName;
        this.replenish = replenish;
        this.enchantName = enchantName;
        this.color = color;
    }

    // item that shows this crop in a card
    public String icon() {
        return switch (this) {
            case WHEAT -> "minecraft:wheat";
            case CARROT -> "minecraft:carrot";
            case POTATO -> "minecraft:potato";
            case NETHER_WART -> "minecraft:nether_wart";
            case PUMPKIN -> "minecraft:pumpkin";
            case MELON -> "minecraft:melon_slice";
            case COCOA_BEANS -> "minecraft:cocoa_beans";
            case SUGAR_CANE -> "minecraft:sugar_cane";
            case CACTUS -> "minecraft:cactus";
            case MUSHROOM -> "minecraft:red_mushroom";
            case SUNFLOWER, MOONFLOWER -> "minecraft:sunflower";
            case WILD_ROSE -> "minecraft:rose_bush";
        };
    }

    // crop colour in a card
    public int tint() {
        return switch (this) {
            case WHEAT -> 0xFFE8C85A;
            case CARROT -> 0xFFFF9F45;
            case POTATO -> 0xFFD8B26E;
            case NETHER_WART -> 0xFFE0506A;
            case PUMPKIN -> 0xFFFF9020;
            case MELON -> 0xFF7FD85F;
            case COCOA_BEANS -> 0xFFC48A5A;
            case SUGAR_CANE -> 0xFF9CE37D;
            case CACTUS -> 0xFF5FBF5F;
            case MUSHROOM -> 0xFFE8705F;
            case SUNFLOWER -> 0xFFFFD84A;
            case MOONFLOWER -> 0xFF7FB3FF;
            case WILD_ROSE -> 0xFFFF6B8A;
        };
    }

    // CropType.multiplier: sugar cane and cactus break 2 blocks per hit
    public int multiplier() {
        return this == SUGAR_CANE || this == CACTUS ? 2 : 1;
    }

    // CropType.getByNameOrNull
    public static CropType getByNameOrNull(String itemName) {
        if (itemName == null) return null;
        if (itemName.equals("Red Mushroom") || itemName.equals("Brown Mushroom")) return MUSHROOM;
        if (itemName.equals("Seeds")) return WHEAT;
        for (CropType c : values())
            if (c.cropName.equalsIgnoreCase(itemName) || c.simpleName.equalsIgnoreCase(itemName) || c.enchantName.equalsIgnoreCase(itemName)) return c;
        return null;
    }

    // BlockState.getCropType by block id path (Blocks.WHEAT, CARROTS, ...)
    public static CropType fromBlock(String blockPath, boolean night) {
        return switch (blockPath) {
            case "wheat" -> WHEAT;
            case "carrots" -> CARROT;
            case "potatoes" -> POTATO;
            case "carved_pumpkin" -> PUMPKIN;
            case "sugar_cane" -> SUGAR_CANE;
            case "melon" -> MELON;
            case "cactus" -> CACTUS;
            case "cocoa" -> COCOA_BEANS;
            case "red_mushroom", "brown_mushroom" -> MUSHROOM;
            case "nether_wart" -> NETHER_WART;
            case "rose_bush" -> WILD_ROSE;
            case "sunflower" -> night ? MOONFLOWER : SUNFLOWER;
            default -> null;
        };
    }

    // NeuInternalName.getCropType: a dedicated farming tool's crop
    public static CropType fromToolId(String internalName, boolean night) {
        if (internalName == null) return null;
        if (internalName.startsWith("THEORETICAL_HOE_SUNFLOWER")) return night ? MOONFLOWER : SUNFLOWER;
        for (CropType c : values()) if (internalName.startsWith(c.toolName)) return c;
        return null;
    }
}
