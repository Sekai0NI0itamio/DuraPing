package com.duraping.notify;

import com.duraping.config.DuraPingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Delivers durability warnings as local client-side chat messages.
 *
 * <p>Messages go only to the local player via
 * {@code ClientPlayerEntity#sendMessage}. Nothing is sent to the server or to
 * other players.
 */
public final class ChatNotifier {
    private ChatNotifier() {
    }

    /** Sends one warning line to the local player; never throws. */
    public static void sendWarning(MinecraftClient client, String itemName, int remaining, boolean critical) {
        if (client == null || client.player == null) {
            return;
        }
        try {
            DuraPingConfig config = DuraPingConfig.get();
            String text = MessageFormatter.buildWarning(config.messagePrefix, config.showPrefix,
                    config.includeItemName, itemName, remaining);
            Formatting color = formattingByName(critical ? config.criticalColor : config.warningColor,
                    critical ? Formatting.RED : Formatting.YELLOW);
            client.player.sendMessage(Text.literal(text).formatted(color), false);
        } catch (Exception ignored) {
            // Warnings must never disturb gameplay.
        }
    }

    /** Sends a plain informational line (command feedback uses the command source instead). */
    public static void sendInfo(MinecraftClient client, String text) {
        if (client == null || client.player == null || text == null) {
            return;
        }
        try {
            client.player.sendMessage(Text.literal(text).formatted(Formatting.GRAY), false);
        } catch (Exception ignored) {
            // Best effort only.
        }
    }

    static Formatting formattingByName(String name, Formatting fallback) {
        if (name != null) {
            for (Formatting formatting : Formatting.values()) {
                if (formatting.getName().equalsIgnoreCase(name)) {
                    return formatting;
                }
            }
        }
        return fallback;
    }
}
