// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.greenhouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

// layout link reading without the game. from skyblocker GreenhousePaste's link reading + the skymutations
// format (lz-string, decompressor from LZString.java by Rufus Huang and pieroxy, MIT, cut down to the one
// form we need) and SkyShardsLayout
// grids are [x][z] from the north west corner. -1 = layout says nothing, 0 = wanted mutation, else a crop id
public final class LayoutCodes {
    private static final int GRID = 10, CELLS = GRID * GRID, LETTERS = 26, MAX_BYTES = 4096, MUTATIONS = 40;
    private static final String[] PALETTE_CROPS = {"Wheat Seeds", "Potato", "Carrot", "Pumpkin Seeds", "Melon Seeds", "Cocoa Beans", "Sugar Cane", "Cactus", "Nether Wart", "Red Mushroom", "Brown Mushroom", "Moonflower", "Sunflower", "Wild Rose", "Fire", "Dead Plants", "Fermento"};
    private static final String URI_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$";

    private LayoutCodes() {}

    // extractLayoutCode: code out of a designer link, share link or a bare code
    public static String extractCode(String clipboard) {
        String t = clipboard.strip();
        int layout = t.indexOf("layout=");
        if (layout >= 0) return endOfCode(t.substring(layout + 7));
        int share = t.indexOf("/share/");
        if (share >= 0) return endOfCode(t.substring(share + 7));
        return t;
    }

