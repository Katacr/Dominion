package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.uis.menu.text.ClickableTextParser;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.stui.TextUserInterfaceManager;
import cn.lunadeer.dominion.utils.LegacyToMiniMessage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the small cross-UI feedback action set supported by Dominion.
 */
public final class BuiltInActionRegistrar {

    private final ActionArgumentParser argumentParser = new ActionArgumentParser();
    private final ClickableTextParser clickableTextParser = new ClickableTextParser();
    private final ToastSender toastSender;

    /**
     * Creates a registrar whose toast lifecycle belongs to the supplied plugin.
     */
    public BuiltInActionRegistrar(ToastSender toastSender) {
        this.toastSender = toastSender;
    }

    /**
     * Adds all built-in action handlers to the shared whitelist.
     */
    public void registerInto(ActionRegistry registry) {
        registry.register("tell", this::tell, this::requireArgument);
        registry.register("actionbar", this::actionBar, this::requireArgument);
        registry.register("title", this::title, this::validateTitle);
        registry.register("toast", this::toast, this::validateToast);
        registry.register("hovertext", this::hoverText, this::validateHoverText);
        registry.register("open", this::open, this::validateOpen);
        registry.register("refresh", (context, action) -> completed(ActionResult.refresh()), this::requireNoArgument);
        registry.register("page", this::page, this::validatePage);
        registry.register("close", (context, action) -> completed(ActionResult.close()), this::requireNoArgument);
    }

    private CompletableFuture<ActionResult> tell(ActionContext context, ActionSpec action) {
        TextUserInterfaceManager.getInstance().sendMessage(
                context.player(), LegacyToMiniMessage.parseTemplate(action.argument(), context.variables()));
        return completed(ActionResult.continueExecution());
    }

    private CompletableFuture<ActionResult> actionBar(ActionContext context, ActionSpec action) {
        Notification.actionBar(context.player(), LegacyToMiniMessage.parseTemplate(action.argument(), context.variables()));
        return completed(ActionResult.continueExecution());
    }

    private CompletableFuture<ActionResult> title(ActionContext context, ActionSpec action) {
        Map<String, String> values = argumentParser.parse(action.argument());
        Notification.title(
                context.player(),
                LegacyToMiniMessage.parseTemplate(values.getOrDefault("title", ""), context.variables()),
                LegacyToMiniMessage.parseTemplate(values.getOrDefault("subtitle", ""), context.variables())
        );
        return completed(ActionResult.continueExecution());
    }

    private CompletableFuture<ActionResult> toast(ActionContext context, ActionSpec action) {
        Map<String, String> values = argumentParser.parse(action.argument());
        String title = values.get("title");
        if (title == null || title.isBlank()) {
            return completed(ActionResult.failure("Toast action requires a title at " + action.configPath()));
        }
        toastSender.send(context.player(), values.getOrDefault("icon", "GRASS_BLOCK"),
                LegacyToMiniMessage.parseTemplate(title, context.variables()),
                values.getOrDefault("frame", "task"));
        return completed(ActionResult.continueExecution());
    }

    private CompletableFuture<ActionResult> hoverText(ActionContext context, ActionSpec action) {
        TextUserInterfaceManager.getInstance().sendMessage(context.player(), clickableTextParser.parse(
                action.argument(), context.variables(), context::registerActionGroupCallback));
        return completed(ActionResult.continueExecution());
    }

    private CompletableFuture<ActionResult> open(ActionContext context, ActionSpec action) {
        String argument = context.resolve(action.argument()).trim();
        if (argument.isEmpty()) {
            return completed(ActionResult.failure("Open action requires a menu id at " + action.configPath()));
        }
        String[] tokens = argument.split("\\s+");
        Map<String, String> routeArguments = new java.util.LinkedHashMap<>();
        for (int index = 1; index < tokens.length; index++) {
            int separator = tokens[index].indexOf('=');
            if (separator <= 0) {
                return completed(ActionResult.failure("Expected route key=value at " + action.configPath()));
            }
            String key = tokens[index].substring(0, separator);
            if (routeArguments.putIfAbsent(key, tokens[index].substring(separator + 1)) != null) {
                return completed(ActionResult.failure("Duplicate route argument '" + key + "' at "
                        + action.configPath()));
            }
        }
        return completed(ActionResult.open(new MenuRoute(tokens[0], 1, routeArguments)));
    }

    private CompletableFuture<ActionResult> page(ActionContext context, ActionSpec action) {
        String argument = context.resolve(action.argument()).trim();
        int targetPage;
        if (argument.equalsIgnoreCase("previous")) {
            targetPage = context.route().page() - 1;
        } else if (argument.equalsIgnoreCase("next")) {
            targetPage = context.route().page() + 1;
        } else {
            try {
                targetPage = Integer.parseInt(argument);
            } catch (NumberFormatException exception) {
                return completed(ActionResult.failure("Invalid page target at " + action.configPath()));
            }
        }
        return completed(ActionResult.open(context.route().withPage(targetPage)));
    }

    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private void requireArgument(ActionSpec action) {
        if (action.argument().isBlank()) {
            throw new IllegalArgumentException("Action '" + action.type() + "' requires an argument at "
                    + action.configPath());
        }
    }

    private void requireNoArgument(ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException("Action '" + action.type() + "' does not accept arguments at "
                    + action.configPath());
        }
    }

    private void validateTitle(ActionSpec action) {
        Map<String, String> values = argumentParser.parse(action.argument());
        validateKeys(values, Set.of("title", "subtitle"), action);
        if (!values.containsKey("title") && !values.containsKey("subtitle")) {
            throw new IllegalArgumentException("Title action requires title or subtitle at " + action.configPath());
        }
    }

    private void validateToast(ActionSpec action) {
        Map<String, String> values = argumentParser.parse(action.argument());
        validateKeys(values, Set.of("title", "icon", "frame"), action);
        if (values.getOrDefault("title", "").isBlank()) {
            throw new IllegalArgumentException("Toast action requires title at " + action.configPath());
        }
        String frame = values.getOrDefault("frame", "task").toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("task", "goal", "challenge").contains(frame)) {
            throw new IllegalArgumentException("Invalid toast frame '" + frame + "' at " + action.configPath());
        }
    }

    private void validateOpen(ActionSpec action) {
        requireArgument(action);
        String[] tokens = action.argument().split("\\s+");
        Set<String> keys = new java.util.HashSet<>();
        Map<String, String> arguments = new java.util.LinkedHashMap<>();
        for (int index = 1; index < tokens.length; index++) {
            int separator = tokens[index].indexOf('=');
            if (separator <= 0 || !keys.add(tokens[index].substring(0, separator))) {
                throw new IllegalArgumentException("Invalid or duplicate route argument at " + action.configPath());
            }
            arguments.put(tokens[index].substring(0, separator), tokens[index].substring(separator + 1));
        }
        new MenuRoute(tokens[0], 1, arguments);
    }

    private void validateHoverText(ActionSpec action) {
        requireArgument(action);
        clickableTextParser.validateStructure(action.argument(), action.configPath());
    }

    private void validatePage(ActionSpec action) {
        requireArgument(action);
        if (action.argument().equalsIgnoreCase("previous") || action.argument().equalsIgnoreCase("next")) {
            return;
        }
        try {
            if (Integer.parseInt(action.argument()) < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid page target at " + action.configPath());
        }
    }

    private void validateKeys(Map<String, String> values, Set<String> allowedKeys, ActionSpec action) {
        for (String key : values.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("Unknown " + action.type() + " argument '" + key + "' at "
                        + action.configPath());
            }
        }
    }
}
