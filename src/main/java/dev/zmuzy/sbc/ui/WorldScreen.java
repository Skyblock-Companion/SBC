// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.tasks.Mission;
import dev.zmuzy.sbc.web.WebPage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// the page of an npc, mob, place or warp from the Item List, like an items page: what and where it is, the
// wikis first lines about it, and the way there
public final class WorldScreen extends Screen {
    // wiki intros by name, asked once a game. null while asking, "" = the wiki has no page
    private static final Map<String, String> INTROS = new ConcurrentHashMap<>();

    private final Ui ui = new Ui();
    private final Screen parent;
    private final WorldIndex.Entry entry;
    private float scale = 1f;

    public WorldScreen(Screen parent, WorldIndex.Entry entry) {
        super(Component.literal(entry.name()));
        this.parent = parent;
        this.entry = entry;
    }

    public static void open(WorldIndex.Entry e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.gui.setScreen(new WorldScreen(mc.gui.screen(), e));
        if (!INTROS.containsKey(e.name())) {
            INTROS.put(e.name(), "\0");
            WebPage.intro(e.name()).thenAccept(text -> INTROS.put(e.name(), text));
        }
    }

    // the wiki has nothing on generic spots ("Fishing Hotspot", "/warp hub"), so say what we know instead
    static String fallback(WorldIndex.Entry e) {
        String where = GameState.pretty(e.island()) + (e.area().isEmpty() ? "" : ", in " + e.area());
        return switch (e.kind()) {
            case "NPC" -> e.name() + " is an NPC on " + where + ".";
            case "Boss" -> e.name() + " is a boss you can fight on " + where + ".";
            case "Mob" -> e.name() + " spawn here on " + where + ".";
            case "Travel" -> "A way to travel on " + where + ".";
            default -> e.name() + " is a place on " + GameState.pretty(e.island()) + ".";
        };
    }

    // what the left box lists
    static List<String> facts(WorldIndex.Entry e, String island, double distance, String warp) {
        List<String> out = new ArrayList<>();
        out.add("§7Kind: §f" + e.kind());
        out.add("§7Island: §f" + GameState.pretty(e.island()));
        if (!e.area().isEmpty()) out.add("§7Area: §f" + e.area());
        out.add("§7At: §f" + (int) Math.floor(e.pos().x) + ", " + (int) Math.floor(e.pos().y) + ", " + (int) Math.floor(e.pos().z));
        if (e.island().equals(island)) out.add("§7Distance: §f" + (int) distance + " blocks");
        else if (!warp.isEmpty()) out.add("§7Get there: §b" + warp);
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int w = 420, h = 178;
        scale = Math.min(1f, Math.min(width / (w + 20f), height / (h + 20f)));
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        ui.begin(g, Math.round(mouseX / scale), Math.round(mouseY / scale), mouseX, mouseY);
        int W = Math.round(width / scale), H = Math.round(height / scale);
        ui.rect(0, 0, W, H, 0x99060810);
        int x = W / 2 - w / 2, y = H / 2 - h / 2;
        ui.panel(x, y, w, h, 10, Ui.alpha(Ui.PANEL, 0.97f));
        ui.textLimit = x + w - 8;
        int accent = WorldIndex.color(entry.kind());
        ui.roundGradH(x, y, w, 3, 2, accent, Ui.CYAN);

        // header: icon, name, where, buttons
        ui.icon(WorldIndex.icon(entry.kind()), x + 12, y + 12, 2f);
        ui.textScaled(entry.name(), x + 52, y + 14, 1.4f, accent, true);
        ui.text(WorldIndex.where(entry), x + 52, y + 30, Ui.DIM);
        String warp = entry.island().equals(GameState.island) ? "" : Navigator.warpFor(new Navigator.Target(entry.island(), entry.name(), entry.pos()));
        int bx = x + 52, by = y + 44;
        bx += button("wl_nav", bx, by, "Navigate", () -> {
            WorldIndex.go(entry);
            onClose();
        });
        if (!warp.isEmpty()) bx += button("wl_warp", bx, by, warp, () -> {
            WorldIndex.go(entry);
            Mission.run(warp.substring(1));
            onClose();
        });
        button("wl_wiki", bx, by, "Wiki", () -> minecraft.gui.setScreen(new BrowserScreen(this, WebPage.wikiSearchUrl(entry.name()))));
        button("wl_back", x + w - 70, y + 12, "‹ Back", this::onClose);

        // left: the facts
        int ly = y + 74, colW = 180;
        double distance = minecraft.player == null ? 0 : Math.sqrt(minecraft.player.distanceToSqr(entry.pos()));
        List<String> facts = facts(entry, GameState.island, distance, warp);
        int boxH = facts.size() * 12 + 10;
        ui.rect(x + 12, ly, colW, boxH, 0x60000000);
        ui.rect(x + 12, ly, 2, boxH, accent);
        for (int i = 0; i < facts.size(); i++) ui.textShadow(ui.ellipsize(facts.get(i), colW - 14), x + 20, ly + 6 + i * 12, 0xFFFFFFFF);

        // right: the wiki about it
        int rx = x + 12 + colW + 14, rw = w - (rx - x) - 14;
        ui.text("About", rx, ly, Ui.CYAN);
        String intro = INTROS.get(entry.name());
        if (intro == null || intro.equals("\0")) ui.text("Asking the wiki...", rx, ly + 14, Ui.DIM);
        else {
            int used = ui.wrap(intro.isEmpty() ? fallback(entry) : intro, rx, ly + 14, rw, Ui.TEXT);
            ui.text(intro.isEmpty() ? "§8The wiki has no page for this" : "§8From the SkyBlock wiki", rx, ly + 20 + used, Ui.DIM);
        }
        g.pose().popMatrix();
        ui.flushTooltip();
    }

    private int button(String key, int x, int y, String label, Runnable r) {
        int bw = ui.width(label) + 16;
        ui.button(key, x, y, bw, 18, label, Ui.SOFT, r);
        return bw + 6;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / scale, my = e.y() / scale;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (mx < hit.x() || my < hit.y() || mx >= hit.x() + hit.w() || my >= hit.y() + hit.h() || hit.click() == null) continue;
            hit.click().run();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
