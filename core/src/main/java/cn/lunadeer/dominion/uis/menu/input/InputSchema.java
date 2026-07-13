package cn.lunadeer.dominion.uis.menu.input;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes renderer-specific submissions into an immutable shared input map.
 */
public final class InputSchema {

    private static final int MAX_FIELDS = 16;

    private final Map<String, InputFieldSpec> fields;

    /**
     * Creates a schema with unique bounded field definitions.
     */
    public InputSchema(List<InputFieldSpec> fields) {
        if (fields.isEmpty() || fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Input schema must contain between 1 and " + MAX_FIELDS + " fields");
        }
        Map<String, InputFieldSpec> indexed = new LinkedHashMap<>();
        for (InputFieldSpec field : fields) {
            if (indexed.putIfAbsent(field.key(), field) != null) {
                throw new IllegalArgumentException("Duplicate input field: " + field.key());
            }
        }
        this.fields = Map.copyOf(indexed);
    }

    /**
     * Returns the only field when a chat workflow collects one value at a time.
     */
    public InputFieldSpec singleField() {
        if (fields.size() != 1) {
            throw new IllegalStateException("Chat input workflow requires a single-field schema");
        }
        return fields.values().iterator().next();
    }

    /**
     * Rejects unknown fields and returns canonical immutable submitted values.
     */
    public Map<String, String> normalize(Map<String, String> rawInputs) {
        for (String key : rawInputs.keySet()) {
            if (!fields.containsKey(key)) {
                throw new InputValidationException(key, "Unexpected submitted input field: " + key);
            }
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (InputFieldSpec field : fields.values()) {
            String value = field.normalize(rawInputs.get(field.key()));
            if (value != null) {
                normalized.put(field.key(), value);
            }
        }
        return Map.copyOf(normalized);
    }
}
