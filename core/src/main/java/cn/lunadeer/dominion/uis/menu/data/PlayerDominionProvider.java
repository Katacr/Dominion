package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.cache.server.ServerCache;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.misc.Converts;
import cn.lunadeer.dominion.utils.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes one player's local dominion tree to server administrators.
 */
public final class PlayerDominionProvider implements MenuDataProvider {

    /**
     * Resolves the route player again before flattening that player's current tree.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            requireAdministrator(context);
            PlayerDTO player = Converts.toPlayerDTO(requiredRouteArgument(context, "player.name"));
            ServerCache local = CacheManager.instance.getCache();
            List<MenuEntry> entries = new ArrayList<>();
            DominionTreeEntries.append(entries,
                    local.getDominionCache().getPlayerDominionNodes(player.getUuid()), local, 0,
                    Set.of("delete-dominion", "manage-dominion", "teleport-dominion"));
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Rejects direct provider access without the administrator permission.
     */
    private void requireAdministrator(MenuContext context) {
        if (!context.player().hasPermission(Dominion.adminPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.adminPermission));
        }
    }

    /**
     * Reads a required server-owned route argument.
     */
    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing route argument: " + key);
        }
        return value;
    }
}
