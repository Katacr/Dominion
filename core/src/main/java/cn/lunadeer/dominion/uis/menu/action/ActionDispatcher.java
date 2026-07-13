package cn.lunadeer.dominion.uis.menu.action;

import java.util.concurrent.CompletionStage;

/**
 * Controls the thread on which each action handler starts executing.
 */
@FunctionalInterface
public interface ActionDispatcher {

    /**
     * Dispatches one handler invocation and exposes its asynchronous result.
     */
    CompletionStage<ActionResult> dispatch(ActionContext context, ActionHandler handler, ActionSpec action);
}
