// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.Interactions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

// from skyhanni BetterContainers + LegacyBetterContainers (from neu): skyblock chests get a darker or lighter
// bg drawn from the chests own items
// filler panes vanish, slots and buttons get joined up
// menu stays hypixels, every slot where it was, every click goes through like before
// skipped their pick block click on a filler pane and their on/off toggles, those 2 pictures arent in their files
// while this is off nothing runs but one field read
public final class ImprovedMenus {
    public static final String ID = "improved_menus";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("sbcompanion", "dynamic_chest_inventory.png");
    private static final List<String> STYLES = List.of("Dark 1", "Dark 2", "Transparent", "Light 1", "Light 2", "Light 3");
    private static final int DEFAULT_TEXT = 4210752;
    private static final long CHECK_MS = 100, CLICK_SHOWN_MS = 500;
    private static final Patterns.P DISALLOWED = Patterns.p("SH.inventory.bettercontainers.disallowed");

    // the one thing the hooks check first
    private static volatile boolean drawing;
    private static DynamicTexture texture;
    private static NativeImage pixels;
    private static BufferedImage base, slot, button;
    private static String loadedStyle = "";
    private static int textColor = DEFAULT_TEXT;
    private static int clickedSlot = -1, signature;
    private static long clickedAt, checkedAt;

    private ImprovedMenus() {}

