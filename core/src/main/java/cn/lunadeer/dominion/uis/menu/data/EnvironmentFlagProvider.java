package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.flag.EnvFlag;
import cn.lunadeer.dominion.api.dtos.flag.Flags;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Exposes enabled environment flags for one route-bound dominion.
 */
public final class EnvironmentFlagProvider implements MenuDataProvider {

    /**
     * Revalidates dominion access and captures each flag's next value as trusted callback state.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            String dominionName = context.route().arguments().get("dominion.name");
            if (dominionName == null || dominionName.isBlank()) {
                throw new IllegalArgumentException("Missing route argument: dominion.name");
            }
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            List<MenuEntry> entries = Flags.getAllEnvFlagsEnable().stream()
                    .map(flag -> entry(dominion, flag))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private MenuEntry entry(DominionDTO dominion, EnvFlag flag) {
        boolean currentValue = dominion.getEnvFlagValue(flag);
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
                Set.of("toggle-env-flag")
        );
    }
}
