// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Option;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

// from skyhanni ColdOverlay + skyblocker GlaciteColdOverlay: vanillas powder snow frost around the screen,
// stronger as cold goes up from threshold to max
// settings are skyhannis two numbers, cold comes from MiningApi
public final class ColdOverlay {
    static final String ID = "mining_cold_overlay";
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    private static int cold, lastCold;
    private static long lastUpdate;

    private ColdOverlay() {}

    // skyhanni getColdAlpha + NumberUtil.interpolate over its 200 ms
    static float alpha(int cold, int lastCold, long sinceUpdate, int threshold, double maxAlpha) {
        float t = Math.min(1f, sinceUpdate / 200f);
        float interp = lastCold + (cold - lastCold) * t;
        float percentage = threshold >= 100 ? 0 : (interp - threshold) / (100 - threshold);
        return Math.max(0f, percentage) * (float) (maxAlpha / 100);
    }

    private static void onCold(int value) {
        lastCold = cold;
        cold = value;
        lastUpdate = System.currentTimeMillis();
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Cold frost", "Frost around the screen as your Cold rises in the Glacite Tunnels and Mineshafts. It changes how the game looks, so it starts off.", "SkyHanni: Cold Overlay; Skyblocker: Cold Overlay")
                .when(MiningEnv::isColdIsland).defaultOff()
                .option(Option.integer("mining_cold_overlay.threshold", "Starts at Cold", "", 25, 0, 100))
                .option(Option.integer("mining_cold_overlay.max_alpha", "Strongest (%)", "How strong the frost gets at 100 Cold.", 100, 0, 100)));
        MiningApi.onColdUpdate(ColdOverlay::onCold);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("sbcompanion", "cold_overlay"), Safe.hud("ColdOverlay", (g, delta) -> {
            if (!Features.on(ID) || !MiningEnv.isColdIsland()) return;
            float a = alpha(cold, lastCold, System.currentTimeMillis() - lastUpdate, MiningPack.number(ID, "mining_cold_overlay.threshold", 25), MiningPack.number(ID, "mining_cold_overlay.max_alpha", 100));
            if (a <= 0f) return;
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight(), ARGB.white(Math.min(1f, a)));
        }));
    }
}
