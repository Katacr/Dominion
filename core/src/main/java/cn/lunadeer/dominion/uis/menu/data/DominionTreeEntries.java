package cn.lunadeer.dominion.uis.menu.data;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.cache.DominionNode;
import cn.lunadeer.dominion.cache.server.ServerCache;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flattens cached dominion trees into trusted configured-menu entries.
 */
final class DominionTreeEntries {

    /**
     * Prevents instantiation of the tree entry utility.
     */
    private DominionTreeEntries() {
    }

    /**
     * Appends a depth-first tree while preserving the legacy text indentation.
     */
    static void append(List<MenuEntry> entries,
                       List<DominionNode> nodes,
                       ServerCache serverCache,
                       int depth,
                       Set<String> actionGroups) {
        for (DominionNode node : nodes) {
            DominionDTO dominion = serverCache.getDominionCache().getDominion(node.getDominionId());
            if (dominion == null) {
                continue;
            }
            entries.add(entry(dominion, depth, actionGroups));
            append(entries, node.getChildren(), serverCache, depth + 1, actionGroups);
        }
    }

    /**
     * Creates one entry whose callback identity is resolved from server and dominion IDs.
     */
    static MenuEntry entry(DominionDTO dominion, int depth, Set<String> actionGroups) {
        return new MenuEntry(
                dominion.getServerId() + ":" + dominion.getId(),
                Map.of(
                        "name", dominion.getName(),
                        "prefix", " | ".repeat(Math.max(0, depth)),
                        "owner", dominion.getOwnerDTO().getLastKnownName()
                ),
                Map.of(
                        "server.id", Integer.toString(dominion.getServerId()),
                        "dominion.id", Integer.toString(dominion.getId())
                ),
                actionGroups
        );
    }

    /**
     * Creates a non-interactive section row for mixed dominion lists.
     */
    static MenuEntry section(String id, String text) {
        return new MenuEntry(id, Map.of("name", text, "prefix", ""), Map.of(), Set.of());
    }
}
