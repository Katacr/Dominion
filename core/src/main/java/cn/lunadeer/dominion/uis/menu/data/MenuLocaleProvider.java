package cn.lunadeer.dominion.uis.menu.data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes the menu locales discovered by the repository as trusted language choices.
 */
public final class MenuLocaleProvider implements MenuDataProvider {

    private final Supplier<Set<String>> locales;

    /**
     * Creates a provider backed by the repository's current atomic locale snapshot.
     */
    public MenuLocaleProvider(Supplier<Set<String>> locales) {
        this.locales = locales;
    }

    /**
     * Loads a sorted page of locale identifiers for all configured UI renderers.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        List<MenuEntry> entries = locales.get().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(locale -> new MenuEntry(
                        locale,
                        Map.of("name", locale),
                        Map.of("locale.id", locale),
                        Set.of("select-language")
                ))
                .toList();
        return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
    }
}
