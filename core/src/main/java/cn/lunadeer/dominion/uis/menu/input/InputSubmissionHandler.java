package cn.lunadeer.dominion.uis.menu.input;

import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;

import java.util.concurrent.CompletionStage;

/**
 * Executes a shared domain operation after renderer input normalization.
 */
@FunctionalInterface
public interface InputSubmissionHandler {

    /**
     * Processes normalized inputs stored in the supplied action context.
     */
    CompletionStage<ActionResult> submit(ActionContext context);
}
