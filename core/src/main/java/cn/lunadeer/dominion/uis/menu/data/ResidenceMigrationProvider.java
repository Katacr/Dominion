package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.configuration.Configuration;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.utils.ResMigration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Exposes Residence roots as migration targets and children as informational rows.
 */
public final class ResidenceMigrationProvider implements MenuDataProvider {

    /**
     * Loads administrator or player-owned Residence data using the existing cache boundary.
     */
    @Override
    public CompletionStage<PageSlice> load(MenuContext context, int page, int pageSize) {
        try {
            if (!Configuration.residenceMigration) {
                throw new IllegalStateException(TextUserInterface.migrateListTuiText.notEnabled);
            }
            List<ResMigration.ResidenceNode> roots = context.player().hasPermission(Dominion.adminPermission)
                    ? CacheManager.instance.getResidenceCache().getResidenceData()
                    : CacheManager.instance.getResidenceCache().getResidenceData(context.player().getUniqueId());
            List<MenuEntry> entries = new ArrayList<>();
            if (roots != null) {
                append(entries, roots, 0);
            }
            return CompletableFuture.completedFuture(PageSlice.paginate(entries, page, pageSize));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Flattens Residence data while allowing migration only from root entries.
     */
    private void append(List<MenuEntry> entries, List<ResMigration.ResidenceNode> nodes, int depth) {
        for (ResMigration.ResidenceNode node : nodes) {
            entries.add(new MenuEntry(
                    depth + ":" + node.name,
                    Map.of(
                            "name", node.name,
                            "prefix", " | ".repeat(Math.max(0, depth)),
                            "owner", node.ownerName
                    ),
                    depth == 0 ? Map.of("residence.name", node.name) : Map.of(),
                    depth == 0 ? Set.of("migrate-residence") : Set.of()
            ));
            if (node.children != null) {
                append(entries, node.children, depth + 1);
            }
        }
    }
}
