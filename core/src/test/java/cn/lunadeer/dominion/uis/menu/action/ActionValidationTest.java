package cn.lunadeer.dominion.uis.menu.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionValidationTest {

    private ActionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        new BuiltInActionRegistrar(null).registerInto(registry);
    }

    @Test
    void acceptsStructuredTitleArguments() {
        assertDoesNotThrow(() -> registry.validate(
                new ActionSpec("title", "title=Dominion;subtitle=Menu", "test.actions[0]")));
    }

    @Test
    void rejectsArgumentsForArgumentlessAction() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("refresh", "unexpected", "test.actions[0]")));
    }

    @Test
    void rejectsUnknownStructuredArgument() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("toast", "title=Saved;sound=ENTITY_EXPERIENCE_ORB_PICKUP", "test.actions[0]")));
    }

    @Test
    void rejectsInvalidPageTarget() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("page", "0", "test.actions[0]")));
    }

    @Test
    void rejectsMalformedClickableText() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(new ActionSpec(
                "hovertext", "<text=Click;actions=open", "test.actions[0]")));
    }

    @Test
    void rejectsUnsafeClickableUrl() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(new ActionSpec(
                "hovertext", "<text=Click;url=javascript:alert(1)>", "test.actions[0]")));
    }
}
