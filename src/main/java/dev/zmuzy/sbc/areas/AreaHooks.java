// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.areas;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.crimson.Ashfang;
import dev.zmuzy.sbc.crimson.Dojo;
import dev.zmuzy.sbc.nav.Kuudra;
import dev.zmuzy.sbc.rift.RiftColosseum;
import dev.zmuzy.sbc.rift.RiftDreadfarm;
import dev.zmuzy.sbc.rift.RiftLivingCave;
import dev.zmuzy.sbc.rift.RiftMarks;
import dev.zmuzy.sbc.rift.RiftMirrorverse;
import dev.zmuzy.sbc.rift.RiftMotesOrb;
import dev.zmuzy.sbc.rift.RiftMountaintop;
import dev.zmuzy.sbc.rift.RiftTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

// what the crimson isle + rift ask of the mixins, in one place so each hook is one line there and costs
// nothing on other islands. skyhanni has these as CheckRenderEntityEvent etc
public final class AreaHooks {
    private AreaHooks() {}

    private static boolean rift() {
        return "THE_RIFT".equals(GameState.island);
    }

    private static boolean crimson() {
        return "CRIMSON_ISLE".equals(GameState.island);
    }

    // outline color for an entity, or -1
    public static int glow(Entity e) {
        if (rift()) return RiftColosseum.glow(e);
        if (crimson()) return Dojo.glow(e);
        if ("KUUDRA".equals(GameState.island)) return Kuudra.glow(e);
        return -1;
    }

    // entity that shouldnt be drawn
    public static boolean hidden(Entity e) {
        if (rift()) return RiftMarks.hidden(e) || RiftMirrorverse.hidden(e);
        if (crimson()) return Ashfang.hidden(e);
        return false;
    }

    // from skyhanni RiftTimer.onEntityDisplayName: name to show instead, or null
    public static Component nameTag(Component name) {
        return rift() && name != null ? RiftTimer.nameTag(name) : null;
    }

    // particle that shouldnt show. runs on both passes of the vanilla handler, only the game threads one counts
    public static boolean blockParticle(ClientboundLevelParticlesPacket p) {
        boolean inRift = rift(), inCrimson = crimson();
        if (!inRift && !inCrimson) return false;
        if (!Minecraft.getInstance().isSameThread()) return false;
        if (inCrimson) return Ashfang.hidesParticles();
        var key = BuiltInRegistries.PARTICLE_TYPE.getKey(p.particle().getType());
        String type = key == null ? "" : key.getPath();
        double x = p.x(), y = p.y(), z = p.z();
        return RiftMotesOrb.hides(type, x, y, z) || RiftDreadfarm.hidesParticle(type, x, y, z) || RiftLivingCave.hidesParticle(type, x, y, z);
    }

    // server title that shouldnt show, also where titles get heard
    public static boolean onTitle(Component title) {
        if (!rift() || !Minecraft.getInstance().isSameThread()) return false;
        String text = title == null ? "" : title.getString();
        RiftLivingCave.onTitle(text);
        return RiftMirrorverse.hidesTitle();
    }

    // ClientLevel.setServerVerifiedBlockState, before the block changes
    public static void onBlockChange(BlockPos pos, BlockState old, BlockState now) {
        boolean inRift = rift(), inCrimson = crimson();
        if (!inRift && !inCrimson) return;
        if (old == now) return;
        if (inCrimson) {
            Dojo.onBlockChange(pos, now);
            return;
        }
        String o = BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath(), n = BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath();
        BlockPos at = pos.immutable();
        RiftDreadfarm.onBlockChange(at, o, n);
        RiftLivingCave.onBlockChange(at, old, now, o, n);
        RiftMountaintop.onBlockChange(at, o, n);
    }

    // skyhanni MobEvent.Hurt from the damage packet
    public static void onDamage(ClientboundDamageEventPacket p) {
        if (rift() && Minecraft.getInstance().isSameThread()) RiftColosseum.onDamage(p.entityId(), p.sourceType().value().msgId());
    }
}