    private static String endOfCode(String code) {
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            boolean valid = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_' || c == '=' || c == '+';
            if (!valid) return code.substring(0, i);
        }
        return code;
    }

    // siteOf + loadFromLink: the site the link names, or skyshards first bc its codes check strictly. null if neither
    public static int[][] decodeLink(String clipboard) {
        String code = extractCode(clipboard);
        if (code.isEmpty()) return null;
        String host = clipboard.strip().toLowerCase(Locale.ENGLISH);
        int scheme = host.indexOf("://");
        if (scheme >= 0) host = host.substring(scheme + 3);
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                host = host.substring(0, i);
                break;
            }
        }
        if (host.contains("skyshards")) return skyShards(code);
        if (host.contains("skymutations")) return skyMutations(code);
        int[][] shards = skyShards(code);
        return shards != null ? shards : skyMutations(code);
    }

    // importGreenhouse: [x, z, "crop", value] entries. value 0 = wanted mutation, x runs the other way than ours
    public static int[][] skyMutations(String code) {
        try {
            JsonArray all = JsonParser.parseString(decompress(code)).getAsJsonArray();
            int[][] out = new int[GRID][GRID];
            for (int[] col : out) Arrays.fill(col, -1);
            for (JsonElement e : all) {
                JsonArray entry = e.getAsJsonArray();
                int x = entry.get(0).getAsInt(), z = entry.get(1).getAsInt();
                String crop = entry.get(2).getAsString();
                if (entry.get(3).getAsInt() == 0) {
                    out[9 - x][z] = 0;
                    continue;
                }
                GreenhouseCrops.Crop c = GreenhouseCrops.BY_NAME.get(crop);
                if (c != null && x >= 0 && x < GRID && z >= 0 && z < GRID) out[9 - x][z] = c.id();
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // SkyShardsLayout.decode: "inputs|targets|grid", raw deflate in base64url
    public static int[][] skyShards(String code) {
        try {
            String text = inflate(code);
            if (text == null) return null;
            String[] parts = text.split("\\|", -1);
            if (parts.length != 3) return null;
            int[] inputs = palette(parts[0]), targets = palette(parts[1]);
            if (inputs == null || targets == null) return null;
            String grid = parts[2];
            int width = grid.length() == CELLS ? 1 : grid.length() == CELLS * 2 ? 2 : 0;
            if (width == 0) return null;
            int[][] screen = new int[GRID][GRID];
            for (int[] row : screen) Arrays.fill(row, -1);
            for (int cell = 0; cell < CELLS; cell++) {
                String chars = grid.substring(cell * width, (cell + 1) * width);
                int index = paletteIndex(chars);
                if (index < 0) continue;
                boolean target = chars.equals(chars.toUpperCase(Locale.ENGLISH));
                int[] palette = target ? targets : inputs;
                if (index >= palette.length) continue;
                int crop = palette[index], value = target ? 0 : crop;
                int row = cell / GRID, col = cell % GRID, size = sizeOf(crop);
                for (int dr = 0; dr < size && row + dr < GRID; dr++) for (int dc = 0; dc < size && col + dc < GRID; dc++) screen[row + dr][col + dc] = value;
            }
            int[][] out = new int[GRID][GRID];
            for (int row = 0; row < GRID; row++) for (int col = 0; col < GRID; col++) out[col][row] = screen[row][col];
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String inflate(String code) throws Exception {
        byte[] compressed = Base64.getUrlDecoder().decode(code);
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true))) {
            byte[] out = in.readNBytes(MAX_BYTES);
            return in.read() != -1 ? null : new String(out, StandardCharsets.UTF_8);
        }
    }

    private static int[] palette(String text) {
        if (text.isEmpty()) return new int[0];
        String[] indices = text.split(",", -1);
        int[] out = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            int index;
            try {
                index = Integer.parseInt(indices[i], 36);
            } catch (NumberFormatException e) {
                return null;
            }
            if (index < 0 || index >= PALETTE_CROPS.length + MUTATIONS) return null;
            out[i] = index < PALETTE_CROPS.length ? GreenhouseCrops.BY_NAME.get(PALETTE_CROPS[index]).id() : index - PALETTE_CROPS.length + 1;
        }
        return out;
    }

    private static int paletteIndex(String chars) {
        int index = 0;
        for (int i = 0; i < chars.length(); i++) {
            char c = Character.toLowerCase(chars.charAt(i));
            if (c < 'a' || c > 'z') return -1;
            index = index * LETTERS + (c - 'a');
        }
        if (!chars.equals(chars.toUpperCase(Locale.ENGLISH)) && !chars.equals(chars.toLowerCase(Locale.ENGLISH))) return -1;
        return index;
    }

    private static int sizeOf(int crop) {
        return switch (crop) {
            case 23, 37 -> 3; // Snoozling, Godseed
            case 22, 26, 36 -> 2; // Noctilume, PlantBoy Advance, Glasscorn
            default -> 1;
        };
    }

    public static int[][] rotate(int[][] grid, boolean left) {
        int[][] out = new int[GRID][GRID];
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                if (left) out[z][9 - x] = grid[x][z];
                else out[9 - z][x] = grid[x][z];
            }
        }
        return out;
    }

    public static int[][] mirror(int[][] grid) {
        int[][] out = new int[GRID][GRID];
        for (int x = 0; x < GRID; x++) System.arraycopy(grid[x], 0, out[9 - x], 0, GRID);
        return out;
    }

    // LZString.decompressFromEncodedURIComponent
    static String decompress(String input) {
        if (input == null) return "";
        if (input.isEmpty()) return null;
        String text = input.replace(' ', '+');
        return lz(text.length(), 32, i -> (char) URI_SAFE.indexOf(text.charAt(i)));
    }

    // read position in a compressed stream
    private static final class Bits {
        final int reset;
        final Function<Integer, Character> next;
        int val, position, index = 1;

        Bits(int reset, Function<Integer, Character> next) {
            this.reset = reset;
            this.next = next;
            val = next.apply(0);
            position = reset;
        }

        int read(int count) {
            int bits = 0, max = 1 << count, power = 1;
            while (power != max) {
                int b = val & position;
                position >>= 1;
                if (position == 0) {
                    position = reset;
                    val = next.apply(index++);
                }
                bits |= (b > 0 ? 1 : 0) * power;
                power <<= 1;
            }
            return bits;
        }
    }

    private static String lz(int length, int reset, Function<Integer, Character> next) {
        List<String> dictionary = new ArrayList<>(List.of("0", "1", "2"));
        int enlargeIn = 4, dictSize = 4, numBits = 3;
        StringBuilder result = new StringBuilder();
        Bits data = new Bits(reset, next);
        String c;
        switch (data.read(2)) {
            case 0 -> c = String.valueOf((char) data.read(8));
            case 1 -> c = String.valueOf((char) data.read(16));
            default -> {
                return "";
            }
        }
        dictionary.add(c);
        String w = c;
        result.append(w);
        while (true) {
            if (data.index > length) return "";
            int cc = data.read(numBits);
            if (cc == 0 || cc == 1) {
                dictionary.add(String.valueOf((char) data.read(cc == 0 ? 8 : 16)));
                dictSize++;
                cc = dictSize - 1;
                enlargeIn--;
            } else if (cc == 2) return result.toString();
            if (enlargeIn == 0) {
                enlargeIn = 1 << numBits;
                numBits++;
            }
            String entry;
            if (cc < dictionary.size()) entry = dictionary.get(cc);
            else if (cc == dictSize) entry = w + w.charAt(0);
            else return null;
            result.append(entry);
            dictionary.add(w + entry.charAt(0));
            dictSize++;
            enlargeIn--;
            w = entry;
            if (enlargeIn == 0) {
                enlargeIn = 1 << numBits;
                numBits++;
            }
        }
    }

    // presets are stored as "name=link|name=link"
    public static Map<String, String> presets(String text) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (text == null) return out;
        for (String p : text.split("\\|")) {
            int eq = p.indexOf('=');
            if (eq > 0) out.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
        }
        return out;
    }
}
