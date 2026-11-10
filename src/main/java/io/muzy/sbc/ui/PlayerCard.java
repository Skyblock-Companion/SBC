// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemIcons;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.Vitals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

// top of the left frame: your head in a ring with the level gem, name, sb level bar, active pet
public final class PlayerCard {
    public static final int W = 196;
    private PlayerCard() {}

    private static void t(Ui ui, String s, float x, float y, float scale, int color) {
        var p = ui.g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(scale, scale);
        ui.g.text(ui.font, s, 0, 0, color, true);
        p.popMatrix();
    }

    public static boolean active() {
        return Minecraft.getInstance().player != null;
    }

    public static int width() {
        return compact() ? S_W : W;
    }

    // including the head poking out above
    public static int height() {
        if (compact()) return S_H;
        return io.muzy.sbc.feature.Features.on("cardstats") ? 102 : 74;
    }

    // --- classic ---

    private static final java.util.regex.Pattern SB_XP = java.util.regex.Pattern.compile("SB Level: \\[\\d+\\] (\\d+)/(\\d+) XP");

    // hypixels sb level color, new one every 40 levels
    private static int levelColor(int level) {
        int[] colors = {0xFFAAAAAA, 0xFFFFFFFF, 0xFFFFFF55, 0xFF55FF55, 0xFF00AA00, 0xFF55FFFF, 0xFF00AAAA, 0xFF5555FF, 0xFFFF55FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF5555, 0xFFAA0000};
        return colors[Math.max(0, Math.min(colors.length - 1, level / 40))];
    }

    // the tab is live, the api copy only changes when the profile loads again, so a level up waits for it
    static int level(Profile.Data d) {
        int live = parse(GameState.sbLevel);
        return live >= 0 ? live : d != null && d.sbXp >= 0 ? (int) (d.sbXp / 100) : -1;
    }

    // xp into the level out of 100, from the tab list or the api while its still the same level. {-1, -1} = dont know
    private static long[] sbXp(Profile.Data d) {
        for (String l : GameState.tab) {
            java.util.regex.Matcher m = SB_XP.matcher(GameState.strip(l));
            if (m.find()) return new long[]{Long.parseLong(m.group(1)), Long.parseLong(m.group(2))};
        }
        if (d != null && d.sbXp >= 0 && (int) (d.sbXp / 100) == level(d)) return new long[]{(long) (d.sbXp % 100), 100};
        return new long[]{-1, -1};
    }

    private static void pair(Ui ui, String label, String value, int x, int y, int w, int valueColor) {
        ui.textShadow(label, x, y, io.muzy.sbc.ui.theme.Modern.LABEL);
        ui.textShadow(value, x + w - ui.width(value), y, valueColor);
    }

    // --- compact ---

    private static final int S_W = 172, S_H = 64, S_HEAD = 16;

    private static boolean compact() {
        return true;
    }

    private static boolean showFps() {
        var f = io.muzy.sbc.feature.Features.get("playercard");
        var o = f == null ? null : f.option("playercard.fps");
        return o == null || o.bool();
    }

    // "FPS - 240", green from 144, yellow from 60
    static String fpsText(int fps) {
        return "§7FPS - " + (fps >= 144 ? "§a" : fps >= 60 ? "§e" : "§c") + fps;
    }

