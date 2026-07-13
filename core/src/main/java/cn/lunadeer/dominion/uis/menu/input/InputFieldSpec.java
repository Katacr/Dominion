package cn.lunadeer.dominion.uis.menu.input;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Defines normalization and validation rules for one submitted UI field.
 */
public record InputFieldSpec(
        String key,
        InputFieldType type,
        boolean required,
        boolean trim,
        int minLength,
        int maxLength,
        String pattern,
        Set<String> options
) {

    private static final int MAX_FIELD_LENGTH = 1024;

    /**
     * Validates and freezes a renderer-neutral input field definition.
     */
    public InputFieldSpec {
        Objects.requireNonNull(type, "type");
        if (!MenuRoute.isValidArgumentKey(key)) {
            throw new IllegalArgumentException("Invalid input field key: " + key);
        }
        if (minLength < 0 || maxLength < minLength || maxLength > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException("Invalid input length bounds for field: " + key);
        }
        pattern = pattern == null ? "" : pattern;
        try {
            if (!pattern.isEmpty()) {
                Pattern.compile(pattern);
            }
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Invalid input pattern for field: " + key, exception);
        }
        options = Set.copyOf(options);
        if (type == InputFieldType.OPTION && options.isEmpty()) {
            throw new IllegalArgumentException("OPTION field requires values: " + key);
        }
        if (type != InputFieldType.OPTION && !options.isEmpty()) {
            throw new IllegalArgumentException("Only OPTION fields may declare values: " + key);
        }
    }

    /**
     * Creates a required trimmed text field.
     */
    public static InputFieldSpec requiredText(String key, int minLength, int maxLength, String pattern) {
        return new InputFieldSpec(key, InputFieldType.TEXT, true, true, minLength, maxLength, pattern, Set.of());
    }

    /**
     * Normalizes one raw renderer value into its canonical string representation.
     */
    public String normalize(String rawValue) {
        if (rawValue == null) {
            if (required) {
                throw failure("Missing required input: " + key);
            }
            return null;
        }
        String value = trim ? rawValue.trim() : rawValue;
        if (value.isEmpty() && !required) {
            return null;
        }
        if (value.length() < minLength || value.length() > maxLength) {
            throw failure("Input length is outside the allowed range for: " + key);
        }
        String normalized = switch (type) {
            case TEXT -> value;
            case INTEGER -> normalizeInteger(value);
            case DECIMAL -> normalizeDecimal(value);
            case BOOLEAN -> normalizeBoolean(value);
            case OPTION -> normalizeOption(value);
        };
        if (!pattern.isEmpty() && !Pattern.matches(pattern, normalized)) {
            throw failure("Input format is invalid for: " + key);
        }
        return normalized;
    }

    private String normalizeInteger(String value) {
        try {
            return new BigInteger(value).toString();
        } catch (NumberFormatException exception) {
            throw failure("Input must be an integer for: " + key);
        }
    }

    private String normalizeDecimal(String value) {
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw failure("Input must be a decimal number for: " + key);
        }
    }

    private String normalizeBoolean(String value) {
        if (value.equalsIgnoreCase("true")) {
            return "true";
        }
        if (value.equalsIgnoreCase("false")) {
            return "false";
        }
        throw failure("Input must be true or false for: " + key);
    }

    private String normalizeOption(String value) {
        return options.stream()
                .filter(option -> option.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> failure("Input is not an allowed option for: " + key));
    }

    private InputValidationException failure(String message) {
        return new InputValidationException(key, message);
    }
}
