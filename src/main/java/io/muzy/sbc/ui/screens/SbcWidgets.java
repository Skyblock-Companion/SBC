// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?}
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Theme;
import io.muzy.sbc.ui.layout.BaseUIComponent;
import io.muzy.sbc.ui.layout.ScrollContainer;
import io.muzy.sbc.ui.layout.CursorStyle;
import io.muzy.sbc.ui.layout.UIGraphics;
import io.muzy.sbc.ui.layout.Sizing;
import io.muzy.sbc.ui.layout.UIComponent;
import io.muzy.sbc.ui.layout.UISounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
//? if <26.3 {
/*import org.lwjgl.glfw.GLFW;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

// our own widgets: flat sharp buttons, switches, sliders, text fields in the theme. replaced wrappers around
// vanilla widgets that looked vanilla
public final class SbcWidgets {
    private SbcWidgets() {}

    private static Font font() {
        return Minecraft.getInstance().font;
    }


    private static int fill() {
        return 0xFF1F2A38;
    }

    private static int fillHover() {
        return 0xFF2B394B;
    }

    private static int border() {
        return 0xFF3C4A5C;
    }

    private static int borderHover() {
        return 0xFF7F93AB;
    }

    private static int groove() {
        return 0xFF0C121B;
    }

    private static int text() {
        return Theme.current().text() | 0xFF000000;
    }

    private static int dim() {
        return Theme.current().dim() | 0xFF000000;
    }

    private static void frame(UIGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    // click sound, nothing without a sound system (tests)
    public static void clickSound() {
        UISounds.playButtonSound();
    }

    // --- button ---

    public static Button button(Component message, Consumer<Button> onPress) {
        return new Button(message, onPress);
    }

    public static final class Button extends BaseUIComponent {
        private Component message;
        private final Consumer<Button> onPress;
        private int[] palette;

        private Button(Component message, Consumer<Button> onPress) {
            this.message = message;
            this.onPress = onPress;
            this.cursorStyle(CursorStyle.HAND);
        }

        public void setMessage(Component message) {
            this.message = message;
            this.notifyParentIfMounted();
        }

        // own colors instead of the plain theme button (menu primary + danger buttons)
        public Button palette(int fill, int fillHover, int border, int borderHover, int text) {
            this.palette = new int[]{fill, fillHover, border, borderHover, text};
            return this;
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return font().width(this.message) + 10;
        }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) {
            return 14;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean hv = this.hovered;
            if (this.palette != null) frame(g, x, y, width, height, this.palette[hv ? 1 : 0], this.palette[hv ? 3 : 2]);
            else frame(g, x, y, width, height, hv ? fillHover() : fill(), hv ? borderHover() : border());
            if (height >= 8) g.fill(x + 1, y + 1, x + width - 1, y + 2, 0x18FFFFFF);
            Font f = font();
            int color = this.palette != null ? this.palette[4] : text();
            g.text(f, this.message, x + (width - f.width(this.message)) / 2, y + (height - 8) / 2, color, true);
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            //? if >=26.3 {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            //?} else {
            /*if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            *///?}
            clickSound();
            this.onPress.accept(this);
            return true;
        }
    }

    // --- switch ---

    public static Toggle toggle(Component label) {
        return new Toggle(label);
    }

    // sharp on/off switch, optional label on the right
    public static final class Toggle extends BaseUIComponent {
        private static final int SW = 18, SH = 10, GAP = 4;
        private final Component label;
        private final List<Consumer<Boolean>> listeners = new ArrayList<>();
        private boolean on;

        private Toggle(Component label) {
            this.label = label;
            this.cursorStyle(CursorStyle.HAND);
        }

        public Toggle checked(boolean checked) {
            this.on = checked;
            return this;
        }

        public Toggle onChanged(Consumer<Boolean> listener) {
            this.listeners.add(listener);
            return this;
        }

        private boolean hasLabel() {
            return !this.label.getString().isEmpty();
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return SW + (hasLabel() ? GAP + font().width(this.label) : 0);
        }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) {
            return 12;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            int sy = y + (height - SH) / 2;
            int track = this.on ? (Theme.current().good() | 0xFF000000) : groove();
            frame(g, x, sy, SW, SH, track, this.hovered ? borderHover() : border());
            int knob = this.on ? x + SW - 8 : x + 1;
            g.fill(knob, sy + 1, knob + 7, sy + SH - 1, this.on ? 0xFFF0F0F0 : 0xFF8E9CAD);
            if (hasLabel()) {
                String shown = Ui.MEASURE.ellipsize(this.label.getString(), Math.max(10, width - SW - GAP));
                g.text(font(), shown, x + SW + GAP, y + (height - 8) / 2, this.on ? text() : dim(), true);
            }
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            //? if >=26.3 {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            //?} else {
            /*if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            *///?}
            clickSound();
            this.on = !this.on;
            for (Consumer<Boolean> l : List.copyOf(this.listeners)) l.accept(this.on);
            return true;
        }
    }

    // --- slider ---

    public static Slider slider(Sizing horizontal, double min, double max) {
        return new Slider(horizontal, min, max);
    }

    // flat slider with its value on it. dragging works outside it too (drags go to the focused component)
    public static final class Slider extends BaseUIComponent {
        private final double min, max;
        private final List<Consumer<Double>> listeners = new ArrayList<>();
        private Runnable onReleased;
        private int decimals;
        private double value;
        private boolean showValue = true;

        private Slider(Sizing horizontal, double min, double max) {
            this.min = min;
            this.max = max;
            this.value = min;
            this.horizontalSizing(horizontal);
            this.verticalSizing(Sizing.fixed(12));
            this.cursorStyle(CursorStyle.MOVE);
        }

        public Slider decimalPlaces(int decimals) {
            this.decimals = decimals;
            return this;
        }

        public Slider showValue(boolean showValue) {
            this.showValue = showValue;
            return this;
        }

        public Slider setFromDiscreteValue(double value) {
            this.value = Math.max(this.min, Math.min(this.max, value));
            return this;
        }

        public Slider onChanged(Consumer<Double> listener) {
            this.listeners.add(listener);
            return this;
        }

        // the menu rebuilds its pages over and over, the slider stays and only takes the newest action
        public void replaceListeners(Consumer<Double> listener) {
            this.listeners.clear();
            this.listeners.add(listener);
        }

        public Slider onReleased(Runnable onReleased) {
            this.onReleased = onReleased;
            return this;
        }

        private double pct() {
            return this.max <= this.min ? 0 : (this.value - this.min) / (this.max - this.min);
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            frame(g, x, y, width, height, groove(), this.hovered ? borderHover() : border());
            int inner = Math.max(0, width - 2);
            int f = (int) Math.round(inner * pct());
            if (f > 0 && false) g.fill(x + 1, y + 1, x + 1 + f, y + height - 1, 0xFF6B4410);
            else if (f > 0) {
                io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y + 1, f, height - 2, 0xFF1E4A66, 0xFF2F86AD, 0xFF2F86AD, 0xFF1E4A66);
                g.fill(x + 1, y + 1, x + 1 + f, y + 2, 0x30FFFFFF);
            }
            int knob = x + 1 + Math.max(0, Math.min(inner - 2, f - 1));
            g.fill(knob, y + 1, knob + 2, y + height - 1, 0xFFF0F0F0);
            if (this.showValue) {
                String t = String.format(Locale.ROOT, "%." + this.decimals + "f", this.value);
                Font font = font();
                g.text(font, t, x + (width - font.width(t)) / 2, y + (height - 8) / 2, text(), true);
            }
        }

        private void setFrom(double relativeX) {
            double t = Math.max(0, Math.min(1, (relativeX - 1) / Math.max(1, width - 2)));
            double v = this.min + t * (this.max - this.min);
            double p = Math.pow(10, this.decimals);
            v = Math.round(v * p) / p;
            if (v == this.value) return;
            this.value = v;
            for (Consumer<Double> l : List.copyOf(this.listeners)) l.accept(v);
        }

        @Override
        public boolean canFocus(FocusSource source) {
            return source == FocusSource.MOUSE_CLICK;
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            //? if >=26.3 {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            //?} else {
            /*if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            *///?}
            var focus = focusHandler();
            if (focus != null) focus.focus(this, FocusSource.MOUSE_CLICK);
            setFrom(click.x());
            return true;
        }

        @Override
        public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
            setFrom(click.x());
            return true;
        }

        @Override
        public boolean onMouseUp(MouseButtonEvent click) {
            if (this.onReleased != null) this.onReleased.run();
            return true;
        }
    }

    // --- text field ---

    public static Input input(Sizing horizontal, String initial) {
        String[] store = {initial == null ? "" : initial};
        return new Input(horizontal, () -> store[0], v -> store[0] = v);
    }

    // field whose text lives somewhere else (menu fields keep theirs across screens)
    public static Input input(Sizing horizontal, Supplier<String> get, Consumer<String> set) {
        return new Input(horizontal, get, set);
    }

    // flat one line text field: cursor, select all, copy, cut, paste, hint, optional masking
    public static final class Input extends BaseUIComponent {
        private final Supplier<String> get;
        private final Consumer<String> set;
        private final List<Consumer<String>> listeners = new ArrayList<>();
        private Runnable onEnter;
        private Component hint = Component.empty();
        private boolean masked, allSelected;
        private int maxLength = 256, cursor, viewStart;

        private Input(Sizing horizontal, Supplier<String> get, Consumer<String> set) {
            this.get = get;
            this.set = set;
            this.cursor = get.get().length();
            this.horizontalSizing(horizontal);
            this.verticalSizing(Sizing.fixed(16));
            this.cursorStyle(CursorStyle.TEXT);
        }

        public void setHint(Component hint) {
            this.hint = hint;
        }

        public Input masked(boolean masked) {
            this.masked = masked;
            return this;
        }

        public Input maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Input onChanged(Consumer<String> listener) {
            this.listeners.add(listener);
            return this;
        }

        public Input onEnter(Runnable onEnter) {
            this.onEnter = onEnter;
            return this;
        }

        public String getValue() {
            return this.get.get();
        }

        private void change(String value, int newCursor) {
            String v = value.length() > this.maxLength ? value.substring(0, this.maxLength) : value;
            this.cursor = Math.max(0, Math.min(v.length(), newCursor));
            this.allSelected = false;
            if (v.equals(this.get.get())) return;
            this.set.accept(v);
            for (Consumer<String> l : List.copyOf(this.listeners)) l.accept(v);
        }

        private void insert(String typed) {
            String v = this.get.get();
            if (this.allSelected) change(typed, typed.length());
            else {
                int c = Math.min(this.cursor, v.length());
                change(v.substring(0, c) + typed + v.substring(c), c + typed.length());
            }
        }

        private boolean isFocused() {
            var focus = focusHandler();
            return focus != null && focus.focused() == this;
        }

        private String shown(String value) {
            return this.masked ? "•".repeat(value.length()) : value;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean focused = isFocused();
            int accent = Theme.current().accent() | 0xFF000000;
            frame(g, x, y, width, height, Ui.INPUT_BG, focused ? accent : this.hovered ? borderHover() : border());
            Font f = font();
            int tx = x + 4, ty = y + (height - 8) / 2, inner = Math.max(1, width - 8);
            String value = this.get.get();
            if (value.isEmpty() && !focused) {
                g.text(f, Ui.MEASURE.ellipsize(this.hint.getString(), inner), tx, ty, dim(), false);
                return;
            }
            String s = shown(value);
            int c = Math.max(0, Math.min(this.cursor, s.length()));
            if (c < this.viewStart) this.viewStart = c;
            if (this.viewStart > s.length()) this.viewStart = 0;
            while (this.viewStart < c && f.width(s.substring(this.viewStart, c)) > inner - 1) this.viewStart++;
            String vis = f.plainSubstrByWidth(s.substring(this.viewStart), inner);
            if (this.allSelected && !vis.isEmpty()) g.fill(tx - 1, ty - 1, tx + f.width(vis) + 1, ty + 9, 0x70FFFFFF);
            g.text(f, vis, tx, ty, text(), false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + f.width(s.substring(this.viewStart, c));
                g.fill(cx, ty - 1, cx + 1, ty + 9, accent);
            }
        }

        @Override
        public boolean canFocus(FocusSource source) {
            return true;
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            //? if >=26.3 {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            //?} else {
            /*if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.onMouseDown(click, doubled);
            *///?}
            var focus = focusHandler();
            if (focus != null) focus.focus(this, FocusSource.MOUSE_CLICK);
            String s = shown(this.get.get());
            if (doubled) {
                this.allSelected = !s.isEmpty();
                this.cursor = s.length();
                return true;
            }
            this.allSelected = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null) {
                this.cursor = s.length();
                return true;
            }
            int rel = (int) click.x() - 4;
            int at = Math.min(this.viewStart, s.length());
            while (at < s.length() && mc.font.width(s.substring(this.viewStart, at + 1)) <= rel) at++;
            this.cursor = at;
            return true;
        }

        @Override
        public boolean onKeyPress(KeyEvent e) {
            String v = this.get.get();
            int k = e.key();
            int c = Math.min(this.cursor, v.length());
            //? if >=26.3 {
            if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
            //?} else {
            /*if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            *///?}
                if (this.onEnter != null) this.onEnter.run();
                return true;
            }
            if (e.hasControlDown()) {
                switch (k) {
                    //? if >=26.3 {
                    case InputConstants.KEY_A -> this.allSelected = !v.isEmpty();
                    case InputConstants.KEY_C -> Minecraft.getInstance().keyboardHandler.setClipboard(this.allSelected ? v : "");
                    case InputConstants.KEY_X -> {
                    //?} else {
                    /*case GLFW.GLFW_KEY_A -> this.allSelected = !v.isEmpty();
                    case GLFW.GLFW_KEY_C -> Minecraft.getInstance().keyboardHandler.setClipboard(this.allSelected ? v : "");
                    case GLFW.GLFW_KEY_X -> {
                    *///?}
                        if (this.allSelected) {
                            Minecraft.getInstance().keyboardHandler.setClipboard(v);
                            change("", 0);
                        }
                    }
                    //? if >=26.3 {
                    case InputConstants.KEY_V -> insert(Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n]", ""));
                    //?} else {
                    /*case GLFW.GLFW_KEY_V -> insert(Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n]", ""));
                    *///?}
                    default -> {
                        return false;
                    }
                }
                return true;
            }
            switch (k) {
                //? if >=26.3 {
                case InputConstants.KEY_BACKSPACE -> {
                //?} else {
                /*case GLFW.GLFW_KEY_BACKSPACE -> {
                *///?}
                    if (this.allSelected) change("", 0);
                    else if (c > 0) change(v.substring(0, c - 1) + v.substring(c), c - 1);
                }
                //? if >=26.3 {
                case InputConstants.KEY_DELETE -> {
                //?} else {
                /*case GLFW.GLFW_KEY_DELETE -> {
                *///?}
                    if (this.allSelected) change("", 0);
                    else if (c < v.length()) change(v.substring(0, c) + v.substring(c + 1), c);
                }
                //? if >=26.3 {
                case InputConstants.KEY_LEFT -> {
                //?} else {
                /*case GLFW.GLFW_KEY_LEFT -> {
                *///?}
                    this.cursor = Math.max(0, c - 1);
                    this.allSelected = false;
                }
                //? if >=26.3 {
                case InputConstants.KEY_RIGHT -> {
                //?} else {
                /*case GLFW.GLFW_KEY_RIGHT -> {
                *///?}
                    this.cursor = Math.min(v.length(), c + 1);
                    this.allSelected = false;
                }
                //? if >=26.3 {
                case InputConstants.KEY_HOME -> {
                //?} else {
                /*case GLFW.GLFW_KEY_HOME -> {
                *///?}
                    this.cursor = 0;
                    this.allSelected = false;
                }
                //? if >=26.3 {
                case InputConstants.KEY_END -> {
                //?} else {
                /*case GLFW.GLFW_KEY_END -> {
                *///?}
                    this.cursor = v.length();
                    this.allSelected = false;
                }
                //? if >=26.3 {
                case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                //?} else {
                /*case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                *///?}
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean onCharTyped(CharacterEvent e) {
            int cp = e.codepoint();
            if (cp < 32 || cp == 127) return false;
            insert(new String(Character.toChars(cp)));
            return true;
        }
    }

    // --- small stuff ---

    // item picture at any size
    public static final class Icon extends BaseUIComponent {
        private net.minecraft.world.item.ItemStack stack;

        public Icon(net.minecraft.world.item.ItemStack stack, int size) {
            this.stack = stack;
            this.sizing(Sizing.fixed(size));
        }

        public void stack(net.minecraft.world.item.ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            var p = g.pose();
            p.pushMatrix();
            p.translate(x, y);
            p.scale(width / 16f, height / 16f);
            g.item(stack, 0, 0);
            p.popMatrix();
        }
    }

    // small heading in the card color with a fading line after it
    public static final class Heading extends BaseUIComponent {
        private final String text;
        private final int color;

        public Heading(String text, int color) {
            this.text = text;
            this.color = color | 0xFF000000;
            this.horizontalSizing(Sizing.fill(100));
            this.verticalSizing(Sizing.fixed(10));
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            Font f = font();
            g.text(f, text, x, y + 1, color, true);
            int tw = f.width(text) + 4;
            if (width > tw) io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + tw, y + 5, width - tw, 1, Ui.alpha(color, 0.6f), Ui.alpha(color, 0.02f), Ui.alpha(color, 0.02f), Ui.alpha(color, 0.6f));
        }
    }

    // --- scrolling ---

    public static <C extends UIComponent> Scroll<C> scroll(Sizing horizontal, Sizing vertical, C child) {
        return new Scroll<>(horizontal, vertical, child);
    }

    // vertical scroll, thin scrollbar that stays visible, bigger step per wheel notch
    public static final class Scroll<C extends UIComponent> extends ScrollContainer<C> {

        private Scroll(Sizing horizontal, Sizing vertical, C child) {
            super(ScrollDirection.VERTICAL, horizontal, vertical, child);
            this.scrollbarThiccness(3);
            this.scrollbar((context, x, y, width, height, trackX, trackY, trackWidth, trackHeight, lastInteractTime, direction, active) -> {
                if (!active) return;
                context.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0x14FFFFFF);
                context.fill(x, y, x + width, y + height, 0xA07F93AB);
            });
        }
        @Override
        public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
            return super.onMouseScroll(mouseX, mouseY, amount * 2);
        }
    }
}
