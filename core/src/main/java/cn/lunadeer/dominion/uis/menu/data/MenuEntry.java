package cn.lunadeer.dominion.uis.menu.data;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Separates display variables from server-trusted callback arguments for one list entry.
 */
public record MenuEntry(
        String id,
        Map<String, String> displayVariables,
        Map<String, String> trustedArguments,
        Set<String> availableActionGroups
) {

    private static final int MAX_VARIABLES = 64;
    private static final int MAX_VALUE_LENGTH = 4096;

    /**
     * Freezes provider output before renderer and callback code consume it.
     */
    public MenuEntry {
        id = Objects.requireNonNull(id, "id");
        displayVariables = Map.copyOf(displayVariables);
        trustedArguments = Map.copyOf(trustedArguments);
        availableActionGroups = Set.copyOf(availableActionGroups);
        if (id.length() > 256) {
            throw new IllegalArgumentException("Menu entry id exceeds 256 characters");
        }
        validateMap(displayVariables, "display variables");
        validateMap(trustedArguments, "trusted arguments");
        if (availableActionGroups.size() > MAX_VARIABLES) {
            throw new IllegalArgumentException("Menu entry has too many available actionGroups");
        }
    }

    private void validateMap(Map<String, String> values, String label) {
        if (values.size() > MAX_VARIABLES) {
            throw new IllegalArgumentException("Menu entry has too many " + label);
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!cn.lunadeer.dominion.uis.menu.route.MenuRoute.isValidArgumentKey(entry.getKey())
                    || entry.getValue().length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Invalid menu entry " + label + " key: " + entry.getKey());
            }
        }
    }
}
