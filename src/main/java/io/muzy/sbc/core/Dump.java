// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import io.muzy.sbc.SbcMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

// ground truth for every parser: dumps what the game shows rn (scoreboard, tab + header/footer, last action
// bars, last chat lines, open menu) as raw text with color codes
public final class Dump {
    private static final int CHAT_KEEP = 200, BAR_KEEP = 40;
    private static final Deque<String> chat = new ArrayDeque<>();
    private static final Deque<String> actionBar = new ArrayDeque<>();
    private Dump() {}

    // every raw chat line, codes intact
    public static synchronized void chat(Component c) {
        if (c == null) return;
        chat.addLast(c.getString());
        while (chat.size() > CHAT_KEEP) chat.removeFirst();
    }

    // every raw action bar line
    public static synchronized void actionBar(Component c) {
        if (c == null) return;
        String s = c.getString();
        if (!actionBar.isEmpty() && actionBar.peekLast().equals(s)) return;
        actionBar.addLast(s);
        while (actionBar.size() > BAR_KEEP) actionBar.removeFirst();
    }

    // text as is + a 2nd copy with every non ascii char as <U+XXXX> so glyphs survive copy paste
    private static void line(StringBuilder sb, String s) {
        sb.append(s).append('\n');
        boolean special = false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 126) special = true;
        if (!special) return;
        sb.append("    = ");
        s.codePoints().forEach(cp -> {
            if (cp == 0xA7) sb.append('§');
            else if (cp > 126) sb.append("<U+").append(Integer.toHexString(cp).toUpperCase()).append('>');
            else sb.append((char) cp);
        });
        sb.append('\n');
    }

    private static void section(StringBuilder sb, String title) {
        sb.append("\n==== ").append(title).append(" ====\n");
    }

    public static synchronized Path write(Minecraft mc) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("SkyBlock Companion dump ").append(LocalDateTime.now()).append('\n');
        sb.append("island=").append(GameState.island).append(" area=").append(GameState.area).append(" inSkyBlock=").append(GameState.inSkyBlock).append('\n');

        section(sb, "SCOREBOARD");
        if (mc.level != null) {
            Scoreboard sbd = mc.level.getScoreboard();
            Objective obj = sbd.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (obj == null) sb.append("(no sidebar)\n");
            else {
                line(sb, "title: " + obj.getDisplayName().getString());
                List<PlayerScoreEntry> entries = new ArrayList<>(sbd.listPlayerScores(obj));
                entries.removeIf(PlayerScoreEntry::isHidden);
                entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
                for (PlayerScoreEntry e : entries) {
                    PlayerTeam team = sbd.getPlayersTeam(e.owner());
                    Component c = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(team, e.ownerName());
                    line(sb, e.value() + ": " + c.getString());
                }
            }
        }

        section(sb, "TAB LIST");
        var conn = mc.getConnection();
        if (conn != null) {
            var overlay = (io.muzy.sbc.mc.mixin.PlayerTabOverlayAccessor) ((io.muzy.sbc.mc.mixin.HudAccessor) mc.gui.hud).sbc$tabList();
            Component h = overlay.sbc$header(), f = overlay.sbc$footer();
            line(sb, "header: " + (h == null ? "(none)" : h.getString().replace("\n", "\\n")));
            List<PlayerInfo> infos = new ArrayList<>(conn.getListedOnlinePlayers());
            infos.sort(Comparator.comparing((PlayerInfo p) -> p.getTeam() != null ? p.getTeam().getName() : "")
                    .thenComparing(p -> p.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
            int i = 0;
            for (PlayerInfo p : infos) {
                Component dn = p.getTabListDisplayName();
                line(sb, (i++) + " [" + (p.getTeam() != null ? p.getTeam().getName() : "-") + "] " + p.getProfile().name() + " | "
                        + (dn == null ? "(no display name)" : dn.getString()));
            }
            line(sb, "footer: " + (f == null ? "(none)" : f.getString().replace("\n", "\\n")));
        }

        section(sb, "ACTION BAR (last " + actionBar.size() + ")");
        for (String s : actionBar) line(sb, s);

        section(sb, "CHAT (last " + chat.size() + ")");
        for (String s : chat) line(sb, s);

        section(sb, "OPEN MENU");
        var screen = mc.gui.screen();
        AbstractContainerMenu menu = mc.player != null ? mc.player.containerMenu : null;
        if (screen == null || menu == null || menu == mc.player.inventoryMenu) sb.append("(no menu open)\n");
        else {
            line(sb, "title: " + screen.getTitle().getString());
            sb.append("slots: ").append(menu.slots.size()).append('\n');
            for (int i = 0; i < menu.slots.size(); i++) {
                ItemStack st = menu.slots.get(i).getItem();
                if (st.isEmpty()) continue;
                var cd = st.get(DataComponents.CUSTOM_DATA);
                String id = cd == null ? "" : cd.copyTag().getStringOr("id", "");
                line(sb, "#" + i + " x" + st.getCount() + " " + st.getItem() + (id.isEmpty() ? "" : " id=" + id) + " | " + st.getHoverName().getString());
                var lore = st.get(DataComponents.LORE);
                if (lore != null) for (Component l : lore.lines()) {
                    line(sb, "      " + l.getString());
                    // the colors too, the patterns read those
                    String colored = LegacyText.of(l);
                    if (!colored.equals(l.getString())) line(sb, "        = " + colored);
                }
            }
        }

        section(sb, "HELD ITEM");
        if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
            ItemStack st = mc.player.getMainHandItem();
            var cd = st.get(DataComponents.CUSTOM_DATA);
            line(sb, st.getHoverName().getString() + (cd == null ? "" : " id=" + cd.copyTag().getStringOr("id", "")));
            var lore = st.get(DataComponents.LORE);
            if (lore != null) for (Component l : lore.lines()) line(sb, "      " + l.getString());
        } else sb.append("(empty hand)\n");

        section(sb, "STATE");
        sb.append("hudEnabled=").append(Config.I.hudEnabled).append(" customBoard=").append(Config.I.customBoard).append('\n');
        sb.append("purse=").append(GameState.purse).append(" bits=").append(GameState.bits).append(" bank=").append(GameState.bank).append(" sbLevel=").append(GameState.sbLevel).append(" profile=").append(GameState.profileName).append('\n');
        sb.append("hp=").append(Vitals.hpNow()).append('/').append(Vitals.hpMax()).append(" mana=").append(Vitals.mana).append('/').append(Vitals.manaMax).append(" defense=").append(Vitals.defense).append(" skill=").append(Vitals.skill).append(' ').append(Vitals.skillGain).append('\n');
        if (mc.player != null) sb.append(String.format(java.util.Locale.ROOT, "pos=%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())).append('\n');
        sb.append("guiScale=").append(mc.getWindow().getGuiScale()).append(" screen=").append(mc.getWindow().getGuiScaledWidth()).append('x').append(mc.getWindow().getGuiScaledHeight()).append('\n');

        section(sb, "VISITORS");
        try {
            sb.append(io.muzy.sbc.garden.visitor.VisitorCards.describe());
        } catch (RuntimeException e) {
            sb.append("(could not read: ").append(e).append(")\n");
        }

        section(sb, "FEATURES");
        int on = 0, off = 0;
        StringBuilder offs = new StringBuilder();
        for (io.muzy.sbc.feature.Feature f : io.muzy.sbc.feature.Features.all()) {
            if (f.paused) sb.append("PAUSED ").append(f.id).append(": ").append(f.lastError).append('\n');
            if (f.enabled()) on++;
            else {
                off++;
                offs.append(f.id).append(' ');
            }
        }
        sb.append("on=").append(on).append(" off=").append(off).append(offs.isEmpty() ? "" : " [" + offs.toString().trim() + "]").append('\n');

        if ("CRIMSON_ISLE".equals(GameState.island)) {
            section(sb, "CRIMSON MINIBOSSES");
            try {
                for (String l : io.muzy.sbc.crimson.Crimson.debugScan(mc)) sb.append(l).append('\n');
            } catch (Throwable t) {
                sb.append("ERROR ").append(t).append('\n');
            }
        }

        section(sb, "HUD ELEMENTS");
        for (io.muzy.sbc.ui.hud.HudElement e : io.muzy.sbc.ui.HudLayout.elements()) {
            try {
                sb.append(e.id).append(" | zone=").append(e.zone).append(" | active=").append(e.active()).append(" visible=").append(e.visible()).append(" shows=").append(e.shows()).append(" mode=").append(e.showMode());
                if (e.shows()) {
                    sb.append(" | ").append(e.width()).append('x').append(e.height()).append(" whole=").append(e.wholeHeight()).append(" rows=").append(e.rows().size());
                    // the sections drawn inside it right now, the ones that come and go make a card jump
                    StringBuilder in = new StringBuilder();
                    for (io.muzy.sbc.ui.hud.HudElement m : e.members()) if (m.shows()) in.append(in.length() == 0 ? "" : ",").append(m.id);
                    if (in.length() > 0) sb.append(" sections=").append(in);
                }
                sb.append('\n');
            } catch (Throwable t) {
                sb.append(e.id).append(" | ERROR ").append(t).append('\n');
            }
        }

        Path dir = Config.DIR.resolve("dumps");
        Files.createDirectories(dir);
        Path file = dir.resolve("dump-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        Files.writeString(file, sb.toString());
        SbcMod.LOG.info("Dump written to {}", file);
        return file;
    }
}
