package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.MenuEntry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogMenuLoaderTest {

    private DialogMenuLoader loader;

    /**
     * Creates one isolated action whitelist for each Dialog loader test.
     */
    @BeforeEach
    void setUp() {
        ActionRegistry actions = new ActionRegistry();
        actions.register("tell", (context, action) -> CompletableFuture.completedFuture(null));
        loader = new DialogMenuLoader(new ActionParser(), actions);
    }

    /**
     * Verifies the four common KaMenu input controls and confirmation footer.
     */
    @Test
    void loadsCommonInputsAndConfirmation() {
        DialogMenuDefinition menu = loader.load("form", "en_US", yaml("""
                schema-version: 1
                Title: Form
                Inputs:
                  name:
                    type: input
                    text: Name
                    min_length: 1
                    max_length: 16
                  amount:
                    type: slider
                    text: Amount
                    min: 1
                    max: 10
                    step: 1
                    default: 2
                  mode:
                    type: dropdown
                    text: Mode
                    options:
                      - 'safe => Safe'
                      - 'fast => Fast'
                    default_id: safe
                  notify:
                    type: checkbox
                    text: Notify
                    default: true
                    on_true: enabled
                    on_false: disabled
                Bottom:
                  type: confirmation
                  confirm:
                    text: Confirm
                    actions: ['tell: ok']
                  deny:
                    text: Cancel
                    actions: ['tell: cancel']
                """), shared("form"));

        assertEquals(List.of(DialogInputType.INPUT, DialogInputType.SLIDER,
                        DialogInputType.DROPDOWN, DialogInputType.CHECKBOX),
                menu.inputs().stream().map(DialogInputDefinition::type).toList());
        assertEquals(DialogBottomDefinition.Type.CONFIRMATION, menu.bottom().type());
        assertEquals("enabled", menu.inputSchema().normalize(Map.of(
                "name", "Dominion", "amount", "2.0", "mode", "safe", "notify", "enabled"
        )).get("notify"));
    }

    /**
     * Keeps Dialogs visible after callbacks unless a menu explicitly requests another lifecycle.
     */
    @Test
    void defaultsAfterActionToNone() {
        DialogMenuDefinition menu = loader.load("lifecycle", "en_US", yaml("""
                schema-version: 1
                Title: Lifecycle
                Bottom:
                  type: notice
                  confirm:
                    text: Refresh
                    actions: ['tell: refresh']
                """), shared("lifecycle"));

        assertEquals(DialogAfterAction.NONE, menu.afterAction());
    }

    /**
     * Rejects the response-waiting lifecycle because Dominion callbacks replace Dialogs themselves.
     */
    @Test
    void rejectsWaitForResponseAfterAction() {
        assertThrows(IllegalArgumentException.class, () -> loader.load("lifecycle", "en_US", yaml("""
                schema-version: 1
                Title: Lifecycle
                Settings:
                  after_action: WAIT_FOR_RESPONSE
                Bottom:
                  type: notice
                  confirm:
                    text: Refresh
                    actions: ['tell: refresh']
                """), shared("lifecycle")));
    }

    /**
     * Loads the common Item Body fields consumed by both platform adapters.
     */
    @Test
    void loadsItemBody() {
        DialogMenuDefinition menu = loader.load("item", "en_US", yaml("""
                schema-version: 1
                Title: Item
                Body:
                  icon:
                    type: item
                    material: STONE
                    amount: 2
                    description: Stone body
                    width: 32
                    height: 24
                    show_overlays: false
                    show_tooltip: true
                Bottom:
                  type: notice
                  confirm:
                    text: Close
                    actions: ['tell: close']
                """), shared("item"));

        DialogBodyDefinition body = menu.body().get(0);
        assertEquals(DialogBodyDefinition.Type.ITEM, body.type());
        assertEquals("minecraft:stone", body.material());
        assertEquals(2, body.amount());
        assertEquals("Stone body", body.text());
        assertEquals(32, body.width());
        assertEquals(24, body.height());
        assertEquals(false, body.showDecorations());
        assertEquals(true, body.showTooltip());
    }

    /**
     * Rejects a slider default that Spigot cannot render inside its bounds.
     */
    @Test
    void rejectsSliderDefaultOutsideRange() {
        assertThrows(IllegalArgumentException.class, () -> loader.load("slider", "en_US", yaml("""
                schema-version: 1
                Title: Slider
                Inputs:
                  amount:
                    type: slider
                    text: Amount
                    min: 1
                    max: 5
                    default: 8
                Bottom:
                  type: notice
                  confirm:
                    text: Close
                    actions: ['tell: close']
                """), shared("slider")));
    }

    /**
     * Verifies provider-backed buttons reuse shared action and data identifiers.
     */
    @Test
    void loadsDynamicButtonTemplates() {
        DialogMenuDefinition menu = loader.load("list", "en_US", yaml("""
                schema-version: 1
                Title: List
                Dynamic:
                  source: entries
                  page-size: 8
                  empty: Empty
                  layout: '{entry.name} {select}'
                  buttons:
                    select:
                      text: 'Select {entry.name}'
                      action-group: select
                Bottom:
                  type: multi
                  buttons:
                    back:
                      text: Back
                      actions: ['tell: back']
                """), new SharedMenuDefinition("list", Set.of(), Set.of(),
                Map.of("entries", "test.entries"), Map.of("select", List.of(
                new cn.lunadeer.dominion.uis.menu.action.ActionSpec("tell", "selected", "test")))));

        assertEquals("entries", menu.dynamic().source());
        assertEquals(8, menu.dynamic().pageSize());
        assertEquals("select", menu.dynamic().buttons().get(0).actionId());
    }

    /**
     * Verifies reusable prompts and KaMenu-style multiline tooltips.
     */
    @Test
    void loadsConfirmationAndInputCapturePrompts() {
        DialogMenuDefinition menu = loader.load("prompts", "en_US", yaml("""
                schema-version: 1
                Title: Prompts
                Bottom:
                  type: multi
                  buttons:
                    delete:
                      text: Delete
                      tooltip:
                        - First line
                        - Second line
                      actions: ['tell: delete']
                      confirmation:
                        title: Confirm
                        body: Delete this entry?
                        confirm:
                          text: Delete
                          tooltip: Permanent
                        deny:
                          text: Cancel
                    rename:
                      text: Rename
                      actions: ['tell: rename']
                      input-capture:
                        title: Rename
                        input:
                          key: entry_name
                          type: input
                          text: New name
                          min_length: 1
                          max_length: 32
                        confirm:
                          text: Save
                        deny:
                          text: Cancel
                """), shared("prompts"));

        DialogButtonDefinition delete = menu.bottom().buttons().get(0);
        DialogButtonDefinition rename = menu.bottom().buttons().get(1);
        assertEquals("First line\nSecond line", delete.tooltip());
        assertEquals(DialogPromptDefinition.Type.CONFIRMATION, delete.prompt().type());
        assertNull(delete.prompt().input());
        assertEquals(DialogPromptDefinition.Type.INPUT_CAPTURE, rename.prompt().type());
        assertEquals("entry_name", rename.prompt().input().key());
    }

    /**
     * Rejects logical dotted keys before either native platform attempts to build the Dialog.
     */
    @Test
    void rejectsInvalidNativeInputName() {
        assertThrows(IllegalArgumentException.class, () -> loader.load("invalid_input", "en_US", yaml("""
                schema-version: 1
                Title: Invalid input
                Inputs:
                  invalid.name:
                    type: input
                    text: Name
                Bottom:
                  type: notice
                  confirm:
                    text: Close
                    actions: ['tell: close']
                """), shared("invalid_input")));
    }

    /**
     * Ensures provider entries render as Body callbacks instead of footer buttons.
     */
    @Test
    void resolvesDynamicEntriesIntoClickableBodyRows() {
        SharedMenuDefinition shared = new SharedMenuDefinition("list", Set.of(), Set.of(),
                Map.of("entries", "test.entries"), Map.of("toggle", List.of(
                new cn.lunadeer.dominion.uis.menu.action.ActionSpec("tell", "toggle", "test"))));
        DialogMenuDefinition menu = loader.load("list", "en_US", yaml("""
                schema-version: 1
                Title: List
                Dynamic:
                  source: entries
                  page-size: 8
                  layout: '&f{entry.name}: &7{entry.description} {toggle}'
                  buttons:
                    toggle:
                      text: '[Toggle]'
                      tooltip: Toggle entry
                      action-group: toggle
                Bottom:
                  type: multi
                  buttons:
                    close:
                      text: Close
                      actions: ['tell: close']
                """), shared);
        MenuDataRegistry data = new MenuDataRegistry();
        data.register("test.entries", (context, page, pageSize) -> CompletableFuture.completedFuture(
                PageSlice.paginate(List.of(
                        new MenuEntry("one",
                                Map.of("name", "TNT", "description", "Allow explosions"),
                                Map.of("flag.name", "tnt_explode"), Set.of("toggle")),
                        new MenuEntry("two",
                                Map.of("name", "Creeper", "description", "Allow creepers"),
                                Map.of("flag.name", "creeper_explode"), Set.of("toggle"))
                ), page, pageSize)));

        DialogMenuDefinition resolved = new DialogMenuResolver(data)
                .resolve(player(), menu, MenuRoute.of("list")).toCompletableFuture().join();

        assertEquals(1, resolved.bottom().buttons().size());
        assertEquals(1, resolved.body().size());
        assertEquals("&fTNT: &7Allow explosions {entry-0-toggle}\n"
                + "&fCreeper: &7Allow creepers {entry-1-toggle}", resolved.body().get(0).text());
        assertEquals(2, resolved.body().get(0).inlineButtons().size());
        assertEquals("toggle", resolved.body().get(0).inlineButtons().get(0).actionId());
        assertEquals("tnt_explode", resolved.body().get(0).inlineButtons().get(0)
                .trustedArguments().get("flag.name"));
    }

    private SharedMenuDefinition shared(String menuId) {
        return new SharedMenuDefinition(menuId, Set.of(), Set.of(), Map.of(), Map.of());
    }

    private YamlConfiguration yaml(String content) {
        return YamlConfiguration.loadConfiguration(new StringReader(content));
    }

    private Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> null);
    }
}
