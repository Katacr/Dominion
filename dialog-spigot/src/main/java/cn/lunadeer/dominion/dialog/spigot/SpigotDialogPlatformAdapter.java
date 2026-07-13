package cn.lunadeer.dominion.dialog.spigot;

import cn.lunadeer.dominion.uis.menu.dialog.DialogBodyDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogBottomDefinition;
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
import cn.lunadeer.dominion.utils.ColorParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.dialog.ConfirmationDialog;
import net.md_5.bungee.api.dialog.Dialog;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.NoticeDialog;
import net.md_5.bungee.api.dialog.action.ActionButton;
import net.md_5.bungee.api.dialog.action.CustomClickAction;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;
import net.md_5.bungee.api.dialog.input.BooleanInput;
import net.md_5.bungee.api.dialog.input.DialogInput;
import net.md_5.bungee.api.dialog.input.InputOption;
import net.md_5.bungee.api.dialog.input.NumberRangeInput;
import net.md_5.bungee.api.dialog.input.SingleOptionInput;
import net.md_5.bungee.api.dialog.input.TextInput;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCustomClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Dominion's neutral Dialog model with the public Spigot Bungee API.
 */
public final class SpigotDialogPlatformAdapter implements DialogPlatformAdapter, Listener {

    private static final String TOKEN_FIELD = "_dominion_token";
    private static final String PROMPT_FIELD = "_dominion_prompt";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private UiCallbackDispatcher callbacks;
    private NamespacedKey callbackKey;
    private UiCallbackSessionManager sessions;
    private final Map<UUID, Set<String>> dialogTokens = new ConcurrentHashMap<>();
    private final Map<String, PromptState> promptStates = new ConcurrentHashMap<>();

