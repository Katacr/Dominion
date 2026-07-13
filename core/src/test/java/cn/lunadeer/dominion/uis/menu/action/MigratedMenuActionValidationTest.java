package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.uis.menu.input.ChatInputWorkflow;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigratedMenuActionValidationTest {

    private DominionActionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DominionActionRegistry();
        UiCallbackSessionManager sessions = new UiCallbackSessionManager();
        new TemplateMenuActionRegistrar(sessions, new ChatInputWorkflow((context, result, throwable) -> {
        })).registerInto(registry);
        new MemberTemplateActionRegistrar().registerInto(registry);
        new MemberFlagActionRegistrar(sessions).registerInto(registry);
        new PrivilegeFlagActionRegistrar(sessions, new ChatInputWorkflow((context, result, throwable) -> {
        })).registerInto(registry);
        new MembershipMenuActionRegistrar(sessions, new ChatInputWorkflow((context, result, throwable) -> {
        })).registerInto(registry);
        new UtilityMenuActionRegistrar(sessions).registerInto(registry);
        new MainManagementActionRegistrar(sessions).registerInto(registry);
        new DominionTreeActionRegistrar(sessions).registerInto(registry);
        new EnvironmentFlagActionRegistrar(sessions).registerInto(registry);
    }

    @Test
    void acceptsRegisteredMigratedOperations() {
        assertDoesNotThrow(() -> registry.validate(action("apply-member-template")));
        assertDoesNotThrow(() -> registry.validate(action("toggle-env-flag")));
        assertDoesNotThrow(() -> registry.validate(action("back-member-flags")));
        assertDoesNotThrow(() -> registry.validate(action("back-dominion-manage")));
        assertDoesNotThrow(() -> registry.validate(action("toggle-member-flag")));
        assertDoesNotThrow(() -> registry.validate(action("open-member-template-list")));
        assertDoesNotThrow(() -> registry.validate(action("back-member-list")));
        assertDoesNotThrow(() -> registry.validate(action("toggle-template-flag")));
        assertDoesNotThrow(() -> registry.validate(action("rename-template-input")));
        assertDoesNotThrow(() -> registry.validate(action("back-template-list")));
        assertDoesNotThrow(() -> registry.validate(action("toggle-guest-flag")));
        assertDoesNotThrow(() -> registry.validate(action("toggle-group-flag")));
        assertDoesNotThrow(() -> registry.validate(action("rename-group-input")));
        assertDoesNotThrow(() -> registry.validate(action("open-member-flags")));
        assertDoesNotThrow(() -> registry.validate(action("add-member")));
        assertDoesNotThrow(() -> registry.validate(action("open-group")));
        assertDoesNotThrow(() -> registry.validate(action("add-group-member")));
        assertDoesNotThrow(() -> registry.validate(action("open-copy-environment")));
        assertDoesNotThrow(() -> registry.validate(action("copy-from-dominion")));
        assertDoesNotThrow(() -> registry.validate(action("toggle-title")));
        assertDoesNotThrow(() -> registry.validate(
                new ActionSpec("dominion", "resize-input EXPAND NORTH", "test.actions[0]")));
        assertDoesNotThrow(() -> registry.validate(action("start-create-dominion-input")));
        assertDoesNotThrow(() -> registry.validate(action("open-member-list")));
        assertDoesNotThrow(() -> registry.validate(action("switch-to-tui")));
        assertDoesNotThrow(() -> registry.validate(action("open-ui-preferences")));
        assertDoesNotThrow(() -> registry.validate(action("open-language-preferences")));
        assertDoesNotThrow(() -> registry.validate(action("set-language")));
        assertDoesNotThrow(() -> registry.validate(action("manage-dominion")));
        assertDoesNotThrow(() -> registry.validate(action("teleport-dominion")));
        assertDoesNotThrow(() -> registry.validate(action("migrate-residence")));
    }

    @Test
    void rejectsYamlArgumentsForTrustedOperations() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "toggle-env-flag value=true", "test.actions[0]")));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "apply-member-template template=admin", "test.actions[0]")));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "toggle-member-flag value=true", "test.actions[0]")));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "rename-template-input name=other", "test.actions[0]")));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(
                new ActionSpec("dominion", "toggle-group-flag value=true", "test.actions[0]")));
    }

    private ActionSpec action(String operation) {
        return new ActionSpec("dominion", operation, "test.actions[0]");
    }
}
