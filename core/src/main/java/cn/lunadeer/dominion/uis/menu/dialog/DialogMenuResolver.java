package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.data.MenuContext;
import cn.lunadeer.dominion.uis.menu.data.MenuDataProvider;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.MenuEntry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Resolves provider-backed Dialog entries before a platform adapter renders them.
 */
public final class DialogMenuResolver {

    private final MenuDataRegistry dataRegistry;

    /**
     * Creates a resolver that shares the TUI and CUI provider registry.
     */
    public DialogMenuResolver(MenuDataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
    }

    /**
     * Expands dynamic entry templates and normalizes pagination variables.
     */
    public CompletionStage<DialogMenuDefinition> resolve(Player player,
                                                         DialogMenuDefinition menu,
                                                         MenuRoute route) {
        DialogDynamicDefinition dynamic = menu.dynamic();
        if (dynamic == null) {
            return CompletableFuture.completedFuture(menu);
        }
        String providerId = menu.shared().dataSources().get(dynamic.source());
        MenuDataProvider provider = providerId == null ? null : dataRegistry.resolve(providerId);
        if (provider == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Dialog data provider is no longer registered: " + providerId));
        }
        CompletionStage<PageSlice> pageStage = provider.load(
                new MenuContext(player, route), route.page(), dynamic.pageSize());
        if (pageStage == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Dialog data provider returned no future: " + providerId));
        }
        return pageStage.thenApply(page -> expand(menu, route, page));
    }

    private DialogMenuDefinition expand(DialogMenuDefinition menu, MenuRoute route, PageSlice page) {
        DialogDynamicDefinition dynamic = menu.dynamic();
        if (page.entries().size() > dynamic.pageSize()) {
            throw new IllegalStateException("Dialog data provider returned more entries than the configured page size");
        }
        List<DialogBodyDefinition> body = new ArrayList<>(menu.body());
        List<String> rows = new ArrayList<>();
        List<DialogButtonDefinition> inlineButtons = new ArrayList<>();
        int entryIndex = 0;
        for (MenuEntry entry : page.entries()) {
            Map<String, String> rowVariables = new LinkedHashMap<>();
            rowVariables.put("entry.id", entry.id());
            entry.displayVariables().forEach((key, value) -> rowVariables.put("entry." + key, value));
            String row = resolve(dynamic.layout(), rowVariables);
            for (DialogButtonDefinition template : dynamic.buttons()) {
                String placeholder = "{" + template.id() + "}";
                if (!entry.availableActionGroups().contains(template.actionId())) {
                    row = row.replace(placeholder, "");
                    continue;
                }
                String contextualId = "entry-" + entryIndex + "-" + template.id();
                inlineButtons.add(template.withContext(
                        contextualId, rowVariables, entry.trustedArguments()));
                row = row.replace(placeholder, "{" + contextualId + "}");
            }
            rows.add(row.stripTrailing());
            entryIndex++;
        }
        if (!rows.isEmpty()) {
            body.add(new DialogBodyDefinition(
                    "entries", String.join("\n", rows), dynamic.width(), inlineButtons));
        }
        List<DialogButtonDefinition> buttons = new ArrayList<>();
        for (DialogButtonDefinition button : menu.bottom().buttons()) {
            if (pageActionAvailable(button.actions(), page)) {
                buttons.add(button);
            }
        }
        if (page.totalItems() == 0 && !dynamic.emptyText().isBlank()) {
            body.add(new DialogBodyDefinition("empty", dynamic.emptyText(), 320));
        }
        Map<String, String> variables = new LinkedHashMap<>(menu.variables());
        variables.put("page.current", Integer.toString(page.currentPage()));
        variables.put("page.total", Integer.toString(page.totalPages()));
        variables.put("page.items", Integer.toString(page.totalItems()));
        MenuRoute normalizedRoute = route.withPage(page.currentPage());
        DialogBottomDefinition bottom = new DialogBottomDefinition(
                menu.bottom().type(), buttons, menu.bottom().exit(), menu.bottom().columns());
        return new DialogMenuDefinition(menu.menuId(), menu.locale(), menu.title(), menu.canEscape(), menu.pause(),
                menu.afterAction(), body, menu.inputs(), bottom, null, variables, menu.shared(), normalizedRoute);
    }

    private String resolve(String template, Map<String, String> variables) {
        String resolved = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    private boolean pageActionAvailable(List<ActionSpec> actions, PageSlice page) {
        for (ActionSpec action : actions) {
            if (!action.type().equals("page")) {
                continue;
            }
            if (action.argument().equalsIgnoreCase("previous")) {
                return page.hasPrevious();
            }
            if (action.argument().equalsIgnoreCase("next")) {
                return page.hasNext();
            }
        }
        return true;
    }
}
