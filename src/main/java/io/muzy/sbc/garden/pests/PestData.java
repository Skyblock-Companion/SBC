// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.pests;

import io.muzy.sbc.garden.milestones.CropType;

// from skyhanni PestType + VinylType, as much as stereo harmony and the kill line need
public enum PestData {
    BEETLE("Beetle", "PEST_BEETLE_MONSTER", "Not Just a Pest", "VINYL_BEETLE", CropType.NETHER_WART),
    CRICKET("Cricket", "PEST_CRICKET_MONSTER", "Cricket Choir", "VINYL_CRICKET_CHOIR", CropType.CARROT),
    EARTHWORM("Earthworm", "PEST_EARTHWORM_MONSTER", "Earthworm Ensemble", "VINYL_EARTHWORM_ENSEMBLE", CropType.MELON),
    FIELD_MOUSE("Field Mouse", "PEST_FIELD_MOUSE_MONSTER", null, null, null),
    FLY("Fly", "PEST_FLY_MONSTER", "Pretty Fly", "VINYL_PRETTY_FLY", CropType.WHEAT),
    LOCUST("Locust", "PEST_LOCUST_MONSTER", "Cicada Symphony", "VINYL_CICADA_SYMPHONY", CropType.POTATO),
    LUNAR_MOTH("Lunar Moth", "PEST_LUNAR_MOTH_MONSTER", null, null, null),
    MITE("Mite", "PEST_MITE_MONSTER", "DynaMITES", "VINYL_DYNAMITES", CropType.CACTUS),
    MOSQUITO("Mosquito", "PEST_MOSQUITO_MONSTER", "Buzzin' Beats", "VINYL_BUZZIN_BEATS", CropType.SUGAR_CANE),
    MOTH("Moth", "PEST_MOTH_MONSTER", "Wings of Harmony", "VINYL_WINGS_OF_HARMONY", CropType.COCOA_BEANS),
    RAT("Rat", "PEST_RAT_MONSTER", "Rodent Revolution", "VINYL_RODENT_REVOLUTION", CropType.PUMPKIN),
    SLUG("Slug", "PEST_SLUG_MONSTER", "Slow and Groovy", "VINYL_SLOW_AND_GROOVY", CropType.MUSHROOM),
    PRAYING_MANTIS("Praying Mantis", "PEST_PRAYING_MANTIS_MONSTER", "Pray For Me", "VINYL_PRAY_FOR_ME", CropType.WILD_ROSE),
    FIREFLY("Firefly", "PEST_FIREFLY_MONSTER", "Firefly in the Hole", "VINYL_FIREFLY", CropType.MOONFLOWER),
    DRAGONFLY("Dragonfly", "PEST_DRAGONFLY_MONSTER", "Imagine Dragonflies", "VINYL_IMAGINE_DRAGONFLIES", CropType.SUNFLOWER);

    public final String pest, headId, vinyl, vinylId;
    public final CropType crop;

    PestData(String pest, String headId, String vinyl, String vinylId, CropType crop) {
        this.pest = pest;
        this.headId = headId;
        this.vinyl = vinyl;
        this.vinylId = vinylId;
        this.crop = crop;
    }

    // VinylType.getByName
    public static PestData byVinyl(String name) {
        for (PestData p : values()) if (name.equals(p.vinyl)) return p;
        return null;
    }

    public static PestData byVinylId(String id) {
        for (PestData p : values()) if (id.equals(p.vinylId)) return p;
        return null;
    }

    public static PestData byName(String name) {
        for (PestData p : values()) if (p.pest.equals(name)) return p;
        return null;
    }
}
