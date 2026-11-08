// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.combat;

// from skyhanni damageindicator/BossType: every boss the indicator knows, under the setting for its group
public enum BossType {
    NETHER_BLADESOUL("§8Bladesoul", Group.CRIMSON),
    NETHER_MAGMA_BOSS("§4Magma Boss", Group.CRIMSON),
    NETHER_ASHFANG("§cAshfang", Group.CRIMSON),
    NETHER_BARBARIAN_DUKE("§eBarbarian Duke", Group.CRIMSON),
    NETHER_MAGE_OUTLAW("§5Mage Outlaw", Group.CRIMSON),
    NETHER_VANQUISHER("§5Vanquisher", Group.CRIMSON),

    END_ENDSTONE_PROTECTOR("§cEndstone Protector", Group.END),
    END_ENDER_DRAGON("Dragon", Group.END),

    SLAYER_ZOMBIE_1("§aRevenant Horror 1", "§aRev 1", true),
    SLAYER_ZOMBIE_2("§eRevenant Horror 2", "§eRev 2", true),
    SLAYER_ZOMBIE_3("§cRevenant Horror 3", "§cRev 3", true),
    SLAYER_ZOMBIE_4("§4Revenant Horror 4", "§4Rev 4", true),
    SLAYER_ZOMBIE_5("§5Revenant Horror 5", "§5Rev 5", true),

    SLAYER_SPIDER_1("§aTarantula Broodfather 1", "§aTara 1", true),
    SLAYER_SPIDER_2("§eTarantula Broodfather 2", "§eTara 2", true),
    SLAYER_SPIDER_3("§cTarantula Broodfather 3", "§cTara 3", true),
    SLAYER_SPIDER_4("§4Tarantula Broodfather 4", "§4Tara 4", true),
    SLAYER_SPIDER_5_1("§5Tarantula Broodfather 5", "§5Tara 5", true),
    SLAYER_SPIDER_5_2("§5Tarantula Broodfather 5", "§5Tara 5", true),

    SLAYER_WOLF_1("§aSven Packmaster 1", "§aSven 1", true),
    SLAYER_WOLF_2("§eSven Packmaster 2", "§eSven 2", true),
    SLAYER_WOLF_3("§cSven Packmaster 3", "§cSven 3", true),
    SLAYER_WOLF_4("§4Sven Packmaster 4", "§4Sven 4", true),

    SLAYER_ENDERMAN_1("§aVoidgloom Seraph 1", "§aVoid 1", true),
    SLAYER_ENDERMAN_2("§eVoidgloom Seraph 2", "§eVoid 2", true),
    SLAYER_ENDERMAN_3("§cVoidgloom Seraph 3", "§cVoid 3", true),
    SLAYER_ENDERMAN_4("§4Voidgloom Seraph 4", "§4Void 4", true),

    SLAYER_BLAZE_1("§aInferno Demonlord 1", "§aInferno 1", true),
    SLAYER_BLAZE_2("§aInferno Demonlord 2", "§aInferno 2", true),
    SLAYER_BLAZE_3("§aInferno Demonlord 3", "§aInferno 3", true),
    SLAYER_BLAZE_4("§aInferno Demonlord 4", "§aInferno 4", true),

    SLAYER_BLAZE_TYPHOEUS_1("§aInferno Typhoeus 1", "§aTyphoeus 1", false),
    SLAYER_BLAZE_TYPHOEUS_2("§eInferno Typhoeus 2", "§eTyphoeus 2", false),
    SLAYER_BLAZE_TYPHOEUS_3("§cInferno Typhoeus 3", "§cTyphoeus 3", false),
    SLAYER_BLAZE_TYPHOEUS_4("§cInferno Typhoeus 4", "§cTyphoeus 4", false),

    SLAYER_BLAZE_QUAZII_1("§aInferno Quazii 1", "§aQuazii 1", false),
    SLAYER_BLAZE_QUAZII_2("§eInferno Quazii 2", "§eQuazii 2", false),
    SLAYER_BLAZE_QUAZII_3("§cInferno Quazii 3", "§cQuazii 3", false),
    SLAYER_BLAZE_QUAZII_4("§cInferno Quazii 4", "§cQuazii 4", false),

    SLAYER_BLOODFIEND_1("§aRiftstalker Bloodfiend 1", "§aBlood 1", true),
    SLAYER_BLOODFIEND_2("§6Riftstalker Bloodfiend 2", "§6Blood 2", true),
    SLAYER_BLOODFIEND_3("§cRiftstalker Bloodfiend 3", "§cBlood 3", true),
    SLAYER_BLOODFIEND_4("§4Riftstalker Bloodfiend 4", "§4Blood 4", true),
    SLAYER_BLOODFIEND_5("§5Riftstalker Bloodfiend 5", "§5Blood 5", true),

    HUB_HEADLESS_HORSEMAN("§6Headless Horseman", Group.DIANA),

