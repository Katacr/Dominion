package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.doos.PlayerDOO;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Exposes known players that may be added to one dominion.
 */
public final class PlayerSelectionProvider implements MenuDataProvider {

    /**
     * Filters the owner and existing members before paginating candidates.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            DominionDTO dominion = toDominionDTO(requiredRouteArgument(context, "dominion.name"));
            assertDominionAdmin(context.player(), dominion);
            Set<java.util.UUID> excluded = new java.util.HashSet<>();
            excluded.add(dominion.getOwner());
            dominion.getMembers().forEach(member -> excluded.add(member.getPlayerUUID()));
            var entries = PlayerDOO.all().stream()
                    .filter(player -> !excluded.contains(player.getUuid()))
                    .map(player -> new MenuEntry(
                            player.getUuid().toString(),
                            Map.of("name", player.getLastKnownName()),
                            Map.of("player.name", player.getLastKnownName()),
                            Set.of("add-member")
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
