package cn.lunadeer.dominion.uis.menu.input;

import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import cn.lunadeer.dominion.utils.stui.inputter.Inputter;
import cn.lunadeer.dominion.utils.stui.inputter.InputterRunner;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Adapts one-field TUI/CUI chat input to renderer-neutral normalized inputs.
 */
public final class ChatInputWorkflow {

    private static final Duration DEFAULT_INPUT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_SUBMISSION_TIMEOUT = Duration.ofSeconds(30);

    private final Duration inputTimeout;
    private final Duration submissionTimeout;
    private final InputWorkflowResultHandler resultHandler;

    /**
     * Creates a chat workflow using bounded input and submission durations.
     */
    public ChatInputWorkflow(InputWorkflowResultHandler resultHandler) {
        this(DEFAULT_INPUT_TIMEOUT, DEFAULT_SUBMISSION_TIMEOUT, resultHandler);
    }

    /**
     * Creates a workflow with explicit input and submission timeout policies.
     */
    ChatInputWorkflow(Duration inputTimeout,
                      Duration submissionTimeout,
                      InputWorkflowResultHandler resultHandler) {
        if (inputTimeout.isZero() || inputTimeout.isNegative()
                || submissionTimeout.isZero() || submissionTimeout.isNegative()) {
            throw new IllegalArgumentException("Input workflow timeouts must be positive");
        }
        this.inputTimeout = inputTimeout;
        this.submissionTimeout = submissionTimeout;
        this.resultHandler = resultHandler;
    }

    /**
     * Starts a one-field chat prompt while retaining trusted route and callback state.
     */
    public void start(ActionContext context,
                      InputSchema schema,
                      String hint,
                      InputSubmissionHandler submissionHandler) {
        if (context.surface() == UiSurface.CUI) {
            context.player().closeInventory();
        }
        prompt(context, schema, hint, submissionHandler);
    }

    private void prompt(ActionContext context,
                        InputSchema schema,
                        String hint,
                        InputSubmissionHandler submissionHandler) {
        InputFieldSpec field = schema.singleField();
        InputterRunner runner = new InputterRunner(context.player(), hint) {
            @Override
            public void run(String input) {
                Map<String, String> normalized;
                try {
                    normalized = schema.normalize(Map.of(field.key(), input));
                } catch (InputValidationException exception) {
                    Notification.error(context.player(), exception.getMessage());
                    prompt(context, schema, hint, submissionHandler);
                    return;
                }
                submit(context.withInputs(normalized), submissionHandler);
            }

            @Override
            public void cancelRun() {
                resultHandler.complete(context, ActionResult.refresh(), null);
            }
        };
        long timeoutTicks = Math.max(1L, inputTimeout.toMillis() / 50L);
        Scheduler.runTaskLater(() -> {
            if (!Inputter.getInstance().unregister(runner)) {
                return;
            }
            Scheduler.runEntityTask(() -> {
                Notification.warn(context.player(), "Input timed out.");
                resultHandler.complete(context, ActionResult.refresh(), null);
            }, context.player());
        }, timeoutTicks);
    }

    private void submit(ActionContext context, InputSubmissionHandler submissionHandler) {
        CompletionStage<ActionResult> submission;
        try {
            submission = submissionHandler.submit(context);
        } catch (Exception exception) {
            resultHandler.complete(context, null, exception);
            return;
        }
        if (submission == null) {
            resultHandler.complete(context, null,
                    new IllegalStateException("Input submission handler returned no future"));
            return;
        }
        submission.toCompletableFuture()
                .orTimeout(submissionTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> Scheduler.runEntityTask(
                        () -> resultHandler.complete(context, result, throwable), context.player()));
    }
}
