package cn.lunadeer.dominion.uis.menu.session;

/**
 * Signals that an asynchronous render tried to register callbacks from an obsolete menu revision.
 */
public final class StaleMenuRevisionException extends IllegalStateException {

    /**
     * Creates a concise stale revision failure without exposing callback state.
     */
    public StaleMenuRevisionException() {
        super("Configured menu revision is no longer active");
    }
}
