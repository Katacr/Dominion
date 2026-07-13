package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.cache.server.ServerCache;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.utils.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes the complete local dominion tree to server administrators.
 */
public final class AllDominionProvider implements MenuDataProvider {

    /**
     * Revalidates administrator permission before flattening the local tree.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            requireAdministrator(context);
            ServerCache local = CacheManager.instance.getCache();
            List<MenuEntry> entries = new ArrayList<>();
            DominionTreeEntries.append(entries, local.getDominionCache().getAllDominionNodes(), local, 0,
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
}
