package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.configuration.Configuration;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Defines the bounded server feature switches that may control configured CUI buttons.
 */
public enum CuiCapability {
    GROUP_TITLE("group-title", () -> Configuration.groupTitle.enable),
    RESIDENCE_MIGRATION("residence-migration", () -> Configuration.residenceMigration);

    private final String configKey;
    private final BooleanSupplier enabled;

    CuiCapability(String configKey, BooleanSupplier enabled) {
        this.configKey = configKey;
        this.enabled = enabled;
    }

    /**
     * Resolves a configured capability through the fixed server-owned whitelist.
     */
    public static CuiCapability fromConfigKey(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CuiCapability capability : values()) {
            if (capability.configKey.equals(normalized)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown CUI capability: " + value);
    }

    /**
     * Returns the current server configuration state for this capability.
     */
    public boolean enabled() {
        return enabled.getAsBoolean();
    }
}
