package cn.lunadeer.dominion.uis.menu.route;

import cn.lunadeer.dominion.uis.menu.tui.TuiMenuDefinition;
import cn.lunadeer.dominion.uis.menu.tui.TuiMenuRepository;
import cn.lunadeer.dominion.uis.menu.tui.TuiRenderer;
import cn.lunadeer.dominion.uis.menu.session.StaleMenuRevisionException;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.XLogger;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Resolves configured menu routes and delegates rendering to the active TUI renderer.
 */
public final class UiRouter {

    private final TuiMenuRepository repository;
    private final TuiRenderer renderer;
    private final UiRequestTracker requests = new UiRequestTracker();

    /**
     * Creates a router for the currently loaded configured TUI registry.
     */
    public UiRouter(TuiMenuRepository repository, TuiRenderer renderer) {
        this.repository = repository;
        this.renderer = renderer;
    }

    /**
     * Opens a route and reports asynchronous provider or render failures.
     */
    public void open(Player player, MenuRoute route) {
        TuiMenuDefinition menu = repository.find(route.menuId(), repository.localeFor(player));
        if (menu == null) {
            Notification.error(player, "Configured TUI menu not found: {0}", route.menuId());
            return;
        }
        if (!validArguments(menu, route)) {
            Notification.error(player, "Invalid route arguments for configured TUI menu: {0}", route.menuId());
            return;
        }
        long revision = repository.revision();
        UUID playerId = player.getUniqueId();
        long requestId = requests.begin(playerId);
        renderer.show(player, menu, route, revision, () -> repository.revision() == revision
                        && requests.isActive(playerId, requestId))
                .whenComplete((ignored, throwable) -> {
            requests.complete(playerId, requestId);
            if (throwable != null) {
                if (rootCause(throwable) instanceof StaleMenuRevisionException) {
                    return;
                }
                Scheduler.runEntityTask(() -> {
                    Notification.error(player, "Failed to render configured TUI menu: {0}", route.menuId());
                    XLogger.error(throwable);
                }, player);
            }
        });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private boolean validArguments(TuiMenuDefinition menu, MenuRoute route) {
        if (!route.arguments().keySet().containsAll(menu.shared().requiredRouteArguments())) {
            return false;
        }
        java.util.Set<String> allowed = new java.util.HashSet<>(menu.shared().requiredRouteArguments());
        allowed.addAll(menu.shared().optionalRouteArguments());
        return allowed.containsAll(route.arguments().keySet());
    }
}
