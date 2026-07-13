package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.doos.GroupDOO;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Exposes groups for the configured two-level group management flow.
 */
public final class GroupListProvider implements MenuDataProvider {

    /**
     * Loads current groups directly from storage after access validation.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            DominionDTO dominion = toDominionDTO(requiredRouteArgument(context, "dominion.name"));
            assertDominionAdmin(context.player(), dominion);
            var entries = GroupDOO.selectByDominionId(dominion.getId()).stream()
                    .map(group -> new MenuEntry(
                            Integer.toString(group.getId()),
                            Map.of(
                                    "name", group.getNamePlain(),
                                    "members", Integer.toString(group.getMembers().size())
                            ),
                            Map.of("group.name", group.getNamePlain()),
                            Set.of("open-group", "delete-group")
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
