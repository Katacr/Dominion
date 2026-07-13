package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores one localized Dialog button and its validated action sequence.
 */
public record DialogButtonDefinition(
        String id,
        String text,
        String tooltip,
        int width,
        String permission,
        String actionId,
        List<ActionSpec> actions,
        Map<String, String> variables,
        Map<String, String> trustedArguments,
        DialogPromptDefinition prompt
) {

    /**
     * Freezes callback actions before platform rendering.
     */
    public DialogButtonDefinition {
        id = Objects.requireNonNull(id, "id");
        text = Objects.requireNonNull(text, "text");
        tooltip = Objects.requireNonNullElse(tooltip, "");
        permission = Objects.requireNonNullElse(permission, "");
        actionId = Objects.requireNonNull(actionId, "actionId");
        actions = List.copyOf(actions);
        variables = Map.copyOf(variables);
        trustedArguments = Map.copyOf(trustedArguments);
    }

    /**
     * Binds one repeated button to provider-owned display and callback data.
     */
    public DialogButtonDefinition withContext(String contextualId,
                                              Map<String, String> contextualVariables,
                                              Map<String, String> contextualArguments) {
        return new DialogButtonDefinition(contextualId, text, tooltip, width, permission,
                actionId, actions, contextualVariables, contextualArguments, prompt);
    }
}
