package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Supplies player and route identity to a menu data provider.
 */
public record MenuContext(Player player, MenuRoute route) {

    /**
     * Rejects incomplete provider contexts.
     */
    public MenuContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(route, "route");
    }
}
