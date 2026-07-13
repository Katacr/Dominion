package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;

import java.util.List;
import java.util.Map;

/**
 * Combines one shared menu definition with its localized text presentation.
 */
public record TuiMenuDefinition(
        String menuId,
        String locale,
        List<String> layout,
        Map<String, TuiButtonDefinition> buttons,
        Map<String, String> variables,
        SharedMenuDefinition shared
) {

    /**
     * Freezes layout and button maps before rendering.
     */
    public TuiMenuDefinition {
        layout = List.copyOf(layout);
        buttons = Map.copyOf(buttons);
        variables = Map.copyOf(variables);
    }
}
