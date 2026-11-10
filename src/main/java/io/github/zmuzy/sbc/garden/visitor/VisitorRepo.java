// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.visitor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemIcons;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.garden.milestones.CropType;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// skyhanni's Garden.json for visitors (GardenVisitorColorNames): rarity, what they ask for, where they stand
// outside the garden
public final class VisitorRepo {
    public record Info(String name, String rarity, List<String> needItems, boolean unknownRewards, String mode, double[] position, String skinOrType, boolean showChatMessage) {}

    private static Map<String, Info> all;

    private VisitorRepo() {}

    public static Map<String, Info> all() {
        if (all != null) return all;
        Map<String, Info> out = new LinkedHashMap<>();
        try (var in = io.github.zmuzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in != null) {
                JsonObject visitors = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("visitors");
                for (Map.Entry<String, JsonElement> e : visitors.entrySet()) out.put(e.getKey(), info(e.getKey(), e.getValue().getAsJsonObject()));
            }
        } catch (Exception e) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("No visitor data in Garden.json", e);
        }
        all = out;
        return out;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static Info info(String name, JsonObject o) {
        List<String> needs = new ArrayList<>();
        if (o.has("need_items") && o.get("need_items").isJsonArray()) for (JsonElement i : o.getAsJsonArray("need_items")) needs.add(i.getAsString());
        double[] position = null;
        String[] at = text(o, "position").split(":");
        if (at.length == 3) {
            try {
                position = new double[]{Double.parseDouble(at[0]), Double.parseDouble(at[1]), Double.parseDouble(at[2])};
            } catch (NumberFormatException ignored) {
                // no place known
            }
        }
        boolean unknown = o.has("unknown_rewards") && o.get("unknown_rewards").getAsBoolean();
        boolean chat = o.has("show_chat_message") && o.get("show_chat_message").getAsBoolean();
        return new Info(name, text(o, "rarity"), needs, unknown, text(o, "mode"), position, text(o, "skinOrType").replace("\\n", "").replace("\n", ""), chat);
    }

    // LorenzRarity chat colours
    public static String color(String rarity) {
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare" -> "§9";
            case "legendary" -> "§6";
            case "mythic" -> "§d";
            case "special" -> "§c";
            default -> "§f";
        };
    }

    // GardenVisitorColorNames.getColoredName
    public static String colored(String plainName) {
        Info info = all().get(GameState.strip(plainName));
        return info == null ? plainName : color(info.rarity()) + plainName;
    }

    // icon of something a visitor asks for, or null
    public static ItemStack icon(String item) {
        CropType crop = CropType.getByNameOrNull(item);
        if (crop != null) return LegacyItems.stack(crop.icon());
        String id = ItemRepo.idForName(item);
        return id == null ? null : ItemIcons.of(id);
    }
}