    DUNGEON_F1_BONZO_FIRST("§cFunny Bonzo", Group.DUNGEON),
    DUNGEON_F1_BONZO_SECOND("§cSad Bonzo", Group.DUNGEON),
    DUNGEON_F2_SUMMON("§eSummon", Group.DUNGEON),
    DUNGEON_F2_SCARF("§cScarf", Group.DUNGEON),
    DUNGEON_F3_GUARDIAN("§eGuardian", Group.DUNGEON),
    DUNGEON_F3_PROFESSOR_1("§cProfessor 1/2", Group.DUNGEON),
    DUNGEON_F3_PROFESSOR_2("§cProfessor 2/2", Group.DUNGEON),
    DUNGEON_F4_THORN("§cThorn", Group.DUNGEON),
    DUNGEON_F5("§dLivid", Group.DUNGEON),
    DUNGEON_F6_GIANT_1("§eBoulder Tosser", "§eGiant 1", Group.DUNGEON),
    DUNGEON_F6_GIANT_2("§eSword Thrower", "§eGiant 2", Group.DUNGEON),
    DUNGEON_F6_GIANT_3("§eBigfoot Jumper", "§eGiant 3", Group.DUNGEON),
    DUNGEON_F6_GIANT_4("§eLaser Shooter", "§eGiant 4", Group.DUNGEON),
    DUNGEON_F6_SADAN("§cSadan", Group.DUNGEON),

    MINOS_INQUISITOR("§5Minos Inquisitor", Group.DIANA),
    MINOS_CHAMPION("§2Minos Champion", Group.DIANA),
    GAIA_CONSTRUCT("§2Gaia Construct", Group.DIANA),
    MINOTAUR("§2Minotaur", Group.DIANA),

    THUNDER("§cThunder", Group.SEA),
    LORD_JAWBUS("§cLord Jawbus", Group.SEA),
    RAGNAROK("§cRagnarok", Group.SEA),

    DUMMY("Dummy", Group.DUMMY),
    ARACHNE_SMALL("§cSmall Arachne", Group.SPIDERS),
    ARACHNE_BIG("§4Big Arachne", Group.SPIDERS),
    BROODMOTHER("§cBroodmother", Group.SPIDERS),

    LEECH_SUPREME("§cLeech Supreme", Group.RIFT),
    BACTE("§aBacte", Group.RIFT),
    SUN_GECKO("§cSun Gecko", Group.RIFT),

    WINTER_REINDRAKE("Reindrake", Group.SEA),

    GARDEN_PEST_BEETLE("§cBeetle", Group.PESTS),
    GARDEN_PEST_CRICKET("§cCricket", Group.PESTS),
    GARDEN_PEST_FLY("§cFly", Group.PESTS),
    GARDEN_PEST_LOCUST("§cLocust", Group.PESTS),
    GARDEN_PEST_MITE("§cMite", Group.PESTS),
    GARDEN_PEST_MOSQUITO("§cMosquito", Group.PESTS),
    GARDEN_PEST_MOTH("§cMoth", Group.PESTS),
    GARDEN_PEST_RAT("§cRat", Group.PESTS),
    GARDEN_PEST_SLUG("§cSlug", Group.PESTS),
    GARDEN_PEST_EARTHWORM("§cEarthworm", Group.PESTS),
    GARDEN_PEST_FIELD_MOUSE("§c§lField Mouse", Group.PESTS),
    GARDEN_PEST_PRAYING_MANTIS("§cPraying Mantis", Group.PESTS),
    GARDEN_PEST_FIREFLY("§cFirefly", Group.PESTS),
    GARDEN_PEST_DRAGONFLY("§cDragonfly", Group.PESTS),
    GARDEN_PEST_LUNAR_MOTH("§c§lLunar Moth", Group.PESTS);

    // skyhannis BossCategory, its 26 entries folded into one setting per kind of boss
    public enum Group {
        SLAYERS("bosses.slayers"), DUNGEON("bosses.dungeon"), CRIMSON("bosses.crimson"), END("bosses.end"), DIANA("bosses.diana"), SEA("bosses.sea"), SPIDERS("bosses.spiders"), RIFT("bosses.rift"), PESTS("bosses.pests"), DUMMY("bosses.dummy");

        public final String option;

        Group(String option) {
            this.option = option;
        }
    }

    public final String fullName, shortName;
    public final Group group;
    public final boolean slayer;

    BossType(String fullName, Group group) {
        this(fullName, fullName, group);
    }

    BossType(String fullName, String shortName, Group group) {
        this.fullName = fullName;
        this.shortName = shortName;
        this.group = group;
        this.slayer = false;
    }

    BossType(String fullName, String shortName, boolean slayer) {
        this.fullName = fullName;
        this.shortName = shortName;
        this.group = Group.SLAYERS;
        this.slayer = slayer;
    }
}
