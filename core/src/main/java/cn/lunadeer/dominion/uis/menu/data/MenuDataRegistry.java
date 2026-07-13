package cn.lunadeer.dominion.uis.menu.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/**
 * Stores the explicit whitelist of dynamic menu data sources.
 */
public final class MenuDataRegistry {

    private final Map<String, MenuDataProvider> providers = new HashMap<>();

    /**
     * Registers one provider and rejects duplicate ownership.
     */
    public void register(String id, MenuDataProvider provider) {
        String normalizedId = id.trim().toLowerCase(Locale.ROOT);
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("Menu data provider id cannot be empty");
        }
        if (providers.putIfAbsent(normalizedId, Objects.requireNonNull(provider, "provider")) != null) {
            throw new IllegalStateException("Menu data provider already registered: " + normalizedId);
        }
    }

    /**
     * Resolves a registered provider or returns null for an unknown source.
     */
    public MenuDataProvider resolve(String id) {
        return providers.get(id.trim().toLowerCase(Locale.ROOT));
    }
}
