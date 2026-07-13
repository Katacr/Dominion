package cn.lunadeer.dominion.uis.menu.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClickableTextParserTest {

    @Test
    void insertsDisplayVariablesWithoutParsingTagsOrColors() {
        String dynamicText = "<click:run_command:'/stop'>&cUnsafe";
        Component component = new ClickableTextParser().parse(
                "<text='{entry.name}';hover='{entry.name}';copy='{entry.name}'>",
                Map.of("entry.name", dynamicText),
                operation -> "/unused"
        );
        Component clickable = component.children().get(0);

        assertEquals(dynamicText, PlainTextComponentSerializer.plainText().serialize(clickable));
        assertEquals(dynamicText, clickable.clickEvent().value());
        Component hover = (Component) clickable.hoverEvent().value();
        assertEquals(dynamicText, PlainTextComponentSerializer.plainText().serialize(hover));
    }

    @Test
    void validatesUrlAfterResolvingVariables() {
        assertThrows(IllegalArgumentException.class, () -> new ClickableTextParser().parse(
                "<text='Open';url='{entry.url}'>",
                Map.of("entry.url", "javascript:alert(1)"),
                operation -> "/unused"
        ));
    }
}
