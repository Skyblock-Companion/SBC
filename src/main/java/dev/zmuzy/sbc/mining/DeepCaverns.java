// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.render.ParkourHelper;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// from skyhanni DeepCavernsGuide + DeepCavernsParkour.json: the way down the deep caverns to the bottom and rhys
// starts by itself when the lift operator says you havent gone down yet, or the lift menu shows you never
// reached the obsidian sanctuary
// skyhanni puts the start button on a lift slot, here its next to the chest
public final class DeepCaverns {
    static final String ID = "mining_deep_caverns";
    private static final Patterns.P NOT_UNLOCKED = Patterns.p("SH.features.mining.deepcavernsguide.notunlocked");
    private static final String AREA = "Gunpowder Mines";
    private static ParkourHelper helper;
    private static boolean loaded, show, liftRead;
    private static final Panel PANEL = new Panel();

    private DeepCaverns() {}

    private static synchronized ParkourHelper helper() {
        if (loaded) return helper;
        loaded = true;
        try (InputStream in = dev.zmuzy.sbc.core.data.DataFiles.sh("DeepCavernsParkour.json")) {
            if (in == null) return null;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Vec3> locations = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("locations")) {
                String[] p = e.getAsString().split(":");
                locations.add(new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            }
            List<ParkourHelper.ShortCut> cuts = new ArrayList<>();
            if (o.has("shortCuts")) for (JsonElement e : o.getAsJsonArray("shortCuts")) {
                JsonObject c = e.getAsJsonObject();
                cuts.add(new ParkourHelper.ShortCut(c.get("from").getAsInt(), c.get("to").getAsInt()));
            }
            helper = new ParkourHelper(locations, cuts, 1.0, 3.5, false, () -> show = false, false);
            helper.playerPos = () -> {
                var p = Minecraft.getInstance().player;
                return p == null ? Vec3.ZERO : p.position();
            };
            helper.onGround = () -> {
                var p = Minecraft.getInstance().player;
                return p != null && p.onGround();
            };
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad DeepCavernsParkour.json", e);
        }
        return helper;
    }

    private static void start(String why) {
        if (show) return;
        show = true;
        if (helper() != null) helper.reset();
        MiningPack.chat(why);
    }

    private static void applyOptions() {
        if (helper == null) return;
        helper.rainbowColor = MiningPack.on(ID, "mining_deep_caverns.rainbow");
        helper.monochromeColor = MiningPack.color(ID, "mining_deep_caverns.color", 0x3C0000FF);
        helper.lookAhead = MiningPack.number(ID, "mining_deep_caverns.look_ahead", 3) + 1;
        helper.showEverything = MiningPack.on(ID, "mining_deep_caverns.all_lines");
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (AREA.equals(GameState.area) && NOT_UNLOCKED.matcher(LegacyText.of(line.component())).matches())
            start("Automatically enabling the Deep Caverns Guide, helping you find the way to the bottom of the Deep Caverns and the path to Rhys.");
        return ChatBus.PASS;
    }

    private static void readLift(MenuView v) {
        if (!AREA.equals(GameState.area) || liftRead) return;
        liftRead = true;
        MenuView.Item it = v.item(31);
        if (!it.empty() && !it.clean.equals("Obsidian Sanctuary")) start("Automatically enabling the Deep Caverns Guide, helping you find the way to the bottom of the Deep Caverns and the path to Rhys.");
    }

    private static void draw(WorldDraw w) {
        if (!show || helper() == null) return;
        applyOptions();
        helper.render(w);
    }

    private static void leave() {
        show = false;
        liftRead = false;
        if (helper != null) helper.reset();
    }

    private static final class Panel implements SideHelper.Content {
        @Override
        public String title() {
            return "Deep Caverns";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("map");
        }

        @Override
        public int accent() {
            return 0xFF7FA6FF;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Guide"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            return 0;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            if (show) {
                ui.textShadow("§aThe guide is on.", x + 2, y + 1, Modern.LABEL);
                return 12;
            }
            boolean hv = ui.hover(x, y, w, 14);
            ui.rect(x, y, w, 14, hv ? 0x66FFFFFF : 0x33000000);
            ui.textShadow("§eShow the way down", x + 4, y + 3, Modern.INK);
            ui.click(x, y, w, 14, () -> start("Manually enabled Deep Caverns Guide."));
            return 16;
        }
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || !AREA.equals(GameState.area)) return null;
        return "Lift".equals(GameState.strip(s.getTitle().getString()).trim()) ? PANEL : null;
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Mining", "Deep Caverns guide", "The way to the bottom of the Deep Caverns and to Rhys, step by step, until you know your way: it starts by itself, or from a button beside the Lift.", "SkyHanni: Deep Caverns Guide")
                .when(() -> "DEEP_CAVERNS".equals(GameState.island) || SbcMod.DEV).world(DeepCaverns::draw).left(DeepCaverns::leave)
                .option(Option.integer("mining_deep_caverns.look_ahead", "Steps ahead", "", 3, 1, 30))
                .option(Option.bool("mining_deep_caverns.all_lines", "Show all the lines", "", false))
                .option(Option.bool("mining_deep_caverns.rainbow", "Rainbow", "", true))
                .option(Option.color("mining_deep_caverns.color", "Colour without rainbow", "", 0x3C0000FF)));
        ChatBus.register(ChatBus.OBSERVE + 28, ID, f, DeepCaverns::onChat);
        MenuMarks.add(ID, "Lift", DeepCaverns::readLift);
        SideHelper.add(DeepCaverns::panel);
    }
}
