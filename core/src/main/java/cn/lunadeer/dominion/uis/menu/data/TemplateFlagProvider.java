package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.doos.TemplateDOO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes enabled privilege flags for one route-bound player template.
 */
public final class TemplateFlagProvider implements MenuDataProvider {

    /**
     * Revalidates template ownership and captures each flag's next value as trusted callback state.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            String templateName = requiredRouteArgument(context, "template.name");
            TemplateDOO template = TemplateDOO.select(context.player().getUniqueId(), templateName);
            if (template == null) {
                throw new IllegalStateException("Template not found: " + templateName);
            }
            List<MenuEntry> entries = Flags.getAllPriFlagsEnable().stream()
                    .map(flag -> entry(template, flag))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Converts one template flag into display state and trusted toggle arguments.
     */
    private MenuEntry entry(TemplateDOO template, PriFlag flag) {
        boolean currentValue = template.getFlagValue(flag);
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
                Set.of("toggle-template-flag")
        );
    }

    /**
     * Reads a required server-owned route argument before storage access.
     */
    private String requiredRouteArgument(MenuContext context, String key) {
        String value = context.route().arguments().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing route argument: " + key);
        }
        return value;
    }
}
