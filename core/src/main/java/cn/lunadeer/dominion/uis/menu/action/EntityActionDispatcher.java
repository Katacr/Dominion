package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.utils.Misc.isPaper;

/**
 * Starts every action on the triggering player's Spigot or Folia entity scheduler.
 */
public final class EntityActionDispatcher implements ActionDispatcher {

    /**
     * Schedules one handler and bridges its eventual completion into a stable future.
     */
    @Override
    public CompletionStage<ActionResult> dispatch(ActionContext context, ActionHandler handler, ActionSpec action) {
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        try {
            Runnable invocation = () -> invoke(context, handler, action, result);
            if (isOwnedThread(context)) {
                invocation.run();
            } else {
                Scheduler.runEntityTask(invocation, context.player());
            }
        } catch (Exception exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private boolean isOwnedThread(ActionContext context) {
        return isPaper()
                ? Bukkit.isOwnedByCurrentRegion(context.player())
                : Bukkit.isPrimaryThread();
    }

    private void invoke(ActionContext context,
                        ActionHandler handler,
                        ActionSpec action,
                        CompletableFuture<ActionResult> result) {
        try {
            CompletionStage<ActionResult> execution = handler.execute(context, action);
            if (execution == null) {
                result.completeExceptionally(new IllegalStateException("Action handler returned no future"));
                return;
            }
            execution.whenComplete((value, throwable) -> {
                if (throwable == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (Exception exception) {
            result.completeExceptionally(exception);
        }
    }
}
