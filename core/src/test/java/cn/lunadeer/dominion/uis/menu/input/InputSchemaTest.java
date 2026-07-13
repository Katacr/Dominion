package cn.lunadeer.dominion.uis.menu.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputSchemaTest {

    @Test
    void normalizesRendererValuesIntoCanonicalStrings() {
        InputSchema schema = new InputSchema(List.of(
                InputFieldSpec.requiredText("name", 1, 16, "\\S+"),
                new InputFieldSpec("amount", InputFieldType.INTEGER, true, true,
                        1, 8, "", Set.of()),
                new InputFieldSpec("enabled", InputFieldType.BOOLEAN, true, true,
                        1, 8, "", Set.of())
        ));

        Map<String, String> normalized = schema.normalize(Map.of(
                "name", "  template  ",
                "amount", "+0042",
                "enabled", "TRUE"
        ));

        assertEquals("template", normalized.get("name"));
        assertEquals("42", normalized.get("amount"));
        assertEquals("true", normalized.get("enabled"));
    }

    @Test
    void rejectsUnknownAndMalformedFieldsWithoutEchoingValues() {
        InputSchema schema = new InputSchema(List.of(
                InputFieldSpec.requiredText("name", 1, 16, "\\S+")
        ));

        InputValidationException unknown = assertThrows(InputValidationException.class,
                () -> schema.normalize(Map.of("unexpected", "secret-value")));
        InputValidationException malformed = assertThrows(InputValidationException.class,
                () -> schema.normalize(Map.of("name", "contains spaces")));

        assertFalse(unknown.getMessage().contains("secret-value"));
        assertFalse(malformed.getMessage().contains("contains spaces"));
    }

    @Test
    void optionFieldUsesDeclaredCanonicalValue() {
        InputFieldSpec option = new InputFieldSpec(
                "mode", InputFieldType.OPTION, true, true, 1, 16, "", Set.of("OWNER", "ADMIN"));

        assertEquals("ADMIN", option.normalize("admin"));
    }
}
