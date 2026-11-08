// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.ui.layout.BaseUIComponent;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.UIGraphics;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

// hosts one of our older screens (K menu, storage, market search, radial, item, pets...) inside a tree
// screen. host owns the window + root, the page draws itself as one component
public final class LegacyHost extends SbcScreen {
    private final Screen inner;
    private boolean inited;

    private LegacyHost(Screen inner) {
        super(inner.getTitle().getString(), null);
        this.inner = inner;
    }

    // the screen a host carries, or the screen itself if its not a host
    public static Screen unwrap(Screen s) {
        return s instanceof LegacyHost h ? h.inner : s;
    }

    // current screen is (or hosts) this class
    public static boolean showing(Class<? extends Screen> cls) {
        Screen s = Minecraft.getInstance().gui.screen();
        return cls.isInstance(unwrap(s));
    }

    public static void open(Screen inner) {
        Minecraft.getInstance().gui.setScreen(new LegacyHost(inner));
    }

    // wraps if needed, hosts + tree screens pass through
    public static Screen wrap(Screen s) {
        if (s == null || s instanceof LegacyHost || s instanceof dev.zmuzy.sbc.ui.layout.BaseUIScreen) return s;
        return new LegacyHost(s);
    }

    private final class Page extends BaseUIComponent {
        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return 0;
        }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) {
            return 0;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            if (!inited) {
                inner.init(LegacyHost.this.width, LegacyHost.this.height);
                inited = true;
            }
            inner.extractBackground(g, mouseX, mouseY, partialTicks);
            inner.extractRenderState(g, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void init() {
        super.init();
        inner.init(width, height);
        inited = true;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.BLANK);
        Page p = new Page();
        p.sizing(Sizing.fill(100), Sizing.fill(100));
        root.child(p);
    }

    @Override
    public void tick() {
        inner.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return inner.isPauseScreen();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return inner.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return inner.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return inner.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        return inner.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        return inner.keyPressed(e);
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        return inner.keyReleased(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return inner.charTyped(e);
    }

    @Override
    public void onClose() {
        inner.onClose();
    }

    @Override
    public void removed() {
        inner.removed();
        super.removed();
    }
}
