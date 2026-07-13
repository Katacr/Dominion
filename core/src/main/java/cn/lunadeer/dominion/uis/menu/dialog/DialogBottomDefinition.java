package cn.lunadeer.dominion.uis.menu.dialog;

import java.util.List;
import java.util.Objects;

/**
 * Defines a KaMenu-compatible notice, confirmation, or multi-button footer.
 */
public record DialogBottomDefinition(
        Type type,
        List<DialogButtonDefinition> buttons,
        DialogButtonDefinition exit,
        int columns
) {

    /**
     * Lists the supported native Dialog footer layouts.
     */
    public enum Type {
        NOTICE,
        CONFIRMATION,
        MULTI
    }

    /**
     * Freezes button ordering because it controls native Dialog placement.
     */
    public DialogBottomDefinition {
        type = Objects.requireNonNull(type, "type");
        buttons = List.copyOf(buttons);
    }
}
