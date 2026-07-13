package cn.lunadeer.dominion.uis.menu.tui;

import java.util.List;
import java.util.Map;

/**
 * Stores one validated flat LIST layout and its entry-scoped buttons.
 */
public record TuiListDefinition(
        String source,
        String providerId,
        int rows,
        String emptyText,
        List<String> layout,
        Map<String, TuiButtonDefinition> buttons
) {

    /**
     * Freezes entry layout state before rendering provider results.
     */
    public TuiListDefinition {
        layout = List.copyOf(layout);
        buttons = Map.copyOf(buttons);
    }
}
