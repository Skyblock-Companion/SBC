// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.market;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.calc.SignCalculator;
import dev.zmuzy.sbc.mc.Interactions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// extra stuff for the bazaar + super craft signs on top of the calculator
// from skyblocker BazaarMax (`max `, `m ` or `x ` in the quantity sign = biggest order you can make)
// skyblocker BazaarQuickQuantities (buttons with your amounts + the clipboard if its a short number),
// skyhanni SuperCraftPresets (buttons in the "of crafts" sign)
public final class BazaarSign {
    private static final String BUY_ORDER_QUANTITY = "Buy Order Quantity", CLICK_TO_SPECIFY = "Click to specify!";
    private static final Pattern MAX_QUANTITY_PATTERN = Pattern.compile("Buy up to ([0-9,]+)x\\.");
    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String TO_ORDER = "to order";
    private static int lastSeenMax = -1;

    private BazaarSign() {}

    private static Option opt(String feature, String key) {
        Feature f = Features.get(feature);
        return f == null ? null : f.option(key);
    }

    private static boolean b(String feature, String key, boolean def) {
        Option o = opt(feature, key);
        return o == null ? def : o.bool();
    }

    private static int i(String key, int def) {
        Option o = opt(Market.BAZAAR, key);
        return o == null ? def : o.integer();
    }

    // skyblocker BazaarMax.checkMaxValue on click: custom amount button says how many you could order
    static void checkMaxValue(List<String> cleanLore) {
        boolean hasBuyOrderQuantity = false, hasClickToSpecify = false;
        Matcher maxQuantityMatcher = null;
        for (String line : cleanLore) {
            if (BUY_ORDER_QUANTITY.equals(line)) {
                hasBuyOrderQuantity = true;
                continue;
            }
            if (CLICK_TO_SPECIFY.equals(line)) {
                hasClickToSpecify = true;
                continue;
            }
            Matcher matcher = MAX_QUANTITY_PATTERN.matcher(line);
            if (matcher.matches()) maxQuantityMatcher = matcher;
        }
        if (hasBuyOrderQuantity && hasClickToSpecify && maxQuantityMatcher != null) {
            try {
                lastSeenMax = Integer.parseInt(maxQuantityMatcher.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    static int lastSeenMax() {
        return lastSeenMax;
    }

    // how much of the line to delete before the number goes in, 0 = nothing to expand
    static int expandLength(String currentLine) {
        return switch (currentLine) {
            case "max " -> 4;
            case "m ", "x " -> 2;
            default -> 0;
        };
    }

    // skyblocker BazaarMax.expandMax, from the mixins charTyped when a space got typed
    public static void expandMax(TextFieldHelper signField, String[] messages, int line) {
        if (!SignCalculator.inSkyBlock() || !Features.on(Market.BAZAAR) || !b(Market.BAZAAR, "market_bazaar.max", true)) return;
        if (!SignCalculator.isInputSign(messages) || !messages[3].equals(TO_ORDER) || lastSeenMax < 0) return;
        int n = expandLength(messages[line]);
        if (n == 0) return;
        signField.removeCharsFromCursor(-n);
        signField.insertText(Integer.toString(lastSeenMax));
    }

    // from skyblocker SignUtils.isSupercraftAmountSetSign
    static boolean isSupercraftAmountSign(String[] messages) {
        return messages[1].equals("^^^^^^") && messages[2].equals("Enter amount") && messages[3].equals("of crafts");
    }

    // skyblocker BazaarQuickQuantities: only offer a short number from the clipboard
    static String clipboardAmount(String clipboard) {
        String s = clipboard.replace(",", "");
        return s.length() <= 5 && NUMERIC.matcher(s).matches() ? s : null;
    }

    // saved presets "4,8,16", skyhanni SuperCraftPresets list. non numbers get skipped
    static List<Integer> presets(String text) {
        List<Integer> out = new ArrayList<>();
        for (String p : text.split(",")) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v >= 1) out.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static void pressed(String[] messages, String value, boolean close, Runnable onClose) {
        messages[0] = value;
        if (close) {
            SignCalculator.calculate(value); // skyblocker BazaarQuickQuantities: avoid an onDone clash with the calculator
            onClose.run();
        }
    }

    // buttons for this sign, from the mixins init
    public static List<Button> buttons(int width, String[] messages, Runnable onClose) {
        List<Button> out = new ArrayList<>();
        if (!SignCalculator.inSkyBlock()) return out;
        int x = width / 2 + 50, y = 45;
        if (Features.on(Market.BAZAAR) && SignCalculator.isInputSign(messages) && messages[3].equals(TO_ORDER) && b(Market.BAZAAR, "market_bazaar.quickQuantities", false)) {
            boolean close = b(Market.BAZAAR, "market_bazaar.quickClose", false);
            int[] amounts = {i("market_bazaar.quick1", 28), i("market_bazaar.quick2", 2240), i("market_bazaar.quick3", 256)};
            for (int k = 0; k < amounts.length; k++) {
                String v = String.valueOf(amounts[k]);
                out.add(Button.builder(Component.literal(v), b -> pressed(messages, v, close, onClose)).bounds(x, y + 20 + k * 20, 50, 20).build());
            }
            String clipboard = clipboardAmount(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (clipboard != null) out.add(Button.builder(Component.literal(clipboard).withStyle(ChatFormatting.AQUA), b -> pressed(messages, clipboard, close, onClose)).bounds(x, y + 80, 50, 20).build());
        }
        if (Features.on(SuperCraft.ID) && b(SuperCraft.ID, "super_craft.presets", true) && isSupercraftAmountSign(messages)) {
            Option o = opt(SuperCraft.ID, "super_craft.presetList");
            List<Integer> presets = presets(o == null ? "" : o.text());
            int perRow = 4, w = 40, h = 18, left = width / 2 - (perRow * (w + 3) - 3) / 2, top = 16;
            for (int k = 0; k < presets.size(); k++) {
                String v = String.valueOf(presets.get(k));
                out.add(Button.builder(Component.literal(String.format(java.util.Locale.ROOT, "%,d", presets.get(k))), b -> messages[0] = v)
                        .bounds(left + k % perRow * (w + 3), top + k / perRow * (h + 2), w, h).build());
            }
        }
        return out;
    }

    public static void register() {
        Interactions.onSlotClick((screen, slot, slotId, button) -> {
            if (slot == null || !SignCalculator.inSkyBlock()) return false;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) return false;
            List<String> clean = new ArrayList<>();
            for (String l : dev.zmuzy.sbc.core.LegacyText.lore(st)) clean.add(GameState.strip(l));
            checkMaxValue(clean);
            return false;
        });
    }
}
