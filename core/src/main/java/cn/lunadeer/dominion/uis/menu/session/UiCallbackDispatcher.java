package cn.lunadeer.dominion.uis.menu.session;

import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionExecutor;
import cn.lunadeer.dominion.uis.menu.route.UiResultRouter;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Consumes transport-neutral callback tokens and executes their trusted action context.
 */
public final class UiCallbackDispatcher {

    private static final Pattern CALLBACK_TOKEN = Pattern.compile("[a-f0-9]{32}");

    private final UiCallbackSessionManager sessions;
    private final ActionExecutor executor;
    private final UiResultRouter results;

    /**
     * Creates a dispatcher shared by command, Inventory, and future Dialog transports.
     */
    public UiCallbackDispatcher(UiCallbackSessionManager sessions,
                                ActionExecutor executor,
                                UiResultRouter results) {
        this.sessions = sessions;
        this.executor = executor;
        this.results = results;
    }

    /**
     * Consumes one player-bound callback and schedules its terminal result on the player entity.
     */
    public boolean dispatch(Player player, String token) {
        return dispatch(player, token, Map.of());
    }

    /**
     * Consumes one callback and normalizes renderer-submitted input before action execution.
     */
    public boolean dispatch(Player player, String token, Map<String, String> rawInputs) {
        return dispatch(player, token, rawInputs, false);
    }

    /**
     * Consumes one callback after a server-owned secondary confirmation.
     */
    public boolean dispatch(Player player,
                            String token,
                            Map<String, String> rawInputs,
                            boolean confirmed) {
        if (!CALLBACK_TOKEN.matcher(token).matches()) {
            Notification.error(player, "Invalid UI callback.");
            return false;
        }
        Optional<UiCallbackSessionManager.RegisteredCallback> stored = sessions.consume(player, token);
        if (stored.isEmpty()) {
            Notification.warn(player, "This menu action has expired. Please reopen the menu.");
            return false;
        }
        UiCallbackSessionManager.RegisteredCallback callback = stored.get();
        Map<String, String> submittedInputs;
        try {
            if (callback.inputSchema() == null) {
                if (!rawInputs.isEmpty()) {
                    throw new IllegalArgumentException("Callback does not accept submitted inputs");
                }
                submittedInputs = Map.of();
            } else {
                submittedInputs = callback.inputSchema().normalize(rawInputs);
            }
        } catch (Exception exception) {
            Notification.error(player, exception.getMessage());
            return false;
        }
        Map<String, String> trustedArguments = new java.util.LinkedHashMap<>(callback.trustedArguments());
        if (confirmed) {
            trustedArguments.put("ui.confirmed", "true");
        }
        ActionContext context = new ActionContext(
                callback.surface(),
                player,
                callback.route(),
                callback.menuRevision(),
                callback.variables(),
                trustedArguments,
                submittedInputs,
                callback.actionGroups(),
                actionGroupId -> sessions.register(player, callback.route(), callback.menuRevision(), actionGroupId,
                        callback.actionGroups(), callback.variables(), callback.trustedArguments(), callback.surface())
        );
        executor.execute(context, callback.actions()).whenComplete((result, throwable) ->
                Scheduler.runEntityTask(() -> {
                    if (!results.dispatch(callback.surface(), player, callback.route(), result, throwable)) {
                        Notification.error(player, "UI result surface is unavailable: {0}", callback.surface());
                    }
                }, player));
        return true;
    }
}
