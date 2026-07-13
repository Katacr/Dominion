package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.data.MenuContext;
import cn.lunadeer.dominion.uis.menu.data.MenuDataProvider;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.MenuEntry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.LegacyToMiniMessage;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import cn.lunadeer.dominion.utils.stui.TextUserInterfaceManager;
import cn.lunadeer.dominion.utils.stui.components.buttons.Button;
import cn.lunadeer.dominion.utils.stui.components.buttons.CommandButton;
import cn.lunadeer.dominion.utils.stui.components.buttons.CopyButton;
import cn.lunadeer.dominion.utils.stui.components.buttons.UrlButton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/**
 * Renders localized `Layout + Buttons` definitions into interactive chat lines.
 */
public final class TuiRenderer {

    private final UiCallbackSessionManager sessions;
    private final MenuDataRegistry dataRegistry;

    /**
     * Creates a renderer backed by fixed callback sessions and whitelisted data providers.
     */
    public TuiRenderer(UiCallbackSessionManager sessions, MenuDataRegistry dataRegistry) {
        this.sessions = sessions;
        this.dataRegistry = dataRegistry;
    }

    /**
     * Loads dynamic data before rendering the complete menu on the player scheduler.
     */
    public CompletionStage<Void> show(Player player,
                                      TuiMenuDefinition menu,
                                      MenuRoute route,
                                      long menuRevision,
                                      BooleanSupplier revisionActive) {
        // Opening a new route immediately retires callbacks from the previous visible menu.
        sessions.beginSession(player);
        TuiButtonDefinition listButton = menu.buttons().values().stream()
                .filter(button -> button.type() == TuiButtonType.LIST)
                .findFirst()
                .orElse(null);
        CompletionStage<PageSlice> pageStage;
        if (listButton == null) {
            pageStage = CompletableFuture.completedFuture(PageSlice.paginate(java.util.List.of(), route.page(), 1));
        } else {
            TuiListDefinition list = listButton.list();
            MenuDataProvider provider = dataRegistry.resolve(list.providerId());
            if (provider == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Menu data provider is no longer registered: " + list.providerId()));
            }
            pageStage = provider.load(new MenuContext(player, route), route.page(), list.rows());
            if (pageStage == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Menu data provider returned no future: " + list.providerId()));
            }
        }

        CompletableFuture<Void> completion = new CompletableFuture<>();
        pageStage.whenComplete((page, throwable) -> {
            if (throwable != null) {
                completion.completeExceptionally(throwable);
                return;
            }
            if (page == null) {
                completion.completeExceptionally(new IllegalStateException("Menu data provider returned no page"));
                return;
            }
            if (listButton != null && page.entries().size() > listButton.list().rows()) {
                completion.completeExceptionally(new IllegalStateException(
                        "Menu data provider returned more entries than the requested page size"));
                return;
            }
            if (!revisionActive.getAsBoolean()) {
                completion.complete(null);
                return;
            }
            if (!player.isOnline()) {
                completion.complete(null);
                return;
            }
            try {
                Scheduler.runEntityTask(() -> {
                    try {
                        if (revisionActive.getAsBoolean()) {
                            render(player, menu, route.withPage(page.currentPage()), menuRevision, listButton, page);
                        }
                        completion.complete(null);
                    } catch (Exception exception) {
                        completion.completeExceptionally(exception);
                    }
                }, player);
            } catch (Exception exception) {
                completion.completeExceptionally(exception);
            }
        });
        return completion;
    }

    private void render(Player player,
                        TuiMenuDefinition menu,
                        MenuRoute route,
                        long menuRevision,
                        TuiButtonDefinition listButton,
                        PageSlice page) {
        Map<String, String> baseVariables = createBaseVariables(player, menu, route, page);
        ActionContext baseContext = createContext(player, menu, route, menuRevision, baseVariables, Map.of());

        for (String line : menu.layout()) {
            if (listButton != null && line.trim().equals("{" + listButton.id() + "}")) {
                renderList(player, menu, route, menuRevision, listButton.list(), page, baseVariables);
            } else {
                send(player, renderLine(player, baseContext, menu.buttons(), line, page, null));
            }
        }
    }

    private void renderList(Player player,
                            TuiMenuDefinition menu,
                            MenuRoute route,
                            long menuRevision,
                            TuiListDefinition list,
                            PageSlice page,
                            Map<String, String> baseVariables) {
        if (page.entries().isEmpty()) {
            send(player, LegacyToMiniMessage.parseTemplate(list.emptyText(), baseVariables));
            return;
        }
        for (MenuEntry entry : page.entries()) {
            Map<String, String> variables = new LinkedHashMap<>(baseVariables);
            entry.displayVariables().forEach((key, value) -> variables.put("entry." + key, value));
            variables.put("entry.id", entry.id());
            ActionContext context = createContext(player, menu, route, menuRevision, variables,
                    entry.trustedArguments());
            for (String line : list.layout()) {
                send(player, renderLine(player, context, list.buttons(), line, page, entry.availableActionGroups()));
            }
        }
    }

