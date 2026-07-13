package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionContextInputTest {

    @Test
    void keepsNormalizedInputsSeparateFromTrustedArguments() {
        ActionContext original = new ActionContext(
                player(), MenuRoute.of("test"), 1,
                Map.of("text.label", "Label"),
                Map.of("resource.id", "trusted-id"),
                Map.of(), operation -> ""
        );
        ActionContext submitted = original.withInputs(Map.of("template.name", "Example"));

        assertEquals("Example", submitted.requireInput("template.name"));
        assertEquals("Example", submitted.variables().get("input.template.name"));
        assertEquals("trusted-id", submitted.requireTrustedArgument("resource.id"));
        assertEquals(UiSurface.TUI, submitted.surface());
        assertThrows(IllegalStateException.class, () -> original.requireInput("template.name"));
    }

    @Test
    void preservesExplicitSurfaceAcrossInputSubmission() {
        ActionContext original = new ActionContext(
                UiSurface.CUI,
                player(), MenuRoute.of("test"), 1,
                Map.of(), Map.of(), Map.of(), Map.of(), operation -> ""
        );

        assertEquals(UiSurface.CUI, original.withInputs(Map.of("value", "test")).surface());
    }

    private Player player() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> null
        );
    }
}
