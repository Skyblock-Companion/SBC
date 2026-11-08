// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.item.custom.CustomLooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// how the held item looks, one screen. from skyblocker item/custom/screen (CustomizeScreen, ItemTab, ArmorTab
// and the rest)
public final class CustomLooksScreen extends Screen {
    private static final int W = 240, ROW = 24;

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private String uuid;
    private String problem = "";
    private String from = "FFFFFF", to = "000000", seconds = "2";
    private boolean cycleBack = true;

    private record Label(String text, int x, int y) {}

    public CustomLooksScreen(Screen parent) {
        super(Component.literal("Custom item looks"));
        this.parent = parent;
    }

    private ItemStack held() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
    }

    private void reopen() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        labels.clear();
        problem = "";
        uuid = CustomLooks.heldUuid(held(), s -> problem = s);
        int x = (width - W) / 2 + 40, y = 30;
        if (uuid == null) {
            labels.add(new Label("§c" + problem, x, y));
            button("Done", x, height - 28, 80, this::onClose);
            return;
        }
        String id = uuid;
        CustomLooks.Data d = CustomLooks.data();
        field("Name, codes with &", x, y, W, () -> d.names.getOrDefault(id, "").replace('§', '&'), v -> CustomLooks.setName(id, v));
        y += ROW;
        String[] trim = d.trims.get(id);
        field("Trim material, like minecraft:gold", x, y, W / 2 - 2, () -> trim == null ? "" : trim[0], v -> {
            String[] now = d.trims.get(id);
            CustomLooks.setTrim(id, v.isBlank() ? null : v, now == null ? "minecraft:coast" : now[1]);
        });
        field("Trim pattern, like minecraft:coast", x + W / 2 + 2, y, W / 2 - 2, () -> trim == null ? "" : trim[1], v -> {
            String[] now = d.trims.get(id);
            CustomLooks.setTrim(id, now == null ? null : now[0], v.isBlank() ? null : v);
        });
        y += ROW;
        Integer dye = d.dyes.get(id);
        field("Dye: RRGGBB", x, y, W / 2 - 2, () -> dye == null ? "" : String.format("%06X", dye), v -> {
            try {
                CustomLooks.setDye(id, v.isBlank() ? null : Integer.valueOf(v.trim().replace("#", ""), 16));
            } catch (NumberFormatException ignored) {
            }
        });
        field("Or a dye's name, like DYE_ROSE", x + W / 2 + 2, y, W / 2 - 2, () -> "", v -> {
            if (!v.isBlank()) CustomLooks.setRepoDye(id, v.trim().toUpperCase(java.util.Locale.ROOT));
        });
        y += ROW;
        labels.add(new Label("§7Animated dye: from, to, seconds", x, y + 6));
        y += 16;
        CustomLooks.AnimatedDye animated = d.animatedDyes.get(id);
        field("From", x, y, 56, () -> from, v -> from = v);
        field("To", x + 60, y, 56, () -> to, v -> to = v);
        field("Seconds", x + 120, y, 32, () -> seconds, v -> seconds = v);
        button(cycleBack ? "§aBack" : "§7Back", x + 156, y, 36, () -> {
            cycleBack = !cycleBack;
            reopen();
        });
        button(animated == null ? "Set" : "§cOff", x + 196, y, 44, () -> {
            if (d.animatedDyes.containsKey(id)) CustomLooks.setAnimatedDye(id, null);
            else setAnimated(id);
            reopen();
        });
        y += ROW;
        field("Item model, like minecraft:stick", x, y, W / 2 - 2, () -> d.models.getOrDefault(id, ""), v -> CustomLooks.setModel(id, v));
        field("Armor model, like minecraft:gold", x + W / 2 + 2, y, W / 2 - 2, () -> d.armorModels.getOrDefault(id, ""), v -> CustomLooks.setArmorModel(id, v));
        y += ROW;
        field("Head texture, or a SkyBlock item's id", x, y, W, () -> d.helmets.getOrDefault(id, ""), v -> CustomLooks.setHelmet(id, v));
        y += ROW;
        Boolean glint = d.glint.get(id);
        button("Glint: " + (glint == null ? "as it is" : glint ? "on" : "off"), x, y, W / 2 - 2, () -> {
            CustomLooks.setGlint(id, glint == null ? Boolean.TRUE : glint ? Boolean.FALSE : null);
            reopen();
        });
        button("§cClear all of it", x + W / 2 + 2, y, W / 2 - 2, () -> {
            CustomLooks.clear(id);
            reopen();
        });
        button("Done", x + W - 80, height - 28, 80, this::onClose);
    }

    private void setAnimated(String id) {
        try {
            int a = Integer.parseInt(from.trim().replace("#", ""), 16) & 0xFFFFFF, b = Integer.parseInt(to.trim().replace("#", ""), 16) & 0xFFFFFF;
            float s = Math.clamp(Float.parseFloat(seconds.trim()), 0.1f, 10f);
            CustomLooks.setAnimatedDye(id, new CustomLooks.AnimatedDye(List.of(new CustomLooks.Keyframe(a, 0), new CustomLooks.Keyframe(b, 1)), cycleBack, 0, s));
        } catch (NumberFormatException ignored) {
        }
    }

    private void button(String text, int x, int y, int w, Runnable run) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> run.run()).pos(x, y).size(w, 20).build());
    }

    private void field(String hint, int x, int y, int w, Supplier<String> get, Consumer<String> set) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(32767);
        box.setHint(Component.literal("§8" + hint));
        box.setValue(get.get());
        box.setResponder(set);
        addRenderableWidget(box);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        for (Label l : labels) g.text(font, l.text(), l.x(), l.y(), 0xFFFFFFFF, true);
        int x = (width - W) / 2 - 90;
        if (minecraft.player != null) InventoryScreen.extractEntityInInventoryFollowsMouse(g, x, 40, x + 110, height - 40, 45, 0.0625f, mouseX, mouseY, minecraft.player);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
