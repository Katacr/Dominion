package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;

import java.util.List;
import java.util.Objects;

/**
 * Stores one validated click action sequence or shared action-group reference.
 */
public record ChestClickDefinition(
        String actionId,
        List<ActionSpec> actions
) {

    /**
     * Freezes resolved actions so runtime clicks never parse YAML strings.
     */
    public ChestClickDefinition {
        actionId = Objects.requireNonNull(actionId, "actionId");
        actions = List.copyOf(actions);
        if (actionId.isBlank() || actions.isEmpty()) {
            throw new IllegalArgumentException("Chest click requires an action id and at least one action");
        }
    }
}
