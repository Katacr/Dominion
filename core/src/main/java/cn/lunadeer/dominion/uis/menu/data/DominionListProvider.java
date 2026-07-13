package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.cache.server.ServerCache;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.utils.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes local managed dominions and remote teleport targets in legacy TUI order.
 */
public final class DominionListProvider implements MenuDataProvider {

    /**
     * Builds one flat page from the local tree, local administrator rows, and remote rows.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            List<MenuEntry> entries = new ArrayList<>();
            ServerCache local = CacheManager.instance.getCache();
            DominionTreeEntries.append(entries,
                    local.getDominionCache().getPlayerDominionNodes(context.player().getUniqueId()),
                    local, 0, Set.of("delete-dominion", "manage-dominion", "teleport-dominion"));

            List<DominionDTO> administered = local.getDominionCache()
                    .getPlayerAdminDominionDTOs(context.player().getUniqueId());
            if (!administered.isEmpty()) {
                entries.add(DominionTreeEntries.section("section:admin",
                        TextUserInterface.dominionListTuiText.adminSection));
                administered.forEach(dominion -> entries.add(DominionTreeEntries.entry(
                        dominion, 0, Set.of("manage-dominion"))));
            }

            for (ServerCache remote : CacheManager.instance.getOtherServerCaches().values()) {
                List<DominionDTO> remoteDominions = new ArrayList<>();
                remoteDominions.addAll(remote.getDominionCache()
                        .getPlayerOwnDominionDTOs(context.player().getUniqueId()));
                remoteDominions.addAll(remote.getDominionCache()
                        .getPlayerAdminDominionDTOs(context.player().getUniqueId()));
                if (remoteDominions.isEmpty()) {
                    continue;
                }
                entries.add(DominionTreeEntries.section("section:server:" + remote.getServerId(),
                        Misc.formatString(TextUserInterface.dominionListTuiText.serverSection,
                                remote.getServerId())));
                remoteDominions.forEach(dominion -> entries.add(DominionTreeEntries.entry(
                        dominion, 0, Set.of("teleport-dominion"))));
            }
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
