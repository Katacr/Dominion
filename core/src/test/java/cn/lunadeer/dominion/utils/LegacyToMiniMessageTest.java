package cn.lunadeer.dominion.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyToMiniMessageTest {

    @Test
    void insertsMenuVariablesAsLiteralText() {
        Component component = LegacyToMiniMessage.parseTemplate(
                "&aTemplate: {entry.name}",
                Map.of("entry.name", "<click:run_command:'/stop'>&cUnsafe")
        );

        assertEquals("Template: <click:run_command:'/stop'>&cUnsafe",
                PlainTextComponentSerializer.plainText().serialize(component));
    }
}
