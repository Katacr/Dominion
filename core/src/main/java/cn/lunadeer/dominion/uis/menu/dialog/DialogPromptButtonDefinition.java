package cn.lunadeer.dominion.uis.menu.dialog;

import java.util.Objects;

/**
 * Defines one localized confirmation or cancellation button in a secondary Dialog.
 */
public record DialogPromptButtonDefinition(String text, String tooltip, int width) {

    /**
     * Normalizes optional hover text before platform rendering.
     */
    public DialogPromptButtonDefinition {
        text = Objects.requireNonNull(text, "text");
        tooltip = Objects.requireNonNullElse(tooltip, "");
    }
}
