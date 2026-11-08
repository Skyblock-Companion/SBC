// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// in armor sets and equipment sets a key equips (or unequips) that wardrobe slot, in loadouts it picks that
// loadout. same click on the same button, just from a key
// key name drawn on each button
// menus, spots and when a press counts are from skyblocker WardrobeKeybinds + LoadoutKeybinds, with skyhannis
// 200 ms between presses (CustomLoadoutKeybinds, CustomWardrobeKeybinds)
// the 12 keys are in the games controls
public final class WardrobeKeys {
    private static final Pattern WARDROBE = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?(Armor Sets|Equipment Sets)$");
    private static final Pattern LOADOUTS = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?(Loadouts)$");
    private static final Ui ui = new Ui();
    private static final int[] DEFAULTS = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL};
    private static final net.minecraft.client.KeyMapping[] KEYS = new net.minecraft.client.KeyMapping[12];
    private static long lastClick;
    private WardrobeKeys() {}

    // 12 keys like skyblockers: 1-9, 0, minus, equals. only do anything inside these menus
    public static void registerKeys(net.minecraft.client.KeyMapping.Category cat) {
        for (int i = 0; i < KEYS.length; i++)
            KEYS[i] = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new net.minecraft.client.KeyMapping(String.format(Locale.ROOT, "key.sbcompanion.loadout_%02d", i + 1), com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, DEFAULTS[i], cat));
    }

    private static String title(Screen s) {
        return GameState.strip(s.getTitle().getString()).trim();
    }

    private static boolean applies(Screen s) {
        if (!Features.on("wardrobe_keys") || !(s instanceof ContainerScreen)) return false;
        String t = title(s);
        return WARDROBE.matcher(t).matches() || LOADOUTS.matcher(t).matches();
    }

    private static String path(ItemStack st) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // skyblocker LoadoutKeybinds.isItemInvalid: not customized, not unlocked, or not there
    private static boolean loadoutInvalid(ItemStack st) {
        String p = path(st);
        return st.isEmpty() || p.equals("gray_dye") || p.equals("red_dye") || p.equals("black_stained_glass_pane");
    }

    // slot a keys number presses here, -1 for none. locked or empty stuff stays unpressed
    private static int slotFor(ContainerScreen cs, int index) {
        var menu = cs.getMenu();
        if (LOADOUTS.matcher(title(cs)).matches()) {
            if (index >= dev.zmuzy.sbc.ui.panels.Loadouts.ICONS.length) return -1;
            int slot = dev.zmuzy.sbc.ui.panels.Loadouts.ICONS[index];
            return slot < menu.slots.size() && !loadoutInvalid(menu.getSlot(slot).getItem()) ? slot : -1;
        }
        // pieces fill the top 4 rows, buttons start on the 5th. only the swap dyes are buttons
        if (index >= 9) return -1;
        int slot = 9 * 4 + index;
        if (slot >= menu.slots.size()) return -1;
        String p = path(menu.getSlot(slot).getItem());
        return p.equals("gray_dye") || p.equals("lime_dye") ? slot : -1;
    }

    // false = eat the key
    public static boolean key(Screen s, net.minecraft.client.input.KeyEvent event) {
        if (!applies(s)) return true;
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] == null || !KEYS[i].matches(event)) continue;
            return !press((ContainerScreen) s, i);
        }
        return true;
    }

    // which of the 12 this key is, -1 for none
    public static int keyIndex(net.minecraft.client.input.KeyEvent event) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i] != null && KEYS[i].matches(event)) return i;
        return -1;
    }

    // same keys bound to a mouse button. false = eat it
    public static boolean mouse(Screen s, net.minecraft.client.input.MouseButtonEvent event) {
        if (!applies(s)) return true;
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] == null || !KEYS[i].matchesMouse(event)) continue;
            return !press((ContainerScreen) s, i);
        }
        return true;
    }

    private static boolean press(ContainerScreen cs, int index) {
        int slot = slotFor(cs, index);
        if (slot < 0) return false;
        long now = System.currentTimeMillis();
        if (now - lastClick < 200) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        lastClick = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    private static String keyName(int index) {
        return KEYS[index] == null ? String.valueOf(index + 1) : KEYS[index].getTranslatedKeyMessage().getString();
    }

    // key hints under the buttons
    public static void draw(Screen s, GuiGraphicsExtractor g) {
        if (!applies(s)) return;
        ContainerScreen cs = (ContainerScreen) s;
        var acc = (dev.zmuzy.sbc.mc.mixin.ContainerAccess) cs;
        int left = acc.sbc$left(), top = acc.sbc$top();
        ui.begin(g, -1, -1);
        paintLegend(ui, left, top, acc.sbc$width());
        for (int i = 0; i < KEYS.length; i++) {
            int slot = slotFor(cs, i);
            if (slot < 0) continue;
            var sl = cs.getMenu().slots.get(slot);
            String k = keyName(i);
            paintKey(ui, left + sl.x, top + sl.y, k, GameState.strip(sl.getItem().getHoverName().getString()));
        }
    }

    // what the buttons own name says, as a color: worn = green, nothing to press = grey
    private static int stateColor(String buttonName) {
        String n = buttonName.toLowerCase(Locale.ROOT);
        if (n.contains("equipped") && !n.contains("unequipped")) return Modern.GOOD;
        if (n.contains("empty") || n.contains("locked")) return Modern.DIM;
        return Modern.GOLD;
    }

    // key cap in the corner of the 16 px button here
    public static void paintKey(Ui ui, int slotX, int slotY, int key, String buttonName) {
        paintKey(ui, slotX, slotY, String.valueOf(key), buttonName);
    }

    // same but with the key name like the controls show it
    public static void paintKey(Ui ui, int slotX, int slotY, String key, String buttonName) {
        int c = stateColor(buttonName), tw = ui.width(key), w = tw + 2, x = slotX + 16 - w, y = slotY + 7, h = 9;
        ui.rect(x, y, w, h, 0xE80A0F17);
        ui.rect(x, y + h - 1, w, 1, c);
        ui.textShadow(key, x + 1, y, Modern.lighten(c, 0.2f));
    }

    // says once what the numbers are, at the end of the menu title line
    public static void paintLegend(Ui ui, int left, int top, int cw) {
        String text = "§eKeys§7 equip";
        int w = ui.width(text) + 6, x = left + cw - 7 - w, y = top + 4;
        ui.rect(x, y, w, 11, 0xE80A0F17);
        ui.rect(x, y + 10, w, 1, Ui.alpha(Modern.GOLD, 0.6f));
        ui.textShadow(text, x + 3, y + 1, Modern.LABEL);
    }

    static Matcher m(String s) {
        return WARDROBE.matcher(s);
    }

    static Matcher loadouts(String s) {
        return LOADOUTS.matcher(s);
    }
}
