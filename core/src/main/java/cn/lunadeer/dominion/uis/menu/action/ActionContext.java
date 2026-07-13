package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carries trusted server context through one ordered action sequence.
 */
public final class ActionContext {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final Player player;
    private final UiSurface surface;
    private final MenuRoute route;
    private final long menuRevision;
    private final Map<String, String> variables;
    private final Map<String, String> displayVariables;
    private final Map<String, String> trustedArguments;
    private final Map<String, String> submittedInputs;
    private final Map<String, List<ActionSpec>> actionGroups;
    private final ActionGroupCallbackRegistrar callbackRegistrar;

    /**
     * Creates an action context for one player and rendered menu revision.
     */
    public ActionContext(Player player,
                         MenuRoute route,
                         long menuRevision,
                         Map<String, String> variables,
                         Map<String, String> trustedArguments,
                         Map<String, List<ActionSpec>> actionGroups,
                         ActionGroupCallbackRegistrar callbackRegistrar) {
        this(UiSurface.TUI, player, route, menuRevision, variables, trustedArguments, Map.of(), actionGroups,
                callbackRegistrar);
    }

    /**
     * Creates an action context with normalized renderer-submitted inputs.
     */
    public ActionContext(Player player,
                         MenuRoute route,
                         long menuRevision,
                         Map<String, String> variables,
                         Map<String, String> trustedArguments,
                         Map<String, String> submittedInputs,
                         Map<String, List<ActionSpec>> actionGroups,
                         ActionGroupCallbackRegistrar callbackRegistrar) {
        this(UiSurface.TUI, player, route, menuRevision, variables, trustedArguments, submittedInputs, actionGroups,
                callbackRegistrar);
    }

    /**
     * Creates an action context tied to the renderer surface that produced the callback.
     */
    public ActionContext(UiSurface surface,
                         Player player,
                         MenuRoute route,
                         long menuRevision,
                         Map<String, String> variables,
                         Map<String, String> trustedArguments,
                         Map<String, String> submittedInputs,
                         Map<String, List<ActionSpec>> actionGroups,
                         ActionGroupCallbackRegistrar callbackRegistrar) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.player = Objects.requireNonNull(player, "player");
        this.route = Objects.requireNonNull(route, "route");
        this.menuRevision = menuRevision;
        this.displayVariables = Map.copyOf(variables);
        this.trustedArguments = Map.copyOf(trustedArguments);
        this.submittedInputs = Map.copyOf(submittedInputs);
        Map<String, String> mergedVariables = new java.util.LinkedHashMap<>(displayVariables);
        submittedInputs.forEach((key, value) -> mergedVariables.put("input." + key, value));
        this.variables = Map.copyOf(mergedVariables);
        this.actionGroups = Map.copyOf(actionGroups);
        this.callbackRegistrar = Objects.requireNonNull(callbackRegistrar, "callbackRegistrar");
    }

    /**
     * Returns the player who actually triggered the callback.
     */
    public Player player() {
        return player;
    }

    /**
     * Returns the renderer surface that must handle navigation and refresh results.
     */
    public UiSurface surface() {
        return surface;
    }

    /**
     * Returns the menu that created this action sequence.
     */
    public String menuId() {
        return route.menuId();
    }

    /**
     * Returns the route captured when this menu revision was rendered.
     */
    public MenuRoute route() {
        return route;
    }

    /**
     * Returns the immutable menu definition revision captured by this callback.
     */
    public long menuRevision() {
        return menuRevision;
    }

    /**
     * Returns the immutable variables captured by the server session.
     */
    public Map<String, String> variables() {
        return variables;
    }

    /**
     * Returns an immutable server-owned callback argument without text substitution.
     */
    public String trustedArgument(String key) {
        return trustedArguments.get(key);
    }

    /**
     * Returns a required server-owned route argument or rejects incomplete action context.
     */
    public String requireRouteArgument(String key) {
        String value = route.arguments().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing route argument: " + key);
        }
        return value;
    }

    /**
     * Returns a required server-owned callback argument or rejects incomplete action context.
     */
    public String requireTrustedArgument(String key) {
        String value = trustedArguments.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing trusted callback argument: " + key);
        }
        return value;
    }

    /**
     * Returns all server-owned callback arguments.
     */
    public Map<String, String> trustedArguments() {
        return trustedArguments;
    }

    /**
     * Returns all normalized but still untrusted submitted input values.
     */
    public Map<String, String> submittedInputs() {
        return submittedInputs;
    }

    /**
     * Returns a required normalized input for explicit domain validation.
     */
    public String requireInput(String key) {
        String value = submittedInputs.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing submitted input: " + key);
        }
        return value;
    }

    /**
     * Returns whether a trusted secondary confirmation preceded this execution.
     */
    public boolean confirmed() {
        return Boolean.parseBoolean(trustedArguments.getOrDefault("ui.confirmed", "false"));
    }

    /**
     * Returns a copy of this trusted context carrying a normalized input submission.
     */
    public ActionContext withInputs(Map<String, String> inputs) {
        return new ActionContext(surface, player, route, menuRevision, displayVariables, trustedArguments, inputs,
                actionGroups, callbackRegistrar);
    }

    /**
     * Returns all validated action groups in the current shared menu definition.
     */
    public Map<String, List<ActionSpec>> actionGroups() {
        return actionGroups;
    }

    /**
     * Resolves known namespaced variables while preserving unknown placeholders.
     */
    public String resolve(String input) {
        Matcher matcher = VARIABLE_PATTERN.matcher(Objects.requireNonNullElse(input, ""));
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /**
     * Creates a callback command for an action group in the current menu.
     */
    public String registerActionGroupCallback(String actionGroupId) {
        if (!actionGroups.containsKey(actionGroupId)) {
            throw new IllegalArgumentException("Unknown action group '" + actionGroupId + "' in menu " + menuId());
        }
        return callbackRegistrar.register(actionGroupId);
    }
}
