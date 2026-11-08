// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.combat;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// inferno demonlord. from skyhanni BlazeSlayerDaggerHelper, HellionShield(Helper),
// BlazeSlayerFirePitsWarning, FirePillarDisplay, BlazeSlayerClearView
// and skyblocker AttunementColors, FirePillarAnnouncer, HellionShieldFilter
// hypixels big attunement title stays, we cant drop a title. so the daggers read attunement from lore every
// sec + on right click
public final class BlazeSlayer {
    public static final String COLORED = "blaze_attunement_colors", DAGGERS = "blaze_daggers", PITS = "blaze_fire_pits", PILLAR = "blaze_fire_pillar", CLEAR = "blaze_clear_view";
    private static final Patterns.P ATTUNEMENT = Patterns.p("SH.slayer.blaze.dagger.attunement"), PILLAR_NAME = Patterns.p("SH.slayer.blaze.firepillar.entityname");
    // from skyblocker FirePillarAnnouncer.FIRE_PILLAR_PATTERN
    private static final Pattern SB_PILLAR = Pattern.compile("(\\d+)s \\d+ hits");
    private static final BossType[] ALL_BLAZES = {BossType.SLAYER_BLAZE_1, BossType.SLAYER_BLAZE_2, BossType.SLAYER_BLAZE_3, BossType.SLAYER_BLAZE_4, BossType.SLAYER_BLAZE_TYPHOEUS_1, BossType.SLAYER_BLAZE_TYPHOEUS_2,
            BossType.SLAYER_BLAZE_TYPHOEUS_3, BossType.SLAYER_BLAZE_TYPHOEUS_4, BossType.SLAYER_BLAZE_QUAZII_1, BossType.SLAYER_BLAZE_QUAZII_2, BossType.SLAYER_BLAZE_QUAZII_3, BossType.SLAYER_BLAZE_QUAZII_4};
    private static final BossType[] LATE_BLAZES = {BossType.SLAYER_BLAZE_3, BossType.SLAYER_BLAZE_4, BossType.SLAYER_BLAZE_QUAZII_3, BossType.SLAYER_BLAZE_QUAZII_4, BossType.SLAYER_BLAZE_TYPHOEUS_3, BossType.SLAYER_BLAZE_TYPHOEUS_4};

    // from skyhanni HellionShield
    public enum Shield {
        AURIC("§e§lAURIC", "Auric", "§e", 0xFFFF55), ASHEN("§8§lASHEN", "Ashen", "§8", 0x404040), SPIRIT("§f§lSPIRIT", "Spirit", "§f", 0xFFFFFF), CRYSTAL("§b§lCRYSTAL", "Crystal", "§b", 0x00FFFF);

        public final String formatted, clean, chat;
        public final int rgb;

        Shield(String formatted, String clean, String chat, int rgb) {
            this.formatted = formatted;
            this.clean = clean;
            this.chat = chat;
            this.rgb = rgb;
        }

        Shield other() {
            return switch (this) {
                case AURIC -> ASHEN;
                case ASHEN -> AURIC;
                case SPIRIT -> CRYSTAL;
                case CRYSTAL -> SPIRIT;
            };
        }
    }

    enum Dagger {
        TWILIGHT(List.of("Twilight Dagger", "Mawdredge Dagger", "Deathripper Dagger"), Shield.SPIRIT, Shield.CRYSTAL),
        FIREDUST(List.of("Firedust Dagger", "Kindlebane Dagger", "Pyrochaos Dagger"), Shield.ASHEN, Shield.AURIC);

        final List<String> names;
        final Shield first, second;

        Dagger(List<String> names, Shield first, Shield second) {
            this.names = names;
            this.first = first;
            this.second = second;
        }

        Dagger other() {
            return this == TWILIGHT ? FIREDUST : TWILIGHT;
        }

        static Dagger of(String itemName) {
            for (Dagger d : values()) for (String n : d.names) if (itemName.contains(n)) return d;
            return null;
        }
    }

