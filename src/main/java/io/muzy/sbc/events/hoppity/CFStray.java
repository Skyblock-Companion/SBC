// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import com.google.gson.JsonElement;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.events.EventSheet;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// stray rabbits in the factory, from skyhanni CFStrayWarning (sound, slot colour, screen flash for the kind
// you pick, optionally no closing while one is there), CFStrayTimer (30s after a meal egg when strays can
// come), HoppityTextureHandler (rarity by skull)
public final class CFStray {
    public static final String WARNING = "cf_stray_warning", TIMER = "cf_stray_timer";
    private static final long TIMER_LENGTH = 30_000;
    private static final String[] LEVELS = {"Special", "Legendary", "Epic", "Rare", "Uncommon", "All", "None"};

    // one stray as the warning sees it: slot, name, lore, skull
    public record Stray(int slot, String name, List<String> lore, String skull) {}

    private static Set<Integer> activeSlots = Set.of();
    private static boolean flash;
    private static long timer, lastChocoTax = -1, lastPing = HoppityEnv.FAR_PAST;
    private static Map<String, String> rarityBySkull;

    private CFStray() {}

    private static boolean on(String id) {
        Feature f = Features.get(id);
        return f == null || f.enabled();
    }

    // --- HoppityTextureHandler ---

    private static synchronized Map<String, String> textures() {
        if (rarityBySkull != null) return rarityBySkull;
        Map<String, String> out = new HashMap<>();
        io.muzy.sbc.core.data.DataFiles.read(io.muzy.sbc.core.data.DataFiles.Source.SKYHANNI, "HoppityRabbitTextures.json", d -> {
            for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("textures").entrySet()) {
                String rarity = e.getKey().toUpperCase(Locale.ROOT);
                for (JsonElement entry : e.getValue().getAsJsonArray()) {
                    var o = entry.getAsJsonObject();
                    if (o.has("texture_value_b64")) out.put(o.get("texture_value_b64").getAsString(), rarity);
                    if (o.has("skull_id")) out.put(o.get("skull_id").getAsString(), rarity);
                }
            }
        });
        rarityBySkull = out;
        return out;
    }

    static boolean rarityOrHigher(String skull, String rarity) {
        String found = textures().get(skull);
        return found != null && HoppityApi.RARITIES.indexOf(found) >= HoppityApi.RARITIES.indexOf(rarity);
    }

    // --- CFStrayWarning ---

    static boolean special(Stray s) {
        return Patterns.get("SH.misc.chocolatefactory.rabbit.clickme.golden").matcher(s.name).matches() || HoppityData.specialRabbits().contains(s.skull);
    }

    // shouldWarnAboutStray, same check for the flash
    static boolean matchesLevel(Stray s, String level) {
        return switch (level) {
            case "Special" -> special(s);
            case "Legendary", "Epic", "Rare", "Uncommon" -> rarityOrHigher(s.skull, level.toUpperCase(Locale.ROOT));
            case "All" -> Patterns.get("SH.misc.chocolatefactory.rabbit.clickme").matcher(s.name).matches() || special(s);
            default -> false;
        };
    }

    private static boolean caught(Stray s) {
        return Patterns.get("SH.misc.chocolatefactory.rabbit.caught").matcher(String.join(" ", s.lore)).matches();
    }

    private static void warn(Stray s) {
        if (caught(s)) return;
        boolean clickMe = Patterns.get("SH.misc.chocolatefactory.rabbit.clickme").matcher(s.name).matches();
        boolean golden = Patterns.get("SH.misc.chocolatefactory.rabbit.clickme.golden").matcher(s.name).matches();
        if (!clickMe && !golden || !matchesLevel(s, Hoppity.text(WARNING, "level", "All"))) return;
        if (golden || HoppityData.specialRabbits().contains(s.skull)) {
            String sound = Hoppity.text(WARNING, "sound", "block.note_block.pling");
            int repeat = (int) Hoppity.n(WARNING, "repeat", 20);
            for (int i = 0; i < repeat; i++) Hoppity.later(i * 100L, () -> HoppityEnv.world.playSound(sound, 1f));
        } else HoppityEnv.world.playSound("entity.experience_orb.pickup", 1f);
    }

    // onInventoryUpdated: first 3 rows of the factory minus the middle one, thats where strays show
    public static void onInventoryUpdated(List<Stray> items) {
        if (!ChocolateFactory.inChocolateFactory) {
            flash = false;
            return;
        }
        if (!on(WARNING)) {
            activeSlots = Set.of();
            flash = false;
            return;
        }
        Set<Integer> active = new HashSet<>();
        boolean flashNow = false;
        String flashLevel = Hoppity.text(WARNING, "flash", "Special");
        for (Stray s : items) {
            if (s.slot == 13 || s.slot > 26 || s.name.isEmpty()) continue;
            warn(s);
            if (caught(s)) continue;
            active.add(s.slot);
            flashNow |= matchesLevel(s, flashLevel);
        }
        activeSlots = active;
        flash = flashNow;
    }

    public static boolean highlighted(int slot) {
        return activeSlots.contains(slot);
    }

    public static int highlightColor() {
        return Hoppity.color(WARNING, "color", 0x80FF5555);
    }

    // onChestGuiRender: screen colour rn, 0 = none
    public static int flashColor(long now) {
        if (!flash || !ChocolateFactory.inChocolateFactory) return 0;
        int alpha = (int) Math.max(0, Math.min(255, (2 + Math.sin(now / 1000.0)) * 255 / 4));
        return alpha << 24 | (Hoppity.color(WARNING, "flash_color", 0x7F00EEFF) & 0xFFFFFF);
    }

    public static void onInventoryClose() {
        flash = false;
        activeSlots = Set.of();
        if (timer > 0) {
            timer = TIMER_LENGTH;
            lastChocoTax = -1;
        }
    }

    // --- closing ---

    // refuse closing the factory rn? says so if yes
    public static boolean refusesClosing(boolean shift) {
        if (shift || !ChocolateFactory.inChocolateFactory) return false;
        if (on(WARNING) && Hoppity.b(WARNING, "block_closing", false) && !activeSlots.isEmpty()) {
            refused("§cStray Rabbit Prevented Close");
            return true;
        }
        if (timerRuns() && Hoppity.b(TIMER, "block_closing", false)) {
            refused("§cStray Timer Prevented Close");
            return true;
        }
        return false;
    }

    // clicking a factory slot that leaves it counts as closing
    public static boolean refusesSlot(int slotId, boolean shift) {
        return HoppityData.destructiveSlots().contains(slotId) && refusesClosing(shift);
    }

    private static void refused(String title) {
        HoppityEnv.world.title(title + " §7(hold §eShift§7)");
        HoppityEnv.world.playSound("block.note_block.bass", 1f);
    }

    // --- CFStrayTimer ---

    static void onEggFound(HoppityEggType type) {
        if (type == HoppityEggType.STRAY) timer = 0;
        else if (type.isResetting() || type == HoppityEggType.HITMAN || type == HoppityEggType.VISITOR) timer = TIMER_LENGTH;
        else return;
        lastChocoTax = -1;
    }

    public static void onIslandChange() {
        timer = 0;
        lastChocoTax = -1;
    }

    private static boolean timerRuns() {
        return on(TIMER) && ChocolateFactory.inChocolateFactory && timer > 0;
    }

    // every tick. outside the factory it sits at 30s waiting for you to go in
    public static void onTick() {
        if (timer > 0 && !ChocolateFactory.inChocolateFactory) {
            timer = TIMER_LENGTH;
            lastChocoTax = -1;
            return;
        }
        if (!timerRuns()) return;
        long now = HoppityEnv.now();
        if (lastChocoTax >= 0) {
            timer = Math.max(0, timer - (now - lastChocoTax));
            if (timer > 0 && timer < Hoppity.n(TIMER, "ding", 3) * 1000 && HoppityEnv.passedSince(lastPing) > 1000) {
                HoppityEnv.world.playSound("block.note_block.pling", 1f);
                lastPing = now;
            }
        }
        lastChocoTax = now;
    }

    static long timerLeft() {
        return timer;
    }

    public static void reset() {
        activeSlots = Set.of();
        flash = false;
        timer = 0;
        lastChocoTax = -1;
        lastPing = HoppityEnv.FAR_PAST;
    }

    private static void timerSheet(EventSheet s, long left) {
        s.hero("Stray in", String.format(Locale.ROOT, "%.2fs", left / 1000.0), left < 5000 ? Modern.WARN : Modern.AQUA);
        s.line("Window", String.format(Locale.ROOT, "%.0f%%", 100.0 * left / TIMER_LENGTH), Modern.LABEL).meter(left / (double) TIMER_LENGTH, Modern.AQUA);
    }

    static HudElement element() {
        return new EventSheet.Card(TIMER, "Stray timer", "Events", Zone.TOP_RIGHT, 47, TIMER) {
            @Override public boolean visible() { return timerRuns(); }
            @Override protected void live(EventSheet s) { timerSheet(s, timer); }
            @Override protected void sample(EventSheet s) { timerSheet(s, 18_420); }
            @Override protected List<Row> plainRows() { return List.of(new Row("§b" + String.format(Locale.ROOT, "%.2f", timer / 1000.0) + "s")); }
            @Override protected List<Row> plainDemo() { return List.of(new Row("§b18.42s")); }
        };
    }

    static List<Feature> features() {
        return List.of(
                new Feature(WARNING, "Events", "Stray rabbit warning", "A sound and a colour on the slot for a stray in the Chocolate Factory, and the screen flashing for the kinds you choose.", "SkyHanni: Stray Rabbit Warning · Skyblocker: Stray Rabbit Sound")
                        .when(() -> HoppityEnv.world.inSkyBlock())
                        .option(Option.choice("cf_stray_warning.level", "Warn for", "This kind of stray and better.", "All", LEVELS))
                        .option(Option.choice("cf_stray_warning.flash", "Flash the screen for", "", "Special", LEVELS))
                        .option(Option.color("cf_stray_warning.color", "Slot colour", "", 0x80FF5555))
                        .option(Option.color("cf_stray_warning.flash_color", "Flash colour", "", 0x7F00EEFF))
                        .option(Option.text("cf_stray_warning.sound", "Sound for a special one", "The game's name of a sound.", "block.note_block.pling"))
                        .option(Option.integer("cf_stray_warning.repeat", "Times it plays", "", 20, 1, 50))
                        .option(Option.bool("cf_stray_warning.block_closing", "No closing while one is there", "Hold Shift to close anyway.", false)),
                new Feature(TIMER, "Events", "Stray timer", "After a meal egg: 30 seconds in the Chocolate Factory during which strays may come.", "SkyHanni: Stray Timer")
                        .when(() -> HoppityEnv.world.inSkyBlock())
                        .option(Option.integer("cf_stray_timer.ding", "Ding below (seconds)", "0 is never.", 3, 0, 30))
                        .option(Option.bool("cf_stray_timer.block_closing", "No closing while it runs", "Hold Shift to close anyway.", false)));
    }
}
