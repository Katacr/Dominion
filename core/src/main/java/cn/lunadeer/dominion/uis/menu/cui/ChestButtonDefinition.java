package cn.lunadeer.dominion.uis.menu.cui;

import java.util.Map;
import java.util.Objects;

/**
 * Combines one layout symbol with display, data source, and click definitions.
 */
public record ChestButtonDefinition(
        char symbol,
        String source,
        ChestDisplayDefinition display,
        ChestDisplayDefinition emptyDisplay,
        Map<ChestClickType, ChestClickDefinition> clicks,
        String permission,
        String capability,
        boolean hiddenWhenDisabled
) {

    /**
     * Freezes symbol behavior before a menu definition enters the registry.
     */
    public ChestButtonDefinition {
        source = Objects.requireNonNullElse(source, "");
        display = Objects.requireNonNull(display, "display");
        clicks = Map.copyOf(clicks);
        permission = Objects.requireNonNullElse(permission, "");
        capability = Objects.requireNonNullElse(capability, "");
    }

    /**
     * Returns whether repeated layout slots consume entries from a shared data source.
     */
    public boolean dynamic() {
        return !source.isBlank();
    }
}
