package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.doos.TemplateDOO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toMemberDTO;

/**
 * Loads templates that the current player may apply to a route-bound dominion member.
 */
public final class TemplateSelectionProvider implements MenuDataProvider {

    /**
     * Revalidates the route target and exposes owner-only ADMIN templates as disabled entries.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            String dominionName = requiredRouteArgument(context, "dominion.name");
            String memberName = requiredRouteArgument(context, "member.name");
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            toMemberDTO(dominion, memberName);

            List<MenuEntry> entries = TemplateDOO.selectAll(context.player().getUniqueId()).stream()
                    .map(template -> new MenuEntry(
                            Integer.toString(template.getId()),
                            Map.of("name", template.getName()),
                            Map.of("template.name", template.getName()),
                            canApply(context, dominion, template) ? Set.of("apply-template") : Set.of()
                    ))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private boolean canApply(MenuContext context, DominionDTO dominion, TemplateDOO template) {
        return !template.getFlagValue(Flags.ADMIN)
                || dominion.getOwner().equals(context.player().getUniqueId());
    }

    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing route argument: " + key);
        }
        return value;
    }
}
