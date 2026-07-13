package cn.lunadeer.dominion.uis.menu.text;

import cn.lunadeer.dominion.uis.menu.action.ActionArgumentParser;
import cn.lunadeer.dominion.utils.LegacyToMiniMessage;
import cn.lunadeer.dominion.utils.stui.components.buttons.CommandButton;
import cn.lunadeer.dominion.utils.stui.components.buttons.CopyButton;
import cn.lunadeer.dominion.utils.stui.components.buttons.UrlButton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.Map;
import java.util.Set;
import java.net.URI;
import java.util.function.Function;

/**
 * Builds Adventure components from Dominion's restricted clickable text markup.
 */
public final class ClickableTextParser {

    private final ActionArgumentParser argumentParser = new ActionArgumentParser();

    /**
     * Parses clickable segments and delegates operation callbacks to the active UI session.
     */
    public Component parse(String input,
                           Map<String, String> variables,
                           Function<String, String> operationCallbackFactory) {
        TextComponent.Builder output = Component.text();
        int cursor = 0;
        while (cursor < input.length()) {
            int opening = input.indexOf('<', cursor);
            if (opening < 0) {
                output.append(parseText(input.substring(cursor), variables));
                break;
            }
            if (opening > cursor) {
                output.append(parseText(input.substring(cursor, opening), variables));
            }
            int closing = findTagEnd(input, opening + 1);
            if (closing < 0) {
                output.append(parseText(input.substring(opening), variables));
                break;
            }
            String rawTag = input.substring(opening + 1, closing);
            output.append(buildTag(argumentParser.parse(rawTag), variables, operationCallbackFactory));
            cursor = closing + 1;
        }
        return output.build();
    }

    /**
     * Validates restricted clickable markup before a menu definition becomes active.
     */
    public void validateStructure(String input, String configPath) {
        int cursor = 0;
        while (cursor < input.length()) {
            int opening = input.indexOf('<', cursor);
            if (opening < 0) {
                return;
            }
            int closing = findTagEnd(input, opening + 1);
            if (closing < 0) {
                throw new IllegalArgumentException("Unclosed clickable text tag at " + configPath);
            }
            Map<String, String> fields = argumentParser.parse(input.substring(opening + 1, closing));
            validateFields(fields, configPath);
            cursor = closing + 1;
        }
    }

    private Component buildTag(Map<String, String> fields,
                               Map<String, String> variables,
                               Function<String, String> operationCallbackFactory) {
        validateFields(fields, "runtime clickable text");
        String text = fields.get("text");
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Clickable text requires a text field");
        }

        Component component = parseText(text, variables);
        String hover = fields.get("hover");
        if (hover != null && !hover.isEmpty()) {
            component = component.hoverEvent(parseText(hover, variables));
        }

        // Server action groups take priority because they preserve permission and session checks.
        if (fields.containsKey("actions")) {
            String command = operationCallbackFactory.apply(fields.get("actions"));
            component = component.clickEvent(clickEventOf(new CommandButton("", command)));
        } else if (fields.containsKey("url")) {
            String url = resolve(fields.get("url"), variables);
            if (!isHttpUrl(url)) {
                throw new IllegalArgumentException("Clickable text URL must use HTTP(S)");
            }
            component = component.clickEvent(clickEventOf(new UrlButton("", url)));
        } else if (fields.containsKey("copy")) {
            component = component.clickEvent(clickEventOf(new CopyButton("", resolve(fields.get("copy"), variables))));
        }

        if (Boolean.parseBoolean(fields.getOrDefault("newline", "false"))) {
            component = component.append(Component.newline());
        }
        return component;
    }

    private void validateFields(Map<String, String> fields, String configPath) {
        Set<String> allowed = Set.of("text", "hover", "actions", "url", "copy", "newline");
        for (String key : fields.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown clickable text field '" + key + "' at " + configPath);
            }
        }
        if (fields.getOrDefault("text", "").isEmpty()) {
            throw new IllegalArgumentException("Clickable text requires text at " + configPath);
        }
        long clickTargets = java.util.stream.Stream.of("actions", "url", "copy")
                .filter(fields::containsKey)
                .count();
        if (clickTargets != 1) {
            throw new IllegalArgumentException("Clickable text requires exactly one click target at " + configPath);
        }
        for (String target : Set.of("actions", "url", "copy")) {
            if (fields.containsKey(target) && fields.get(target).isEmpty()) {
                throw new IllegalArgumentException("Clickable text target '" + target + "' cannot be empty at "
                        + configPath);
            }
        }
        String url = fields.get("url");
        if (url != null && !url.contains("{") && !isHttpUrl(url)) {
            throw new IllegalArgumentException("Clickable text URL must use HTTP(S) at " + configPath);
        }
        if (fields.containsKey("newline")
                && !Set.of("true", "false").contains(fields.get("newline").toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Clickable text newline must be true or false at " + configPath);
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null
                    && (uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ClickEvent clickEventOf(cn.lunadeer.dominion.utils.stui.components.buttons.Button button) {
        ClickEvent clickEvent = button.build().style().clickEvent();
        if (clickEvent == null) {
            throw new IllegalStateException("Unable to create a compatible click event");
        }
        return clickEvent;
    }

    private Component parseText(String value, Map<String, String> variables) {
        return LegacyToMiniMessage.parseTemplate(value, variables);
    }

    private String resolve(String input, Map<String, String> variables) {
        String resolved = input;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    private int findTagEnd(String input, int start) {
        char quote = 0;
        boolean escaped = false;
        for (int index = start; index < input.length(); index++) {
            char character = input.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"' || character == '`') {
                quote = character;
            } else if (character == '>') {
                return index;
            }
        }
        return -1;
    }
}
