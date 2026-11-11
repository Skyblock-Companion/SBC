// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni RareMobWaypointShare: share your dug rare diana mob (inquisitor, sphinx, king minos,
// manticore) in party as "x: .., y: .., z: .. | Mob", party coords become waypoints for 75s
// and "Rare Diana Mob dead!" when yours dies
public final class RareMobWaypointShare {
    // matches: §9Party §8> User Name§f: §rx: 2.3, y: 4.5, z: -6.7
    // and: §9Party §8> UserName§f: §rA MINOS INQUISITOR has spawned near [Foraging Island ] at Coords 1 2 -3
    // and: §9Party §8> §b[MVP§5+§b] Throwpo§f: §rx: -144, y: 59, z: -119 | Sphinx
    static final Patterns.P COORDS_0 = Patterns.p("SH.diana.waypoints.inquisitor.coords.0");
    static final Patterns.P COORDS_1 = Patterns.p("SH.diana.waypoints.inquisitor.coords.1");
    // matches: §9Party §8> User Name§f: §rInquisitor dead!
    static final Patterns.P DIED = Patterns.p("SH.diana.waypoints.inquisitor.died");
    // matches: §c§lUh oh! §r§eYou dug out a §r§2Minos Inquisitor§r§e!
    static final Patterns.P RARE_MOB_FOUND_CHAT = Patterns.p("SH.diana.waypoints.inquisitor.dug");

    // rare mob entity in the world (a RemotePlayer on hypixel) as the share logic sees it
    public interface Mob {
        int id();

        String name();

        Vec3 pos();

        // health hit 0 or entity gone
        boolean dead();

        // still in range (EntityUtils.getEntityByID != null)
        boolean exists();
    }

    public static final class SharedRareMob {
        public final String fromPlayer, playerDisplayName, mobName;
        public final Vec3 location;
        public final long spawnTime;

        SharedRareMob(String fromPlayer, String playerDisplayName, Vec3 location, long spawnTime, String mobName) {
            this.fromPlayer = fromPlayer;
            this.playerDisplayName = playerDisplayName;
            this.location = location;
            this.spawnTime = spawnTime;
            this.mobName = mobName;
        }
    }

    private static int rareMob = -1;
    private static int lastRareMob = -1;
    private static long lastShareTime = DianaEnv.FAR_PAST;
    private static final Map<Integer, Mob> sweatyMobsNearby = new LinkedHashMap<>();
    private static final Map<String, SharedRareMob> waypoints = new LinkedHashMap<>();
    private static final List<Long> rareMobTime = new ArrayList<>();
    private static long lastSecond;
    private static int seconds;

    // your rare mob died after you shared it and party wasnt told yet
    private static boolean deathToTell;

    private RareMobWaypointShare() {}

    public static Map<String, SharedRareMob> waypoints() {
        return waypoints;
    }

    public static Map<Integer, Mob> nearby() {
        return sweatyMobsNearby;
    }

    private static boolean toggle(String group, String mobName, boolean def) {
        String key = switch (mobName) {
            case "Sphinx" -> "sphinx";
            case "Minos Inquisitor" -> "minosInquisitor";
            case "Manticore" -> "manticore";
            case "King Minos" -> "kingMinos";
            default -> null;
        };
        return key == null || Diana.b(Diana.SHARE, group + "." + key, def);
    }

    // RareDianaMob.fromName: first rare mob whose name is in the text
    static String rareMobFromName(String name) {
        for (String m : new String[]{"Sphinx", "Minos Inquisitor", "Manticore", "King Minos"}) if (name.contains(m)) return m;
        return null;
    }

    private static boolean isMobShareEnabled(String name) {
        String mob = rareMobFromName(name);
        return mob == null || toggle("share", mob, true);
    }

    private static boolean isMobReceiveEnabled(String name) {
        String mob = rareMobFromName(name);
        return mob == null || toggle("receive", mob, true);
    }

