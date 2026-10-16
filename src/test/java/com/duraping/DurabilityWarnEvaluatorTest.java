package com.duraping;

import com.duraping.logic.DurabilityWarnEvaluator;
import com.duraping.logic.DurabilityWarnEvaluator.Warning;
import com.duraping.logic.SlotKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pure warning logic: crossings, exact hits, skipped values,
 * critical countdown, dedup, repairs, equipment changes, and non-damageables.
 */
class DurabilityWarnEvaluatorTest {
    private static final List<Integer> DEFAULTS = List.of(10, 3);
    private static final int COUNTDOWN = 3;

    private static List<Warning> feed(DurabilityWarnEvaluator evaluator, SlotKey slot, int... remaining) {
        List<Warning> last = List.of();
        for (int value : remaining) {
            last = evaluator.evaluate(slot, true, value, "pickaxe", DEFAULTS, COUNTDOWN);
        }
        return last;
    }

    @Test
    void crossingOrdinaryThresholdWarns() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        List<Warning> warnings = feed(evaluator, SlotKey.MAINHAND, 12, 11, 9);
        assertEquals(1, warnings.size());
        assertEquals(new Warning(10, false), warnings.get(0));
    }

    @Test
    void reachingThresholdExactlyWarns() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        List<Warning> warnings = feed(evaluator, SlotKey.MAINHAND, 11, 10);
        assertEquals(List.of(new Warning(10, false)), warnings);
    }

    @Test
    void skippingOverThresholdStillWarns() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        List<Warning> warnings = feed(evaluator, SlotKey.MAINHAND, 11, 8);
        assertEquals(List.of(new Warning(10, false)), warnings);
    }

    @Test
    void enteringCriticalRangeWarnsCritically() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        List<Warning> warnings = feed(evaluator, SlotKey.MAINHAND, 5, 3);
        assertEquals(List.of(new Warning(3, true)), warnings);
    }

    @Test
    void countsDownThreeToTwoToOne() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        feed(evaluator, SlotKey.MAINHAND, 4);
        assertEquals(List.of(new Warning(3, true)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 3, "pickaxe", DEFAULTS, COUNTDOWN));
        assertEquals(List.of(new Warning(2, true)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 2, "pickaxe", DEFAULTS, COUNTDOWN));
        assertEquals(List.of(new Warning(1, true)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 1, "pickaxe", DEFAULTS, COUNTDOWN));
    }

    @Test
    void noDuplicateWarningsOnConsecutiveTicks() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        feed(evaluator, SlotKey.MAINHAND, 12, 9);
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 8, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
    }

    @Test
    void multipleOrdinaryThresholdsEachWarnOnce() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        List<Warning> first = feed(evaluator, SlotKey.MAINHAND, 12, 9);
        assertEquals(List.of(new Warning(10, false)), first);
        List<Warning> second =
                evaluator.evaluate(SlotKey.MAINHAND, true, 4, "pickaxe", List.of(10, 5), COUNTDOWN);
        assertEquals(List.of(new Warning(5, false)), second);
    }

    @Test
    void overlappingOrdinaryAndCountdownWarnsOnceAsCritical() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        List<Warning> warnings = feed(evaluator, SlotKey.MAINHAND, 4, 3);
        // Threshold 3 and countdown value 3 must not produce two messages.
        assertEquals(1, warnings.size());
        assertEquals(new Warning(3, true), warnings.get(0));
        // And the follow-up countdown still works without repeating 3.
        assertEquals(List.of(new Warning(2, true)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 2, "pickaxe", DEFAULTS, COUNTDOWN));
    }

    @Test
    void durabilityRestorationRearmsThresholds() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        assertEquals(List.of(new Warning(10, false)), feed(evaluator, SlotKey.MAINHAND, 12, 9));
        // Repairing warns nothing...
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 12, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
        // ...but re-arms threshold 10 for the next wear-down.
        assertEquals(List.of(new Warning(10, false)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN));
    }

    @Test
    void swappingItemsResetsTrackingWithoutSwapWarnings() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        feed(evaluator, SlotKey.MAINHAND, 12, 9);
        // A different item at 9 remaining: no warning for the swap itself...
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 9, "axe", DEFAULTS, COUNTDOWN).isEmpty());
        // ...and no stale warning for threshold 10 afterwards.
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 8, "axe", DEFAULTS, COUNTDOWN).isEmpty());
        // The countdown still works for the new item.
        evaluator.evaluate(SlotKey.MAINHAND, true, 4, "axe", DEFAULTS, COUNTDOWN);
        assertEquals(List.of(new Warning(3, true)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 3, "axe", DEFAULTS, COUNTDOWN));
    }

    @Test
    void movingLowItemBetweenSlotsDoesNotSpam() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        feed(evaluator, SlotKey.MAINHAND, 12, 9);
        // Item moved to the offhand: single baseline tick, no warning...
        assertTrue(evaluator.evaluate(SlotKey.OFFHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
        // ...and steady state stays quiet.
        assertTrue(evaluator.evaluate(SlotKey.OFFHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
        // The old slot went empty: cleared, quiet.
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, false, 0, null, DEFAULTS, COUNTDOWN).isEmpty());
    }

    @Test
    void nonDamageableItemsAreIgnoredAndClearState() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        feed(evaluator, SlotKey.MAINHAND, 12, 9);
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, false, 0, null, DEFAULTS, COUNTDOWN).isEmpty());
        // A fresh damageable item afterwards starts from a clean baseline.
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
    }

    @Test
    void reachingZeroWarnsBrokenOnce() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        feed(evaluator, SlotKey.MAINHAND, 2);
        List<Warning> warnings = evaluator.evaluate(SlotKey.MAINHAND, true, 0, "pickaxe", DEFAULTS, COUNTDOWN);
        assertEquals(1, warnings.stream().filter(w -> w.remaining() == 0).count());
        assertTrue(warnings.get(warnings.size() - 1).critical());
        assertTrue(evaluator.evaluate(SlotKey.MAINHAND, true, 0, "pickaxe", DEFAULTS, COUNTDOWN).isEmpty());
    }

    @Test
    void slotsAreTrackedIndependently() {
        DurabilityWarnEvaluator evaluator = new DurabilityWarnEvaluator();
        evaluator.evaluate(SlotKey.MAINHAND, true, 12, "pickaxe", DEFAULTS, COUNTDOWN);
        evaluator.evaluate(SlotKey.HELMET, true, 12, "helmet", DEFAULTS, COUNTDOWN);
        assertEquals(List.of(new Warning(10, false)),
                evaluator.evaluate(SlotKey.MAINHAND, true, 9, "pickaxe", DEFAULTS, COUNTDOWN));
        // The helmet has its own history and warns for its own crossing too.
        assertEquals(List.of(new Warning(10, false)),
                evaluator.evaluate(SlotKey.HELMET, true, 9, "helmet", DEFAULTS, COUNTDOWN));
    }
}
