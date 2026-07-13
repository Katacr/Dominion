package cn.lunadeer.dominion.uis.menu.cui;

import java.util.List;
import java.util.Objects;

/**
 * Stores the immutable ItemStack presentation configured for one chest symbol.
 */
public record ChestDisplayDefinition(
        String material,
        String name,
        List<String> lore,
        int amount,
        Integer customData,
        String itemModel,
        boolean glow
) {

    /**
     * Freezes display text before it reaches the future ItemStack renderer.
     */
    public ChestDisplayDefinition {
        material = Objects.requireNonNull(material, "material");
        name = Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        itemModel = Objects.requireNonNullElse(itemModel, "");
    }
}
