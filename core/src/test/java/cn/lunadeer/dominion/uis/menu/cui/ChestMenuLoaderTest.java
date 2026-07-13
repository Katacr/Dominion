package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChestMenuLoaderTest {

    private ActionRegistry actions;
    private MenuDataRegistry data;

    /**
     * Creates isolated action and provider whitelists for each loader test.
     */
    @BeforeEach
    void setUp() {
        actions = new ActionRegistry();
        actions.register("tell", (context, action) -> CompletableFuture.completedFuture(null));
        data = new MenuDataRegistry();
    }

    /**
     * Verifies both bundled MainMenu locales against the same shared action-group contract.
     */
    @Test
    void bundledMainMenuDefinitionsLoad() throws Exception {
        ChestMenuLoader loader = new ChestMenuLoader(new ActionParser(), actions, data);
        SharedMenuDefinition shared = mainMenuShared();

        ChestMenuDefinition english = loader.load("main_menu", "en_US",
                loadYaml("uis/en_US/chest_menu/main_menu.yml"), shared);
        ChestMenuDefinition chinese = loader.load("main_menu", "zh_CN",
                loadYaml("uis/zh_CN/chest_menu/main_menu.yml"), shared);

        assertEquals(6, english.layout().size());
        assertEquals("&0Dominion Menu", english.title());
        assertEquals("&0领地主菜单", chinese.title());
        assertNull(english.dynamicSymbol());
        assertEquals(0, english.dynamicPageSize());
        assertEquals("create", english.buttons().get('c').clicks().get(ChestClickType.LEFT).actionId());
        assertEquals("dominion.admin", english.buttons().get('a').permission());
    }

    /**
     * Verifies that repeated source symbols define the provider page capacity.
     */
    @Test
    void dynamicSourceUsesLayoutSlotCount() {
        data.register("test.entries", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        ChestMenuDefinition menu = loader().load("dynamic", "en_US", yaml("""
                schema-version: 1
                Title: Dynamic
                Layout: ['DDDDDDDDD']
                Buttons:
                  'D':
                    source: entries
                    Display: {material: STONE, name: Entry}
                    Clicks:
                      left: {action-group: select}
                """), shared("dynamic", Map.of("entries", "test.entries"), Set.of("select")));

        assertEquals('D', menu.dynamicSymbol());
        assertEquals(9, menu.dynamicPageSize());
    }

    /**
     * Rejects rows that cannot map exactly to a Bukkit chest width.
     */
    @Test
    void rejectsInvalidLayoutWidth() {
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "invalid", "en_US", yaml("""
                        schema-version: 1
                        Title: Invalid
                        Layout: ['########']
                        Buttons:
                          '#':
                            Display: {material: STONE, name: Border}
                        """), shared("invalid", Map.of(), Set.of())));
    }

    /**
     * Rejects layout symbols without an explicit display definition.
     */
    @Test
    void rejectsUnknownLayoutSymbol() {
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "invalid", "en_US", yaml("""
                        schema-version: 1
                        Title: Invalid
                        Layout: ['########X']
                        Buttons:
                          '#':
                            Display: {material: STONE, name: Border}
                        """), shared("invalid", Map.of(), Set.of())));
    }

    /**
     * Rejects clicks that ambiguously combine a shared action group with inline actions.
     */
    @Test
    void rejectsAmbiguousClickActions() {
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "invalid", "en_US", yaml("""
                        schema-version: 1
                        Title: Invalid
                        Layout: ['#########']
                        Buttons:
                          '#':
                            Display: {material: STONE, name: Border}
                            Clicks:
                              left:
                                action-group: select
                                actions: ['tell: duplicate']
                        """), shared("invalid", Map.of(), Set.of("select"))));
    }

    /**
     * Rejects multiple independently paged sources in the first chest schema version.
     */
    @Test
    void rejectsMultipleDynamicSymbols() {
        data.register("test.entries", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "invalid", "en_US", yaml("""
                        schema-version: 1
                        Title: Invalid
                        Layout: ['AAAABBBBB']
                        Buttons:
                          'A':
                            source: entries
                            Display: {material: STONE, name: First}
                          'B':
                            source: entries
                            Display: {material: PAPER, name: Second}
                        """), shared("invalid", Map.of("entries", "test.entries"), Set.of())));
    }

    /**
     * Accepts provider-owned entry materials only on dynamic source buttons.
     */
    @Test
    void restrictsEntryMaterialPlaceholderToDynamicButtons() {
        data.register("test.entries", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        ChestMenuDefinition dynamic = loader().load("dynamic", "en_US", yaml("""
                schema-version: 1
                Title: Dynamic
                Layout: ['DDDDDDDDD']
                Buttons:
                  'D':
                    source: entries
                    Display: {material: '{entry.material}', name: Entry}
                """), shared("dynamic", Map.of("entries", "test.entries"), Set.of()));

        assertEquals("{entry.material}", dynamic.buttons().get('D').display().material());
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "static", "en_US", yaml("""
                        schema-version: 1
                        Title: Static
                        Layout: ['#########']
                        Buttons:
                          '#':
                            Display: {material: '{entry.material}', name: Invalid}
                        """), shared("static", Map.of(), Set.of())));
    }

    /**
     * Validates bounded capabilities and dynamic-only empty displays.
     */
    @Test
    void validatesCapabilitiesAndEmptyDisplays() {
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "invalid", "en_US", yaml("""
                        schema-version: 1
                        Title: Invalid
                        Layout: ['#########']
                        Buttons:
                          '#':
                            capability: arbitrary-expression
                            Display: {material: STONE, name: Invalid}
                        """), shared("invalid", Map.of(), Set.of())));
        assertThrows(IllegalArgumentException.class, () -> loader().load(
                "invalid", "en_US", yaml("""
                        schema-version: 1
                        Title: Invalid
                        Layout: ['#########']
                        Buttons:
                          '#':
                            Display: {material: STONE, name: Invalid}
                            Empty-Display: {material: BARRIER, name: Empty}
                        """), shared("invalid", Map.of(), Set.of())));
    }

    /**
     * Creates a loader using the current test whitelists.
     */
    private ChestMenuLoader loader() {
        return new ChestMenuLoader(new ActionParser(), actions, data);
    }

    /**
     * Creates the shared contract used by bundled MainMenu resources.
     */
    private SharedMenuDefinition mainMenuShared() {
        return shared("main_menu", Map.of(), Set.of(
                "create", "dominions", "titles", "templates", "migrate",
                "all-dominions", "reload-cache", "reload-config", "switch-tui"));
    }

    /**
     * Builds a minimal immutable shared definition for loader-only tests.
     */
    private SharedMenuDefinition shared(String menuId,
                                        Map<String, String> sources,
                                        Set<String> actionGroups) {
        Map<String, List<ActionSpec>> resolved = new LinkedHashMap<>();
        actionGroups.forEach(operation -> resolved.put(operation,
                List.of(new ActionSpec("tell", operation, "test.actionGroups." + operation))));
        return new SharedMenuDefinition(menuId, Set.of(), Set.of(), sources, resolved);
    }

    /**
     * Parses inline YAML fixtures without touching the file system.
     */
    private YamlConfiguration yaml(String value) {
        return YamlConfiguration.loadConfiguration(new StringReader(value));
    }

    /**
     * Loads one bundled YAML resource as UTF-8.
     */
    private YamlConfiguration loadYaml(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }
}
