package cn.lunadeer.dominion.uis.menu.data;

import java.util.concurrent.CompletionStage;

/**
 * Loads renderer-sized pages without exposing domain objects to UI code.
 */
@FunctionalInterface
public interface MenuDataProvider {

    /**
     * Loads one page for the supplied trusted menu context.
     */
    CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize);
}
