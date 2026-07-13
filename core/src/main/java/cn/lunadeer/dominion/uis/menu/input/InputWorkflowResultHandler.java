package cn.lunadeer.dominion.uis.menu.input;

import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;

/**
 * Routes the terminal result of a renderer-specific input workflow.
 */
@FunctionalInterface
public interface InputWorkflowResultHandler {

    /**
     * Handles submission, cancellation, timeout, or an unexpected workflow failure.
     */
    void complete(ActionContext context, ActionResult result, Throwable throwable);
}
