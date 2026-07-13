package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toMemberDTO;

/**
 * Exposes editable privilege flags for one route-bound dominion member.
 */
public final class MemberFlagProvider implements MenuDataProvider {

    /**
     * Revalidates member access and limits administrator members to the ADMIN flag.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            String dominionName = requiredRouteArgument(context, "dominion.name");
            String memberName = requiredRouteArgument(context, "member.name");
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            MemberDTO member = toMemberDTO(dominion, memberName);
            List<PriFlag> flags = member.getFlagValue(Flags.ADMIN)
                    ? List.of(Flags.ADMIN)
                    : Flags.getAllPriFlagsEnable();
            List<MenuEntry> entries = flags.stream()
                    .map(flag -> entry(member, flag))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Converts one member flag into display state and trusted toggle arguments.
     */
    private MenuEntry entry(MemberDTO member, PriFlag flag) {
        boolean currentValue = member.getFlagValue(flag);
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
                Set.of("toggle-member-flag")
        );
    }

    /**
     * Reads a required server-owned route argument before domain validation.
     */
    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing route argument: " + key);
        }
        return value;
    }
}
