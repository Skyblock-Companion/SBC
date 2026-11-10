// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni MagicalPowerDisplay: how much mp an accessory is worth, on its icon in the accessory bag and ah
// hegemony counts double, rift prism is 11, an abicase adds half your abiphone contacts
public final class MagicalPowerDisplay {
    public static final String ID = "magical_power";
    private static final Patterns.P ACCEPTED = Patterns.p("SH.inv.acceptable");
    private static final Patterns.P ABIPHONE = Patterns.p("SH.data.abiphone.name");
    private static final Patterns.P CONTACTS = Patterns.p("SH.data.abiphone.contacts");
    // the rarity line at the end of the lore: "LEGENDARY ACCESSORY", with "a " in front on a recombobulated items
    // obfuscated letters
    private static final Pattern RARITY_LINE = Pattern.compile("(?:\\S )?(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL) (?:DUNGEON )?(ACCESSORY|HATCESSORY)(?: \\S)?");
    private static final Map<String, Integer> MP = Map.of("COMMON", 3, "SPECIAL", 3, "UNCOMMON", 5, "VERY SPECIAL", 5, "RARE", 8, "EPIC", 12, "LEGENDARY", 16, "MYTHIC", 22);
    private static final Map<String, String> COLOR = Map.of("COMMON", "§f", "SPECIAL", "§c", "UNCOMMON", "§a", "VERY SPECIAL", "§c", "RARE", "§9", "EPIC", "§5", "LEGENDARY", "§6", "MYTHIC", "§d");
    private static final Map<ItemStack, String> CACHE = new WeakHashMap<>();
    private static String cachedTitle = "";
    private static boolean titleOk;

    private MagicalPowerDisplay() {}

    // skyhanni toMP with the 3 exceptions, -1 when the rarity has no power
    static int power(String rarity, String id, int contacts) {
        Integer mp = MP.get(rarity);
        if (mp == null) return -1;
        if (id.equals("HEGEMONY_ARTIFACT")) return mp * 2;
        if (id.equals("RIFT_PRISM")) return 11;
        return id.startsWith("ABICASE_") ? mp + contacts / 2 : mp;
    }

    static String rarityOf(List<String> lore) {
        // from the bottom bc the ah writes its own lines below it
        for (int i = lore.size() - 1; i >= 0; i--) {
            Matcher m = RARITY_LINE.matcher(GameState.strip(lore.get(i)).trim());
            if (m.matches()) return m.group(1);
        }
        return null;
    }

    // what to draw instead of the stack count, or null
    public static String of(ItemStack stack) {
        if (!Features.on(ID) || "THE_RIFT".equals(GameState.island)) return null;
        var screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return null;
        String title = screen.getTitle().getString();
        if (!title.equals(cachedTitle)) {
            cachedTitle = title;
            titleOk = ACCEPTED.matcher(GameState.strip(title)).matches();
            CACHE.clear();
        }
        if (!titleOk) return null;
        if (CACHE.containsKey(stack)) return CACHE.get(stack);
        String text = null;
        String rarity = rarityOf(LegacyText.lore(stack));
        String id = Gain.sbId(stack);
        if (rarity != null && !id.isEmpty()) {
            int mp = power(rarity, id, Store.I.abiphoneContacts);
            Feature f = Features.get(ID);
            Option colored = f == null ? null : f.option("magical_power.colored");
            if (mp >= 0) text = (colored != null && colored.bool() ? COLOR.getOrDefault(rarity, "§7") : "§7") + mp;
        }
        CACHE.put(stack, text);
        return text;
    }

    // skyhanni onInventoryFullyOpened, from Markets tick: the abiphones book says how many contacts you have
    public static void onMenu(String title, ChestMenu menu, int n) {
        if (!Features.on(ID) || n <= 51 || !ABIPHONE.matcher(title).matches()) return;
        for (String line : LegacyText.lore(menu.slots.get(51).getItem())) {
            Matcher m = CONTACTS.matcher(GameState.strip(line));
            if (m.matches()) {
                Store.I.abiphoneContacts = Integer.parseInt(m.group("contacts"));
                Store.markDirty();
                return;
            }
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Magical Power on accessories", "How much Magical Power an accessory gives, on its icon, in the Accessory Bag and the Auction House.", "SkyHanni: magical power display")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("magical_power.colored", "In the rarity's colour", "", false)));
    }
}
