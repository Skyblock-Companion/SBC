// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.item.tooltip;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the item card, our own tooltip for skyblock items
// head has the item, its name and what it is. stats in 2 lined up columns, abilities + bonuses get a marked
// heading, gem slots are small tiles, progress + fuel are bars
// every line hypixel wrote stays, same colors same order. only the rarity line moves up into the head
public final class FancyTooltip {
    // hold it = plain tooltip. unbound at first
    public static net.minecraft.client.KeyMapping plainKey;
    private static final Identifier STYLE_CLASSIC = Identifier.fromNamespaceAndPath("sbcompanion", "sbc_classic");
    private static final Identifier STYLE_CARD = Identifier.fromNamespaceAndPath("sbcompanion", "sbc_rarity");
    private static final int ROW = 11, HEAD = 24, PANEL = 0xF20E1117;

    private static final Pattern CODE = Pattern.compile("§(.)");
    private static final Pattern RARITY = Pattern.compile("^(?:\\S )?(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL|ULTIMATE|ADMIN)\\b.*");
    private static final Pattern STAT = Pattern.compile("^([A-Za-z][\\w' ]{0,24}): ([+-]?\\d[\\d,.]*[%kKmMbBsx]?.{0,20})$");
    private static final Pattern PROGRESS = Pattern.compile("^(?:.* )?([\\d.]+)%$");
    private static final Pattern PROGRESS_NUMBERS = Pattern.compile("^-*\\s*([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)$");
    private static final Pattern FUEL = Pattern.compile("^Fuel: ([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)$");
    private static final Pattern SLOTS = Pattern.compile("^\\s*(Gemstones: )?(?:\\[.\\]\\s*)+$");
    private static final Pattern SLOT = Pattern.compile("((?:§.)*)\\[((?:§.)*)(.)(?:§.)*\\]");
    private static final Pattern ABILITY = Pattern.compile("^(.*Ability: .+?)\\s{2,}([A-Z ]*CLICK|[A-Z ]*SNEAK)$");
    private FancyTooltip() {}

    public static boolean active() {
        return Features.on("fancy_tooltip") && (GameState.inSkyBlock || SbcMod.DEV) && !plainHeld();
    }

    private static boolean plainHeld() {
        return io.github.zmuzy.sbc.mc.KeyHeld.held(plainKey);
    }

    // what vanilla adds under a skyblock item says nothing true about it
    public static boolean hidesVanilla() {
        return active() && opt("hideVanilla", true);
    }

    private static boolean opt(String key, boolean def) {
        Feature f = Features.get("fancy_tooltip");
        var o = f == null ? null : f.option("fancy_tooltip." + key);
        return o == null ? def : o.bool();
    }

    public static Identifier style() {
        return opt("rarityFrame", true) ? STYLE_CARD : STYLE_CLASSIC;
    }

    public static boolean rarityStyle(Identifier style) {
        return STYLE_CARD.equals(style);
    }

    private static volatile int frameColor = 0xFFAAAAAA;

