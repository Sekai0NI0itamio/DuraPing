package com.duraping.notify;

/**
 * Builds the human-readable text of durability warnings.
 *
 * <p>Pure string building without Minecraft classes, so message wording is
 * unit-testable. Colors are applied by the sender ({@code ChatNotifier}),
 * not here.
 */
public final class MessageFormatter {
    private MessageFormatter() {
    }

    /**
     * Builds one warning line, e.g. {@code "[DuraPing] Diamond Pickaxe: 3 durability remaining!"}.
     *
     * @param prefix          configured prefix, e.g. {@code "[DuraPing]"}
     * @param showPrefix      whether the prefix is displayed
     * @param includeItemName whether the item's display name is included
     * @param itemName        display name of the item (may be blank)
     * @param remaining       remaining durability at the reported point (0 means broken)
     * @return the message text, never blank
     */
    public static String buildWarning(String prefix, boolean showPrefix, boolean includeItemName,
            String itemName, int remaining) {
        StringBuilder message = new StringBuilder();
        if (showPrefix && prefix != null && !prefix.isBlank()) {
            message.append(prefix.strip()).append(' ');
        }
        if (includeItemName && itemName != null && !itemName.isBlank()) {
            message.append(itemName.strip()).append(": ");
        }
        if (remaining <= 0) {
            message.append("0 durability remaining (broken!)");
        } else {
            message.append(remaining).append(" durability remaining!");
        }
        String text = message.toString().strip();
        return text.isEmpty() ? "durability warning!" : text;
    }
}