    private static final Map<Mobs.Mob, Shield> SHIELDS = new HashMap<>();
    private static final Map<Dagger, Shield> attuned = new EnumMap<>(Dagger.class);
    private static String top, bottom;
    private static Shield nearest;
    private static boolean nearBlaze;
    private static long lastPits;
    private static String pillar;
    private static long pillarSeen;
    private static int lastAnnounced = -1;

    private BlazeSlayer() {}

    // from skyhanni HellionShieldHelper.setHellionShield
    static void setShield(Mobs.Mob mob, Shield s) {
        if (s == null) SHIELDS.remove(mob);
        else SHIELDS.put(mob, s);
    }

    // "Attuned: Spirit" in the lore
    private static void readDaggers(Minecraft mc) {
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
            Dagger d = Dagger.of(st.getHoverName().getString());
            if (d == null) continue;
            for (String line : LegacyText.lore(st)) {
                if (!line.contains("§7Attuned: ")) continue;
                if (line.contains(d.first.clean)) attuned.put(d, d.first);
                else if (line.contains(d.second.clean)) attuned.put(d, d.second);
            }
        }
    }

    private static Shield active(Dagger d) {
        return attuned.getOrDefault(d, d.first);
    }

    // shield of the closest living one within 10 blocks
    private static Shield findNearest(Minecraft mc) {
        Shield best = null;
        double dist = 10;
        for (var e : SHIELDS.entrySet()) {
            var b = e.getKey().base;
            if (b.isRemoved() || Combat.health(b) <= 0) continue;
            double d = b.position().distanceTo(mc.player.position());
            if (d < dist) {
                dist = d;
                best = e.getValue();
            }
        }
        return best;
    }

    // which name shows how in the 2 lines
    private static String format(Dagger holding, boolean activeLine, Dagger compare, Map<Dagger, Shield> attuned, Shield near) {
        boolean inHand = holding == compare;
        Dagger dagger = inHand ? holding : holding.other();
        Shield shield = attuned.getOrDefault(dagger, dagger.first);
        if (!activeLine) shield = shield.other();
        if (inHand && activeLine) {
            if (near == null) return "§7[" + shield.chat + shield.clean + "§7]";
            if (shield == near) return "§a[" + shield.chat + shield.clean.toUpperCase(Locale.ROOT) + "§a]";
            return "§c[§m" + shield.chat + shield.clean + "§c]";
        }
        return shield == near ? "§6[" + shield.chat + shield.clean + "§6]" : shield.chat + shield.clean;
    }

    private static void tickDaggers(Minecraft mc) {
        Dagger holding = Dagger.of(mc.player.getMainHandItem().getHoverName().getString());
        if (holding == null) {
            top = bottom = null;
            return;
        }
        if (mc.player.tickCount % 20 == 0) readDaggers(mc);
        if (mc.player.tickCount % 2 == 0) nearest = Combat.opt(DAGGERS, "mark_right") ? findNearest(mc) : null;
        Dagger first = Combat.choice(DAGGERS, "first", "Spirit or Crystal").startsWith("Spirit") ? Dagger.TWILIGHT : Dagger.FIREDUST;
        Dagger second = first.other();
        top = format(holding, true, first, attuned, nearest) + " " + format(holding, true, second, attuned, nearest);
        bottom = format(holding, false, first, attuned, nearest) + " " + format(holding, false, second, attuned, nearest);
    }

    // right click flips the attunement before the lore catches up
    private static boolean onItem(boolean right) {
        var mc = Minecraft.getInstance();
        if (!right || mc.player == null || !Features.on(DAGGERS)) return false;
        Dagger d = Dagger.of(mc.player.getMainHandItem().getHoverName().getString());
        if (d != null) attuned.put(d, active(d).other());
        return false;
    }

    private static void tickPillar(Minecraft mc) {
        if (mc.player.tickCount % 2 != 0) return;
        long now = System.currentTimeMillis();
        boolean fighting = SlayerApi.active() == SlayerApi.Type.INFERNO;
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(24), ArmorStand::hasCustomName)) {
            Matcher m = PILLAR_NAME.matcher(LegacyText.of(a.getName(), true, false));
            if (!m.matches()) continue;
            pillar = m.group("seconds");
            pillarSeen = now;
            Matcher sb = SB_PILLAR.matcher(GameState.strip(a.getName().getString()));
            if (fighting && Combat.opt(PILLAR, "title") && sb.matches()) {
                int seconds = Integer.parseInt(sb.group(1));
                if (seconds <= 5 && seconds != lastAnnounced) {
                    lastAnnounced = seconds;
                    Combat.title("§5§l" + GameState.strip(a.getName().getString()), 15);
                    if (Combat.opt(PILLAR, "sound")) Combat.pling(1f);
                }
            }
            return;
        }
        if (now - pillarSeen > 500) {
            pillar = null;
            lastAnnounced = -1;
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        SHIELDS.keySet().removeIf(m -> m.base.isRemoved());
        if (Features.on(DAGGERS)) tickDaggers(mc);
        else top = bottom = null;
        if (Features.on(PILLAR)) tickPillar(mc);
        else pillar = null;
        if (mc.player.tickCount % 60 == 0) nearBlaze = DamageIndicator.distanceTo(ALL_BLAZES) < 10;
        if (Features.on(PITS) && mc.player.tickCount % 10 == 0 && System.currentTimeMillis() - lastPits < 2000 && SlayerApi.inBossFight() && DamageIndicator.spawned(LATE_BLAZES))
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
    }

    // from skyhanni BlazeSlayerFirePitsWarning.onBossHealthChange
    private static void onHealth(DamageIndicator.Boss b, long last, long health, long max) {
        if (!Features.on(PITS) || !SlayerApi.inBossFight()) return;
        if (b.type != BossType.SLAYER_BLAZE_3 && b.type != BossType.SLAYER_BLAZE_4) return;
        double third = max * 0.33;
        if (health < third && last > third) {
            Combat.title("§cFire Pits!", 40);
            lastPits = System.currentTimeMillis();
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Combat.opt(DAGGERS, "hide_chat")) return ChatBus.PASS;
        String m = LegacyText.of(line.component());
        return ATTUNEMENT.matcher(m).matches() || m.equals("§cYour hit was reduced by Hellion Shield!") ? ChatBus.CANCEL : ChatBus.PASS;
    }

    private static void left() {
        SHIELDS.clear();
        top = bottom = pillar = null;
        nearest = null;
        nearBlaze = false;
        lastAnnounced = -1;
    }

    private static RichCard.Content daggerCard(String a, String b) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Attuned", a).raw(), RichCard.Line.row("Other", b).raw()));
    }

    private static final HudElement DAGGER_CARD = new RichCard(DAGGERS, "Blaze daggers", "Slayer", Zone.BOTTOM_CENTER, 42, DAGGERS) {
        @Override public boolean visible() {
            return top != null;
        }

        @Override protected Content live() {
            return top == null ? new Content("", List.of()) : daggerCard(top, bottom);
        }

        @Override protected Content sample() {
            return daggerCard("§a[§fSPIRIT§a] §8Ashen", "§bCrystal §6[§eAuric§6]");
        }

        @Override protected List<Row> classicRows() {
            return top == null ? List.of() : List.of(new Row("Attuned", top, 0), new Row("Other", bottom, 0));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Attuned", "§a[§fSPIRIT§a] §8Ashen", 0), new Row("Other", "§bCrystal §6[§eAuric§6]", 0));
        }
    };

    private static RichCard.Content pillarCard(String seconds) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Fire Pillar", "§b" + seconds + "s  §c8 hits").raw()));
    }

    private static final HudElement PILLAR_CARD = new RichCard(PILLAR, "Fire Pillar", "Slayer", Zone.BOTTOM_CENTER, 43, PILLAR) {
        @Override public boolean visible() {
            return pillar != null;
        }

        @Override public String heading() {
            return "";
        }

        @Override protected Content live() {
            return pillar == null ? new Content("", List.of()) : pillarCard(pillar);
        }

        @Override protected Content sample() {
            return pillarCard("5");
        }

        @Override protected List<Row> classicRows() {
            return pillar == null ? List.of() : List.of(new Row("Fire Pillar", pillar + "s", Theme.current().bad()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Fire Pillar", "5s", Modern.BAD));
        }
    };

    static void register() {
        Features.add(new Feature(COLORED, "Slayer", "Hellion shield colours", "An Inferno Demonlord and its demons glow in the colour of their shield, and it is written over them.", "Skyblocker: Attunement Highlights · SkyHanni: Colored Mobs").when(Combat::sb));
        Feature daggers = Features.add(new Feature(DAGGERS, "Slayer", "Blaze daggers", "While you hold a blaze dagger: which attunements it and its twin are on, and which one the nearest shield wants.", "SkyHanni: Blaze Daggers").when(Combat::sb)
                .option(Option.choice("blaze_daggers.first", "Which comes first", "", "Spirit or Crystal", "Spirit or Crystal", "Ashen or Auric"))
                .option(Option.bool("blaze_daggers.mark_right", "Mark the right one", "", true))
                .option(Option.bool("blaze_daggers.hide_chat", "Hide the chat lines", "'Strike using the...' and 'Your hit was reduced...'.", false)));
        ChatBus.register(ChatBus.FILTER, DAGGERS, daggers, BlazeSlayer::onChat);
        Features.add(new Feature(PITS, "Slayer", "Fire Pits warning", "A title and a sound when an Inferno Demonlord III or IV falls under a third of its health.", "SkyHanni: Fire Pits").when(Combat::sb));
        Features.add(new Feature(PILLAR, "Slayer", "Fire Pillar", "The seconds of a Fire Pillar near you, and a title for the last five when it is your slayer.", "SkyHanni: Pillar Display · Skyblocker: Fire Pillar Countdown").when(() -> Combat.island("CRIMSON_ISLE"))
                .option(Option.bool("blaze_fire_pillar.title", "Countdown title", "", true))
                .option(Option.bool("blaze_fire_pillar.sound", "Countdown sound", "", true)));
        Features.add(new Feature(CLEAR, "Slayer", "Clear view near the Demonlord", "Hides all particles and fireballs within ten blocks of an Inferno Demonlord.", "SkyHanni: Clear View").when(Combat::sb).defaultOff());
        CombatMarks.particles(() -> Features.on(CLEAR) && nearBlaze, (type, x, y, z) -> true);
        CombatMarks.hiding(() -> Features.on(CLEAR) && nearBlaze, e -> e instanceof Fireball);
        CombatMarks.glowing(mark -> {
            if (!Features.on(COLORED)) return;
            for (var e : SHIELDS.entrySet()) CombatMarks.seenGlow(mark, e.getKey().base, e.getValue().rgb);
            for (DamageIndicator.Boss b : DamageIndicator.bosses())
                if (java.util.Arrays.asList(ALL_BLAZES).contains(b.type) && Combat.tagWith(b.entity(), 3, "IMMUNE") != null) CombatMarks.seenGlow(mark, b.entity(), 0xFF0000);
        });
        Features.add(new Feature("blaze_tick", "Slayer", "Inferno Demonlord background", "Keeps the Inferno Demonlord helpers running.", "").alwaysOn().when(Combat::sb).tick(BlazeSlayer::tick).left(BlazeSlayer::left));
        DamageIndicator.onHealthChange(BlazeSlayer::onHealth);
        Interactions.onItemClick(BlazeSlayer::onItem);
        HudLayout.add(DAGGER_CARD);
        HudLayout.add(PILLAR_CARD);
    }

    // tests: the 2 lines without the game
    static String[] lines(String holdingName, Shield near, boolean spiritFirst, Map<String, Shield> on) {
        Map<Dagger, Shield> a = new EnumMap<>(Dagger.class);
        on.forEach((k, v) -> a.put(Dagger.valueOf(k), v));
        Dagger holding = Dagger.of(holdingName);
        Dagger first = spiritFirst ? Dagger.TWILIGHT : Dagger.FIREDUST, second = first.other();
        List<String> out = new ArrayList<>();
        out.add(format(holding, true, first, a, near) + " " + format(holding, true, second, a, near));
        out.add(format(holding, false, first, a, near) + " " + format(holding, false, second, a, near));
        return out.toArray(new String[0]);
    }
}