    // the card: dark panel, thin edge in the items rarity that glows faintly behind the head
    public static void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int c = frameColor, x0 = x - 4, y0 = y - 4, x1 = x + w + 4, y1 = y + h + 4;
        g.fill(x0 + 1, y0, x1 - 1, y1, PANEL);
        g.fill(x0, y0 + 1, x1, y1 - 1, PANEL);
        g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y + HEAD, alpha(c, 0x38), alpha(c, 0));
        int edge = alpha(c, 0xD0), soft = alpha(c, 0x60);
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, edge);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, soft);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, soft);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, soft);
    }

    private static int alpha(int color, int a) {
        return (color & 0xFFFFFF) | (a << 24);
    }

    // reading the lines

    private static int minecraftColor(char code) {
        return switch (code) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            default -> 0xFFFFFFFF;
        };
    }

    // first color a line is written in, '\0' if none
    static char firstColor(String legacy) {
        Matcher m = CODE.matcher(legacy);
        while (m.find()) {
            char c = Character.toLowerCase(m.group(1).charAt(0));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) return c;
        }
        return '\0';
    }

    private static String strip(String legacy) {
        return GameState.strip(legacy);
    }

    private static double amount(String text) {
        String t = text.replace(",", "").trim();
        if (t.isEmpty()) return 0;
        double mul = switch (Character.toLowerCase(t.charAt(t.length() - 1))) {
            case 'k' -> 1e3;
            case 'm' -> 1e6;
            case 'b' -> 1e9;
            default -> 1;
        };
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // the parts of a card

    // one row of the card
    interface Part {
        int width(Font f);

        int height();

        void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full);

        // what it says, so a test can prove nothing got dropped
        String text();
    }

    private static Component literal(String legacy) {
        return Component.literal(legacy);
    }

    private static void fillRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    record Head(ItemStack stack, boolean icon, String name, String subtitle, int color) implements Part {
        private int textX() {
            return icon ? 25 : 0;
        }

        @Override
        public int width(Font f) {
            return textX() + Math.max(f.width(literal(name)), f.width(literal(subtitle)));
        }

        @Override
        public int height() {
            return HEAD;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            if (icon) {
                fillRow(g, x, y, 20, 20, alpha(color, 0x30));
                fillRow(g, x, y, 20, 1, alpha(color, 0x90));
                fillRow(g, x, y + 19, 20, 1, alpha(color, 0x90));
                fillRow(g, x, y + 1, 1, 18, alpha(color, 0x90));
                fillRow(g, x + 19, y + 1, 1, 18, alpha(color, 0x90));
                g.item(stack, x + 2, y + 2);
            }
            g.text(f, literal(name), x + textX(), y + (subtitle.isEmpty() ? 6 : 1), -1);
            if (!subtitle.isEmpty()) g.text(f, literal(subtitle), x + textX(), y + 11, -1);
            g.fillGradient(x, y + HEAD - 2, x + full, y + HEAD - 1, alpha(color, 0x70), alpha(color, 0x70));
        }

        @Override
        public String text() {
            return name + " " + subtitle;
        }
    }

    // a line as hypixel wrote it
    record Plain(Component line) implements Part {
        @Override
        public int width(Font f) {
            return f.width(line);
        }

        @Override
        public int height() {
            return ROW;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            g.text(f, line, x, y + 1, -1);
        }

        @Override
        public String text() {
            return line.getString();
        }
    }

    // space between two blocks with a hairline
    record Gap() implements Part {
        @Override
        public int width(Font f) {
            return 0;
        }

        @Override
        public int height() {
            return 7;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            fillRow(g, x, y + 3, full, 1, 0x1CFFFFFF);
        }

        @Override
        public String text() {
            return "";
        }
    }

    // label left, value on the right edge. every other row a shade lighter so the eye can follow it across
    record Stat(String icon, int iconColor, String label, String value, boolean shaded) implements Part {
        @Override
        public int width(Font f) {
            return (icon.isEmpty() ? 0 : 12) + f.width(literal(label)) + 14 + f.width(literal(value));
        }

        @Override
        public int height() {
            return ROW;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            if (shaded) fillRow(g, x - 2, y, full + 4, ROW, 0x0DFFFFFF);
            int tx = x;
            if (!icon.isEmpty()) {
                String[] art = StatEmblems.of(GameState.strip(label), icon);
                if (art != null) StatEmblems.draw(g, art, tx, y + 1, iconColor & 0xFFFFFF);
                else g.text(f, Component.literal(icon), tx + 1, y + 1, iconColor);
                tx += 12;
            }
            g.text(f, literal(label), tx, y + 1, -1);
            g.text(f, literal(value), x + full - f.width(literal(value)), y + 1, -1);
        }

        @Override
        public String text() {
            return label + " " + value;
        }
    }

    // start of an ability or bonus, marked on the side with its key on the right
    record Heading(String title, String key, int color) implements Part {
        @Override
        public int width(Font f) {
            return 5 + f.width(literal(title)) + (key.isEmpty() ? 0 : 12 + f.width(literal(key)));
        }

        @Override
        public int height() {
            return ROW + 1;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            fillRow(g, x, y, 2, ROW, color);
            g.text(f, literal(title), x + 5, y + 2, -1);
            if (key.isEmpty()) return;
            int w = f.width(literal(key)), kx = x + full - w - 3;
            fillRow(g, kx - 3, y, w + 6, ROW, alpha(color, 0x2C));
            g.text(f, literal(key), kx, y + 2, -1);
        }

        @Override
        public String text() {
            return title + " " + key;
        }
    }

    // row of slots, each a small tile in its color
    record Slots(String label, List<String> symbols, List<Integer> colors) implements Part {
        @Override
        public int width(Font f) {
            return (label.isEmpty() ? 0 : f.width(literal(label)) + 3) + symbols.size() * 13;
        }

        @Override
        public int height() {
            return 13;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            int tx = x;
            if (!label.isEmpty()) {
                g.text(f, literal(label), tx, y + 2, -1);
                tx += f.width(literal(label)) + 3;
            }
            for (int i = 0; i < symbols.size(); i++) {
                int c = colors.get(i);
                fillRow(g, tx, y, 11, 11, alpha(c, 0x28));
                fillRow(g, tx, y, 11, 1, alpha(c, 0xB0));
                fillRow(g, tx, y + 10, 11, 1, alpha(c, 0xB0));
                fillRow(g, tx, y + 1, 1, 9, alpha(c, 0xB0));
                fillRow(g, tx + 10, y + 1, 1, 9, alpha(c, 0xB0));
                String[] art = StatEmblems.of(null, symbols.get(i));
                if (art != null) {
                    StatEmblems.draw(g, art, tx + 1, y + 1, c & 0xFFFFFF);
                } else {
                    Component s = Component.literal(symbols.get(i));
                    g.text(f, s, tx + 6 - f.width(s) / 2, y + 2, c);
                }
                tx += 13;
            }
        }

        @Override
        public String text() {
            return label + " " + String.join(" ", symbols);
        }
    }

    // title with a bar + its numbers under it
    record Bar(String title, String numbers, double part, int color) implements Part {
        @Override
        public int width(Font f) {
            return Math.max(80, f.width(literal(title)) + (numbers.isEmpty() ? 0 : 12 + f.width(literal(numbers))));
        }

        @Override
        public int height() {
            return ROW + 5;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, Font f, int x, int y, int full) {
            g.text(f, literal(title), x, y + 1, -1);
            if (!numbers.isEmpty()) g.text(f, literal(numbers), x + full - f.width(literal(numbers)), y + 1, -1);
            fillRow(g, x, y + ROW, full, 3, 0x30FFFFFF);
            fillRow(g, x, y + ROW, (int) Math.round(full * Math.max(0, Math.min(1, part))), 3, color);
        }

        @Override
        public String text() {
            return title + " " + numbers;
        }
    }

    // arranging

    // rows for these lore lines. the first is the item name, the card head comes from it
    static List<Part> arrange(ItemStack stack, boolean icon, List<Component> lines, boolean statRows, boolean bars, boolean slots) {
        List<String> legacy = new ArrayList<>();
        for (Component c : lines) legacy.add(LegacyText.of(c));
        int last = legacy.size() - 1;
        while (last > 0 && strip(legacy.get(last)).isBlank()) last--;
        // rarity goes to the head
        String rarity = "";
        if (last > 0 && RARITY.matcher(strip(legacy.get(last)).trim()).matches()) {
            rarity = legacy.get(last);
            last--;
            while (last > 0 && strip(legacy.get(last)).isBlank()) last--;
        }
        // and so does the small line right under the name, like "Combat Pet" or "Breaking Power 10"
        int first = 1;
        String kind = "";
        if (first <= last && firstColor(legacy.get(first)) == '8' && strip(legacy.get(first)).length() <= 30 && (first == last || strip(legacy.get(first + 1)).isBlank())) {
            kind = legacy.get(first);
            first++;
        }
        char colorCode = firstColor(rarity.isEmpty() ? legacy.get(0) : rarity);
        int color = colorCode == '\0' ? 0xFFAAAAAA : minecraftColor(colorCode);
        frameColor = color;
        String subtitle = rarity + (!rarity.isEmpty() && !kind.isEmpty() ? " §8· " : "") + kind;

        List<Part> out = new ArrayList<>();
        out.add(new Head(stack, icon, legacy.get(0), subtitle, color));
        boolean shaded = false, wasGap = true;
        for (int i = first; i <= last; i++) {
            String line = legacy.get(i), plain = strip(line).stripTrailing();
            if (plain.isBlank()) {
                if (!wasGap) out.add(new Gap());
                wasGap = true;
                shaded = false;
                continue;
            }
            String next = i < last ? legacy.get(i + 1) : "";
            String nextPlain = strip(next).trim();

            Matcher m = FUEL.matcher(plain.trim());
            if (bars && m.matches()) {
                double max = amount(m.group(2));
                out.add(new Bar("§7Fuel", line.substring(line.indexOf(": ") + 2), max <= 0 ? 0 : amount(m.group(1)) / max, 0xFF00AA00));
                wasGap = false;
                continue;
            }
            if (bars && plain.startsWith("Progress")) {
                Matcher pct = PROGRESS.matcher(plain.trim());
                Matcher nums = PROGRESS_NUMBERS.matcher(nextPlain);
                if (pct.matches()) {
                    double part = Double.parseDouble(pct.group(1)) / 100;
                    boolean withNumbers = nums.matches();
                    String numbers = "";
                    if (withNumbers) {
                        numbers = next.replaceAll("(?:§.)*-+(?:§.)*\\s*", "");
                        i++;
                    }
                    out.add(new Bar(line, numbers, part, color));
                    wasGap = false;
                    continue;
                }
            }
            m = SLOTS.matcher(plain);
            if (slots && m.matches()) {
                List<String> symbols = new ArrayList<>();
                List<Integer> colors = new ArrayList<>();
                Matcher s = SLOT.matcher(line);
                while (s.find()) {
                    char code = firstColor(s.group(1) + s.group(2));
                    symbols.add(s.group(3));
                    colors.add(code == '\0' ? 0xFFAAAAAA : minecraftColor(code));
                }
                out.add(new Slots(m.group(1) == null ? "" : "§7Gemstones", symbols, colors));
                wasGap = false;
                continue;
            }
            m = ABILITY.matcher(plain);
            if (m.matches()) {
                int at = line.lastIndexOf(m.group(2).trim().split(" ")[0]);
                String key = line.substring(Math.max(0, at));
                int codeAt = at;
                while (codeAt >= 2 && line.charAt(codeAt - 2) == '§') codeAt -= 2;
                key = line.substring(Math.max(0, codeAt));
                out.add(new Heading(line.substring(0, Math.max(0, codeAt)).stripTrailing(), key, color));
                wasGap = false;
                continue;
            }
            char code = firstColor(line);
            boolean headingShape = wasGap && !nextPlain.isEmpty() && code != '\0' && code != '7' && code != '8' && plain.length() <= 44
                    && !plain.endsWith(".") && !plain.endsWith("!") && firstColor(next) != code;
            if (headingShape) {
                out.add(new Heading(line, "", minecraftColor(code)));
                wasGap = false;
                continue;
            }
            m = STAT.matcher(plain.trim());
            if (statRows && m.matches() && !plain.endsWith(".")) {
                int colon = line.indexOf(": ");
                if (colon > 0) {
                    StatIcons.Stat stat = StatIcons.of(m.group(1));
                    out.add(new Stat(stat == null ? "" : stat.icon(), stat == null ? 0 : stat.color(), line.substring(0, colon), line.substring(colon + 2), shaded));
                    shaded = !shaded;
                    wasGap = false;
                    continue;
                }
            }
            out.add(new Plain(lines.get(i)));
            wasGap = false;
            shaded = false;
        }
        return out;
    }

    // the row the way vanillas tooltip wants it
    static final class Comp implements ClientTooltipComponent {
        final Part part;
        int full;

        Comp(Part part) {
            this.part = part;
        }

        @Override
        public int getWidth(Font f) {
            return part.width(f);
        }

        @Override
        public int getHeight(Font f) {
            return part.height();
        }

        @Override
        public void extractImage(Font f, int x, int y, int width, int height, GuiGraphicsExtractor g) {
            part.draw(g, f, x, y, full);
        }

        @Override
        public void extractText(GuiGraphicsExtractor g, Font f, int x, int y) {
        }
    }

    // builds the components, null = keep vanillas tooltip for this item
    public static List<ClientTooltipComponent> build(ItemStack stack, List<Component> lines) {
        if (lines == null || lines.isEmpty() || Gain.sbId(stack).isEmpty()) return null;
        Font font = Minecraft.getInstance().font;
        List<Part> parts = arrange(stack, opt("icon", true), lines, opt("alignedStats", true), opt("bars", true), opt("gemstones", true));
        int full = 0;
        for (Part p : parts) full = Math.max(full, p.width(font));
        List<ClientTooltipComponent> out = new ArrayList<>(parts.size());
        for (Part p : parts) {
            Comp c = new Comp(p);
            c.full = full;
            out.add(c);
        }
        return out;
    }

    // total height of a built tooltip, for scrolling
    public static int height(List<ClientTooltipComponent> comps, Font f) {
        int h = 0;
        for (ClientTooltipComponent c : comps) h += c.getHeight(f);
        return h + 8;
    }
}