    // same info less air: smaller head next to 2 lines, xp under them, numbers on the meter line
    private static void drawShort(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        var M = io.muzy.sbc.ui.hud.ElementStyle.of("playercard");
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        boolean sample = p == null;
        Profile.Data d = sample ? null : Profile.current;
        int level = sample ? 196 : level(d);
        long[] xp = sample ? new long[]{52, 100} : sbXp(d);
        int accent = level >= 0 ? levelColor(level) : io.muzy.sbc.ui.theme.Modern.AQUA;
        io.muzy.sbc.ui.theme.Theme.current().panel(ui, x, y, S_W, S_H, M);
        ui.gradH(x + 1, y + 1, S_W - 2, 21, Ui.alpha(accent, 0.3f), Ui.alpha(accent, 0.02f));
        ui.rect(x + 3, y + 3, S_HEAD + 2, S_HEAD + 2, 0x90050911);
        ui.rect(x + 3, y + 3, S_HEAD + 2, 1, Ui.alpha(accent, 0.8f));
        if (sample) ui.rect(x + 4, y + 4, S_HEAD, S_HEAD, 0xFF8A6B3D);
        else try {
            var id = p.getSkin().body().texturePath();
            var pl = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
            g.blit(pl, id, x + 4, y + 4, 8f, 8f, S_HEAD, S_HEAD, 8, 8, 64, 64);
            g.blit(pl, id, x + 4, y + 4, 40f, 8f, S_HEAD, S_HEAD, 8, 8, 64, 64);
        } catch (Exception ignored) {
        }
        int tx = x + 24, tw = S_W - 24 - 4;
        String name = sample ? "Player_Name" : p.getGameProfile().name();
        String gem = level >= 0 ? String.valueOf(level) : "";
        int chip = gem.isEmpty() ? 0 : ui.width(gem) + 6;
        String fps = showFps() ? fpsText(sample ? 240 : mc.getFps()) : "";
        int fw = fps.isEmpty() ? 0 : ui.width(fps) + 4;
        ui.textShadow(ui.ellipsize(name, tw - chip - fw - 3), tx, y + 3, io.muzy.sbc.ui.theme.Modern.INK);
        if (!fps.isEmpty()) ui.textShadow(fps, x + S_W - 4 - chip - fw, y + 3, io.muzy.sbc.ui.theme.Modern.LABEL);
        if (!gem.isEmpty()) io.muzy.sbc.ui.theme.Modern.chip(ui, x + S_W - 4 - chip, y + 3, gem, accent);
        String profile = sample ? "Coconut" : GameState.profileName, type = sample ? "Co-op" : GameState.profileType;
        String under = profile.isEmpty() ? "SkyBlock" : profile + (type.isEmpty() ? "" : " §8· §7" + type);
        ui.textShadow(ui.ellipsize(under, tw), tx, y + 12, io.muzy.sbc.ui.theme.Modern.LABEL);
        int rx = x + 4, rw = S_W - 8, gap = io.muzy.sbc.ui.hud.Compact.GAP, half = (rw - gap) / 2, ry = y + 22;
        if (xp[0] >= 0) {
            double pct = xp[1] > 0 ? (double) xp[0] / xp[1] : 0;
            String left = xp[0] + "/" + xp[1] + " XP", right = io.muzy.sbc.ui.hud.RowText.percent(pct);
            ui.textShadow(left, rx, ry, io.muzy.sbc.ui.theme.Modern.LABEL);
            ui.textShadow(right, rx + rw - ui.width(right), ry, io.muzy.sbc.ui.theme.Modern.INK);
            io.muzy.sbc.ui.theme.Modern.meter(ui, rx, ry + 9, rw, 2, pct, accent);
        }
        ry += 12;
        long purse = sample ? 1_423_085_776L : GameState.purse, bank = sample ? 1_250_000_000L : GameState.bank, bits = sample ? 140_965 : GameState.bits;
        // purse and bank as they are now, the rest from the last profile read
        double networth = sample ? 9_860_000_000d : d == null || !d.knows(io.muzy.sbc.core.game.GameProfile.NETWORTH) ? 0
                : d.networth() - d.worth.getOrDefault("Purse", 0d) - d.worth.getOrDefault("Bank", 0d) + Math.max(0, purse) + Math.max(0, bank);
        int bx = rx + half + gap, bw = rw - half - gap;
        ui.rect(rx + half + gap / 2, ry, 1, 17, 0x30FFFFFF);
        pair(ui, "Purse", purse >= 0 ? io.muzy.sbc.core.Prices.coins(purse) : "-", rx, ry, half, io.muzy.sbc.ui.theme.Modern.GOLD);
        pair(ui, "Bank", bank >= 0 ? io.muzy.sbc.core.Prices.coins(bank) : "-", bx, ry, bw, io.muzy.sbc.ui.theme.Modern.GOLD);
        ry += 9;
        pair(ui, "Bits", bits >= 0 ? io.muzy.sbc.core.Prices.coins(bits) : "-", rx, ry, half, io.muzy.sbc.ui.theme.Modern.AQUA);
        // a profile built from the game has no museum, storage or wardrobe, say its only part of it
        boolean partial = d != null && d.fromGame;
        pair(ui, "Networth", networth > 0 ? (partial ? "≈" : "") + io.muzy.sbc.core.Prices.coins(networth) : "-", bx, ry, bw, partial ? io.muzy.sbc.ui.theme.Modern.WARN : io.muzy.sbc.ui.theme.Modern.GOOD);
        ry += 9;
        String pet = sample ? "Elephant" : Vitals.petName;
        if (!pet.isEmpty()) {
            var st = sample ? null : petStack();
            int px = rx;
            if (st != null) {
                ui.icon(st, px, ry - 1, 0.6f);
                px += 12;
            }
            int color = sample ? 0xFFFFAA00 : Vitals.petColor != 0 ? 0xFF000000 | Vitals.petColor : io.muzy.sbc.ui.theme.Modern.INK;
            int lvl = sample ? 95 : Vitals.petLevel;
            double petPct = sample ? 39.7 : Vitals.petXpPct;
            String right = "Lvl " + lvl + (petPct >= 0 ? "  " + String.format(java.util.Locale.ROOT, "%.1f%%", petPct) : "");
            int rwid = ui.width(right);
            ui.textShadow(ui.ellipsize(pet, rx + rw - px - rwid - 4), px, ry, color);
            ui.textShadow(right, rx + rw - rwid, ry, io.muzy.sbc.ui.theme.Modern.INK);
            if (petPct >= 0) io.muzy.sbc.ui.theme.Modern.meter(ui, rx, ry + 9, rw, 2, petPct / 100, color);
        } else ui.textShadow("No pet", rx, ry, io.muzy.sbc.ui.theme.Modern.DIM);
    }

