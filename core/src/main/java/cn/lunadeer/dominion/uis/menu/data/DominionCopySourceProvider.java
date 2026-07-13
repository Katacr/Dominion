package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.uis.dominion.copy.DominionCopy;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Exposes player-owned source dominions for one validated copy target and type.
 */
public final class DominionCopySourceProvider implements MenuDataProvider {

    /**
     * Filters the target dominion and captures source names as trusted callback state.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            DominionDTO target = toDominionDTO(requiredRouteArgument(context, "dominion.name"));
            assertDominionAdmin(context.player(), target);
            DominionCopy.CopyType.valueOf(requiredRouteArgument(context, "copy.type"));
            var entries = CacheManager.instance.getPlayerOwnDominionDTOs(context.player().getUniqueId()).stream()
                    .filter(source -> !source.getId().equals(target.getId()))
                    .map(source -> new MenuEntry(
                            Integer.toString(source.getId()),
                            Map.of("name", source.getName()),
                            Map.of("source.name", source.getName()),
                            Set.of("copy")
                    ))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing route argument: " + key);
        return value;
    }
}
