package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;

/**
 * Describes whether an action sequence should continue or change UI lifecycle.
 */
public record ActionResult(Kind kind, MenuRoute route, String message, Throwable cause) {

    /**
     * Lists all control-flow outcomes understood by the shared executor.
     */
    public enum Kind {
        CONTINUE,
        STOP,
        OPEN,
        REFRESH,
        CLOSE,
        FAILURE
    }

    /**
     * Continues with the next configured action.
     */
    public static ActionResult continueExecution() {
        return new ActionResult(Kind.CONTINUE, null, null, null);
    }

    /**
     * Stops the sequence without treating it as an error.
     */
    public static ActionResult stop() {
        return new ActionResult(Kind.STOP, null, null, null);
    }

    /**
     * Requests opening another menu route.
     */
    public static ActionResult open(MenuRoute route) {
        return new ActionResult(Kind.OPEN, route, null, null);
    }

    /**
     * Requests refreshing the current menu route.
     */
    public static ActionResult refresh() {
        return new ActionResult(Kind.REFRESH, null, null, null);
    }

    /**
     * Requests closing the current UI.
     */
    public static ActionResult close() {
        return new ActionResult(Kind.CLOSE, null, null, null);
    }

    /**
     * Stops the sequence with a user-facing failure message.
     */
    public static ActionResult failure(String message) {
        return new ActionResult(Kind.FAILURE, null, message, null);
    }

    /**
     * Stops the sequence after an unexpected handler failure.
     */
    public static ActionResult failure(String message, Throwable cause) {
        return new ActionResult(Kind.FAILURE, null, message, cause);
    }

    /**
     * Returns whether the executor should invoke the next action.
     */
    public boolean shouldContinue() {
        return kind == Kind.CONTINUE;
    }
}
