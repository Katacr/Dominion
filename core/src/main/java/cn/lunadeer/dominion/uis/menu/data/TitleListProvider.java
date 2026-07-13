package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.GroupDTO;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.cache.CacheManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes deduplicated group titles available to the current player.
 */
public final class TitleListProvider implements MenuDataProvider {

    /**
     * Merges membership and ownership titles while marking the active title.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            PlayerDTO player = CacheManager.instance.getPlayer(context.player().getUniqueId());
            if (player == null) throw new IllegalStateException("Player data not found in cache");
            Map<Integer, GroupDTO> groups = new LinkedHashMap<>();
            CacheManager.instance.getPlayerCache().getPlayerGroupTitleList(context.player().getUniqueId())
                    .forEach(group -> groups.put(group.getId(), group));
            CacheManager.instance.getPlayerOwnDominionDTOs(context.player().getUniqueId()).stream()
                    .map(DominionDTO::getGroups)
                    .flatMap(java.util.Collection::stream)
                    .forEach(group -> groups.put(group.getId(), group));
            var entries = groups.values().stream()
                    .map(group -> entry(group, player.getUsingGroupTitleID()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private MenuEntry entry(GroupDTO group, Integer activeId) {
        DominionDTO dominion = CacheManager.instance.getDominion(group.getDomID());
        if (dominion == null) return null;
        boolean active = group.getId().equals(activeId);
        return new MenuEntry(
                Integer.toString(group.getId()),
                Map.of(
                        "name", group.getNamePlain(),
                        "dominion", dominion.getName(),
                        "state", active ? "ACTIVE" : "INACTIVE"
                ),
                Map.of("title.id", active ? "-1" : Integer.toString(group.getId())),
                Set.of("toggle-title")
        );
    }
}
