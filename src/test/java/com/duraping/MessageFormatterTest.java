package com.duraping;

import com.duraping.notify.MessageFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    @Test
    void fullWarningMatchesSpecExample() {
        assertEquals("[DuraPing] Diamond Pickaxe: 10 durability remaining!",
                MessageFormatter.buildWarning("[DuraPing]", true, true, "Diamond Pickaxe", 10));
    }

    @Test
    void finalWarningReadsNaturally() {
        assertEquals("[DuraPing] Diamond Pickaxe: 1 durability remaining!",
                MessageFormatter.buildWarning("[DuraPing]", true, true, "Diamond Pickaxe", 1));
    }

    @Test
    void prefixCanBeHidden() {
        assertEquals("Diamond Pickaxe: 3 durability remaining!",
                MessageFormatter.buildWarning("[DuraPing]", false, true, "Diamond Pickaxe", 3));
    }

    @Test
    void itemNameCanBeHidden() {
        assertEquals("[DuraPing] 3 durability remaining!",
                MessageFormatter.buildWarning("[DuraPing]", true, false, "Diamond Pickaxe", 3));
    }

    @Test
    void brokenItemNeverShowsNegativeNumbers() {
        assertEquals("[DuraPing] Diamond Pickaxe: 0 durability remaining (broken!)",
                MessageFormatter.buildWarning("[DuraPing]", true, true, "Diamond Pickaxe", 0));
    }
}
