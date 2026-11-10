// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.combat;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

// arachne stuff. from skyhanni ArachneKillTimer, ArachneSpawnTimer, ArachneMinisNametagHider,
// ArachneChatMessageHider + the MobHighlight line
public final class Arachne {
    public static final String ID = "arachne", HIDE = "arachne_chat_hider";
    private static final Patterns.P CALLING = Patterns.p("SH.chat.arachne.calling"), CRYSTAL = Patterns.p("SH.chat.arachne.crystal"), CALLING_SPAWNED = Patterns.p("SH.chat.arachne.calling.spawned"),
            CRYSTAL_SPAWNED = Patterns.p("SH.chat.arachne.crystal.spawned"), DEAD = Patterns.p("SH.chat.arachne.dead"), DAMAGE = Patterns.p("SH.chat.arachne.damage"), SPAWN = Patterns.p("SH.chat.arachne.spawn"),
            VENOM = Patterns.p("SH.chat.arachne.venom"), SPAWN_FRAGMENT = Patterns.p("SH.combat.mobs.spawntime.arachne.fragment"), SPAWN_CRYSTAL = Patterns.p("SH.combat.mobs.spawntime.arachne.crystal");
    private static final Vec3 ALTAR = new Vec3(-283, 51, -179);
    private static final String SANCTUARY = "Arachne's Sanctuary";
    private static final String DOWN_LINE = "                              §r§6§lARACHNE DOWN!", BORDER = "§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    private static long spawnedAt, killTime, spawnAt, searchAt, lastTickAt;
    private static boolean saveParticles, hideDead;
    private static int particles;

    private Arachne() {}

    private static boolean spidersDen() {
        return Combat.island("SPIDER_DEN");
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String m = LegacyText.of(line.component());
        if (Combat.opt(ID, "kill_timer")) killTimer(m);
        if (Combat.opt(ID, "spawn_timer") && SANCTUARY.equals(GameState.area)) spawnTimer(line.plain().trim().toLowerCase(Locale.ROOT));
        return ChatBus.PASS;
    }

    // from skyhanni ArachneKillTimer.onChat
    private static void killTimer(String m) {
        long now = System.currentTimeMillis();
        if (CALLING_SPAWNED.matcher(m).matches() || CRYSTAL_SPAWNED.matcher(m).matches()) spawnedAt = now;
        if (DEAD.matcher(m).matches() && spawnedAt != 0) killTime = now - spawnedAt;
        if (CALLING.matcher(m).matches() || CRYSTAL.matcher(m).matches()) spawnedAt = 0;
        if (killTime > 0 && DAMAGE.matcher(m).matches()) {
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("                   §eArachne took §b" + Combat.seconds(killTime) + "§e to kill."));
            killTime = 0;
            spawnedAt = 0;
        }
    }

    // from skyhanni ArachneSpawnTimer.onChat. crystal time = how many particles it gives off 3 sec in
    private static void spawnTimer(String lower) {
        if (SPAWN_CRYSTAL.matcher(lower).matches()) {
            saveParticles = true;
            searchAt = System.currentTimeMillis();
            particles = 0;
            lastTickAt = 0;
        } else if (SPAWN_FRAGMENT.matcher(lower).matches()) {
            spawnAt = System.currentTimeMillis() + 19_000;
        }
    }

    private static void onParticle(ParticleHooks.Particle p) {
        if (!saveParticles || !spidersDen() || !Features.on(ID)) return;
        long now = System.currentTimeMillis();
        if (now - searchAt < 3000) return;
        if (particles == 0 && lastTickAt == 0) lastTickAt = now;
        if (now - lastTickAt > 60) {
            spawnAt = now + (particles <= 20 ? 21_000 : 37_000);
            saveParticles = false;
            return;
        }
        if (ALTAR.distanceToSqr(p.x(), p.y(), p.z()) > 900) return;
        if (p.is("minecraft:dust") && p.speed() == 1f) particles++;
    }

    private static void draw(WorldDraw w) {
        long now = System.currentTimeMillis();
        if (Combat.opt(ID, "spawn_timer") && SANCTUARY.equals(GameState.area) && spawnAt > now) w.dynamicText(ALTAR, "§b" + Combat.seconds(spawnAt - now), 1.5, 0);
        if (!Combat.opt(ID, "line")) return;
        for (Mobs.Mob m : Mobs.all()) {
            if (m.name.equals("Arachne") && Combat.seen(m.base, 10, 0.5)) w.line(Combat.fromCrosshair(w), m.base.getBoundingBox().getCenter(), 0xFFFF5555, Combat.integer(ID, "line_width", 5), true);
        }
    }

    // from skyhanni ArachneChatMessageHider.shouldHide
    static boolean hideLine(String message, String area) {
        if (VENOM.matcher(message).matches()) return true;
        if (SANCTUARY.equals(area)) return false;
        if (CALLING.matcher(message).matches() || CRYSTAL.matcher(message).matches() || SPAWN.matcher(message).matches()) return true;
        if (message.equals(BORDER)) {
            hideDead = !hideDead;
            return true;
        }
        if (message.equals(DOWN_LINE)) hideDead = true;
        return hideDead;
    }

    private static void left() {
        spawnedAt = killTime = spawnAt = 0;
        saveParticles = false;
        particles = 0;
    }

    private static void leftHider() {
        hideDead = false;
    }

    static void register() {
        Feature f = Features.add(new Feature(ID, "Combat", "Arachne", "How long a kill took, the seconds until she spawns over the altar, and no name tags on her small ones.", "SkyHanni: Arachne Kill Timer, Spawn Timer, Minis Hider").when(Arachne::spidersDen).world(Arachne::draw).left(Arachne::left)
                .option(Option.bool("arachne.kill_timer", "Kill timer", "", true))
                .option(Option.bool("arachne.spawn_timer", "Spawn timer", "", true))
                .option(Option.bool("arachne.hide_minis", "Hide her Brood's name tags", "", true))
                .option(Option.bool("arachne.line", "Line to her", "", false))
                .option(Option.integer("arachne.line_width", "Line width", "", 5, 1, 10)));
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, Arachne::onChat);
        ParticleHooks.listen(Arachne::onParticle);
        CombatMarks.hiding(() -> Features.on(ID) && Combat.opt(ID, "hide_minis"), e -> e instanceof ArmorStand a && a.hasCustomName() && LegacyText.of(a.getName(), true, false).contains("§cArachne's Brood§r"));
        Feature hide = Features.add(new Feature(HIDE, "Combat", "Hide Arachne chat", "Her calls and Venom Shots away from her Sanctuary.", "SkyHanni: Arachne Hider").when(Arachne::spidersDen).defaultOff().left(Arachne::leftHider));
        ChatBus.register(ChatBus.FILTER, HIDE, hide, line -> hideLine(LegacyText.of(line.component()), GameState.area) ? ChatBus.CANCEL : ChatBus.PASS);
    }
}
