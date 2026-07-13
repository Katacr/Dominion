package cn.lunadeer.dominion.uis.menu.action;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses semicolon-delimited key/value arguments while respecting quoted values.
 */
public final class ActionArgumentParser {

    /**
     * Parses `key=value;key="value"` into an ordered map.
     */
    public Map<String, String> parse(String input) {
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder field = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index <= input.length(); index++) {
            char character = index < input.length() ? input.charAt(index) : ';';
            if (escaped) {
                field.append(character);
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
                } else {
                    field.append(character);
                }
                continue;
            }
            if (character == '\'' || character == '"' || character == '`') {
                quote = character;
                continue;
            }
            if (character == ';') {
                addField(values, field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed quote in action arguments");
        }
        return Map.copyOf(values);
    }

    private void addField(Map<String, String> values, String rawField) {
        String value = rawField.trim();
        if (value.isEmpty()) {
            return;
        }
        int separator = value.indexOf('=');
        if (separator <= 0) {
            throw new IllegalArgumentException("Expected key=value argument: " + value);
        }
        String key = value.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String fieldValue = value.substring(separator + 1).trim();
        if (values.putIfAbsent(key, fieldValue) != null) {
            throw new IllegalArgumentException("Duplicate action argument: " + key);
        }
    }
}
