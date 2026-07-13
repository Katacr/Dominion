package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Exposes enabled non-administrator guest flags for one dominion.
 */
public final class GuestFlagProvider implements MenuDataProvider {

    /**
     * Revalidates dominion access and paginates guest flag toggle state.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            String dominionName = requiredRouteArgument(context, "dominion.name");
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            List<MenuEntry> entries = Flags.getAllPriFlagsEnable().stream()
                    .filter(flag -> !flag.equals(Flags.ADMIN))
                    .map(flag -> entry(dominion, flag))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Separates guest flag display values from trusted toggle arguments.
     */
    private MenuEntry entry(DominionDTO dominion, PriFlag flag) {
        boolean currentValue = dominion.getGuestFlagValue(flag);
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
                Set.of("toggle-guest-flag")
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
