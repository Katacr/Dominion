package cn.lunadeer.dominion.uis.menu.dialog;

import java.util.Objects;

/**
 * Describes a reusable secondary confirmation or input-capture Dialog.
 */
public record DialogPromptDefinition(
        Type type,
        String title,
        String body,
        DialogInputDefinition input,
        DialogPromptButtonDefinition confirm,
        DialogPromptButtonDefinition deny
) {

    /**
     * Lists the two secondary Dialog workflows supported by Dominion.
     */
    public enum Type {
        CONFIRMATION,
        INPUT_CAPTURE
    }

    /**
     * Enforces the input requirement of each prompt type.
     */
    public DialogPromptDefinition {
        type = Objects.requireNonNull(type, "type");
        title = Objects.requireNonNull(title, "title");
        body = Objects.requireNonNullElse(body, "");
        confirm = Objects.requireNonNull(confirm, "confirm");
        deny = Objects.requireNonNull(deny, "deny");
        if ((type == Type.INPUT_CAPTURE) != (input != null)) {
            throw new IllegalArgumentException("Only input-capture prompts may define an input");
        }
    }
}
