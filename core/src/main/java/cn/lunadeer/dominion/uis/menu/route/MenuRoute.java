package cn.lunadeer.dominion.uis.menu.route;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifies one menu page and its server-owned navigation arguments.
 */
public record MenuRoute(String menuId, int page, Map<String, String> arguments) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern ARGUMENT_KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    /**
     * Validates and freezes route state before it enters a UI session.
     */
    public MenuRoute {
        menuId = Objects.requireNonNull(menuId, "menuId").trim();
        if (!ID_PATTERN.matcher(menuId).matches()) {
            throw new IllegalArgumentException("Invalid menu route id: " + menuId);
        }
        page = Math.max(1, page);
        arguments = Map.copyOf(arguments);
        if (arguments.size() > 16) {
            throw new IllegalArgumentException("Menu route cannot contain more than 16 arguments");
        }
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (!ARGUMENT_KEY_PATTERN.matcher(entry.getKey()).matches() || entry.getValue().length() > 256) {
                throw new IllegalArgumentException("Invalid menu route argument: " + entry.getKey());
            }
        }
    }

    /**
     * Creates a first-page route without additional arguments.
     */
    public static MenuRoute of(String menuId) {
        return new MenuRoute(menuId, 1, Map.of());
    }

    /**
     * Returns the same route on a different positive page.
     */
    public MenuRoute withPage(int targetPage) {
        return new MenuRoute(menuId, targetPage, arguments);
    }

    /**
     * Checks whether a route argument key follows the shared schema convention.
     */
    public static boolean isValidArgumentKey(String key) {
        return key != null && ARGUMENT_KEY_PATTERN.matcher(key).matches();
    }
}
