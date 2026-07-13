package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.GroupDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toGroupDTO;

/**
 * Exposes editable flags for one route-bound dominion group.
 */
public final class GroupFlagProvider implements MenuDataProvider {

    /**
     * Preserves the legacy rule that administrator groups only expose ADMIN.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            DominionDTO dominion = toDominionDTO(requiredRouteArgument(context, "dominion.name"));
            assertDominionAdmin(context.player(), dominion);
            GroupDTO group = toGroupDTO(dominion, requiredRouteArgument(context, "group.name"));
            List<PriFlag> flags = group.getFlagValue(Flags.ADMIN)
                    ? List.of(Flags.ADMIN)
                    : Flags.getAllPriFlagsEnable();
            List<MenuEntry> entries = flags.stream().map(flag -> entry(group, flag)).toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Separates group flag display values from trusted toggle arguments.
     */
    private MenuEntry entry(GroupDTO group, PriFlag flag) {
        boolean currentValue = group.getFlagValue(flag);
        return new MenuEntry(
                flag.getFlagName(),
                Map.of(
                        "name", flag.getDisplayName(),
                        "description", flag.getDescription(),
                        "state", currentValue ? "ON" : "OFF",
                        "material", flag.getMaterial().name()
                ),
                Map.of(
                        "flag.name", flag.getFlagName(),
                        "flag.next-value", Boolean.toString(!currentValue)
                ),
                Set.of("toggle-group-flag")
        );
    }

    /**
     * Reads one required server-owned route argument.
     */
    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing route argument: " + key);
        }
        return value;
    }
}
