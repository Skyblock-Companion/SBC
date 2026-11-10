// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

// how our commands reach the server and how a client command node lets one through. fabrics command api
// injects at HEAD of sendCommand/sendUnattendedCommand and runs its own dispatcher first, so a node with the
// same name as a server command would eat it
public final class ServerCommand {
    // what send() calls. tests stub it bc the real connection needs the game
    public static Consumer<String> sink = ServerCommand::viaConnection;

    private ServerCommand() {}

    // sends a command like the game would, from anywhere except a same named node that forwards it
    public static void send(String command) {
        sink.accept(command);
    }

    private static void viaConnection(String command) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand(command);
    }

    // for a nodes executes: not ours, so what was typed goes to the server unchanged
    public static int toServer() throws CommandSyntaxException {
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
    }
}
