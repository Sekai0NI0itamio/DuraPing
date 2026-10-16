package com.duraping.command;

import com.duraping.client.DuraPingClientHooks;
import com.duraping.config.DuraPingConfig;
import com.duraping.logic.SlotKey;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * Client-side {@code /duraping} command tree.
 *
 * <p>Registered only on the client via Fabric's client command API, so the
 * commands work in singleplayer and on any server without server-side
 * installation and never send anything to the server. Every mutating command
 * updates the in-memory config immediately and persists it to
 * {@code config/duraping.json}.
 */
public final class DuraPingCommands {
    private DuraPingCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("duraping");
        root.executes(ctx -> help(ctx.getSource()));
        root.then(ClientCommandManager.literal("status")
                .executes(ctx -> status(ctx.getSource())));
        root.then(ClientCommandManager.literal("on")
                .executes(ctx -> setEnabled(ctx.getSource(), true)));
        root.then(ClientCommandManager.literal("off")
                .executes(ctx -> setEnabled(ctx.getSource(), false)));
        root.then(ClientCommandManager.literal("reload")
                .executes(ctx -> reload(ctx.getSource())));
        root.then(ClientCommandManager.literal("reset")
                .executes(ctx -> reset(ctx.getSource())));
        root.then(thresholdNode());
        root.then(ClientCommandManager.literal("countdown")
                .then(ClientCommandManager.argument("value",
                                IntegerArgumentType.integer(1, DuraPingConfig.MAX_COUNTDOWN))
                        .executes(ctx -> setCountdown(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value")))));
        root.then(slotNode());
        root.then(messageNode());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> thresholdNode() {
        LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommandManager.literal("threshold");
        node.executes(ctx -> thresholdList(ctx.getSource()));
        node.then(ClientCommandManager.literal("list")
                .executes(ctx -> thresholdList(ctx.getSource())));
        node.then(ClientCommandManager.literal("clear")
                .executes(ctx -> thresholdClear(ctx.getSource())));
        node.then(ClientCommandManager.literal("add")
                .then(ClientCommandManager.argument("value",
                                IntegerArgumentType.integer(0, DuraPingConfig.MAX_THRESHOLD))
                        .executes(ctx -> thresholdAdd(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value")))));
        node.then(ClientCommandManager.literal("remove")
                .then(ClientCommandManager.argument("value",
                                IntegerArgumentType.integer(0, DuraPingConfig.MAX_THRESHOLD))
                        .executes(ctx -> thresholdRemove(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "value")))));
        return node;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> slotNode() {
        LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommandManager.literal("slot");
        node.executes(ctx -> slotList(ctx.getSource()));
        for (SlotKey key : SlotKey.values()) {
            LiteralArgumentBuilder<FabricClientCommandSource> slot = ClientCommandManager.literal(key.configKey());
            slot.executes(ctx -> slotShow(ctx.getSource(), key));
            slot.then(ClientCommandManager.literal("on")
                    .executes(ctx -> setSlot(ctx.getSource(), key, true)));
            slot.then(ClientCommandManager.literal("off")
                    .executes(ctx -> setSlot(ctx.getSource(), key, false)));
            node.then(slot);
        }
        return node;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> messageNode() {
        LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommandManager.literal("message");
        node.executes(ctx -> messageShow(ctx.getSource()));
        node.then(ClientCommandManager.literal("prefix")
                .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> setPrefix(ctx.getSource(),
                                StringArgumentType.getString(ctx, "text")))));
        node.then(ClientCommandManager.literal("showprefix")
                .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setShowPrefix(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setShowPrefix(ctx.getSource(), false))));
        node.then(ClientCommandManager.literal("itemname")
                .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setIncludeItemName(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setIncludeItemName(ctx.getSource(), false))));
        node.then(ClientCommandManager.literal("warncolor")
                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                        .executes(ctx -> setWarnColor(ctx.getSource(),
                                StringArgumentType.getString(ctx, "color")))));
        node.then(ClientCommandManager.literal("critcolor")
                .then(ClientCommandManager.argument("color", StringArgumentType.word())
                        .executes(ctx -> setCritColor(ctx.getSource(),
                                StringArgumentType.getString(ctx, "color")))));
        return node;
    }

    private static int help(FabricClientCommandSource source) {
        DuraPingConfig config = DuraPingConfig.get();
        send(source, "DuraPing " + (config.enabled ? "ON" : "OFF")
                + " | thresholds " + DuraPingConfig.describeThresholds(config)
                + " | countdown " + config.countdownThreshold);
        send(source, "Usage: /duraping status|on|off|reload|reset");
        send(source, "  threshold add|remove <n>|list|clear; countdown <n>");
        send(source, "  slot <mainhand|offhand|helmet|chestplate|leggings|boots> <on|off>");
        send(source, "  message prefix <text>|showprefix|itemname|warncolor|critcolor");
        return 1;
    }

    private static int status(FabricClientCommandSource source) {
        DuraPingConfig config = DuraPingConfig.get();
        send(source, "monitoring: " + (config.enabled ? "ON" : "OFF"));
        send(source, "thresholds: " + DuraPingConfig.describeThresholds(config));
        send(source, "critical countdown: " + config.countdownThreshold
                + " (warns at " + config.countdownThreshold + "...1, critical color)");
        send(source, "slots: " + DuraPingConfig.describeSlots(config));
        send(source, "messages: prefix=" + (config.showPrefix ? "'" + config.messagePrefix + "'" : "(hidden)")
                + " itemName=" + (config.includeItemName ? "on" : "off")
                + " warn=" + config.warningColor + " crit=" + config.criticalColor);
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        DuraPingConfig config = DuraPingConfig.get();
        config.enabled = enabled;
        DuraPingConfig.save();
        if (!enabled) {
            DuraPingClientHooks.monitor().reset();
        }
        send(source, "DuraPing monitoring " + (enabled ? "ON" : "OFF"));
        return 1;
    }

    private static int reload(FabricClientCommandSource source) {
        DuraPingConfig.load();
        DuraPingClientHooks.monitor().reset();
        send(source, "Config reloaded: thresholds "
                + DuraPingConfig.describeThresholds(DuraPingConfig.get())
                + ", countdown " + DuraPingConfig.get().countdownThreshold);
        return 1;
    }

    private static int reset(FabricClientCommandSource source) {
        DuraPingConfig.get().copyFrom(DuraPingConfig.defaults());
        DuraPingConfig.save();
        DuraPingClientHooks.monitor().reset();
        send(source, "DuraPing settings restored to defaults.");
        return status(source);
    }

    private static int thresholdList(FabricClientCommandSource source) {
        send(source, "Ordinary thresholds: "
                + DuraPingConfig.describeThresholds(DuraPingConfig.get())
                + " | countdown: " + DuraPingConfig.get().countdownThreshold);
        return 1;
    }

    private static int thresholdAdd(FabricClientCommandSource source, int value) {
        if (!DuraPingConfig.isValidThreshold(value)) {
            send(source, "Rejected: threshold must be 0.." + DuraPingConfig.MAX_THRESHOLD + ".");
            return 0;
        }
        DuraPingConfig config = DuraPingConfig.get();
        if (config.thresholds.contains(value)) {
            send(source, "Threshold " + value + " is already configured.");
            return 1;
        }
        config.thresholds.add(value);
        config.normalize();
        DuraPingConfig.save();
        send(source, "Added threshold " + value + ". Now: " + DuraPingConfig.describeThresholds(config));
        return 1;
    }

    private static int thresholdRemove(FabricClientCommandSource source, int value) {
        DuraPingConfig config = DuraPingConfig.get();
        if (!config.thresholds.contains(value)) {
            send(source, "Threshold " + value + " is not configured.");
            return 0;
        }
        config.thresholds.remove(Integer.valueOf(value));
        config.normalize();
        DuraPingConfig.save();
        send(source, "Removed threshold " + value + ". Now: " + DuraPingConfig.describeThresholds(config));
        return 1;
    }

    private static int thresholdClear(FabricClientCommandSource source) {
        DuraPingConfig config = DuraPingConfig.get();
        config.thresholds.clear();
        config.normalize();
        DuraPingConfig.save();
        send(source, "Ordinary thresholds cleared. Critical countdown (" + config.countdownThreshold
                + ") is unchanged and still warns.");
        return 1;
    }

    private static int setCountdown(FabricClientCommandSource source, int value) {
        if (!DuraPingConfig.isValidCountdown(value)) {
            send(source, "Rejected: countdown must be 1.." + DuraPingConfig.MAX_COUNTDOWN + ".");
            return 0;
        }
        DuraPingConfig.get().countdownThreshold = value;
        DuraPingConfig.get().normalize();
        DuraPingConfig.save();
        send(source, "Critical countdown set to " + value + " (warns at " + value + "...1).");
        return 1;
    }

    private static int slotList(FabricClientCommandSource source) {
        send(source, "Slots: " + DuraPingConfig.describeSlots(DuraPingConfig.get()));
        return 1;
    }

    private static int slotShow(FabricClientCommandSource source, SlotKey key) {
        send(source, "Slot " + key.configKey() + ": "
                + (DuraPingConfig.get().isSlotEnabled(key) ? "ON" : "OFF"));
        return 1;
    }

    private static int setSlot(FabricClientCommandSource source, SlotKey key, boolean enabled) {
        DuraPingConfig.get().setSlotEnabled(key, enabled);
        DuraPingConfig.save();
        if (!enabled) {
            DuraPingClientHooks.monitor().onSlotDisabled(key);
        }
        send(source, "Slot " + key.configKey() + " monitoring " + (enabled ? "ON" : "OFF"));
        return 1;
    }

    private static int messageShow(FabricClientCommandSource source) {
        DuraPingConfig config = DuraPingConfig.get();
        send(source, "Messages: prefix='" + config.messagePrefix + "' show=" + config.showPrefix
                + " itemName=" + config.includeItemName
                + " warn=" + config.warningColor + " crit=" + config.criticalColor);
        return 1;
    }

    private static int setPrefix(FabricClientCommandSource source, String text) {
        if (text == null || text.isBlank()) {
            send(source, "Rejected: prefix must not be blank.");
            return 0;
        }
        DuraPingConfig.get().messagePrefix = text.strip();
        DuraPingConfig.save();
        send(source, "Message prefix set to '" + DuraPingConfig.get().messagePrefix + "'.");
        return 1;
    }

    private static int setShowPrefix(FabricClientCommandSource source, boolean value) {
        DuraPingConfig.get().showPrefix = value;
        DuraPingConfig.save();
        send(source, "Message prefix " + (value ? "shown" : "hidden") + ".");
        return 1;
    }

    private static int setIncludeItemName(FabricClientCommandSource source, boolean value) {
        DuraPingConfig.get().includeItemName = value;
        DuraPingConfig.save();
        send(source, "Item names in warnings " + (value ? "ON" : "OFF") + ".");
        return 1;
    }

    private static int setWarnColor(FabricClientCommandSource source, String color) {
        if (!DuraPingConfig.isValidColor(color)) {
            send(source, "Rejected: unknown color '" + color
                    + "'. Use a Minecraft color name like YELLOW or GOLD.");
            return 0;
        }
        DuraPingConfig.get().warningColor = color.strip().toUpperCase();
        DuraPingConfig.save();
        send(source, "Warning color set to " + DuraPingConfig.get().warningColor + ".");
        return 1;
    }

    private static int setCritColor(FabricClientCommandSource source, String color) {
        if (!DuraPingConfig.isValidColor(color)) {
            send(source, "Rejected: unknown color '" + color
                    + "'. Use a Minecraft color name like RED or LIGHT_PURPLE.");
            return 0;
        }
        DuraPingConfig.get().criticalColor = color.strip().toUpperCase();
        DuraPingConfig.save();
        send(source, "Critical color set to " + DuraPingConfig.get().criticalColor + ".");
        return 1;
    }

    private static void send(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal("[DuraPing] " + message));
    }
}
