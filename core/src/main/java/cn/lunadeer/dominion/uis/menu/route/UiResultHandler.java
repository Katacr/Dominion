package cn.lunadeer.dominion.uis.menu.route;

import cn.lunadeer.dominion.uis.menu.action.ActionResult;
import org.bukkit.entity.Player;

/**
 * Applies a terminal action result using one renderer-specific navigation policy.
 */
@FunctionalInterface
public interface UiResultHandler {

    /**
     * Handles an action result after execution returns to the player's scheduler.
     */
    void handle(Player player, MenuRoute currentRoute, ActionResult result, Throwable throwable);
}