    // SecondPassedEvent
    public static void onSecondPassed() {
        if (!isEnabled()) return;
        seconds++;
        if (seconds % 3 == 0) sweatyMobsNearby.values().removeIf(Mob::dead);
        waypoints.values().removeIf(w -> DianaEnv.passedSince(w.spawnTime) > 75_000);
    }

    // every tick: the per-second stuff + health watch on the mob we found (EntityHealthUpdateEvent)
    public static void onTick() {
        long now = DianaEnv.now();
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            onSecondPassed();
        }
        if (!isEnabled()) return;
        for (Mob m : new ArrayList<>(sweatyMobsNearby.values())) {
            if (!m.dead()) continue;
            if (m.id() == rareMob) sendDeath();
            sweatyMobsNearby.remove(m.id());
        }
    }

    public static void onWorldChange() {
        waypoints.clear();
        sweatyMobsNearby.clear();
    }

    // RareDianaMobFoundEvent: rare mob showed up near you
    public static void onRareDianaMobFound(Mob mob) {
        sweatyMobsNearby.put(mob.id(), mob);
        GriffinBurrowHelper.update();
        lastRareMob = mob.id();
        checkRareMobFound();
    }

    // dont know if the chat line or the entity spawn comes first, only run foundRareMob when both happen within 1.5s
    private static void checkRareMobFound() {
        rareMobTime.add(DianaEnv.now());
        if (rareMobTime.size() < 2) return;
        long a = rareMobTime.get(rareMobTime.size() - 2), b = rareMobTime.get(rareMobTime.size() - 1);
        if (DianaEnv.passedSince(a) < 1500 && DianaEnv.passedSince(b) < 1500) {
            rareMobTime.clear();
            foundRareMob(lastRareMob);
        }
    }

    // RareMobWaypointShare.onChat (hub), formatted line. true = hide it
    public static boolean onChat(String message) {
        if (!isEnabled()) return false;
        boolean hide = false;
        if (RARE_MOB_FOUND_CHAT.matcher(message).matches()) checkRareMobFound();

        for (Patterns.P p : new Patterns.P[]{COORDS_0, COORDS_1}) {
            Matcher m = p.matcher(message);
            if (!m.matches()) continue;
            if (detectFromChat(m)) hide = true;
        }

        Matcher d = DIED.matcher(message);
        if (d.matches()) {
            if (block(d)) return hide;
            String name = cleanPlayerName(d.group("playerName"));
            waypoints.remove(name);
            GriffinBurrowHelper.update();
        }
        return hide;
    }

    private static void foundRareMob(int rareMobId) {
        lastShareTime = DianaEnv.FAR_PAST;
        rareMob = rareMobId;
        deathToTell = false;
        DianaEnv.world.clickableChat("§l§bYou found a Rare Diana Mob! Click here or press the share key to share the location!", "/sbc sharerare", "Click to share");
    }

    // skyhanni's instant share sends by itself. here the option only offers the death line, nothing goes to party
    // without a click or key
    private static boolean offerDeath() {
        return Diana.b(Diana.SHARE, "instantShare", false);
    }

    // share key pressed, bound in the parent's key handler
    public static void onKeyPress() {
        if (!isEnabled()) return;
        if (DianaEnv.world.screenOpen()) return;
        share();
    }

    // key or clickable line: the death if one is waiting, else the mob
    public static void share() {
        if (!isEnabled()) return;
        if (deathToTell) {
            deathToTell = false;
            DianaEnv.world.sendCommand("pc Rare Diana Mob dead!");
            return;
        }
        sendRareMob();
    }

    private static void sendDeath() {
        if (!isEnabled()) return;
        if (DianaEnv.passedSince(lastShareTime) < 5000) return;
        // already dead
        if (rareMob == -1) return;
        rareMob = -1;
        if (!offerDeath()) return;
        deathToTell = true;
        DianaEnv.world.clickableChat("§eYour Rare Diana Mob died. Click here or press the share key to tell your party.", "/sbc sharerare", "Click to tell them");
    }

    public static void sendRareMob() {
        if (!isEnabled()) return;
        if (DianaEnv.passedSince(lastShareTime) < 5000) return;
        if (rareMob == -1) return;
        Mob mob = sweatyMobsNearby.get(rareMob);
        if (mob == null || !mob.exists()) {
            DianaEnv.world.chat("§cRare Mob out of range!");
            return;
        }
        if (mob.dead()) {
            DianaEnv.world.chat("§cRare Mob is dead");
            return;
        }
        Vec3 l = mob.pos();
        String location = "x: " + (int) l.x + ", y: " + (int) l.y + ", z: " + (int) l.z;
        String mobName = GameState.strip(mob.name());
        if (!isMobShareEnabled(mobName)) return;
        lastShareTime = DianaEnv.now();
        String name = mobName.isEmpty() ? "" : "| " + mobName;
        DianaEnv.world.sendCommand("pc " + location + " " + name);
    }

    private static boolean block(Matcher m) {
        return m.group("party") == null && !Diana.b(Diana.SHARE, "globalChat", false);
    }

    // StringUtils.cleanPlayerName: name without rank, colours or 's
    public static String cleanPlayerName(String raw) {
        String[] split = raw.trim().split(" ");
        String s = split.length > 1 ? split[1] : split[0];
        s = GameState.strip(s);
        if (s.endsWith("'s")) s = s.substring(0, s.length() - 2);
        return s;
    }

    private static Double num(String s) {
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean detectFromChat(Matcher m) {
        if (block(m)) return false;
        String rawPlayerName = m.group("playerName");
        Double x = num(m.group("x")), y = num(m.group("y")), z = num(m.group("z"));
        if (x == null || y == null || z == null) return false;
        Vec3 location = new Vec3(x, y, z);

        String rawMobName = hasGroup(m, "mobName") ? m.group("mobName").replace(" | ", "").trim().toLowerCase(Locale.ROOT) : "Rare Mob";
        String mobName = "Rare Mob";
        for (DianaData.Creature mob : DianaData.creatures().values()) {
            if (!mob.rare()) continue;
            if (!mob.mobAliases().contains(rawMobName)) continue;
            mobName = mob.cleanName();
        }
        if (!isMobReceiveEnabled(mobName)) return false;

        String optionalAn = "aeiouAEIOU".indexOf(mobName.charAt(0)) >= 0 ? "an" : "a";
        String name = cleanPlayerName(rawPlayerName);
        String playerDisplayName = rawPlayerName;
        if (!waypoints.containsKey(name)) {
            DianaEnv.world.chat(playerDisplayName + " §l§efound " + optionalAn + " " + mobName + " at §l§c" + (int) location.x + " " + (int) location.y + " " + (int) location.z + "!");
            if (!name.equals(DianaEnv.world.playerName())) {
                DianaEnv.world.title("§d" + mobName + " §efrom §b" + playerDisplayName);
                Diana.playShareSound();
            }
        }
        waypoints.put(name, new SharedRareMob(name, playerDisplayName, location, DianaEnv.now(), mobName));
        GriffinBurrowHelper.update();
        return true;
    }

    private static boolean hasGroup(Matcher m, String g) {
        try {
            return m.group(g) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isEnabled() {
        return DianaApi.isDoingDiana() && Diana.shareEnabled();
    }

    // standing at a shared waypoint with no rare mob around = its gone
    public static void maybeRemove(SharedRareMob shared) {
        if (sweatyMobsNearby.isEmpty()) {
            waypoints.remove(shared.fromPlayer);
            GriffinBurrowHelper.update();
            DianaEnv.world.chat(shared.mobName + " from " + shared.playerDisplayName + " §enot found, deleting.");
        }
    }

    public static void reset() {
        waypoints.clear();
        sweatyMobsNearby.clear();
        rareMobTime.clear();
        rareMob = lastRareMob = -1;
        deathToTell = false;
        lastShareTime = DianaEnv.FAR_PAST;
    }
}
