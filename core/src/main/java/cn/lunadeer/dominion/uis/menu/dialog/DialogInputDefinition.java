package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.input.InputFieldSpec;
import cn.lunadeer.dominion.uis.menu.input.InputFieldType;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stores one renderer-neutral Dialog input and its validation contract.
 */
public record DialogInputDefinition(
        String key,
        DialogInputType type,
        String label,
        int width,
        boolean labelVisible,
        String initial,
        int minLength,
        int maxLength,
        String pattern,
        float minimum,
        float maximum,
        Float step,
        String format,
        String onTrue,
        String onFalse,
        List<DialogOptionDefinition> options
) {

    /**
     * Freezes localized presentation and option state before rendering.
     */
    public DialogInputDefinition {
        key = Objects.requireNonNull(key, "key");
        type = Objects.requireNonNull(type, "type");
        label = Objects.requireNonNull(label, "label");
        initial = Objects.requireNonNullElse(initial, "");
        pattern = Objects.requireNonNullElse(pattern, "");
        format = Objects.requireNonNullElse(format, "options.generic_value");
        onTrue = Objects.requireNonNullElse(onTrue, "true");
        onFalse = Objects.requireNonNullElse(onFalse, "false");
        options = List.copyOf(options);
    }

    /**
     * Converts platform input metadata into the shared normalization schema.
     */
    public InputFieldSpec fieldSpec() {
        InputFieldType fieldType = switch (type) {
            case INPUT -> InputFieldType.TEXT;
            case SLIDER -> InputFieldType.DECIMAL;
            case DROPDOWN, CHECKBOX -> InputFieldType.OPTION;
        };
        Set<String> allowed = switch (type) {
            case DROPDOWN -> options.stream().map(DialogOptionDefinition::id).collect(Collectors.toUnmodifiableSet());
            case CHECKBOX -> Set.of(onTrue, onFalse);
            default -> Set.of();
        };
        return new InputFieldSpec(key, fieldType, true, type == DialogInputType.INPUT,
                minLength, maxLength, pattern, allowed);
    }
}