    private static BufferedImage image(int style, String file) {
        try (InputStream in = ImprovedMenus.class.getResourceAsStream("/assets/sbcompanion/dynamic_54/style" + style + "/" + file)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static int textColorOf(int style) {
        try (InputStream in = ImprovedMenus.class.getResourceAsStream("/assets/sbcompanion/dynamic_54/style" + style + "/dynamic_config.json")) {
            if (in == null) return DEFAULT_TEXT;
            return (int) Long.parseLong(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().get("text-colour").getAsString(), 16);
        } catch (Exception e) {
            return DEFAULT_TEXT;
        }
    }

    private static int styleNumber(String key) {
        return STYLES.indexOf(Features.get(ID).option(key).choice()) + 1;
    }

    // from skyhanni generateBufferedImages
    private static void loadImages() {
        int menu = styleNumber("improved_menus.menu_style"), buttons = styleNumber("improved_menus.button_style");
        String now = menu + "/" + buttons;
        if (now.equals(loadedStyle)) return;
        loadedStyle = now;
        base = image(menu, "dynamic_54.png");
        slot = image(buttons, "dynamic_54_slot_ctm.png");
        button = image(buttons, "dynamic_54_button_ctm.png");
        textColor = textColorOf(menu);
        signature = 0;
    }

    // skyhanni isBlankStack: the black filler pane with nothing to say
    static boolean isBlank(ItemStack stack) {
        return stack.is(Items.STAINED_GLASS_PANE.black()) && stack.getCount() == 1 && GameState.strip(stack.getHoverName().getString()).isBlank() && LegacyText.lore(stack).isEmpty();
    }

    // skyhanni isButtonStack: anything with a skyblock id is a thing, not a pane
    private static boolean isButton(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Items.STAINED_GLASS_PANE.black()) && !Gain.sbId(stack).isEmpty();
    }

    private static boolean dye(ItemStack stack) {
        return stack.getItem().toString().endsWith("_dye");
    }

    // one column and row each: which chest spots are buttons and which are slots. first half of processChestIndices
    static boolean[][][] layout(boolean[] blank, boolean[] asButton, int clicked) {
        int rows = blank.length / 9;
        boolean[][] isSlot = new boolean[9][rows], isButton = new boolean[9][rows];
        for (int i = 0; i < blank.length; i++) {
            isButton[i % 9][i / 9] = asButton[i];
            if (!asButton[i] || clicked != i) isSlot[i % 9][i / 9] = !blank[i] && !asButton[i];
        }
        return new boolean[][][]{isButton, isSlot};
    }

    // neighbours of a spot + which piece of the picture they mean
    static int piece(boolean[][] kind, int x, int y) {
        boolean up = y > 0 && kind[x][y - 1], right = x < kind.length - 1 && kind[x + 1][y], down = y < kind[x].length - 1 && kind[x][y + 1], left = x > 0 && kind[x - 1][y];
        boolean upLeft = y > 0 && x > 0 && kind[x - 1][y - 1], upRight = y > 0 && x < kind.length - 1 && kind[x + 1][y - 1];
        boolean downRight = x < kind.length - 1 && y < kind[x + 1].length - 1 && kind[x + 1][y + 1], downLeft = x > 0 && y < kind[x - 1].length - 1 && kind[x - 1][y + 1];
        return new CtmIndex(up, right, down, left, upLeft, upRight, downRight, downLeft).index();
    }

    private static void draw(boolean[][] kind, BufferedImage from, BufferedImage to, int x, int y, int xMult, int yMult) {
        int index = piece(kind, x, y);
        int[] rgb = from.getRGB((index % 12) * 19 * xMult, (index / 12) * 19 * yMult, 18 * xMult, 18 * yMult, null, 0, 18 * yMult);
        to.setRGB((7 + x * 18) * xMult, (17 + y * 18) * yMult, 18 * xMult, 18 * yMult, rgb, 0, 18 * yMult);
    }

    private static void upload(BufferedImage made) {
        Minecraft mc = Minecraft.getInstance();
        if (pixels == null) {
            pixels = new NativeImage(NativeImage.Format.RGBA, made.getWidth(), made.getHeight(), false);
            texture = new DynamicTexture(() -> "sbcompanion improved menus", pixels);
            mc.getTextureManager().register(TEXTURE, texture);
        }
        for (int y = 0; y < made.getHeight(); y++) for (int x = 0; x < made.getWidth(); x++) pixels.setPixel(x, y, made.getRGB(x, y));
        texture.upload();
    }

    // skyhanni generateModernTex, only when the chest contents changed. theirs does it every frame
    private static boolean update(ContainerScreen screen) {
        long now = System.currentTimeMillis();
        if (now - checkedAt < CHECK_MS) return texture != null && drawing;
        checkedAt = now;
        Container chest = screen.getMenu().getContainer();
        int size = chest.getContainerSize();
        boolean[] blank = new boolean[size], asButton = new boolean[size];
        boolean hasItem = false, hasPane = false;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        boolean stakes = title.contains("Stakes"), sequencer = title.startsWith("Ultrasequencer") && !stakes, pairs = title.startsWith("Superpairs") && !stakes;
        for (int i = 0; i < size; i++) {
            ItemStack st = chest.getItem(i);
            hasItem |= !st.isEmpty();
            blank[i] = isBlank(st);
            hasPane |= blank[i];
            asButton[i] = isButton(st) && !(sequencer && dye(st)) && !(pairs && i > 9 && i < size - 9);
        }
        if (!hasItem || !hasPane) return false;
        loadImages();
        if (base == null || slot == null || button == null) return false;
        int clicked = now - clickedAt <= CLICK_SHOWN_MS ? clickedSlot : -1;
        int sig = 31 * (31 * Arrays.hashCode(blank) + Arrays.hashCode(asButton)) + clicked;
        if (sig == signature && texture != null) return true;
        signature = sig;
        boolean[][][] kinds = layout(blank, asButton, clicked);
        int xMult = base.getWidth() / 256, yMult = base.getHeight() / 256;
        BufferedImage made = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = made.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.dispose();
        for (int i = 0; i < size; i++) {
            int x = i % 9, y = i / 9;
            if (kinds[0][x][y]) draw(kinds[0], button, made, x, y, xMult, yMult);
            else if (kinds[1][x][y]) draw(kinds[1], slot, made, x, y, xMult, yMult);
        }
        upload(made);
        return true;
    }

    // skyhanni MixinContainerScreen.getCustomTexture: the chest bg for this frame
    public static Identifier background(Identifier original) {
        try {
            if (!GameState.inSkyBlock || !Features.on(ID) || !(Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen)
                    || DISALLOWED.matcher(GameState.strip(screen.getTitle().getString())).find()) {
                drawing = false;
                return original;
            }
            drawing = update(screen);
            return drawing ? TEXTURE : original;
        } catch (Exception e) {
            drawing = false;
            return original;
        }
    }

    // skyhanni onSlotPre: filler panes dont get drawn. true keeps it
    public static boolean drawsSlot(Slot s) {
        return !drawing || !isBlank(s.getItem());
    }

    // from skyhanni slotCanBeHighlighted
    public static boolean highlights(Slot s, boolean original) {
        return drawing ? !isBlank(s.getItem()) && original : original;
    }

    // from skyhanni getTextColor
    public static int labelColor(int original) {
        return drawing ? textColor | 0xFF000000 : original;
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Menus", "Improved SkyBlock menus", "Changes how SkyBlock's chests look: the filler panes go, and slots and buttons get a darker or lighter background. Every slot stays where it is.", "SkyHanni: Improved SkyBlock Menus")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .left(() -> drawing = false)
                .option(Option.choice("improved_menus.menu_style", "Menu background", "", STYLES.get(0), STYLES.toArray(new String[0])))
                .option(Option.choice("improved_menus.button_style", "Button background", "", STYLES.get(0), STYLES.toArray(new String[0]))));
        Interactions.onSlotClick((screen, s, slotId, button) -> {
            if (drawing) {
                clickedSlot = slotId;
                clickedAt = System.currentTimeMillis();
                checkedAt = 0;
            }
            return false;
        });
    }
}
