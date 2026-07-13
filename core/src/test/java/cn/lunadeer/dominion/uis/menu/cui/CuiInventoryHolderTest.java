package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuiInventoryHolderTest {

    private static final String LEFT_TOKEN = "11111111111111111111111111111111";
    private static final String RIGHT_TOKEN = "22222222222222222222222222222222";

    @Test
    void freezesSlotCallbacksAndBindsInventoryOnce() {
        Map<ChestClickType, String> clickMap = new HashMap<>();
        clickMap.put(ChestClickType.LEFT, LEFT_TOKEN);
        CuiInventoryHolder holder = holder(Map.of(4, clickMap));
        clickMap.put(ChestClickType.RIGHT, RIGHT_TOKEN);
        Inventory inventory = inventory(9);

        holder.bindInventory(inventory);

        assertSame(inventory, holder.getInventory());
        assertEquals(LEFT_TOKEN, holder.callbackToken(4, ChestClickType.LEFT));
        assertNull(holder.callbackToken(4, ChestClickType.RIGHT));
        assertEquals(java.util.Set.of(LEFT_TOKEN), holder.callbackTokens());
        assertThrows(IllegalStateException.class, () -> holder.bindInventory(inventory));
    }

    @Test
    void rejectsInvalidTokensAndSlotsOutsideBoundInventory() {
        assertThrows(IllegalArgumentException.class, () -> holder(Map.of(
                0, Map.of(ChestClickType.LEFT, "not-a-token"))));
        CuiInventoryHolder holder = holder(Map.of(
                10, Map.of(ChestClickType.LEFT, LEFT_TOKEN)));
        assertThrows(IllegalArgumentException.class, () -> holder.bindInventory(inventory(9)));
        assertThrows(IllegalArgumentException.class, () -> holder(Map.of(
                0, Map.of(ChestClickType.LEFT, LEFT_TOKEN),
                1, Map.of(ChestClickType.RIGHT, LEFT_TOKEN))));
    }

    @Test
    void mapsOnlySupportedBukkitClickTypes() {
        assertEquals(ChestClickType.LEFT, CuiListener.toClickType(ClickType.LEFT));
        assertEquals(ChestClickType.SHIFT_RIGHT, CuiListener.toClickType(ClickType.SHIFT_RIGHT));
        assertEquals(ChestClickType.DROP, CuiListener.toClickType(ClickType.CONTROL_DROP));
        assertNull(CuiListener.toClickType(ClickType.NUMBER_KEY));
    }

    private CuiInventoryHolder holder(Map<Integer, Map<ChestClickType, String>> callbacks) {
        return new CuiInventoryHolder(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                MenuRoute.of("test"), 1, callbacks
        );
    }

    private Inventory inventory(int size) {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                (proxy, method, arguments) -> method.getName().equals("getSize") ? size : null
        );
    }
}
