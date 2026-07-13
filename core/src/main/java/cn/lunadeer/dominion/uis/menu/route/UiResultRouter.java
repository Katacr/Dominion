package cn.lunadeer.dominion.uis.menu.route;

import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Routes terminal action outcomes back to the renderer surface that created them.
 */
public final class UiResultRouter {

    private final Map<UiSurface, UiResultHandler> handlers = new EnumMap<>(UiSurface.class);

    /**
     * Registers one surface handler and rejects duplicate renderer ownership.
     */
    public synchronized void register(UiSurface surface, UiResultHandler handler) {
        if (handlers.putIfAbsent(Objects.requireNonNull(surface, "surface"),
                Objects.requireNonNull(handler, "handler")) != null) {
            throw new IllegalStateException("UI result handler already registered for " + surface);
        }
    }

    /**
     * Dispatches an input workflow result using its preserved action context surface.
     */
    public boolean dispatch(ActionContext context, ActionResult result, Throwable throwable) {
        return dispatch(context.surface(), context.player(), context.route(), result, throwable);
    }

    /**
     * Dispatches one callback result and reports whether its surface is active.
     */
    public boolean dispatch(UiSurface surface,
                            org.bukkit.entity.Player player,
                            MenuRoute route,
                            ActionResult result,
                            Throwable throwable) {
        UiResultHandler handler;
        synchronized (this) {
            handler = handlers.get(surface);
        }
        if (handler == null) {
            return false;
        }
        handler.handle(player, route, result, throwable);
        return true;
    }
}
