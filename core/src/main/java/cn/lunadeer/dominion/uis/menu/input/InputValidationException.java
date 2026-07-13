package cn.lunadeer.dominion.uis.menu.input;

/**
 * Reports a field-specific validation failure without retaining submitted input text.
 */
public final class InputValidationException extends IllegalArgumentException {

    private final String field;

    /**
     * Creates a safe validation message associated with one schema field.
     */
    public InputValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * Returns the schema key that rejected the submitted value.
     */
    public String field() {
        return field;
    }
}
