package com.duraping;

import com.duraping.config.DuraPingConfig;
import com.duraping.logic.SlotKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers config validation, defaults for missing fields, malformed input,
 * and per-slot toggles. File I/O itself is exercised in CI via the mod run,
 * not here: these tests stay on the pure JSON/validation path.
 */
class DuraPingConfigTest {
    @Test
    void defaultsMatchSpec() {
        DuraPingConfig config = DuraPingConfig.defaults();
        assertTrue(config.enabled);
        assertEquals(List.of(3, 10), config.sortedThresholds());
        assertEquals(3, config.countdownThreshold);
        for (SlotKey key : SlotKey.values()) {
            assertTrue(config.isSlotEnabled(key));
        }
        assertEquals("[DuraPing]", config.messagePrefix);
        assertTrue(config.showPrefix);
        assertTrue(config.includeItemName);
    }

    @Test
    void rejectsInvalidThresholds() {
        assertFalse(DuraPingConfig.isValidThreshold(-1));
        assertTrue(DuraPingConfig.isValidThreshold(0));
        assertTrue(DuraPingConfig.isValidThreshold(10));
        assertTrue(DuraPingConfig.isValidThreshold(DuraPingConfig.MAX_THRESHOLD));
        assertFalse(DuraPingConfig.isValidThreshold(DuraPingConfig.MAX_THRESHOLD + 1));
    }

    @Test
    void rejectsInvalidCountdown() {
        assertFalse(DuraPingConfig.isValidCountdown(0));
        assertFalse(DuraPingConfig.isValidCountdown(-3));
        assertTrue(DuraPingConfig.isValidCountdown(1));
        assertTrue(DuraPingConfig.isValidCountdown(3));
        assertTrue(DuraPingConfig.isValidCountdown(DuraPingConfig.MAX_COUNTDOWN));
        assertFalse(DuraPingConfig.isValidCountdown(DuraPingConfig.MAX_COUNTDOWN + 1));
    }

    @Test
    void missingFieldsFallBackToDefaults() {
        DuraPingConfig config = DuraPingConfig.parseOrDefaults("{\"enabled\":false}");
        assertFalse(config.enabled);
        assertEquals(List.of(3, 10), config.sortedThresholds());
        assertEquals(3, config.countdownThreshold);
        assertTrue(config.isSlotEnabled(SlotKey.BOOTS));
        assertEquals("[DuraPing]", config.messagePrefix);
    }

    @Test
    void malformedJsonFallsBackToDefaultsWithoutThrowing() {
        DuraPingConfig config = DuraPingConfig.parseOrDefaults("not json {{{");
        assertEquals(DuraPingConfig.defaults().toJson(), config.toJson());
    }

    @Test
    void thresholdsAreDedupedAndSorted() {
        DuraPingConfig config = DuraPingConfig.parseOrDefaults(
                "{\"thresholds\":[3,10,3,-5,1000001]}");
        assertEquals(List.of(3, 10), config.sortedThresholds());
    }

    @Test
    void invalidCountdownFallsBackToDefault() {
        DuraPingConfig config = DuraPingConfig.parseOrDefaults("{\"countdownThreshold\":0}");
        assertEquals(3, config.countdownThreshold);
    }

    @Test
    void unknownColorsFallBackToDefaults() {
        DuraPingConfig config = DuraPingConfig.parseOrDefaults(
                "{\"warningColor\":\"NOTACOLOR\",\"criticalColor\":\"also-bad\"}");
        assertEquals("YELLOW", config.warningColor);
        assertEquals("RED", config.criticalColor);
    }

    @Test
    void disabledSlotsAreRespected() {
        DuraPingConfig config = DuraPingConfig.defaults();
        config.setSlotEnabled(SlotKey.HELMET, false);
        assertFalse(config.isSlotEnabled(SlotKey.HELMET));
        assertTrue(config.isSlotEnabled(SlotKey.MAINHAND));
        assertTrue(config.isSlotEnabled(SlotKey.BOOTS));
    }

    @Test
    void slotKeysParseCaseInsensitively() {
        assertEquals(SlotKey.MAINHAND, SlotKey.fromConfigKey("MainHand"));
        assertEquals(SlotKey.BOOTS, SlotKey.fromConfigKey("boots"));
        assertNull(SlotKey.fromConfigKey("cape"));
        assertNull(SlotKey.fromConfigKey(null));
    }
}
