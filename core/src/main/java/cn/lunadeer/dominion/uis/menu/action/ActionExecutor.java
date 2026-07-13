package cn.lunadeer.dominion.uis.menu.action;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes menu actions strictly in order, including asynchronous handlers.
 */
public final class ActionExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final ActionRegistry registry;
    private final Duration timeout;
    private final ActionDispatcher dispatcher;

    /**
     * Creates an executor backed by one immutable action ownership registry.
     */
    public ActionExecutor(ActionRegistry registry) {
        this(registry, DEFAULT_TIMEOUT, new EntityActionDispatcher());
    }

    /**
     * Creates an executor with a bounded total workflow duration.
     */
    ActionExecutor(ActionRegistry registry, Duration timeout) {
        this(registry, timeout, (context, handler, action) -> handler.execute(context, action));
    }

    /**
     * Creates an executor with explicit workflow timeout and handler dispatcher.
     */
    ActionExecutor(ActionRegistry registry, Duration timeout, ActionDispatcher dispatcher) {
        this.registry = registry;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Action workflow timeout must be positive");
        }
        this.timeout = timeout;
        this.dispatcher = dispatcher;
    }

    /**
     * Starts an ordered action sequence.
     */
    public CompletionStage<ActionResult> execute(ActionContext context, List<ActionSpec> actions) {
        CompletableFuture<ActionResult> execution = execute(context, actions, 0).toCompletableFuture();
        return execution.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).exceptionally(throwable -> {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause() : throwable;
            if (cause instanceof TimeoutException) {
                return ActionResult.failure("UI action workflow timed out after " + timeout.toSeconds() + " seconds",
                        cause);
            }
            return ActionResult.failure("UI action workflow failed", cause);
        });
    }

    // A composed future chain preserves order when a later handler becomes asynchronous.
    private CompletionStage<ActionResult> execute(ActionContext context, List<ActionSpec> actions, int index) {
        if (index >= actions.size()) {
            return CompletableFuture.completedFuture(ActionResult.stop());
        }

        ActionSpec action = actions.get(index);
        ActionHandler handler = registry.resolve(action.type());
        if (handler == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "Unknown action type '" + action.type() + "' at " + action.configPath()));
        }

        CompletionStage<ActionResult> execution;
        try {
            execution = dispatcher.dispatch(context, handler, action);
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "Action failed at " + action.configPath(), exception));
        }
        if (execution == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    "Action handler returned no future at " + action.configPath()));
        }

        return execution.handle((result, throwable) -> {
            if (throwable == null) {
                return result == null
                        ? ActionResult.failure("Action handler returned no result at " + action.configPath())
                        : result;
            }
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause() : throwable;
            return ActionResult.failure("Action failed at " + action.configPath(), cause);
        }).thenCompose(result -> result.shouldContinue()
                ? execute(context, actions, index + 1)
                : CompletableFuture.completedFuture(result));
    }
}
