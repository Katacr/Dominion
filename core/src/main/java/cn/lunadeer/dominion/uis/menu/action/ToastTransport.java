package cn.lunadeer.dominion.uis.menu.action;

import org.bukkit.entity.Player;

/**
 * Sends one already validated toast through a platform-specific transport.
 */
public interface ToastTransport {

    /**
     * Sends a toast without registering persistent server advancement data.
     */
    void send(Player player, String itemId, String titleJson, String frame);
}
