package cn.lunadeer.dominion.uis.menu.action;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionParserTest {

    @Test
    void parsesTypeAndPreservesRemainingColons() {
        ActionSpec action = new ActionParser().parse("tell: value:with:colons", "menu.actions[0]");

        assertEquals("tell", action.type());
        assertEquals("value:with:colons", action.argument());
        assertEquals("menu.actions[0]", action.configPath());
    }

    @Test
    void parsesQuotedArgumentFields() {
        Map<String, String> values = new ActionArgumentParser().parse(
                "title=\"Hello; world\";subtitle='Dominion';frame=task");

        assertEquals("Hello; world", values.get("title"));
        assertEquals("Dominion", values.get("subtitle"));
        assertEquals("task", values.get("frame"));
    }

    @Test
    void rejectsDuplicateArgumentFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActionArgumentParser().parse("title=one;title=two"));
    }

    @Test
    void rejectsOversizedActionList() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActionParser().parseAll(Collections.nCopies(33, "tell: test"), "menu.actions"));
    }
}
