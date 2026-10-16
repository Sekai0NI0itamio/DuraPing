package com.duraping.client;

import com.duraping.command.DuraPingCommands;
import com.duraping.monitor.DurabilityMonitor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Wires the monitor and the client commands to Fabric events.
 * Everything runs on the client thread; no server code or packets.
 */
public final class DuraPingClientHooks {
    private static final DurabilityMonitor MONITOR = new DurabilityMonitor();

    private DuraPingClientHooks() {
    }

    public static DurabilityMonitor monitor() {
        return MONITOR;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MONITOR::onEndTick);
        // Fresh tracking state per session so warnings from another world never leak over.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MONITOR.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MONITOR.reset());
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> DuraPingCommands.register(dispatcher));
    }
}
