package cn.lunadeer.dominion.uis.menu.action;

import java.util.Objects;

/**
 * Stores one validated action parsed from a menu configuration.
 */
public record ActionSpec(String type, String argument, String configPath) {

    /**
     * Creates an immutable action specification.
     */
    public ActionSpec {
        type = Objects.requireNonNull(type, "type").trim().toLowerCase();
        argument = Objects.requireNonNullElse(argument, "").trim();
        configPath = Objects.requireNonNullElse(configPath, "unknown");
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Action type cannot be empty at " + configPath);
        }
    }
}
