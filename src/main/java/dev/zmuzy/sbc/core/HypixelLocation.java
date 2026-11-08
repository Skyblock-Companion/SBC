// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import dev.zmuzy.sbc.SbcMod;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;

// exact island from the hypixel mod api (only loaded when that mod is installed)
public final class HypixelLocation {
    private HypixelLocation() {}

    public static void init() {
        try {
            HypixelModAPI api = HypixelModAPI.getInstance();
            api.subscribeToEventPacket(ClientboundLocationPacket.class);
            api.createHandler(ClientboundLocationPacket.class, packet -> Safe.run("HypixelLocation", () -> {
                GameState.apiServerType = packet.getServerType().map(Object::toString).orElse("");
                GameState.apiMode = packet.getMode().orElse("");
                GameState.apiMap = packet.getMap().orElse("");
                GameState.apiServer = packet.getServerName();
                GameState.apiLobby = packet.getLobbyName().orElse("");
            }));
            SbcMod.LOG.info("Using Hypixel Mod API for location");
        } catch (Throwable t) {
            SbcMod.LOG.warn("Hypixel Mod API not usable, falling back to tab list for location", t);
        }
    }
}
