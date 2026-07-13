package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;

import java.util.List;

/**
 * Stores one validated named button from a localized TUI menu.
 */
public record TuiButtonDefinition(
        String id,
        TuiButtonType type,
        String text,
        String hover,
        String actionId,
        List<ActionSpec> actions,
        String url,
        String copy,
        String permission,
        String disabledText,
        boolean hiddenWhenDisabled,
        TuiListDefinition list
) {

    /**
     * Freezes resolved actions before the button enters the menu registry.
     */
    public TuiButtonDefinition {
        actions = List.copyOf(actions);
    }
}