    // one head layer as a cube: front, top (slanted rows) and left side (slanted columns)
    private static void cube(Ui ui, GuiGraphicsExtractor g, net.minecraft.resources.Identifier id, int x, int y, int s, int d,
                             int frontU, int topU, int sideU, int v0, int topV, boolean shade) {
        var pl = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
        g.blit(pl, id, x, y, (float) frontU, (float) v0, s, s, 8, 8, 64, 64);
        for (int r = 0; r < 8; r++) { // r = 7 touches the front edge
            int from = Math.round((7 - r) * d / 8f), to = Math.round((8 - r) * d / 8f);
            if (to <= from) continue;
            int shift = Math.round((7 - r + 0.5f) * d / 8f);
            g.blit(pl, id, x + shift, y - to, (float) topU, (float) (topV + r), s, to - from, 8, 1, 64, 64);
        }
        for (int c = 0; c < 8; c++) {
            int from = Math.round(c * d / 8f), to = Math.round((c + 1) * d / 8f);
            if (to <= from) continue;
            int top = y - Math.round((c + 0.5f) * d / 8f);
            g.blit(pl, id, x + s + from, top, (float) (sideU + c), (float) v0, to - from, s, 1, 8, 64, 64);
            if (shade) ui.rect(x + s + from, top, to - from, s, 0x55000000);
        }
    }

    // small 3d head with the hat layer, no frame
    private static void head(Ui ui, GuiGraphicsExtractor g, net.minecraft.world.entity.player.PlayerSkin skin, int x, int y, int s) {
        var id = skin.body().texturePath();
        int d = Math.round(s * 0.34f);
        DripKit.ellipse(ui, x + (s + d) / 2, y + s + 1, (s + d) / 2, 3, 0x55000000);
        cube(ui, g, id, x, y, s, d, 8, 8, 16, 8, 0, true);
        cube(ui, g, id, x, y, s, d, 40, 40, 48, 8, 0, true);
    }

