package cn.lunadeer.dominion.dialog.spigot;

import com.google.gson.annotations.SerializedName;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Supplies the Mojang item-body JSON fields omitted by Spigot's public Bungee Dialog model.
 */
final class SpigotItemDialogBody extends DialogBody {

    private final Map<String, Object> item;
    private final PlainMessageBody description;
    @SerializedName("show_decorations")
    private final boolean showDecorations;
    @SerializedName("show_tooltip")
    private final boolean showTooltip;
    private final int width;
    private final int height;

    /**
     * Creates a codec-compatible item body without linking the plugin to versioned NMS classes.
     */
    SpigotItemDialogBody(String itemId,
                          int amount,
                          PlainMessageBody description,
                          boolean showDecorations,
                          boolean showTooltip,
                          int width,
                          int height) {
        super("minecraft:item");
        Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put("id", itemId);
        if (amount != 1) {
            itemData.put("count", amount);
        }
        this.item = Map.copyOf(itemData);
        this.description = description;
        this.showDecorations = showDecorations;
        this.showTooltip = showTooltip;
        this.width = width;
        this.height = height;
    }
}
