package cn.lunadeer.dominion.uis.menu.dialog;

import java.util.List;
import java.util.Objects;

/**
 * Stores one renderer-neutral plain-message or item Dialog body component.
 */
public record DialogBodyDefinition(
        String id,
        Type type,
        String text,
        int width,
        int height,
        String material,
        int amount,
        boolean showDecorations,
        boolean showTooltip,
        List<DialogButtonDefinition> inlineButtons
) {

    /**
     * Identifies the renderer-neutral body payload variant.
     */
    public enum Type {
        MESSAGE,
        ITEM
    }

    /**
     * Creates a plain body message without inline callbacks.
     */
    public DialogBodyDefinition(String id, String text, int width) {
        this(id, text, width, List.of());
    }

    /**
     * Creates a plain body message with optional inline callbacks.
     */
    public DialogBodyDefinition(String id,
                                String text,
                                int width,
                                List<DialogButtonDefinition> inlineButtons) {
        this(id, Type.MESSAGE, text, width, 0, "", 1, true, true, inlineButtons);
    }

    /**
     * Creates an item body whose material payload can be rendered by both platform adapters.
     */
    public static DialogBodyDefinition item(String id,
                                            String material,
                                            int amount,
                                            String description,
                                            int width,
                                            int height,
                                            boolean showDecorations,
                                            boolean showTooltip) {
        return new DialogBodyDefinition(id, Type.ITEM, description, width, height, material, amount,
                showDecorations, showTooltip, List.of());
    }

    /**
     * Rejects missing body identity and text.
     */
    public DialogBodyDefinition {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        text = Objects.requireNonNull(text, "text");
        material = Objects.requireNonNull(material, "material");
        inlineButtons = List.copyOf(inlineButtons);
    }
}
