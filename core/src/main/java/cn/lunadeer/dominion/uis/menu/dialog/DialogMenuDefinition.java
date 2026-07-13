package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.input.InputSchema;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores one immutable localized Dialog definition without platform API types.
 */
public record DialogMenuDefinition(
        String menuId,
        String locale,
        String title,
        boolean canEscape,
        boolean pause,
        DialogAfterAction afterAction,
        List<DialogBodyDefinition> body,
        List<DialogInputDefinition> inputs,
        DialogBottomDefinition bottom,
        DialogDynamicDefinition dynamic,
        Map<String, String> variables,
        SharedMenuDefinition shared,
        MenuRoute resolvedRoute
) {

    /**
     * Freezes all nested menu state for atomic repository snapshots.
     */
    public DialogMenuDefinition {
        menuId = Objects.requireNonNull(menuId, "menuId");
        locale = Objects.requireNonNull(locale, "locale");
        title = Objects.requireNonNull(title, "title");
        afterAction = Objects.requireNonNull(afterAction, "afterAction");
        body = List.copyOf(body);
        inputs = List.copyOf(inputs);
        bottom = Objects.requireNonNull(bottom, "bottom");
        variables = Map.copyOf(variables);
        shared = Objects.requireNonNull(shared, "shared");
    }

    /**
     * Returns the provider-normalized route or the route supplied by the caller.
     */
    public MenuRoute effectiveRoute(MenuRoute fallback) {
        return resolvedRoute == null ? fallback : resolvedRoute;
    }

    /**
     * Creates the shared input validator used by every server callback button.
     */
    public InputSchema inputSchema() {
        return inputs.isEmpty() ? null : new InputSchema(inputs.stream().map(DialogInputDefinition::fieldSpec).toList());
    }
}
