package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.GroupDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.cache.CacheManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Exposes members and operation availability for one dominion.
 */
public final class MemberListProvider implements MenuDataProvider {

    /**
     * Revalidates administrator access before building member entries.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            DominionDTO dominion = toDominionDTO(requiredRouteArgument(context, "dominion.name"));
            assertDominionAdmin(context.player(), dominion);
            boolean owner = dominion.getOwner().equals(context.player().getUniqueId());
            List<MenuEntry> entries = dominion.getMembers().stream()
                    .map(member -> entry(member, owner))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Applies legacy owner, ADMIN, and group restrictions to one member row.
     */
    private MenuEntry entry(MemberDTO member, boolean owner) {
        GroupDTO group = CacheManager.instance.getGroup(member.getGroupId());
        boolean admin = member.getFlagValue(Flags.ADMIN);
        String tag = group != null ? "[G]" : admin ? "[A]" : member.getFlagValue(Flags.MOVE) ? "[N]" : "[B]";
        Set<String> actionGroups = new LinkedHashSet<>();
        if (owner || !admin) {
            actionGroups.add("remove-member");
            if (group == null) {
                actionGroups.add("open-member-flags");
            }
        }
        String playerName = member.getPlayer().getLastKnownName();
        return new MenuEntry(
                Integer.toString(member.getId()),
                Map.of(
                        "name", playerName,
                        "tag", tag,
                        "group", group == null ? "" : group.getNamePlain()
                ),
                Map.of("member.name", playerName),
                actionGroups
        );
    }

    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing route argument: " + key);
        return value;
    }
}
