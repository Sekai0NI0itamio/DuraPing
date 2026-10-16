package com.duraping.config;

import com.duraping.logic.SlotKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent configuration stored at {@code config/duraping.json}.
 *
 * <p>All load/save/validation edge cases are handled defensively: missing
 * files generate defaults, missing fields fall back to defaults, malformed
 * files are preserved as {@code duraping.json.invalid} instead of being
 * overwritten, and every mutated value is validated before it is accepted.
 */
public final class DuraPingConfig {
    // NOTE: constants must stay above INSTANCE: the singleton is built from
    // them during class initialization, and statics initialize in order.
    /** Default ordinary thresholds: warn at 10 and at 3 remaining. */
    public static final List<Integer> DEFAULT_THRESHOLDS = List.of(10, 3);
    /** Default critical countdown: 3, 2, 1. */
    public static final int DEFAULT_COUNTDOWN = 3;
    /** Largest accepted ordinary threshold (covers heavily modded gear). */
    public static final int MAX_THRESHOLD = 1_000_000;
    /** Largest accepted critical countdown range. */
    public static final int MAX_COUNTDOWN = 1_000;
    public static final String DEFAULT_PREFIX = "[DuraPing]";
    public static final String DEFAULT_WARNING_COLOR = "YELLOW";
    public static final String DEFAULT_CRITICAL_COLOR = "RED";

    private static final Logger LOG = Logger.getLogger("DuraPing");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DuraPingConfig INSTANCE = defaults();

    public volatile boolean enabled = true;
    public volatile List<Integer> thresholds = new ArrayList<>(DEFAULT_THRESHOLDS);
    public volatile int countdownThreshold = DEFAULT_COUNTDOWN;
    public volatile Map<String, Boolean> slots = defaultSlots();
    public volatile String messagePrefix = DEFAULT_PREFIX;
    public volatile boolean showPrefix = true;
    public volatile boolean includeItemName = true;
    public volatile String warningColor = DEFAULT_WARNING_COLOR;
    public volatile String criticalColor = DEFAULT_CRITICAL_COLOR;

    public DuraPingConfig() {
    }

    public static DuraPingConfig get() {
        return INSTANCE;
    }

    /** Fresh instance holding defaults (used by tests and reset). */
    public static DuraPingConfig defaults() {
        DuraPingConfig config = new DuraPingConfig();
        config.normalize();
        return config;
    }

    public static Map<String, Boolean> defaultSlots() {
        Map<String, Boolean> slots = new LinkedHashMap<>();
        for (SlotKey key : SlotKey.values()) {
            slots.put(key.configKey(), true);
        }
        return slots;
    }

    public static boolean isValidThreshold(int value) {
        return value >= 0 && value <= MAX_THRESHOLD;
    }

    public static boolean isValidCountdown(int value) {
        return value >= 1 && value <= MAX_COUNTDOWN;
    }