    /**
     * Registers the Spigot custom-click listener without exposing its event type to core.
     */
    @Override
    public void initialize(JavaPlugin plugin, UiCallbackDispatcher callbacks) {
        this.callbacks = callbacks;
        this.callbackKey = new NamespacedKey(plugin, "dialog");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Converts one validated neutral menu to Bungee Dialog model objects and opens it.
     */
    @Override
    public void open(Player player,
                     DialogMenuDefinition menu,
                     MenuRoute route,
                     long revision,
                     UiCallbackSessionManager sessions) {
        this.sessions = sessions;
        Set<String> previousTokens = dialogTokens.remove(player.getUniqueId());
        if (previousTokens != null) {
            sessions.invalidateTokens(previousTokens);
        }
        List<String> registeredTokens = new ArrayList<>();
        Map<String, String> variables = variables(player, menu, route);
        List<DialogBody> body = menu.body().stream()
                .map(component -> bodyComponent(player, component, menu, route, revision,
                        sessions, variables, registeredTokens))
                .toList();
        List<DialogInput> inputs = menu.inputs().stream().map(input -> input(input, variables)).toList();
        DialogBase base = new DialogBase(text(resolve(menu.title(), variables)))
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(menu.canEscape())
                .pause(menu.pause())
                .afterAction(DialogBase.AfterAction.valueOf(menu.afterAction().name()));
        InputSchema schema = menu.inputSchema();
        Dialog dialog = switch (menu.bottom().type()) {
            case NOTICE -> new NoticeDialog(base,
                    button(player, menu.bottom().buttons().get(0), menu, route, revision, sessions, variables, schema,
                            registeredTokens));
            case CONFIRMATION -> new ConfirmationDialog(base,
                    button(player, menu.bottom().buttons().get(0), menu, route, revision, sessions, variables, schema,
                            registeredTokens),
                    button(player, menu.bottom().buttons().get(1), menu, route, revision, sessions, variables, schema,
                            registeredTokens));
            case MULTI -> multi(player, base, menu, route, revision, sessions, variables, schema, registeredTokens);
        };
        player.showDialog(dialog);
        dialogTokens.put(player.getUniqueId(), Set.copyOf(registeredTokens));
    }

    /**
     * Clears the current native Spigot Dialog.
     */
    @Override
    public void close(Player player) {
        player.clearDialog();
    }

    /**
     * Restores one RUN_COMMAND inline callback and opens its prompt when configured.
     */
    @Override
    public boolean dispatchInline(Player player, String token) {
        Set<String> currentTokens = dialogTokens.remove(player.getUniqueId());
        if (currentTokens == null || !currentTokens.contains(token)) {
            return false;
        }
        List<String> siblings = currentTokens.stream().filter(candidate -> !candidate.equals(token)).toList();
        sessions.invalidateTokens(siblings);
        siblings.forEach(promptStates::remove);
        PromptState promptState = promptStates.get(token);
        if (promptState == null) {
            return callbacks.dispatch(player, token);
        }
        dialogTokens.put(player.getUniqueId(), Set.of(token));
        showPrompt(player, token, promptState);
        return true;
    }

    /**
     * Drops adapter service references during plugin shutdown.
     */
    @Override
    public void shutdown() {
        if (sessions != null) {
            dialogTokens.values().forEach(sessions::invalidateTokens);
        }
        dialogTokens.clear();
        promptStates.clear();
        sessions = null;
        callbacks = null;
        callbackKey = null;
    }

    /**
     * Converts one neutral body to Spigot's message model or Mojang-compatible item JSON.
     */
    private DialogBody bodyComponent(Player player,
                                     DialogBodyDefinition definition,
                                     DialogMenuDefinition menu,
                                     MenuRoute route,
                                     long revision,
                                     UiCallbackSessionManager sessions,
                                     Map<String, String> variables,
                                     List<String> registeredTokens) {
        if (definition.type() == DialogBodyDefinition.Type.ITEM) {
            PlainMessageBody description = definition.text().isBlank() ? null
                    : new PlainMessageBody(text(resolve(definition.text(), variables)));
            return new SpigotItemDialogBody(definition.material(), definition.amount(), description,
                    definition.showDecorations(), definition.showTooltip(), definition.width(), definition.height());
        }
        return new PlainMessageBody(body(player, definition, menu, route, revision,
                sessions, variables, registeredTokens), definition.width());
    }

    /**
     * Extracts the opaque callback token and untrusted input strings from Spigot JSON.
     */
    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (callbacks == null || callbackKey == null) {
            return;
        }
        JsonObject payload = event.getData() != null && event.getData().isJsonObject()
                ? event.getData().getAsJsonObject() : new JsonObject();
        if (!callbackKey.equals(event.getId())) return;
        JsonElement tokenValue = payload.get(TOKEN_FIELD);
        if (tokenValue == null || !tokenValue.isJsonPrimitive()) return;
        String token = tokenValue.getAsString();
        Set<String> currentTokens = dialogTokens.remove(event.getPlayer().getUniqueId());
        if (currentTokens == null || !currentTokens.contains(token)) {
            return;
        }
        if (sessions != null) {
            List<String> siblings = currentTokens.stream().filter(candidate -> !candidate.equals(token)).toList();
            sessions.invalidateTokens(siblings);
            siblings.forEach(promptStates::remove);
        }
        PromptState promptState = promptStates.get(token);
        String promptAction = payload.has(PROMPT_FIELD) ? payload.get(PROMPT_FIELD).getAsString() : "";
        if (promptState != null && (promptAction.isEmpty() || promptAction.equals("open"))) {
            dialogTokens.put(event.getPlayer().getUniqueId(), Set.of(token));
            showPrompt(event.getPlayer(), token, promptState);
            return;
        }
        if (promptState != null && promptAction.equals("deny")) {
            promptStates.remove(token);
            sessions.invalidateTokens(List.of(token));
            open(event.getPlayer(), promptState.parent(), promptState.route(), promptState.revision(),
                    promptState.sessions());
            return;
        }
        promptStates.remove(token);
        Map<String, String> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
            if (!entry.getKey().equals(TOKEN_FIELD) && !entry.getKey().equals(PROMPT_FIELD)
                    && entry.getValue().isJsonPrimitive()) {
                inputs.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        callbacks.dispatch(event.getPlayer(), token, inputs, promptAction.equals("confirm"));
    }

    /**
     * Builds a filtered native multi-action footer and optional exit action.
     */
    private MultiActionDialog multi(Player player,
                                    DialogBase base,
                                    DialogMenuDefinition menu,
                                    MenuRoute route,
                                    long revision,
                                    UiCallbackSessionManager sessions,
                                    Map<String, String> variables,
                                    InputSchema schema,
                                    List<String> registeredTokens) {
        List<ActionButton> buttons = menu.bottom().buttons().stream()
                .map(definition -> button(player, definition, menu, route, revision, sessions, variables, schema,
                        registeredTokens))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (buttons.isEmpty()) {
            throw new IllegalStateException("Dialog multi footer has no buttons available to player");
        }
        ActionButton exit = button(player, menu.bottom().exit(), menu, route, revision, sessions, variables, schema,
                registeredTokens);
        return new MultiActionDialog(base, buttons, menu.bottom().columns(), exit);
    }

    /**
     * Registers one player-bound token and creates its native action button.
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
        JsonObject additions = new JsonObject();
        additions.addProperty(TOKEN_FIELD, token);
        if (definition.prompt() != null) {
            additions.addProperty(PROMPT_FIELD, "open");
            promptStates.put(token, new PromptState(definition.prompt(), menu, route, revision, sessions,
                    buttonVariables));
        }
        CustomClickAction action = new CustomClickAction(callbackKey.toString()).additions(additions);
        BaseComponent tooltip = definition.tooltip().isEmpty()
                ? null : text(resolve(definition.tooltip(), buttonVariables));
        return new ActionButton(text(resolve(definition.text(), buttonVariables)), tooltip, definition.width(), action);
    }

    /**
     * Builds a body message whose configured placeholders become clickable CUSTOM events.
     */
    private BaseComponent body(Player player,
                               DialogBodyDefinition definition,
                               DialogMenuDefinition menu,
                               MenuRoute route,
                               long revision,
                               UiCallbackSessionManager sessions,
                               Map<String, String> variables,
                               List<String> registeredTokens) {
        if (definition.inlineButtons().isEmpty()) {
            return text(resolve(definition.text(), variables));
        }
        Map<String, DialogButtonDefinition> buttons = definition.inlineButtons().stream()
                .collect(java.util.stream.Collectors.toMap(DialogButtonDefinition::id, button -> button));
        Matcher matcher = VARIABLE_PATTERN.matcher(definition.text());
        TextComponent output = new TextComponent();
        int cursor = 0;
        while (matcher.find()) {
            DialogButtonDefinition button = buttons.get(matcher.group(1));
            if (button == null) continue;
            output.addExtra(text(resolve(definition.text().substring(cursor, matcher.start()), variables)));
            output.addExtra(inlineButton(player, button, menu, route, revision,
                    sessions, variables, registeredTokens));
            cursor = matcher.end();
        }
        output.addExtra(text(resolve(definition.text().substring(cursor), variables)));
        return output;
    }

    /**
     * Registers one inline body callback and encodes its token in a CUSTOM click key.
     */
    private BaseComponent inlineButton(Player player,
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
        if (definition.prompt() != null) {
            promptStates.put(token, new PromptState(definition.prompt(), menu, route, revision, sessions,
                    buttonVariables));
        }
        BaseComponent component = text(resolve(definition.text(), buttonVariables));
        String command = definition.prompt() == null
                ? sessions.commandFor(token) : "/dominion ui_dialog_callback " + token;
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        if (!definition.tooltip().isEmpty()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new BaseComponent[]{text(resolve(definition.tooltip(), buttonVariables))}));
        }
        return component;
    }

    /**
     * Opens a platform-native secondary confirmation or input-capture Dialog.
     */
    private void showPrompt(Player player, String token, PromptState state) {
        DialogPromptDefinition prompt = state.prompt();
        List<DialogBody> body = prompt.body().isEmpty() ? List.of()
                : List.of(new PlainMessageBody(text(resolve(prompt.body(), state.variables())), 360));
        List<DialogInput> inputs = prompt.input() == null ? List.of()
                : List.of(input(prompt.input(), state.variables()));
        DialogBase base = new DialogBase(text(resolve(prompt.title(), state.variables())))
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.AfterAction.NONE);
        ActionButton confirm = promptButton(prompt.confirm(), state.variables(), promptAction(token, "confirm"));
        ActionButton deny = promptButton(prompt.deny(), state.variables(), promptAction(token, "deny"));
        player.showDialog(new ConfirmationDialog(base, confirm, deny));
    }

    private CustomClickAction promptAction(String token, String action) {
        JsonObject additions = new JsonObject();
        additions.addProperty(TOKEN_FIELD, token);
        additions.addProperty(PROMPT_FIELD, action);
        return new CustomClickAction(callbackKey.toString()).additions(additions);
    }

    private ActionButton promptButton(DialogPromptButtonDefinition definition,
                                      Map<String, String> variables,
                                      CustomClickAction action) {
        BaseComponent tooltip = definition.tooltip().isEmpty()
                ? null : text(resolve(definition.tooltip(), variables));
        return new ActionButton(text(resolve(definition.text(), variables)), tooltip, definition.width(), action);
    }

    /**
     * Maps one neutral input definition to the corresponding Spigot model.
     */
    private DialogInput input(DialogInputDefinition definition, Map<String, String> variables) {
        BaseComponent label = text(resolve(definition.label(), variables));
        return switch (definition.type()) {
            case INPUT -> new TextInput(definition.key(), definition.width(), label, definition.labelVisible(),
                    resolve(definition.initial(), variables), definition.maxLength());
            case SLIDER -> new NumberRangeInput(definition.key(), definition.width(), label, definition.format(),
                    definition.minimum(), definition.maximum(), definition.step(), initialFloat(definition.initial()));
            case CHECKBOX -> new BooleanInput(definition.key(), label,
                    Boolean.parseBoolean(definition.initial()), definition.onTrue(), definition.onFalse());
            case DROPDOWN -> new SingleOptionInput(definition.key(), definition.width(), label,
                    definition.labelVisible(), options(definition.options(), variables));
        };
    }

    /**
     * Preserves configured dropdown option order and initial selection.
     */
    private List<InputOption> options(List<DialogOptionDefinition> definitions, Map<String, String> variables) {
        List<InputOption> options = new ArrayList<>();
        for (DialogOptionDefinition definition : definitions) {
            options.add(new InputOption(definition.id(), text(resolve(definition.display(), variables)),
                    definition.initial()));
        }
        return List.copyOf(options);
    }

    /**
     * Converts an optional validated slider default to the Spigot float model.
     */
    private Float initialFloat(String initial) {
        return initial.isBlank() ? null : Float.parseFloat(initial);
    }

    /**
     * Creates the bounded display-variable namespace for one rendered route.
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
     * Resolves known variables literally while preserving unknown placeholders.
     */
    private String resolve(String value, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = variables.get(matcher.group(1));
            matcher.appendReplacement(output,
                    Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /**
     * Converts Dominion legacy color text to one Bungee component root.
     */
    private BaseComponent text(String value) {
        return TextComponent.fromLegacy(ColorParser.getBukkitType(value));
    }

    private record PromptState(
            DialogPromptDefinition prompt,
            DialogMenuDefinition parent,
            MenuRoute route,
            long revision,
            UiCallbackSessionManager sessions,
            Map<String, String> variables
    ) {
        private PromptState {
            variables = Map.copyOf(variables);
        }
    }
}
