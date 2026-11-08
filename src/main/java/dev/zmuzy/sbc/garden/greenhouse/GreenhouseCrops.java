// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.greenhouse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// from skyblocker GreenhouseCrops: crops a greenhouse grid holds, with the id layout sites use, the word in
// the name of the head the stand wears, and the block for crops that are plain blocks
// head textures are skyblocker's HeadTextures, only for the crops that need them to be told apart
public final class GreenhouseCrops {
    private GreenhouseCrops() {}

    public record Crop(String name, String standName, int id, String block, String specialTexture) {}

    private static final Map<String, String> TEXTURE = Map.of(
            "HELIANTHUS", "ewogICJ0aW1lc3RhbXAiIDogMTc2MDk4MzE2ODM3NiwKICAicHJvZmlsZUlkIiA6ICI2NmRmYzFmNTRlNTU0ZTZmODJjNTA5ZjM1NTJiYTkwZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJadWFyaWciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTk4YWM5MjI0NDkxYTlkZDQ4MDUzMWJkOTcxNTkxNzI0Y2UyOTkyMWVjOGYxNDE0NjEyNzZmMWJmM2E1OWVkMyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "FERMENTO", "ewogICJ0aW1lc3RhbXAiIDogMTY2MjUwMjkxNjU1MiwKICAicHJvZmlsZUlkIiA6ICIwOTZkYWUzZWY1MmU0YWU4ODk3ODY2N2EyOGIwZWFhNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJHd0FiaXlCZ2dnIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2NiNDFkYWViNTdkMmFlNjJjNjZlNThlYjZkZWJiMmE3ZDQ0NmUzNDU0MWE3NzEzNTA3MjhjOWRiMTViZWFmYmEiCiAgICB9CiAgfQp9",
            "SQUASH", "ewogICJ0aW1lc3RhbXAiIDogMTY2MjUwMjk0ODA3NCwKICAicHJvZmlsZUlkIiA6ICI2ODAwNzFmZTIxOWE0OWNmYmM3MDIxMjc1NTI0NDIwMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZXphYm95ODAiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzZhZTA3NjY0OWVmMjJmNjBlODUxMTgzMWM2OGZkMmI2ZWE2M2MzMjE2NGRhYjMzYThhZWJjMThmZjJhNTRjOCIKICAgIH0KICB9Cn0",
            "CROPIE", "ewogICJ0aW1lc3RhbXAiIDogMTY2MjUwMjkyOTIyNCwKICAicHJvZmlsZUlkIiA6ICJjOWRlZTM4MDUzYjg0YzI5YjZlZjA5YjJlMDM5OTc0ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJTQVJfRGVjZW1iZXI1IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2RkMDFjYmEyM2VkZTJjZDI4OTUxMDdmMGMwMjU4ZTk3MWQyNDg1NTM4ZmU5NjQ5ZWYyODUzYmQyNmU2MjMyZGMiCiAgICB9CiAgfQp9",
            "RED_MUSHROOM", "ewogICJ0aW1lc3RhbXAiIDogMTc0NzYxNTM0MDQ2MCwKICAicHJvZmlsZUlkIiA6ICI5ZDYzZjMxMGNiZWE0MWI1YmQ4YTM4ZmExYTBiMDI4MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTa3libHVlZm94dGFpbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xNjJmMGU4ZDMyZjNkYjAyZmZkZjFhMTZlZmIyMWQ2MTRhM2YwNDE3ZDQ5MTM4ZmRjMThiZmU1MmVhZDcwNWI5IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
            "BROWN_MUSHROOM", "ewogICJ0aW1lc3RhbXAiIDogMTc0NzYxNTMzNjg5NCwKICAicHJvZmlsZUlkIiA6ICI1ZTEwYjc5MTY2ZTA0MTNkYjI2MThkMzE3MTc2M2Y4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJPTUZfQmxvY2tCdXN0ZXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzAxOTk5MmI1ZDQ0MGY4NWQyYjA1MTQ4YWE5Yjg1ZjQ1MDk4NWQ1ZjE2YWU5NjBkMWNkYjMyZTA2ZTNjODk2ZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");

