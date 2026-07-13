package cn.lunadeer.dominion.uis.menu.action;

import org.bukkit.entity.Player;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionExecutorTest {

    @Test
    void waitsForEachActionBeforeStartingTheNext() {
        ActionRegistry registry = new ActionRegistry();
        List<String> executionOrder = new ArrayList<>();
        CompletableFuture<ActionResult> firstCompletion = new CompletableFuture<>();
        registry.register("first", (context, action) -> {
            executionOrder.add("first-start");
            return firstCompletion;
        });
        registry.register("second", (context, action) -> {
            executionOrder.add("second-start");
            return CompletableFuture.completedFuture(ActionResult.continueExecution());
        });

        CompletableFuture<ActionResult> result = new ActionExecutor(registry, Duration.ofSeconds(1)).execute(
                context(),
                List.of(new ActionSpec("first", "", "first"), new ActionSpec("second", "", "second"))
        ).toCompletableFuture();

        assertEquals(List.of("first-start"), executionOrder);
        firstCompletion.complete(ActionResult.continueExecution());
        result.join();
        assertEquals(List.of("first-start", "second-start"), executionOrder);
    }

    @Test
    void stopsAfterFailureResult() {
        ActionRegistry registry = new ActionRegistry();
        List<String> executionOrder = new ArrayList<>();
        registry.register("fail", (context, action) -> {
            executionOrder.add("fail");
            return CompletableFuture.completedFuture(ActionResult.failure("expected"));
        });
        registry.register("skipped", (context, action) -> {
            executionOrder.add("skipped");
            return CompletableFuture.completedFuture(ActionResult.continueExecution());
        });

        ActionResult result = new ActionExecutor(registry, Duration.ofSeconds(1)).execute(
                context(),
                List.of(new ActionSpec("fail", "", "fail"), new ActionSpec("skipped", "", "skipped"))
        ).toCompletableFuture().join();

        assertEquals(ActionResult.Kind.FAILURE, result.kind());
        assertEquals(List.of("fail"), executionOrder);
    }

    @Test
    void failsWorkflowThatNeverCompletes() {
        ActionRegistry registry = new ActionRegistry();
        registry.register("wait", (context, action) -> new CompletableFuture<>());

        ActionResult result = new ActionExecutor(registry, Duration.ofMillis(20)).execute(
                context(), List.of(new ActionSpec("wait", "", "wait"))
        ).toCompletableFuture().join();

        assertEquals(ActionResult.Kind.FAILURE, result.kind());
        assertTrue(result.cause() instanceof java.util.concurrent.TimeoutException);
    }

    private ActionContext context() {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType())
        );
        return new ActionContext(player, MenuRoute.of("test"), 1, Map.of(), Map.of(), Map.of(), operation -> "");
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
