package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.session.UiCallbackDispatcher;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

/**
 * Transports chest slot clicks to the shared callback dispatcher without domain knowledge.
 */
public final class CuiListener implements Listener {

    private final UiCallbackSessionManager sessions;
    private final UiCallbackDispatcher callbacks;

    /**
     * Creates an Inventory listener backed by shared callback lifecycle services.
     */
    public CuiListener(UiCallbackSessionManager sessions, UiCallbackDispatcher callbacks) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    /**
     * Cancels chest interaction and dispatches a validated top-slot callback once.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CuiInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.ownerId().equals(player.getUniqueId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        ChestClickType clickType = toClickType(event.getClick());
        if (clickType == null) {
            return;
        }
        String token = holder.callbackToken(slot, clickType);
        if (token == null) {
            return;
        }
        if (callbacks.dispatch(player, token)) {
            sessions.invalidateTokens(holder.callbackTokens());
        }
    }

    /**
     * Invalidates only callbacks belonging to the Inventory that actually closed.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CuiInventoryHolder holder) {
            sessions.invalidateTokens(holder.callbackTokens());
        }
    }

    /**
     * Prevents drag operations from inserting or extracting items while a CUI is open.
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CuiInventoryHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Maps Bukkit click variants to the bounded chest configuration vocabulary.
     */
    static ChestClickType toClickType(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> ChestClickType.LEFT;
            case RIGHT -> ChestClickType.RIGHT;
            case SHIFT_LEFT -> ChestClickType.SHIFT_LEFT;
            case SHIFT_RIGHT -> ChestClickType.SHIFT_RIGHT;
            case MIDDLE -> ChestClickType.MIDDLE;
            case DROP, CONTROL_DROP -> ChestClickType.DROP;
            default -> null;
        };
    }
}
