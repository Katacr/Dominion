package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combines one shared route contract with a localized chest presentation.
 */
public record ChestMenuDefinition(
        String menuId,
        String locale,
        String title,
        List<String> layout,
        Map<Character, ChestButtonDefinition> buttons,
        Map<String, String> variables,
        SharedMenuDefinition shared
) {

    /**
     * Freezes all nested presentation data before runtime rendering.
     */
    public ChestMenuDefinition {
        menuId = Objects.requireNonNull(menuId, "menuId");
        locale = Objects.requireNonNull(locale, "locale");
        title = Objects.requireNonNull(title, "title");
        layout = List.copyOf(layout);
        buttons = Map.copyOf(buttons);
        variables = Map.copyOf(variables);
        shared = Objects.requireNonNull(shared, "shared");
    }

    /**
     * Returns the single dynamic symbol or null for a static chest menu.
     */
    public Character dynamicSymbol() {
        return buttons.values().stream()
                .filter(ChestButtonDefinition::dynamic)
                .map(ChestButtonDefinition::symbol)
                .findFirst()
                .orElse(null);
    }

    /**
     * Counts the slots available to the dynamic provider on each page.
     */
    public int dynamicPageSize() {
        Character symbol = dynamicSymbol();
        if (symbol == null) {
            return 0;
        }
        return (int) layout.stream().flatMapToInt(String::chars).filter(value -> value == symbol).count();
    }
}