    // top left at x, y. the head sticks out above the plate
    public static void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        ui.g = g;
        if (compact()) {
            drawShort(ui, g, x, y);
            return;
        }
        if (p == null) return;
        boolean stats = io.muzy.sbc.feature.Features.on("cardstats");
        Profile.Data d = Profile.current;
        int level = level(d);
        double pct = d != null && d.sbXp >= 0 ? (d.sbXp % 100) / 100.0 : 0;
        int ph = height() - 16;
        Bling.plate(ui, x, y + 16, W, ph, 10);

        // head floats half out of the card, no frame or backdrop
        int hs = 30, hx = x + 14, hy = y + 12;
        try {
            head(ui, g, p.getSkin(), hx, hy, hs);
        } catch (Exception ignored) {
        }
        if (level >= 0) {
            int bx = hx + (hs + Math.round(hs * 0.34f)) / 2, by = y + 59;
            Bling.diamond(ui, bx, by, 11, 0xFF000000);
            Bling.diamond(ui, bx, by, 10, DripKit.GOLD);
            Bling.diamond(ui, bx, by, 8, 0xFF2A1A06);
            String ls = String.valueOf(level);
            float sc = ls.length() > 2 ? 0.7f : 0.9f;
            t(ui, ls, bx - ui.width(ls) * sc / 2f + 0.5f, by - 4f * sc, sc, DripKit.GOLD);
        }

        int tx = x + 64, tw = x + W - 10 - tx;
        String name = p.getGameProfile().name();
        t(ui, ui.ellipsize(name, (int) (tw / 1.2f)), tx, y + 22, 1.2f, DripKit.GOLD);
        Bling.wing(ui, tx - 2, y + 34, Math.min(tw, (int) (ui.width(name) * 1.2f) + 36), 1, DripKit.GOLD);
        Bling.capsule(ui, tx, y + 39, tw, 7, pct, pct, 0xFFB7E57A, 0xFF4E8A2A);
        t(ui, level >= 0 ? "SkyBlock Level " + level : "SkyBlock Level", tx, y + 49, 0.85f, DripKit.DIM);
        if (d != null) {
            String pc = Math.round(pct * 100) + "%";
            t(ui, pc, tx + tw - ui.width(pc) * 0.85f, y + 49, 0.85f, DripKit.TEXT);
        }
        if (!Vitals.petName.isEmpty()) {
            var st = petStack();
            int px = tx;
            if (st != null) {
                ui.icon(st, px - 1, y + 58, 0.75f);
                px += 14;
            }
            String pt = "Lv" + Vitals.petLevel + " " + Vitals.petName;
            t(ui, ui.ellipsize(pt, (int) ((tw - 15) / 0.9f)), px, y + 62, 0.9f, DripKit.TEXT);
        }

        if (stats) {
            Bling.line(ui, x + 12, y + 73, W - 24, Bling.GOLD);
            List<String[]> rows = InfoPanel.rows();
            for (int i = 0; i < rows.size(); i++) {
                String[] r = rows.get(i);
                int cx = x + 10 + (i % 2) * 90, cy = y + 78 + (i / 2) * 12;
                int col = (int) Long.parseLong(r[2], 16) | 0xFF000000;
                Bling.gem(ui, cx + 3, cy + 4, 2, col);
                t(ui, r[0], cx + 10, cy, 0.85f, DripKit.DIM);
                t(ui, r[1], cx + 82 - ui.width(r[1]) * 0.9f, cy, 0.9f, col);
            }
        }
    }

    private static net.minecraft.world.item.ItemStack cached;
    private static String cachedFor = "";

    private static net.minecraft.world.item.ItemStack petStack() {
        String key = Vitals.petName;
        if (key.equals(cachedFor)) return cached;
        cachedFor = key;
        cached = null;
        Profile.Data d = Profile.current;
        String type = key.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        String tier = "LEGENDARY";
        if (d != null) for (Profile.Pet pp : d.pets) if (pp.name().equalsIgnoreCase(key)) {
            type = pp.type();
            if (pp.active()) tier = pp.tier();
        }
        try {
            cached = ItemIcons.pet(type, tier);
        } catch (Exception ignored) {
        }
        return cached;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return -1;
        }
    }
}
