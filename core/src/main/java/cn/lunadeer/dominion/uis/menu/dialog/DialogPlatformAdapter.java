package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackDispatcher;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Isolates high-version Dialog API types from the legacy-compatible core module.
 */
public interface DialogPlatformAdapter {

    /**
     * Registers platform callback listeners after capability detection succeeds.
     */
    void initialize(JavaPlugin plugin, UiCallbackDispatcher callbacks);

    /**
     * Renders and opens one validated Dialog route.
     */
    void open(Player player, DialogMenuDefinition menu, MenuRoute route, long revision,
              UiCallbackSessionManager sessions);

    /**
     * Handles a command-backed inline callback that may open a secondary prompt.
     */
    boolean dispatchInline(Player player, String token);

    /**
     * Closes the currently displayed native Dialog.
     */
    void close(Player player);

    /**
     * Releases adapter-owned callback state during plugin shutdown.
     */
    void shutdown();
}
