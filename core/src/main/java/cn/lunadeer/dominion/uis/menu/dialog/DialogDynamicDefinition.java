package cn.lunadeer.dominion.uis.menu.dialog;

import java.util.List;
import java.util.Objects;

/**
 * Defines one provider-backed group of repeated Dialog buttons.
 */
public record DialogDynamicDefinition(
        String source,
        int pageSize,
        String emptyText,
        String layout,
        int width,
        List<DialogButtonDefinition> buttons
) {

    /**
     * Freezes the entry button templates before runtime expansion.
     */
    public DialogDynamicDefinition {
        source = Objects.requireNonNull(source, "source");
        emptyText = Objects.requireNonNullElse(emptyText, "");
        layout = Objects.requireNonNull(layout, "layout");
        buttons = List.copyOf(buttons);
    }
}
