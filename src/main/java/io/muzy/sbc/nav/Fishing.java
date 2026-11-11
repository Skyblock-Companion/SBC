// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.nav;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// fishing helpers, display only: how long your line is out + a loud mark on the bobber when a fish bites
// (skyblock puts "!!!" over it)
public final class Fishing {
    private static boolean bitten;
    private static int lastHookId = -1;

    private Fishing() {}

    private static void draw(WorldDraw w) {
        var mc = w.mc;
        if (mc.player == null || mc.level == null) return;
        FishingHook hook = mc.player.fishing;
        if (hook == null || !hook.isAlive()) {
            bitten = false;
            lastHookId = -1;
            return;
        }
        if (hook.getId() != lastHookId) {
            lastHookId = hook.getId();
            bitten = false;
        }
        Vec3 at = hook.position();
        boolean bite = false;
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(at, at).inflate(3))) {
            if (e instanceof ArmorStand st && GameState.strip(st.getName().getString()).contains("!!!")) {
                bite = true;
                break;
            }
        }
        if (bite) {
            if (!bitten) {
                bitten = true;
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.6f);
            }
            w.box(hook.getBoundingBox().inflate(0.35), 0xFFFF3B3B, true, true);
            w.label("§cBITE!", at.add(0, 0.9, 0), true);
        } else {
            bitten = false;
            int secs = hook.tickCount / 20;
            w.label("§7" + secs + "s", at.add(0, 0.6, 0), true);
        }
    }

    // ---- sea creature catches ----

    private record Creature(String name, String rarity, boolean rare) {}

    private static java.util.Map<String, Creature> creatures;
    private static final java.util.Map<String, Integer> caught = new java.util.LinkedHashMap<>();

    private static java.util.Map<String, Creature> creatures() {
        if (creatures != null) return creatures;
        java.util.Map<String, Creature> out = new java.util.HashMap<>();
        var root = io.muzy.sbc.core.Repo.constant("SeaCreatures.json");
        if (root != null)
            for (var area : root.entrySet()) {
                if (!area.getValue().isJsonObject() || !area.getValue().getAsJsonObject().has("sea_creatures")) continue;
                for (var e : area.getValue().getAsJsonObject().getAsJsonObject("sea_creatures").entrySet()) {
                    var o = e.getValue().getAsJsonObject();
                    if (!o.has("chat_message")) continue;
                    String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "COMMON";
                    boolean rare = o.has("rare") && o.get("rare").getAsBoolean();
                    out.put(o.get("chat_message").getAsString(), new Creature(e.getKey(), rarity, rare));
                }
            }
        creatures = out;
        return out;
    }

    // the line saying a sea creature came up
    public static boolean isCatchLine(String plain) {
        return creatures().containsKey(plain);
    }

    private static io.muzy.sbc.chat.ChatBus.Result onCatch(io.muzy.sbc.chat.ChatBus.Line line) {
        Creature c = creatures().get(line.plain().trim());
        if (c == null) return io.muzy.sbc.chat.ChatBus.PASS;
        int n = caught.merge(c.name(), 1, Integer::sum);
        io.muzy.sbc.track.ProfitCards.seaCreature(c.name());
        if (Features.on("fishing_catches") && big(c.rare(), c.rarity()) && option("own")) {
            io.muzy.sbc.ui.Toasts.push(option("name") ? c.name() : "Rare catch", c.rarity().charAt(0) + c.rarity().substring(1).toLowerCase() + " catch - " + n + " this session", 0xFFFFD36B);
            var mc = Minecraft.getInstance();
            if (mc.player != null && option("sound")) mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
        }
        return io.muzy.sbc.chat.ChatBus.PASS;
    }

    private static boolean option(String key) {
        return Features.get("fishing_catches").option("fishing_catches." + key).bool();
    }

    private static final java.util.List<String> RARITIES = java.util.List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

    // skyblocker minimumNotificationRarity, the alerts old behaviour is its first choice
    private static boolean big(boolean rare, String rarity) {
        String from = Features.get("fishing_catches").option("fishing_catches.min_rarity").choice();
        if (from.equals("Rare ones")) return rare || rarity.equals("LEGENDARY") || rarity.equals("MYTHIC") || rarity.equals("EPIC");
        return RARITIES.indexOf(rarity) >= RARITIES.indexOf(from.toUpperCase());
    }

    // from skyhanni SeaCreatureFeatures.onSkyblockMobFirstSeen: a rare one that came up for someone else
    private static void otherCatch(io.muzy.sbc.areas.FishState.Live l) {
        if (l.own || !Features.on("fishing_catches") || !option("other") || !big(l.kind.rare(), l.kind.rarity())) return;
        if (l.kind.name().equals("Water Hydra") && l.maxHp > 0 && l.hp * 2 == l.maxHp) return;
        io.muzy.sbc.areas.FishHooks.title(option("name") ? l.kind.name() + " NEARBY!" : "RARE SEA CREATURE!");
        if (option("sound")) io.muzy.sbc.areas.FishHooks.pling();
    }

    // what you caught since the game started, newest kind last
    public static java.util.Map<String, Integer> caught() {
        return caught;
    }

    public static void register() {
        Features.add(new Feature("fishing_hook", "Fishing", "Bobber timer and bite mark", "Shows how long your line has been out and marks the bobber with a sound when a fish bites.", "SkyHanni: fishing timer")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).world(Fishing::draw));
        Feature sc = Features.add(new Feature("fishing_catches", "Fishing", "Sea creature alerts", "Counts the sea creatures you catch and gives a toast and a chime for the rare ones.", "SkyHanni: sea creature tracker")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("fishing_catches.own", "Alert for your own", "", true))
                .option(Option.bool("fishing_catches.other", "Alert for other players' catches", "A title when a rare one comes up near you.", false))
                .option(Option.bool("fishing_catches.name", "Say which creature", "", true))
                .option(Option.bool("fishing_catches.sound", "With a sound", "", true))
                .option(Option.choice("fishing_catches.min_rarity", "Alert from", "", "Rare ones", "Rare ones", "Rare", "Epic", "Legendary", "Mythic"))
                .option(Option.bool("fishing_catches.highlight", "Outline rare ones", "Up to 30 blocks.", false)));
        io.muzy.sbc.areas.FishState.ON_SPAWN.add(Fishing::otherCatch);
        io.muzy.sbc.chat.ChatBus.register(io.muzy.sbc.chat.ChatBus.OBSERVE + 8, "seacreatures", null, Fishing::onCatch);
    }
}
