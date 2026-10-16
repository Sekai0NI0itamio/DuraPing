package com.duraping.logic;

/**
 * Equipment slots monitored by DuraPing.
 *
 * <p>Each value carries the lowercase key used in the JSON config
 * ({@code slots} section) and in the {@code /duraping slot} commands.
 * The enum itself is free of Minecraft classes so it can be used from
 * pure-logic unit tests.
 */
public enum SlotKey {
    MAINHAND("mainhand"),
    OFFHAND("offhand"),
    HELMET("helmet"),
    CHESTPLATE("chestplate"),
    LEGGINGS("leggings"),
    BOOTS("boots");

    private final String configKey;

    SlotKey(String configKey) {
        this.configKey = configKey;
    }

    /** Lowercase key used in config and commands, e.g. {@code "mainhand"}. */
    public String configKey() {
        return configKey;
    }

    /**
     * Parses a config/command slot name (case-insensitive).
     *
     * @return the matching slot, or {@code null} when unknown
     */
    public static SlotKey fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        for (SlotKey slot : values()) {
            if (slot.configKey.equalsIgnoreCase(key)) {
                return slot;
            }
        }
        return null;
    }
}
