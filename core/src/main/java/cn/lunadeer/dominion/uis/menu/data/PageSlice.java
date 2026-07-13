package cn.lunadeer.dominion.uis.menu.data;

import java.util.List;

/**
 * Stores one clamped page of immutable menu entries and pagination metadata.
 */
public record PageSlice(List<MenuEntry> entries, int currentPage, int totalPages, int totalItems) {

    /**
     * Validates provider pagination metadata and freezes the entry list.
     */
    public PageSlice {
        entries = List.copyOf(entries);
        if (currentPage < 1 || totalPages < 1 || currentPage > totalPages || totalItems < 0) {
            throw new IllegalArgumentException("Invalid menu page metadata");
        }
    }

    /**
     * Paginates an in-memory provider result and clamps an out-of-range page.
     */
    public static PageSlice paginate(List<MenuEntry> entries, int requestedPage, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Menu page size must be positive");
        }
        int totalItems = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        int currentPage = Math.min(Math.max(1, requestedPage), totalPages);
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        return new PageSlice(entries.subList(fromIndex, toIndex), currentPage, totalPages, totalItems);
    }

    /**
     * Returns whether a previous page exists.
     */
    public boolean hasPrevious() {
        return currentPage > 1;
    }

    /**
     * Returns whether a next page exists.
     */
    public boolean hasNext() {
        return currentPage < totalPages;
    }
}
