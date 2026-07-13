package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NavigationActionTest {

    private ActionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        new BuiltInActionRegistrar(null).registerInto(registry);
    }

    @Test
    void nextPageKeepsRouteArguments() {
        ActionResult result = registry.resolve("page").execute(
                context(new MenuRoute("templates", 2, Map.of("owner", "player"))),
                new ActionSpec("page", "next", "test.page")
        ).toCompletableFuture().join();

        assertEquals(ActionResult.Kind.OPEN, result.kind());
        assertEquals(3, result.route().page());
        assertEquals("player", result.route().arguments().get("owner"));
    }

    @Test
    void refreshKeepsTheCurrentRoute() {
        MenuRoute route = new MenuRoute("templates", 4, Map.of());
        ActionResult result = registry.resolve("refresh").execute(
                context(route), new ActionSpec("refresh", "", "test.refresh")
        ).toCompletableFuture().join();

        assertEquals(ActionResult.Kind.REFRESH, result.kind());
    }

    @Test
    void routeRejectsUnboundedArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new MenuRoute("templates", 1, Map.of("invalid key", "value")));
    }

    private ActionContext context(MenuRoute route) {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> null
        );
        return new ActionContext(player, route, 1, Map.of(), Map.of(), Map.of(), operation -> "");
    }
}
