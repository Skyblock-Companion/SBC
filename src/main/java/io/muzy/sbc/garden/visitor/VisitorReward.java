// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.visitor;

import io.muzy.sbc.core.GameState;

// from skyhanni VisitorReward: visitor rare drops, and which ones RewardWarningConfig.drops warns about by default
public enum VisitorReward {
    FLOWERING_BOUQUET("FLOWERING_BOUQUET", "§9Flowering Bouquet", false),
    OVERGROWN_GRASS("OVERGROWN_GRASS", "§9Overgrown Grass", true),
    GREEN_BANDANA("GREEN_BANDANA", "§9Green Bandana", true),
    DEDICATION("ENCHANTMENT_DEDICATION_4", "§9Dedication IV", true),
    MUSIC_RUNE("MUSIC_RUNE;1", "§9Music Rune", true),
    SPACE_HELMET("DCTR_SPACE_HELM", "§cSpace Helmet", true),
    CULTIVATING("ENCHANTMENT_CULTIVATING_1", "§9Cultivating I", true),
    REPLENISH("ENCHANTMENT_REPLENISH_1", "§9Replenish I", true),
    DELICATE("ENCHANTMENT_DELICATE_5", "§9Delicate V", false),
    COPPER_DYE("DYE_COPPER", "§8Copper Dye", true),
    JUNGLE_KEY("JUNGLE_KEY", "§5Jungle Key", false),
    FRUIT_BOWL("FRUIT_BOWL", "§9Fruit Bowl", false),
    HARVEST_HARBINGER("POTION_HARVEST_HARBINGER;5", "§9Harvest Harbinger V", false),
    HYPERCHARGE_CHIP("HYPERCHARGE_GARDEN_CHIP", "§9Hypercharge Chip", false),
    QUICKDRAW_CHIP("QUICKDRAW_GARDEN_CHIP", "§9Quickdraw Chip", false),
    FARMING_EXP_BOOST_EPIC("PET_ITEM_FARMING_SKILL_BOOST_EPIC", "§5Farming Exp Boost", true),
    UNFULFILLED_JERRYSEED("UNFULFILLED_JERRYSEED", "§aUnfulfilled Jerryseed", false),
    VOTER_BADGE("VOTER_BADGE", "§fVoter's Badge", false),
    VOTER_BADGE_VIP("VOTER_BADGE_VIP", "§aVIP Voter's Badge", false),
    VOTER_BADGE_ELITE("VOTER_BADGE_ELITE", "§9Elite Voter's Badge", false),
    VOTER_BADGE_SUPREME("VOTER_BADGE_SUPREME", "§5Supreme Voter's Badge", false),
    DYE_WILD_STRAWBERRY("DYE_WILD_STRAWBERRY", "§dWild Strawberry Dye", true),
    VELVET_TOP_HAT("VELVET_TOP_HAT", "§9Velvet Top Hat", false),
    CASHMERE_JACKET("CASHMERE_JACKET", "§9Cashmere Jacket", false),
    SATIN_TROUSERS("SATIN_TROUSERS", "§9Satin Trousers", false),
    OXFORD_SHOES("OXFORD_SHOES", "§9Oxford Shoes", false),
    CARNIVAL_TICKET("CARNIVAL_TICKET", "§aCarnival Ticket", false),
    VISITORS_GRATITUDE("VISITORS_GRATITUDE", "§fVisitors' Gratitude", false),
    FARMING_CONTEST_DISPLAY("FARMING_CONTEST_DISPLAY", "§aFarming Contest Display", false),
    ASTRONAUT_PERSONALITY("ASTRONAUT_PERSONALITY", "§fAstronaut Minion Skin", false),
    FAST_FOOD_BARN_SKIN("FAST_FOOD_BARN_SKIN", "§6Fast Food Barn Skin", false),
    JELLY_GREENHOUSE_SKIN("JELLY_GREENHOUSE_SKIN", "§6Jelly Garden Greenhouse Skin", false);

    public final String id, displayName, plain;
    // on in the warning list unless you turn it off
    public final boolean warnsAtFirst;

    VisitorReward(String id, String displayName, boolean warnsAtFirst) {
        this.id = id;
        this.displayName = displayName;
        this.plain = GameState.strip(displayName);
        this.warnsAtFirst = warnsAtFirst;
    }

    // this reward's option in the warning list
    public String optionKey() {
        return "garden_visitor_block.reward_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    // skyhanni goes from the line name to the item id and compares those, here we compare the line name
    // a rune and a potion say more than this list: "◆ Music Rune I", "Harvest Harbinger V Potion"
    public static VisitorReward byName(String colouredName) {
        String name = GameState.strip(colouredName).trim().replace("◆ ", "");
        for (VisitorReward r : values()) if (r.plain.equals(name)) return r;
        for (VisitorReward r : values()) if (name.startsWith(r.plain + " ")) return r;
        return null;
    }
}
