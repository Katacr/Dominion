package cn.lunadeer.dominion.dialog.paper;

import cn.lunadeer.dominion.uis.menu.dialog.DialogButtonDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogInputDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogOptionDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogPlatformAdapter;
import cn.lunadeer.dominion.uis.menu.dialog.DialogPromptButtonDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogPromptDefinition;
import cn.lunadeer.dominion.uis.menu.input.InputSchema;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackDispatcher;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.LegacyToMiniMessage;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders Dominion's neutral Dialog model with the public Paper Dialog API.
 */
public final class PaperDialogPlatformAdapter implements DialogPlatformAdapter {

    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);

    private UiCallbackDispatcher callbacks;
    private UiCallbackSessionManager sessions;
    private final Map<UUID, Set<String>> dialogTokens = new ConcurrentHashMap<>();

    /**
     * Captures shared callback services; Paper custom actions use direct closures.
     */
    @Override
    public void initialize(JavaPlugin plugin, UiCallbackDispatcher callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Converts one validated neutral menu to Paper builders and opens it for the player.
     */
    @Override
    public void open(Player player,
                     DialogMenuDefinition menu,
                     MenuRoute route,
                     long revision,
                     UiCallbackSessionManager sessions) {
        this.sessions = sessions;
        invalidatePrevious(player, sessions);
        List<String> registeredTokens = new ArrayList<>();
        Map<String, String> variables = variables(player, menu, route);
        List<DialogBody> body = menu.body().stream()
                .map(component -> bodyComponent(player, component, menu, route, revision,
                        sessions, variables, registeredTokens))
                .toList();
        List<DialogInput> inputs = menu.inputs().stream()
                .map(input -> input(input, variables))
                .toList();
        DialogBase base = DialogBase.builder(text(menu.title(), variables))
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(menu.canEscape())
                .pause(menu.pause())
                .afterAction(DialogBase.DialogAfterAction.valueOf(menu.afterAction().name()))
                .build();
        InputSchema schema = menu.inputSchema();
        io.papermc.paper.registry.data.dialog.type.DialogType type = switch (menu.bottom().type()) {
            case NOTICE -> DialogType.notice(button(player, menu.bottom().buttons().get(0), menu, route,
                    revision, sessions, variables, schema, registeredTokens));
            case CONFIRMATION -> DialogType.confirmation(
                    button(player, menu.bottom().buttons().get(0), menu, route,
                            revision, sessions, variables, schema, registeredTokens),
                    button(player, menu.bottom().buttons().get(1), menu, route,
                            revision, sessions, variables, schema, registeredTokens));
            case MULTI -> multi(player, menu, route, revision, sessions, variables, schema, registeredTokens);
        };
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
        dialogTokens.put(player.getUniqueId(), Set.copyOf(registeredTokens));
    }

    /**
     * Closes the current Paper Dialog through the Adventure audience contract.
     */
    @Override
    public void close(Player player) {
        try {
            player.getClass().getMethod("closeDialog").invoke(player);
        } catch (NoSuchMethodException ignored) {
            // Paper 1.21.7 has no explicit close API; explicit close actions remain unavailable there.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to close Paper Dialog", exception);
        }
    }

    /**
     * Paper inline components use direct Adventure callbacks instead of commands.
     */
    @Override
    public boolean dispatchInline(Player player, String token) {
        return false;
    }

    /**
     * Invalidates adapter-owned callback tokens and releases service references.
     */
    @Override
    public void shutdown() {
        if (sessions != null) {
            dialogTokens.values().forEach(sessions::invalidateTokens);
        }
        dialogTokens.clear();
        sessions = null;
        callbacks = null;
    }

    /**
     * Converts one neutral body definition through Paper's official message or item builder.
     */
    private DialogBody bodyComponent(Player player,
                                     cn.lunadeer.dominion.uis.menu.dialog.DialogBodyDefinition definition,
                                     DialogMenuDefinition menu,
                                     MenuRoute route,
                                     long revision,
                                     UiCallbackSessionManager sessions,
                                     Map<String, String> variables,
                                     List<String> registeredTokens) {
        if (definition.type()
                == cn.lunadeer.dominion.uis.menu.dialog.DialogBodyDefinition.Type.ITEM) {
            Material material = Material.matchMaterial(definition.material());
            if (material == null || !material.isItem()) {
                throw new IllegalStateException("Dialog item material is no longer available: "
                        + definition.material());
            }
            io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody description =
                    definition.text().isBlank() ? null : DialogBody.plainMessage(text(definition.text(), variables));
            return DialogBody.item(new ItemStack(material, definition.amount()), description,
                    definition.showDecorations(), definition.showTooltip(), definition.width(), definition.height());
        }
        return DialogBody.plainMessage(body(player, definition, menu, route, revision,
                sessions, variables, registeredTokens), definition.width());
    }

    /**
     * Builds a permission-filtered Paper multi-action footer.
     */
    private io.papermc.paper.registry.data.dialog.type.DialogType multi(
            Player player,
            DialogMenuDefinition menu,
            MenuRoute route,
            long revision,
            UiCallbackSessionManager sessions,
            Map<String, String> variables,
            InputSchema schema,
            List<String> registeredTokens) {
        List<ActionButton> buttons = menu.bottom().buttons().stream()
                .map(definition -> button(player, definition, menu, route, revision, sessions,
                        variables, schema, registeredTokens))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (buttons.isEmpty()) {
            throw new IllegalStateException("Dialog multi footer has no buttons available to player");
        }
        ActionButton exit = button(player, menu.bottom().exit(), menu, route, revision, sessions,
                variables, schema, registeredTokens);
        var builder = DialogType.multiAction(buttons).columns(menu.bottom().columns());
        if (exit != null) {
            builder.exitAction(exit);
        }
        return builder.build();
    }

    /**
     * Creates a one-use Paper callback that restores the shared opaque token context.
     */
    private ActionButton button(Player player,
                                DialogButtonDefinition definition,
                                DialogMenuDefinition menu,
                                MenuRoute route,
                                long revision,
                                UiCallbackSessionManager sessions,
                                Map<String, String> variables,
                                InputSchema schema,
                                List<String> registeredTokens) {
        if (definition == null || !definition.permission().isBlank()
                && !player.hasPermission(definition.permission())) {
            return null;
        }
        Map<String, String> buttonVariables = new LinkedHashMap<>(variables);
        buttonVariables.putAll(definition.variables());
        InputSchema callbackSchema = definition.prompt() == null
                ? schema : definition.prompt().input() == null
                ? null : new InputSchema(List.of(definition.prompt().input().fieldSpec()));
        String token = sessions.registerActionsToken(player, route, revision, definition.actionId(),
                definition.actions(), menu.shared().actionGroups(), buttonVariables,
                definition.trustedArguments(), UiSurface.DIALOG, callbackSchema);
        registeredTokens.add(token);
        DialogAction action = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player callbackPlayer)
                    || !callbackPlayer.getUniqueId().equals(player.getUniqueId())
                    || !consumeCurrentToken(callbackPlayer, token)) {
                return;
            }
            handleClick(callbackPlayer, token, definition, menu, route, revision, sessions,
                    buttonVariables, response);
        }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build());
        ActionButton.Builder builder = ActionButton.builder(text(definition.text(), buttonVariables))
                .width(definition.width())
                .action(action);
        if (!definition.tooltip().isEmpty()) {
            builder.tooltip(text(definition.tooltip(), buttonVariables));
        }
        return builder.build();
    }

    /**
     * Builds a body message whose configured placeholders become clickable inline actions.
     */
    private Component body(Player player,
                           cn.lunadeer.dominion.uis.menu.dialog.DialogBodyDefinition definition,
                           DialogMenuDefinition menu,
                           MenuRoute route,
                           long revision,
                           UiCallbackSessionManager sessions,
                           Map<String, String> variables,
                           List<String> registeredTokens) {
        if (definition.inlineButtons().isEmpty()) {
            return text(definition.text(), variables);
        }
        Map<String, DialogButtonDefinition> buttons = definition.inlineButtons().stream()
                .collect(java.util.stream.Collectors.toMap(DialogButtonDefinition::id, button -> button));
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{([a-z0-9_.-]+)}")
                .matcher(definition.text());
        Component output = Component.empty();
        int cursor = 0;
        while (matcher.find()) {
            DialogButtonDefinition button = buttons.get(matcher.group(1));
            if (button == null) {
                continue;
            }
            output = output.append(text(definition.text().substring(cursor, matcher.start()), variables));
            output = output.append(inlineButton(player, button, menu, route, revision,
                    sessions, variables, registeredTokens));
            cursor = matcher.end();
        }
        return output.append(text(definition.text().substring(cursor), variables));
    }

    /**
     * Registers one inline body callback and applies its hover tooltip.
     */
    private Component inlineButton(Player player,
                                   DialogButtonDefinition definition,
                                   DialogMenuDefinition menu,
                                   MenuRoute route,
                                   long revision,
                                   UiCallbackSessionManager sessions,
                                   Map<String, String> variables,
                                   List<String> registeredTokens) {
        Map<String, String> buttonVariables = new LinkedHashMap<>(variables);
        buttonVariables.putAll(definition.variables());
        InputSchema schema = definition.prompt() != null && definition.prompt().input() != null
                ? new InputSchema(List.of(definition.prompt().input().fieldSpec())) : null;
        String token = sessions.registerActionsToken(player, route, revision, definition.actionId(),
                definition.actions(), menu.shared().actionGroups(), buttonVariables,
                definition.trustedArguments(), UiSurface.DIALOG, schema);
        registeredTokens.add(token);
        Component component = text(definition.text(), buttonVariables).clickEvent(ClickEvent.callback(audience -> {
            if (!(audience instanceof Player callbackPlayer)
                    || !callbackPlayer.getUniqueId().equals(player.getUniqueId())
                    || !consumeCurrentToken(callbackPlayer, token)) {
                return;
            }
            handleClick(callbackPlayer, token, definition, menu, route, revision, sessions,
                    buttonVariables, null);
        }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()));
        if (!definition.tooltip().isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(text(definition.tooltip(), buttonVariables)));
        }
        return component;
    }

    private void handleClick(Player player,
                             String token,
                             DialogButtonDefinition definition,
                             DialogMenuDefinition menu,
                             MenuRoute route,
                             long revision,
                             UiCallbackSessionManager sessions,
                             Map<String, String> variables,
                             DialogResponseView response) {
        if (definition.prompt() != null) {
            showPrompt(player, token, definition.prompt(), menu, route, revision, sessions, variables);
            return;
        }
        callbacks.dispatch(player, token, response == null ? Map.of() : inputs(response, menu.inputs()));
    }

    /**
     * Opens a reusable secondary confirmation or input-capture Dialog.
     */
    private void showPrompt(Player player,
                            String token,
                            DialogPromptDefinition prompt,
                            DialogMenuDefinition parent,
                            MenuRoute route,
                            long revision,
                            UiCallbackSessionManager sessions,
                            Map<String, String> variables) {
        List<DialogBody> body = prompt.body().isEmpty()
                ? List.of() : List.of(DialogBody.plainMessage(text(prompt.body(), variables), 360));
        List<DialogInput> inputs = prompt.input() == null
                ? List.of() : List.of(input(prompt.input(), variables));
        DialogBase base = DialogBase.builder(text(prompt.title(), variables))
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();
        ActionButton confirm = promptButton(prompt.confirm(), variables, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player callbackPlayer && consumeCurrentToken(callbackPlayer, token)) {
                callbacks.dispatch(callbackPlayer, token, prompt.input() == null
                        ? Map.of() : inputs(response, List.of(prompt.input())), true);
            }
        }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()));
        ActionButton deny = promptButton(prompt.deny(), variables, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player callbackPlayer && consumeCurrentToken(callbackPlayer, token)) {
                sessions.invalidateTokens(List.of(token));
                open(callbackPlayer, parent, route, revision, sessions);
            }
        }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()));
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base)
                .type(DialogType.confirmation(confirm, deny)));
        dialogTokens.put(player.getUniqueId(), Set.of(token));
        player.showDialog(dialog);
    }

    private ActionButton promptButton(DialogPromptButtonDefinition definition,
                                      Map<String, String> variables,
                                      DialogAction action) {
        ActionButton.Builder builder = ActionButton.builder(text(definition.text(), variables))
                .width(definition.width())
                .action(action);
        if (!definition.tooltip().isEmpty()) {
            builder.tooltip(text(definition.tooltip(), variables));
        }
        return builder.build();
    }

    /**
     * Converts one neutral input definition to its typed Paper input control.
     */
    private DialogInput input(DialogInputDefinition definition, Map<String, String> variables) {
        Component label = text(definition.label(), variables);
        return switch (definition.type()) {
            case INPUT -> DialogInput.text(definition.key(), label)
                    .width(definition.width())
                    .labelVisible(definition.labelVisible())
                    .initial(resolve(definition.initial(), variables))
                    .maxLength(definition.maxLength())
                    .build();
            case SLIDER -> DialogInput.numberRange(definition.key(), label,
                            definition.minimum(), definition.maximum())
                    .width(definition.width())
                    .labelFormat(definition.format())
                    .step(definition.step())
                    .initial(initialFloat(definition.initial()))
                    .build();
            case CHECKBOX -> DialogInput.bool(definition.key(), label)
                    .initial(Boolean.parseBoolean(definition.initial()))
                    .onTrue(definition.onTrue())
                    .onFalse(definition.onFalse())
                    .build();
            case DROPDOWN -> DialogInput.singleOption(definition.key(), label,
                            options(definition.options(), variables))
                    .width(definition.width())
                    .labelVisible(definition.labelVisible())
                    .build();
        };
    }

    /**
     * Preserves configured dropdown ordering and initial selection.
     */
    private List<SingleOptionDialogInput.OptionEntry> options(
            List<DialogOptionDefinition> definitions,
            Map<String, String> variables) {
        return definitions.stream()
                .map(option -> SingleOptionDialogInput.OptionEntry.create(
                        option.id(), text(option.display(), variables), option.initial()))
                .toList();
    }

    /**
     * Extracts only declared typed values from the Paper response view.
     */
    private Map<String, String> inputs(DialogResponseView response, List<DialogInputDefinition> definitions) {
        Map<String, String> values = new LinkedHashMap<>();
        for (DialogInputDefinition definition : definitions) {
            String value = switch (definition.type()) {
                case INPUT, DROPDOWN -> response.getText(definition.key());
                case SLIDER -> decimal(response.getFloat(definition.key()));
                case CHECKBOX -> checkbox(response, definition);
            };
            if (value != null) {
                values.put(definition.key(), value);
            }
        }
        return Map.copyOf(values);
    }

    /**
     * Converts Paper checkbox responses to configured on/off values.
     */
    private String checkbox(DialogResponseView response, DialogInputDefinition definition) {
        String text = response.getText(definition.key());
        if (text != null) {
            return text;
        }
        Boolean value = response.getBoolean(definition.key());
        return value == null ? null : value ? definition.onTrue() : definition.onFalse();
    }

    /**
     * Converts the optional slider default to Paper's float representation.
     */
    private Float initialFloat(String initial) {
        return initial.isBlank() ? null : Float.parseFloat(initial);
    }

    /**
     * Converts a typed float submission to a stable decimal string.
     */
    private String decimal(Float value) {
        return value == null ? null : Float.toString(value);
    }

    /**
     * Invalidates callback tokens left by the player's previous Dialog.
     */
    private void invalidatePrevious(Player player, UiCallbackSessionManager sessions) {
        Set<String> previous = dialogTokens.remove(player.getUniqueId());
        if (previous != null) {
            sessions.invalidateTokens(previous);
        }
    }

    /**
     * Accepts one current button and invalidates every sibling callback.
     */
    private boolean consumeCurrentToken(Player player, String token) {
        Set<String> current = dialogTokens.remove(player.getUniqueId());
        if (current == null || !current.contains(token)) {
            return false;
        }
        if (sessions != null) {
            sessions.invalidateTokens(current.stream().filter(candidate -> !candidate.equals(token)).toList());
        }
        return true;
    }

    /**
     * Creates the display-variable namespace for one rendered Dialog route.
     */
    private Map<String, String> variables(Player player, DialogMenuDefinition menu, MenuRoute route) {
        Map<String, String> variables = new LinkedHashMap<>(menu.variables());
        variables.put("player.name", player.getName());
        variables.put("menu.id", menu.menuId());
        variables.put("page.current", Integer.toString(route.page()));
        route.arguments().forEach((key, value) -> variables.put("route." + key, value));
        return Map.copyOf(variables);
    }

    /**
     * Resolves known variables literally while retaining configured formatting.
     */
    private Component text(String template, Map<String, String> variables) {
        return LegacyToMiniMessage.parseTemplate(template, variables);
    }

    /**
     * Resolves known variables to plain input defaults without parsing formatting.
     */
    private String resolve(String value, Map<String, String> variables) {
        String resolved = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }
}
