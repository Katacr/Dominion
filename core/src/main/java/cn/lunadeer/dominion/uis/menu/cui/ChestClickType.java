package cn.lunadeer.dominion.uis.menu.cui;

import java.util.Locale;

/**
 * Defines the bounded Bukkit click types configurable by chest menus.
 */
public enum ChestClickType {
    LEFT("left"),
    RIGHT("right"),
    SHIFT_LEFT("shift-left"),
    SHIFT_RIGHT("shift-right"),
    MIDDLE("middle"),
    DROP("drop");

    private final String configKey;

    /**
     * Associates one runtime click type with its stable YAML key.
     */
    ChestClickType(String configKey) {
        this.configKey = configKey;
    }

    /**
     * Returns the canonical key written to chest menu YAML.
     */
    public String configKey() {
        return configKey;
    }

    /**
     * Resolves a strict click key without accepting Bukkit implementation names.
     */
    public static ChestClickType fromConfigKey(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (ChestClickType type : values()) {
            if (type.configKey.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown chest click type: " + value);
    }
}
