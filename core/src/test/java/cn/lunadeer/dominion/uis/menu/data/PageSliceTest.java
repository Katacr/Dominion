package cn.lunadeer.dominion.uis.menu.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageSliceTest {

    @Test
    void clampsPageAfterEntriesAreRemoved() {
        List<MenuEntry> entries = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> entry(Integer.toString(index)))
                .toList();

        PageSlice page = PageSlice.paginate(entries, 3, 10);

        assertEquals(2, page.currentPage());
        assertEquals(2, page.totalPages());
        assertEquals(1, page.entries().size());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void representsEmptyResultsAsPageOneOfOne() {
        PageSlice page = PageSlice.paginate(List.of(), 5, 10);

        assertEquals(1, page.currentPage());
        assertEquals(1, page.totalPages());
        assertEquals(0, page.totalItems());
    }

    @Test
    void rejectsOversizedProviderValues() {
        assertThrows(IllegalArgumentException.class, () -> new MenuEntry(
                "entry", Map.of("name", "x".repeat(4097)), Map.of(), Set.of()));
    }

    private MenuEntry entry(String id) {
        return new MenuEntry(id, Map.of(), Map.of(), Set.of());
    }
}
