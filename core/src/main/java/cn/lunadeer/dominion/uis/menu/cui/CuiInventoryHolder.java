package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Holds immutable server-side callback identity for one rendered chest Inventory.
 */
public final class CuiInventoryHolder implements InventoryHolder {

    private static final Pattern CALLBACK_TOKEN = Pattern.compile("[a-f0-9]{32}");

    private final UUID ownerId;
    private final UUID sessionId;
    private final MenuRoute route;
    private final long menuRevision;
    private final Map<Integer, Map<ChestClickType, String>> callbacks;
    private Inventory inventory;

    /**
     * Creates a holder whose slot map contains only opaque callback tokens.
     */
    public CuiInventoryHolder(UUID ownerId,
                              UUID sessionId,
                              MenuRoute route,
                              long menuRevision,
                              Map<Integer, Map<ChestClickType, String>> callbacks) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.route = Objects.requireNonNull(route, "route");
        if (menuRevision < 1) {
            throw new IllegalArgumentException("CUI menu revision must be positive");
        }
        this.menuRevision = menuRevision;
        this.callbacks = freezeCallbacks(callbacks);
    }

    /**
     * Binds the Bukkit Inventory exactly once after `createInventory` returns.
     */
    public synchronized void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("CUI holder Inventory is already bound");
        }
        Inventory bound = Objects.requireNonNull(inventory, "inventory");
        if (callbacks.keySet().stream().anyMatch(slot -> slot >= bound.getSize())) {
            throw new IllegalArgumentException("CUI callback slot exceeds Inventory size");
        }
        this.inventory = bound;
    }

    /**
     * Returns the bound Inventory required by Bukkit's holder contract.
     */
    @Override
    public synchronized @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("CUI holder Inventory has not been bound");
        }
        return inventory;
    }

    /**
     * Returns the player UUID allowed to execute this holder's callbacks.
     */
    public UUID ownerId() {
        return ownerId;
    }

    /**
     * Returns the unique lifecycle identity of this rendered Inventory.
     */
    public UUID sessionId() {
        return sessionId;
    }

    /**
     * Returns the trusted route captured when the Inventory was rendered.
     */
    public MenuRoute route() {
        return route;
    }

    /**
     * Returns the menu revision used to build all slot callbacks.
     */
    public long menuRevision() {
        return menuRevision;
    }

    /**
     * Resolves one server-owned token for the clicked slot and click type.
     */
    public String callbackToken(int slot, ChestClickType clickType) {
        return callbacks.getOrDefault(slot, Map.of()).get(clickType);
    }

    /**
     * Returns all tokens owned by this holder for precise close or transition cleanup.
     */
    public Set<String> callbackTokens() {
        return callbacks.values().stream()
                .flatMap(clicks -> clicks.values().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Validates slot bounds and freezes nested callback maps.
     */
    private Map<Integer, Map<ChestClickType, String>> freezeCallbacks(
            Map<Integer, Map<ChestClickType, String>> source) {
        Objects.requireNonNull(source, "callbacks");
        Map<Integer, Map<ChestClickType, String>> frozen = new LinkedHashMap<>();
        Set<String> usedTokens = new java.util.HashSet<>();
        for (Map.Entry<Integer, Map<ChestClickType, String>> entry : source.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= 54) {
                throw new IllegalArgumentException("CUI callback slot must be between 0 and 53");
            }
            Map<ChestClickType, String> clicks = new LinkedHashMap<>();
            for (Map.Entry<ChestClickType, String> click : entry.getValue().entrySet()) {
                String token = Objects.requireNonNull(click.getValue(), "callback token");
                if (!CALLBACK_TOKEN.matcher(token).matches()) {
                    throw new IllegalArgumentException("Invalid opaque CUI callback token");
                }
                if (!usedTokens.add(token)) {
                    throw new IllegalArgumentException("CUI callback tokens cannot be reused across slots or clicks");
                }
                clicks.put(Objects.requireNonNull(click.getKey(), "click type"), token);
            }
            frozen.put(slot, Map.copyOf(clicks));
        }
        return Map.copyOf(frozen);
    }
}
