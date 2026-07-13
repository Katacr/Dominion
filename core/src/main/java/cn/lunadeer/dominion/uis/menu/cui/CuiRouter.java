package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiRequestTracker;
import cn.lunadeer.dominion.uis.menu.session.StaleMenuRevisionException;
import cn.lunadeer.dominion.uis.menu.tui.TuiMenuRepository;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.XLogger;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.UUID;

/**
 * Resolves configured chest routes and guards asynchronous render requests against stale reloads.
 */
public final class CuiRouter {

    private final TuiMenuRepository repository;
    private final CuiRenderer renderer;
    private final UiRequestTracker requests = new UiRequestTracker();

    /**
     * Creates a chest route adapter over the shared atomic menu repository.
     */
    public CuiRouter(TuiMenuRepository repository, CuiRenderer renderer) {
        this.repository = repository;
        this.renderer = renderer;
    }

    /**
     * Opens a validated chest route and reports render failures on the player scheduler.
     */
    public void open(Player player, MenuRoute route) {
        ChestMenuDefinition menu = repository.findChest(route.menuId(), repository.localeFor(player));
        if (menu == null) {
            Notification.error(player, "Configured CUI menu not found: {0}", route.menuId());
            return;
        }
        if (!validArguments(menu, route)) {
            Notification.error(player, "Invalid route arguments for configured CUI menu: {0}", route.menuId());
            return;
        }
        long revision = repository.revision();
        UUID playerId = player.getUniqueId();
        long requestId = requests.begin(playerId);
        renderer.show(player, menu, route, revision, () -> repository.revision() == revision
                        && requests.isActive(playerId, requestId))
                .whenComplete((ignored, throwable) -> {
                    requests.complete(playerId, requestId);
                    if (throwable == null || rootCause(throwable) instanceof StaleMenuRevisionException) {
                        return;
                    }
                    Scheduler.runEntityTask(() -> {
                        Notification.error(player, "Failed to render configured CUI menu: {0}", route.menuId());
                        XLogger.error(throwable);
                    }, player);
                });
    }

    private boolean validArguments(ChestMenuDefinition menu, MenuRoute route) {
        if (!route.arguments().keySet().containsAll(menu.shared().requiredRouteArguments())) {
            return false;
        }
        HashSet<String> allowed = new HashSet<>(menu.shared().requiredRouteArguments());
        allowed.addAll(menu.shared().optionalRouteArguments());
        return allowed.containsAll(route.arguments().keySet());
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
