package com.duraping.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure threshold/countdown evaluation for one durability state per slot.
 *
 * <p>No Minecraft classes are referenced here, so the whole class is
 * unit-testable. The caller ({@code DurabilityMonitor}) feeds it the
 * remaining-durability value it computed from {@code ItemStack} and delivers
 * the returned warnings as chat messages.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>One notification per threshold event, never chat spam: a threshold is
 *       reported at most once until the item is repaired or replaced.</li>
 *   <li>Thresholds are detected by <em>crossing</em>, not by equality. An item
 *       going from 11 to 8 remaining still reports threshold 10.</li>
 *   <li>The critical countdown covers remaining values {@code 1..countdown}.
 *       Every crossed countdown value reports a critical warning.</li>
 *   <li>When an ordinary threshold and the countdown cover the same remaining
 *       value, exactly one (critical) warning is emitted.</li>
 *   <li>The reported number is the threshold/countdown value that was crossed,
 *       highest first, when one action crosses several values at once.</li>
 *   <li>Equipping, swapping, or moving an item only establishes a new baseline;
 *       it never warns by itself. Thresholds already above the new baseline
 *       are treated as passed elsewhere.</li>
 *   <li>Repairs re-arm thresholds at or below the restored level so a later
 *       wear-down warns again, without any repair-time message.</li>
 *   <li>Reaching 0 reports a single critical "broken" warning.</li>
 * </ul>
 *
 * <h2>Identity note</h2>
 * <p>Minecraft does not always provide a stable identity for two otherwise
 * identical stacks, so the caller passes a best-effort {@code fingerprint}
 * (item id + max durability + display name + count). Tracking is additionally
 * keyed by slot, so moving an item between slots never transfers its warning
 * history. Two indistinguishable items sharing one slot are conservatively
 * treated as the same item; see the README's known limitations.
 */
public final class DurabilityWarnEvaluator {
    /** One warning to deliver as a chat message. */
    public record Warning(int remaining, boolean critical) {
    }

    private static final class SlotState {
        Integer lastRemaining;
        String fingerprint;
        final Set<Integer> warnedOrdinary = new HashSet<>();
        final Set<Integer> warnedCountdown = new HashSet<>();
        boolean warnedBroken;
    }

    private final Map<SlotKey, SlotState> states = new EnumMap<>(SlotKey.class);

    /**
     * Evaluates the current durability of one slot.
     *
     * @param slot               which slot is being evaluated
     * @param damageable         {@code false} for empty slots and items without
     *                           durability; clears the slot state and warns nothing
     * @param remaining          remaining durability (may be any integer;
     *                           negative values are treated as broken)
     * @param fingerprint        caller-computed stable-across-damage identity,
     *                           or {@code null} when unknown
     * @param ordinaryThresholds ordinary warning thresholds (invalid entries ignored)
     * @param countdownThreshold critical countdown range {@code 1..N}
     *                           (values below 1 disable the countdown)
     * @return warnings to deliver, highest remaining first; never {@code null}
     */
    public List<Warning> evaluate(SlotKey slot, boolean damageable, int remaining,
            String fingerprint, Collection<Integer> ordinaryThresholds, int countdownThreshold) {
        if (slot == null) {
            return List.of();
        }
        if (!damageable) {
            states.remove(slot);
            return List.of();
        }
        int countdown = Math.max(0, countdownThreshold);
        TreeSet<Integer> thresholds = new TreeSet<>();
        if (ordinaryThresholds != null) {
            for (Integer threshold : ordinaryThresholds) {
                if (threshold != null && threshold >= 0) {
                    thresholds.add(threshold);
                }
            }
        }
        int current = Math.max(0, remaining);
        SlotState state = states.computeIfAbsent(slot, key -> new SlotState());

        // New or swapped item: establish a baseline without warning for the swap itself.
        if (state.lastRemaining == null || !Objects.equals(state.fingerprint, fingerprint)) {
            state.fingerprint = fingerprint;
            state.lastRemaining = current;
            state.warnedOrdinary.clear();
            state.warnedCountdown.clear();
            state.warnedBroken = current <= 0;
            for (int threshold : thresholds) {
                if (threshold > current) {
                    state.warnedOrdinary.add(threshold);
                }
            }
            for (int value = 1; value <= countdown; value++) {
                if (value > current) {
                    state.warnedCountdown.add(value);
                }
            }
            return List.of();
        }

        // Repaired or otherwise restored: re-arm everything at or below the new level.
        if (current > state.lastRemaining) {
            for (int threshold : thresholds) {
                if (threshold <= current) {
                    state.warnedOrdinary.remove(threshold);
                }
            }
            for (int value = 1; value <= countdown; value++) {
                if (value <= current) {
                    state.warnedCountdown.remove(value);
                }
            }
            if (current > 0) {
                state.warnedBroken = false;
            }
            state.lastRemaining = current;
            return List.of();
        }

        if (current == state.lastRemaining) {
            return List.of();
        }

        // Durability was lost: report every crossed value, highest first.
        List<Warning> warnings = new ArrayList<>();
        Set<Integer> countdownValues = new HashSet<>();
        for (int value = 1; value <= countdown; value++) {
            countdownValues.add(value);
        }
        for (int threshold : thresholds) {
            if (threshold <= state.lastRemaining && threshold >= current
                    && !state.warnedOrdinary.contains(threshold)) {
                // Overlap with the countdown (including the final point 0) warns once, as critical.
                boolean critical = countdownValues.contains(threshold) || threshold == 0;
                warnings.add(new Warning(threshold, critical));
                state.warnedOrdinary.add(threshold);
                if (critical) {
                    state.warnedCountdown.add(threshold);
                }
            }
        }
        for (int value = 1; value <= countdown; value++) {
            if (value <= state.lastRemaining && value >= current
                    && !state.warnedCountdown.contains(value)) {
                warnings.add(new Warning(value, true));
                state.warnedCountdown.add(value);
            }
        }
        boolean zeroAlreadyReported = false;
        for (Warning warning : warnings) {
            if (warning.remaining() == 0) {
                zeroAlreadyReported = true;
                break;
            }
        }
        if (current <= 0 && state.lastRemaining > 0 && !state.warnedBroken && !zeroAlreadyReported) {
            warnings.add(new Warning(0, true));
            state.warnedBroken = true;
        } else if (zeroAlreadyReported) {
            state.warnedBroken = true;
        }
        warnings.sort(Comparator.comparingInt(Warning::remaining).reversed());
        state.lastRemaining = current;
        return List.copyOf(warnings);
    }

    /** Forgets all tracking state for one slot (used when monitoring is disabled for it). */
    public void clear(SlotKey slot) {
        states.remove(slot);
    }

    /** Forgets all tracking state (used on disconnect, world change, reload). */
    public void clearAll() {
        states.clear();
    }
}
