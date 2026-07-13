package cn.lunadeer.dominion.uis.menu.cui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuiRendererTest {

    /**
     * Verifies literal replacement and preservation of unresolved configured placeholders.
     */
    @Test
    void resolvesKnownVariablesWithoutInterpretingReplacementCharacters() {
        assertEquals("Hello A$1\\B {entry.name}", CuiRenderer.resolveVariables(
                "Hello {player.name} {entry.name}", Map.of("player.name", "A$1\\B")));
    }

    /**
     * Verifies route and menu variables can coexist in ItemStack presentation text.
     */
    @Test
    void resolvesMultipleNamespacedVariables() {
        assertEquals("main_menu:2", CuiRenderer.resolveVariables(
                "{menu.id}:{page.current}", Map.of("menu.id", "main_menu", "page.current", "2")));
    }
}
