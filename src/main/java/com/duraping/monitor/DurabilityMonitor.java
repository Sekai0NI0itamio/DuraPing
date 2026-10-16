package com.duraping.monitor;

import com.duraping.config.DuraPingConfig;
import com.duraping.logic.DurabilityWarnEvaluator;
import com.duraping.logic.SlotKey;
import com.duraping.notify.ChatNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Polls the six monitored equipment slots once per client tick and turns
 * durability changes into chat warnings.
 *
 * <p>Each slot owns independent tracking state inside
 * {@link DurabilityWarnEvaluator}, so switching tools, moving items between
 * slots, repairs, and multi-point damage are all handled without transferring
 * one item's warning history to another.
 */
public final class DurabilityMonitor {
    private static final Map<SlotKey, EquipmentSlot> VANILLA_SLOTS = new EnumMap<>(SlotKey.class);

    static {
        VANILLA_SLOTS.put(SlotKey.MAINHAND, EquipmentSlot.MAINHAND);
        VANILLA_SLOTS.put(SlotKey.OFFHAND, EquipmentSlot.OFFHAND);
        VANILLA_SLOTS.put(SlotKey.HELMET, EquipmentSlot.HEAD);
        VANILLA_SLOTS.put(SlotKey.CHESTPLATE, EquipmentSlot.CHEST);
        VANILLA_SLOTS.put(SlotKey.LEGGINGS, EquipmentSlot.LEGS);
        VANILLA_SLOTS.put(SlotKey.BOOTS, EquipmentSlot.FEET);
    }

    private final DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();

    /** Called from {@code ClientTickEvents.END_CLIENT_TICK}; never throws. */
    public void onEndTick(MinecraftClient client) {
        try {
            poll(client);
        } catch (Exception ignored) {
            // Monitoring must never crash the client.
        }
    }

    private void poll(MinecraftClient client) {
        DuraPingConfig config = DuraPingConfig.get();
        if (!config.enabled) {
            return;
        }
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        // Creative and spectator players neither need warnings nor special item states.
        if (client.player.isCreative() || client.player.isSpectator()) {
            return;
        }
        List<Integer> thresholds = config.sortedThresholds();
        int countdown = config.countdownThreshold;
        for (SlotKey key : SlotKey.values()) {
            checkSlot(client, config, key, thresholds, countdown);
        }
    }

    private void checkSlot(MinecraftClient client, DuraPingConfig config, SlotKey key,
            List<Integer> thresholds, int countdown) {
        if (!config.isSlotEnabled(key)) {
            evaluator.clear(key);
            return;
        }
        ItemStack stack;
        try {
            stack = client.player.getEquippedStack(VANILLA_SLOTS.get(key));
        } catch (Exception e) {
            evaluator.clear(key);
            return;
        }
        if (stack == null || stack.isEmpty()) {
            evaluator.clear(key);
            return;
        }
        boolean damageable;
        int maxDamage;
        int damage;
        String itemName;
        try {
            damageable = stack.isDamageable();
            maxDamage = stack.getMaxDamage();
            damage = stack.getDamage();
            itemName = stack.getName().getString();
        } catch (Exception e) {
            evaluator.clear(key);
            return;
        }
        if (!damageable || maxDamage <= 0) {
            evaluator.clear(key);
            return;
        }
        int remaining = maxDamage - damage;
        String fingerprint = fingerprintOf(stack, maxDamage, itemName);
        List<DurabilityWarnEvaluator.Warning> warnings =
                evaluator.evaluate(key, true, remaining, fingerprint, thresholds, countdown);
        for (DurabilityWarnEvaluator.Warning warning : warnings) {
            ChatNotifier.sendWarning(client, itemName, warning.remaining(), warning.critical());
        }
    }

    /**
     * Best-effort identity that is stable across damage: item id, maximum
     * durability, display name, and stack count. Damage itself is excluded on
     * purpose so normal wear is not mistaken for an item swap.
     */
    static String fingerprintOf(ItemStack stack, int maxDamage, String itemName) {
        String id;
        int count;
        try {
            id = Registries.ITEM.getId(stack.getItem()).toString();
        } catch (Exception e) {
            id = stack.getItem().toString();
        }
        try {
            count = stack.getCount();
        } catch (Exception e) {
            count = 1;
        }
        return id + "|" + maxDamage + "|" + (itemName == null ? "" : itemName) + "|" + count;
    }

    /** Forgets all per-slot tracking state. */
    public void reset() {
        evaluator.clearAll();
    }

    /** Forgets one slot (called when its monitoring is disabled). */
    public void onSlotDisabled(SlotKey key) {
        evaluator.clear(key);
    }
}
