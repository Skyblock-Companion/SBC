// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.item;

import io.muzy.sbc.core.AuctionPrices;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Prices;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// whats inside a container item, on its tooltip: backpack items (+ total value), what a personal compactor
// compacts / deletor deletes
// all from the items own data, nothing asked from the server
public final class Previews {
    private Previews() {}

    private static CompoundTag extra(ItemStack st) {
        var cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    // personal compactor / deletor: the configured items
    public static void compactor(ItemStack st, List<Component> lines) {
        String id = Gain.sbId(st);
        if (!id.startsWith("PERSONAL_COMPACTOR") && !id.startsWith("PERSONAL_DELETOR")) return;
        CompoundTag ea = extra(st);
        boolean deletor = id.startsWith("PERSONAL_DELETOR");
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String key = (deletor ? "personal_deletor_" : "personal_compact_") + i;
            String v = ea.getStringOr(key, "");
            if (!v.isEmpty()) items.add(ItemRepo.name(v));
        }
        if (items.isEmpty()) return;
        lines.add(Component.literal(""));
        lines.add(Component.literal("§7" + (deletor ? "Deletes:" : "Compacts:")));
        for (String row : rows(items, 34)) lines.add(Component.literal(" " + row));
    }

    // names a few to a line, no wider than max letters, so the tooltip stays narrow
    static List<String> rows(List<String> names, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        int len = 0;
        for (String n : names) {
            if (len > 0 && len + 2 + n.length() > max) {
                out.add(row.toString());
                row.setLength(0);
                len = 0;
            }
            if (len > 0) {
                row.append("§7, ");
                len += 2;
            }
            row.append("§f").append(n);
            len += n.length();
        }
        if (len > 0) out.add(row.toString());
        return out;
    }

    // backpack: items inside + their value
    public static void backpack(ItemStack st, List<Component> lines) {
        CompoundTag ea = extra(st);
        byte[] data = null;
        for (String k : ea.keySet()) if (k.endsWith("backpack_data") || k.equals("new_year_cake_bag_data")) {
            data = ea.getByteArray(k).orElse(null);
            break;
        }
        if (data == null || data.length == 0) return;
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
            ListTag items = root.getListOrEmpty("i");
            Map<String, Integer> counts = new LinkedHashMap<>();
            double total = 0;
            int stacks = 0;
            for (int i = 0; i < items.size(); i++) {
                CompoundTag it = items.getCompoundOrEmpty(i);
                if (it.isEmpty()) continue;
                CompoundTag tag = it.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
                String sid = tag.getStringOr("id", "");
                int count = it.getByteOr("Count", (byte) 1);
                if (sid.isEmpty()) continue;
                stacks++;
                counts.merge(ItemRepo.name(sid), count, Integer::sum);
                double v = AuctionPrices.value(sid);
                if (v > 0) total += v * count;
            }
            if (stacks == 0) return;
            lines.add(Component.literal(""));
            lines.add(Component.literal("§7Inside: §f" + stacks + " stacks" + (total > 0 ? "  §6" + Prices.coins(total) : "")));
            int shown = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (shown++ >= 8) {
                    lines.add(Component.literal("§8  ... and " + (counts.size() - 8) + " more"));
                    break;
                }
                lines.add(Component.literal("§8  " + e.getValue() + "x §7" + e.getKey()));
            }
        } catch (Exception ignored) {
        }
    }
}
