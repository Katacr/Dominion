package cn.lunadeer.dominion.uis.menu.route;

import cn.lunadeer.dominion.uis.menu.action.ActionResult;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiResultRouterTest {

    @Test
    void dispatchesOnlyToTheOriginSurface() {
        UiResultRouter router = new UiResultRouter();
        AtomicReference<String> handled = new AtomicReference<>();
        router.register(UiSurface.TUI, (player, route, result, throwable) -> handled.set("tui"));
        router.register(UiSurface.CUI, (player, route, result, throwable) -> handled.set("cui"));

        assertTrue(router.dispatch(UiSurface.CUI, player(), MenuRoute.of("test"), ActionResult.refresh(), null));
        assertEquals("cui", handled.get());
    }

    @Test
    void reportsUnavailableSurfaceAndRejectsDuplicateOwnership() {
        UiResultRouter router = new UiResultRouter();
        assertFalse(router.dispatch(UiSurface.DIALOG, player(), MenuRoute.of("test"), ActionResult.stop(), null));
        router.register(UiSurface.TUI, (player, route, result, throwable) -> {
        });
        assertThrows(IllegalStateException.class, () -> router.register(UiSurface.TUI,
                (player, route, result, throwable) -> {
                }));
    }

    private Player player() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> null
        );
    }
}
