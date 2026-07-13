package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.doos.TemplateDOO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes templates owned by the current player without leaking database objects to renderers.
 */
public final class TemplateListProvider implements MenuDataProvider {

    /**
     * Loads and paginates the current player's template rows.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            List<MenuEntry> entries = TemplateDOO.selectAll(context.player().getUniqueId()).stream()
                    .map(template -> new MenuEntry(
                            Integer.toString(template.getId()),
                            Map.of("name", template.getName()),
                            Map.of("template.name", template.getName()),
                            Set.of("open-template", "delete-template")
                    ))
                    .toList();
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
