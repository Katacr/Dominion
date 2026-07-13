package cn.lunadeer.dominion.uis.menu.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DominionActionRegistryTest {

    private DominionActionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DominionActionRegistry();
        registry.register("known-operation", (context, action) ->
                CompletableFuture.completedFuture(ActionResult.stop()), action -> {
            if (!action.argument().isBlank()) {
                throw new IllegalArgumentException("Arguments are not supported");
            }
        });
    }

    @Test
    void acceptsRegisteredOperation() {
        assertDoesNotThrow(() -> registry.validate(
                new ActionSpec("dominion", "known-operation", "test.actions[0]")));
    }

    @Test
    void rejectsUnknownOperationDuringLoading() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "unknown-operation", "test.actions[0]")));
    }

    @Test
    void appliesOperationSpecificValidator() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "known-operation unexpected", "test.actions[0]")));
    }
}
