package cn.lunadeer.dominion.uis.menu.action;

import java.util.concurrent.CompletionStage;

/**
 * Executes one parsed action without controlling the remaining sequence.
 */
@FunctionalInterface
public interface ActionHandler {

    /**
     * Executes an action and returns its control-flow result.
     */
    CompletionStage<ActionResult> execute(ActionContext context, ActionSpec action);
}
