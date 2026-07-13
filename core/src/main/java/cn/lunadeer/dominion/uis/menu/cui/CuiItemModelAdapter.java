package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.utils.XLogger;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Applies the modern ItemMeta item-model property without linking the Java 17 core to a newer Bukkit API.
 */
final class CuiItemModelAdapter {

    private static final Method SET_ITEM_MODEL = findSetter();
    private static boolean warnedUnsupported;

    private CuiItemModelAdapter() {
    }

    /**
     * Applies a configured namespaced model or logs one explicit compatibility downgrade.
     */
    static void apply(ItemMeta meta, String itemModel) {
        if (itemModel.isBlank()) {
            return;
        }
        if (SET_ITEM_MODEL == null) {
            warnUnsupported();
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(itemModel);
        if (key == null) {
            throw new IllegalArgumentException("Invalid configured CUI item-model: " + itemModel);
        }
        try {
            SET_ITEM_MODEL.invoke(meta, key);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to apply configured CUI item-model: " + itemModel, exception);
        }
    }

    private static Method findSetter() {
        try {
            return ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static synchronized void warnUnsupported() {
        if (!warnedUnsupported) {
            XLogger.warn("Configured CUI item-model requires a newer Bukkit API and was ignored.");
            warnedUnsupported = true;
        }
    }
}