    /** Accepted color names are the {@code net.minecraft.util.Formatting} names. */
    public static boolean isValidColor(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String allowed : new String[]{
                "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED",
                "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN",
                "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE"}) {
            if (allowed.equalsIgnoreCase(name.strip())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses JSON without ever throwing: malformed input yields defaults.
     * Used by {@link #load()} and by unit tests.
     */
    public static DuraPingConfig parseOrDefaults(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        try {
            DuraPingConfig parsed = GSON.fromJson(json, DuraPingConfig.class);
            if (parsed == null) {
                return defaults();
            }
            parsed.normalize();
            return parsed;
        } catch (JsonParseException e) {
            LOG.log(Level.WARNING, "[DuraPing] Ignoring malformed config, using defaults: " + e.getMessage());
            return defaults();
        }
    }

    /** Validates every field in place; missing/invalid values become defaults. */
    public void normalize() {
        if (thresholds == null) {
            thresholds = new ArrayList<>(DEFAULT_THRESHOLDS);
        } else {
            TreeSet<Integer> cleaned = new TreeSet<>();
            for (Integer threshold : thresholds) {
                if (threshold != null && isValidThreshold(threshold)) {
                    cleaned.add(threshold);
                }
            }
            thresholds = new ArrayList<>(cleaned);
        }
        if (!isValidCountdown(countdownThreshold)) {
            countdownThreshold = DEFAULT_COUNTDOWN;
        }
        if (slots == null) {
            slots = defaultSlots();
        } else {
            for (SlotKey key : SlotKey.values()) {
                slots.putIfAbsent(key.configKey(), true);
            }
        }
        if (messagePrefix == null || messagePrefix.isBlank()) {
            messagePrefix = DEFAULT_PREFIX;
        } else {
            messagePrefix = messagePrefix.strip();
        }
        if (!isValidColor(warningColor)) {
            warningColor = DEFAULT_WARNING_COLOR;
        } else {
            warningColor = warningColor.strip().toUpperCase();
        }
        if (!isValidColor(criticalColor)) {
            criticalColor = DEFAULT_CRITICAL_COLOR;
        } else {
            criticalColor = criticalColor.strip().toUpperCase();
        }
    }

    /** Ordinary thresholds in predictable ascending order. */
    public List<Integer> sortedThresholds() {
        return new ArrayList<>(new TreeSet<>(thresholds == null ? List.of() : thresholds));
    }

    public boolean isSlotEnabled(SlotKey key) {
        if (key == null || slots == null) {
            return false;
        }
        return slots.getOrDefault(key.configKey(), false);
    }

    public void setSlotEnabled(SlotKey key, boolean enabled) {
        if (key == null) {
            return;
        }
        if (slots == null) {
            slots = defaultSlots();
        }
        slots.put(key.configKey(), enabled);
    }

    /** Copies all settings from another instance (used by reload/reset/tests). */
    public void copyFrom(DuraPingConfig other) {
        if (other == null) {
            return;
        }
        enabled = other.enabled;
        thresholds = new ArrayList<>(other.thresholds == null ? DEFAULT_THRESHOLDS : other.thresholds);
        countdownThreshold = other.countdownThreshold;
        slots = new LinkedHashMap<>(other.slots == null ? defaultSlots() : other.slots);
        messagePrefix = other.messagePrefix;
        showPrefix = other.showPrefix;
        includeItemName = other.includeItemName;
        warningColor = other.warningColor;
        criticalColor = other.criticalColor;
        normalize();
    }

    public String toJson() {
        normalize();
        return GSON.toJson(this);
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("duraping.json");
    }

    /** Loads the config, generating defaults on first run and surviving bad files. */
    public static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            INSTANCE.normalize();
            save();
            return;
        }
        try {
            String json = Files.readString(path);
            try {
                DuraPingConfig parsed = GSON.fromJson(json, DuraPingConfig.class);
                if (parsed == null) {
                    throw new JsonParseException("empty config");
                }
                parsed.normalize();
                INSTANCE.copyFrom(parsed);
            } catch (JsonParseException bad) {
                preserveInvalidFile(path);
                LOG.log(Level.WARNING, "[DuraPing] Malformed config preserved, keeping defaults: " + bad.getMessage());
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[DuraPing] Could not read config, keeping defaults: " + e.getMessage());
        }
    }

    /** Saves atomically (temp file + move) so an interruption cannot corrupt the config. */
    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("duraping.json.tmp");
            Files.writeString(temp, GSON.toJson(INSTANCE));
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[DuraPing] Could not save config: " + e.getMessage());
        }
    }

    private static void preserveInvalidFile(Path path) {
        try {
            Path backup = path.resolveSibling("duraping.json.invalid");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[DuraPing] Could not preserve invalid config: " + e.getMessage());
        }
    }

    /** Human-readable one-line summary of slot states, used by commands. */
    public static String describeSlots(DuraPingConfig config) {
        List<String> parts = new ArrayList<>();
        for (SlotKey key : SlotKey.values()) {
            parts.add(key.configKey() + "=" + (config.isSlotEnabled(key) ? "on" : "off"));
        }
        return String.join(", ", parts);
    }

    /** Human-readable summary of thresholds, used by commands. */
    public static String describeThresholds(DuraPingConfig config) {
        List<Integer> thresholds = config.sortedThresholds();
        return thresholds.isEmpty() ? "(none)" : Arrays.toString(thresholds.toArray());
    }
}