    public static final List<Crop> ALL = List.of(
            new Crop("Ashwreath", "ashwreath", 1, null, null),
            new Crop("Choconut", "Choconut", 2, null, null),
            new Crop("Dustgrain", "Dustgrain", 3, null, null),
            new Crop("Gloomgourd", "Gloomgourd", 4, null, null),
            new Crop("Lonelily", "Lonelilly", 5, null, null),
            new Crop("Scourroot", "Scourroot", 6, null, null),
            new Crop("Shadevine", "shadevine", 7, null, null),
            new Crop("Veilshroom", "Veilshroom", 8, null, null),
            new Crop("Witherbloom", "Witherbloom", 9, null, null),
            new Crop("Chocoberry", "Chocoberry", 10, null, null),
            new Crop("Cindershade", "Cindershade", 11, null, null),
            new Crop("Coalroot", "Coalroot", 12, null, null),
            new Crop("Creambloom", "Creambloom", 13, null, null),
            new Crop("Duskbloom", "duskbloom", 14, null, null),
            new Crop("Thornshade", "thornshade", 15, null, null),
            new Crop("Blastberry", "blastberry", 16, null, null),
            new Crop("Cheesebite", "cheesebite", 17, null, null),
            new Crop("Chloronite", "chloronite", 18, null, null),
            new Crop("Do Not Eat Shroom", "donoteatshroom", 19, null, null),
            new Crop("Fleshtrap", "fleshtrap", 20, null, null),
            new Crop("Magic Jellybean", "magicjellybean", 21, null, null),
            new Crop("Noctilume", "noctilume", 22, null, null),
            new Crop("Snoozling", "snoozlingFlower", 23, null, null),
            new Crop("Soggybud", "soggybud", 24, null, null),
            new Crop("Chorus Fruit", "chorusFruit", 25, null, null),
            new Crop("PlantBoy Advance", "Plantboy", 26, null, null),
            new Crop("Puffercloud", "puffercloud", 27, null, null),
            new Crop("Shellfruit", "shellfruit", 28, null, null),
            new Crop("Startlevine", "startlevine", 29, null, null),
            new Crop("Stoplight Petal", "stoplightpetal", 30, null, null),
            new Crop("Thunderling", "thunderling", 31, null, null),
            new Crop("Turtlellini", "turtlellini", 32, null, null),
            new Crop("Zombud", "zombud", 33, null, null),
            new Crop("All In Aloe", "allinaloe", 34, null, null),
            new Crop("Devourer", "devourer", 35, null, null),
            new Crop("Glasscorn", "glasscorn", 36, null, null),
            new Crop("Godseed", "godseed", 37, null, null),
            new Crop("Jerryflower", "jerryseed", 38, null, null),
            new Crop("Phantomleaf", "phantomleaf", 39, null, null),
            new Crop("Timestalk", "timestalk", 40, null, null),
            new Crop("Cocoa Beans", "coco", 41, null, null),
            new Crop("Wild Rose", "wildrose", 42, null, null),
            new Crop("Moonflower", "moonflower", 43, null, null),
            new Crop("Sunflower", "sunflower", 44, null, null),
            new Crop("Cactus", "cactus", 45, null, null),
            new Crop("Melon Seeds", "melon", 46, null, null),
            new Crop("Pumpkin Seeds", "pumpkin", 47, null, null),
            new Crop("Brown Mushroom", "brownmushroom", 48, null, TEXTURE.get("BROWN_MUSHROOM")),
            new Crop("Red Mushroom", "redmushroom", 49, null, TEXTURE.get("RED_MUSHROOM")),
            new Crop("Wheat Seeds", "wheat", 50, "wheat", null),
            new Crop("Carrot", "carrot", 51, "carrots", null),
            new Crop("Potato", "potato", 52, "potatoes", null),
            new Crop("Nether Wart", "netherwart", 53, "nether_wart", null),
            new Crop("Sugar Cane", "sugarcane", 54, "sugar_cane", null),
            new Crop("Dead Plants", "deadplant", 55, "dead_bush", null),
            new Crop("Fire", "fire", 56, "fire", null),
            new Crop("Helianthus", "helianthus", 57, null, TEXTURE.get("HELIANTHUS")),
            new Crop("Fermento", "fermento", 58, null, TEXTURE.get("FERMENTO")),
            new Crop("Squash", "squash", 59, null, TEXTURE.get("SQUASH")),
            new Crop("Cropie", "cropie", 60, null, TEXTURE.get("CROPIE")));

    public static final Map<String, Crop> BY_NAME = new HashMap<>();
    public static final Map<Integer, Crop> BY_ID = new HashMap<>();
    public static final Map<String, Crop> BY_TEXTURE = new HashMap<>();

    static {
        for (Crop c : ALL) {
            BY_NAME.put(c.name(), c);
            BY_ID.put(c.id(), c);
            if (c.specialTexture() != null) BY_TEXTURE.put(c.specialTexture(), c);
        }
    }

}