    private ActionContext createContext(Player player,
                                        TuiMenuDefinition menu,
                                        MenuRoute route,
                                        long menuRevision,
                                        Map<String, String> variables,
                                        Map<String, String> trustedArguments) {
        return new ActionContext(
                player,
                route,
                menuRevision,
                variables,
                trustedArguments,
                menu.shared().actionGroups(),
                actionGroupId -> sessions.register(player, route, menuRevision, actionGroupId,
                        menu.shared().actionGroups(), variables, trustedArguments)
        );
    }

    private Map<String, String> createBaseVariables(Player player,
                                                     TuiMenuDefinition menu,
                                                     MenuRoute route,
                                                     PageSlice page) {
        Map<String, String> variables = new LinkedHashMap<>(menu.variables());
        variables.put("player.name", player.getName());
        variables.put("menu.id", menu.menuId());
        variables.put("page.current", Integer.toString(page.currentPage()));
        variables.put("page.total", Integer.toString(page.totalPages()));
        variables.put("page.items", Integer.toString(page.totalItems()));
        route.arguments().forEach((key, value) -> variables.put("route." + key, value));
        return Map.copyOf(variables);
    }

    private Component renderLine(Player player,
                                 ActionContext context,
                                 Map<String, TuiButtonDefinition> buttons,
                                 String line,
                                 PageSlice page,
                                 Set<String> availableActionGroups) {
        TextComponent.Builder output = Component.text();
        StringBuilder plainText = new StringBuilder();
        int cursor = 0;
        while (cursor < line.length()) {
            if (line.startsWith("{{", cursor)) {
                plainText.append('{');
                cursor += 2;
                continue;
            }
            if (line.startsWith("}}", cursor)) {
                plainText.append('}');
                cursor += 2;
                continue;
            }
            if (line.charAt(cursor) != '{') {
                plainText.append(line.charAt(cursor++));
                continue;
            }

            int closing = line.indexOf('}', cursor + 1);
            if (closing < 0) {
                plainText.append(line.substring(cursor));
                break;
            }
            String placeholder = line.substring(cursor + 1, closing);
            TuiButtonDefinition button = buttons.get(placeholder);
            if (button == null) {
                plainText.append(line, cursor, closing + 1);
            } else {
                appendPlain(output, plainText.toString(), context.variables());
                plainText.setLength(0);
                output.append(renderButton(player, context, button, page, availableActionGroups));
            }
            cursor = closing + 1;
        }
        appendPlain(output, plainText.toString(), context.variables());
        return output.build();
    }

    private Component renderButton(Player player,
                                   ActionContext context,
                                   TuiButtonDefinition definition,
                                   PageSlice page,
                                   Set<String> availableActionGroups) {
        boolean allowed = definition.permission().isBlank() || player.hasPermission(definition.permission());
        if (availableActionGroups != null && definition.type() == TuiButtonType.BUTTON) {
            allowed = allowed && availableActionGroups.contains(definition.actionId());
        }
        allowed = allowed && pageActionAvailable(definition.actions(), page);
        if (!allowed && definition.hiddenWhenDisabled()) {
            return Component.empty();
        }

        String text = allowed ? definition.text() : definition.disabledText();
        Component component = LegacyToMiniMessage.parseTemplate(text, context.variables());
        if (!definition.hover().isBlank()) {
            component = component.hoverEvent(LegacyToMiniMessage.parseTemplate(
                    definition.hover(), context.variables()));
        }
        if (!allowed) {
            return component;
        }

        Button clickSource = switch (definition.type()) {
            case BUTTON -> new CommandButton("", sessions.registerActions(
                    player,
                    context.route(),
                    context.menuRevision(),
                    definition.actionId(),
                    definition.actions(),
                    context.actionGroups(),
                    context.variables(),
                    context.trustedArguments()
            ));
            case URL -> new UrlButton("", context.resolve(definition.url()));
            case COPY -> new CopyButton("", context.resolve(definition.copy()));
            case LIST -> throw new IllegalStateException("LIST cannot be rendered as an entry button");
        };
        ClickEvent clickEvent = clickSource.build().style().clickEvent();
        if (clickEvent == null) {
            throw new IllegalStateException("Unable to create click event for button " + definition.id());
        }
        return component.clickEvent(clickEvent);
    }

    private boolean pageActionAvailable(java.util.List<ActionSpec> actions, PageSlice page) {
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

    private void send(Player player, Component component) {
        TextUserInterfaceManager.getInstance().sendMessage(player, component);
    }

    private void appendPlain(TextComponent.Builder output, String text, Map<String, String> variables) {
        if (!text.isEmpty()) {
            output.append(LegacyToMiniMessage.parseTemplate(text, variables));
        }
    }
}
