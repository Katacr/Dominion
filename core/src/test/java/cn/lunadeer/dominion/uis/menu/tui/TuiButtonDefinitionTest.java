package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TuiButtonDefinitionTest {

    private TuiMenuRepository repository;
    private SharedMenuDefinition shared;

    @BeforeEach
    void setUp() {
        ActionRegistry registry = new ActionRegistry();
        registry.register("tell", (context, action) ->
                CompletableFuture.completedFuture(ActionResult.continueExecution()));
        MenuDataRegistry dataRegistry = new MenuDataRegistry();
        dataRegistry.register("test.entries", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        repository = new TuiMenuRepository(null, new ActionParser(), registry, dataRegistry);
        shared = new SharedMenuDefinition("test", Set.of(), Set.of(), Map.of("entries", "test.entries"), Map.of(
                "shared-message", List.of(new ActionSpec("tell", "shared", "test.actionGroups.shared-message"))
        ));
    }

    @Test
    void parsesInlineActionsIntoTheButtonDefinition() {
        TuiButtonDefinition button = loadButton("""
                type: BUTTON
                text: '&a[Inline]'
                actions:
                  - 'tell: local'
                """);

        assertEquals("inline", button.actionId());
        assertEquals("local", button.actions().get(0).argument());
    }

    @Test
    void resolvesSharedOperationIntoTheButtonDefinition() {
        TuiButtonDefinition button = loadButton("""
                type: BUTTON
                text: '&a[Shared]'
                action-group: shared-message
                """);

        assertEquals("shared-message", button.actionId());
        assertEquals("shared", button.actions().get(0).argument());
    }

    @Test
    void rejectsActionsAndActionGroupTogether() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loadButton("""
                type: BUTTON
                text: '&c[Invalid]'
                action-group: shared-message
                actions:
                  - 'tell: local'
                """));

        assertEquals("BUTTON requires exactly one of actions or action-group at test.yml.Buttons.inline",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownInlineActionType() {
        assertThrows(IllegalArgumentException.class, () -> loadButton("""
                type: BUTTON
                text: '&c[Invalid]'
                actions:
                  - 'server-command: stop'
                """));
    }

    @Test
    void parsesFlatListDefinition() {
        TuiButtonDefinition button = loadButton("""
                type: LIST
                source: entries
                rows: 10
                empty: '&7Empty'
                Layout:
                  - '{open} {entry.name}'
                Buttons:
                  open:
                    type: BUTTON
                    text: '&a[Open]'
                    action-group: shared-message
                """);

        assertEquals(TuiButtonType.LIST, button.type());
        assertEquals("test.entries", button.list().providerId());
        assertEquals(10, button.list().rows());
    }

    @Test
    void rejectsOversizedButtonText() {
        assertThrows(IllegalArgumentException.class, () -> loadButton("""
                type: BUTTON
                text: '%s'
                action-group: shared-message
                """.formatted("x".repeat(4097))));
    }

    private TuiButtonDefinition loadButton(String yamlText) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(yamlText);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid test YAML", exception);
        }
        ConfigurationSection section = yaml.getConfigurationSection("");
        return repository.loadButton("inline", section == null ? yaml : section, shared, "test.yml");
    }
}
