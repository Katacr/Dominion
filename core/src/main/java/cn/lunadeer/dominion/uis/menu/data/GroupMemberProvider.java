package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.GroupDTO;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toGroupDTO;

/**
 * Exposes members currently assigned to one dominion group.
 */
public final class GroupMemberProvider implements MenuDataProvider {

    /**
     * Revalidates group identity and paginates removable members.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            DominionDTO dominion = toDominionDTO(requiredRouteArgument(context, "dominion.name"));
            assertDominionAdmin(context.player(), dominion);
            GroupDTO group = toGroupDTO(dominion, requiredRouteArgument(context, "group.name"));
            var entries = group.getMembers().stream()
                    .map(member -> new MenuEntry(
                            Integer.toString(member.getId()),
                            Map.of("name", member.getPlayer().getLastKnownName()),
                            Map.of("member.name", member.getPlayer().getLastKnownName()),
                            Set.of("remove-group-member")
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
