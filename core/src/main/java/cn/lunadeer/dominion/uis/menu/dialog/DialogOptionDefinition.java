package cn.lunadeer.dominion.uis.menu.dialog;

import java.util.Objects;

/**
 * Stores one immutable dropdown option and its localized display text.
 */
public record DialogOptionDefinition(String id, String display, boolean initial) {

    /**
     * Rejects incomplete option definitions before platform rendering.
     */
    public DialogOptionDefinition {
        id = Objects.requireNonNull(id, "id");
        display = Objects.requireNonNull(display, "display");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Dialog option id cannot be blank");
        }
    }
}
