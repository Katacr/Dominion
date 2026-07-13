package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.data.MenuContext;
import cn.lunadeer.dominion.uis.menu.data.MenuDataProvider;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.MenuEntry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.uis.menu.session.StaleMenuRevisionException;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.ColorParser;
import cn.lunadeer.dominion.utils.LegacyToMiniMessage;
import cn.lunadeer.dominion.utils.Misc;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.lunadeer.dominion.managers.HooksManager.setPlaceholder;

/**
 * Renders validated chest definitions without embedding Dominion business behavior in Inventory items.
 */
public final class CuiRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final UiCallbackSessionManager sessions;
    private final MenuDataRegistry dataRegistry;

    /**
     * Creates a renderer backed by shared opaque callback sessions.
     */
    public CuiRenderer(UiCallbackSessionManager sessions, MenuDataRegistry dataRegistry) {
        this.sessions = sessions;
        this.dataRegistry = dataRegistry;
    }

    /**
     * Loads an optional dynamic page and opens the chest menu on the player's entity scheduler.
     */
    public CompletionStage<Void> show(Player player,
                                      ChestMenuDefinition menu,
                                      MenuRoute route,
                                      long menuRevision,
                                      BooleanSupplier revisionActive) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        sessions.beginSession(player);
        CompletionStage<PageSlice> pageStage = loadPage(player, menu, route);
        pageStage.whenComplete((page, throwable) -> {
            if (throwable != null) {
                completion.completeExceptionally(throwable);
                return;
            }
            if (page == null) {
                completion.completeExceptionally(new IllegalStateException("Menu data provider returned no page"));
                return;
            }
            if (page.entries().size() > Math.max(1, menu.dynamicPageSize())) {
                completion.completeExceptionally(new IllegalStateException(
                        "Menu data provider returned more entries than the configured CUI page size"));
                return;
            }
            try {
                Scheduler.runEntityTask(() -> {
                    try {
                        if (!revisionActive.getAsBoolean() || !player.isOnline()) {
                            completion.complete(null);
                            return;
                        }
                        MenuRoute normalizedRoute = route.withPage(page.currentPage());
                        render(player, menu, normalizedRoute, menuRevision, page);
                        completion.complete(null);
                    } catch (Exception exception) {
                        completion.completeExceptionally(exception);
                    }
                }, player);
            } catch (Exception exception) {
                completion.completeExceptionally(exception);
            }
        });
        return completion;
    }

    private CompletionStage<PageSlice> loadPage(Player player,
                                                ChestMenuDefinition menu,
                                                MenuRoute route) {
        Character dynamicSymbol = menu.dynamicSymbol();
        if (dynamicSymbol == null) {
            return CompletableFuture.completedFuture(PageSlice.paginate(List.of(), route.page(), 1));
        }
        ChestButtonDefinition dynamicButton = menu.buttons().get(dynamicSymbol);
        String providerId = menu.shared().dataSources().get(dynamicButton.source());
        MenuDataProvider provider = dataRegistry.resolve(providerId);
        if (provider == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Menu data provider is no longer registered: " + providerId));
        }
        CompletionStage<PageSlice> stage = provider.load(
                new MenuContext(player, route), route.page(), menu.dynamicPageSize());
        return stage == null
                ? CompletableFuture.failedFuture(
                new IllegalStateException("Menu data provider returned no future: " + providerId))
                : stage;
    }

    private void render(Player player,
                        ChestMenuDefinition menu,
                        MenuRoute route,
                        long menuRevision,
                        PageSlice page) {
        Map<String, String> variables = createVariables(player, menu, route, page);
        Map<Integer, Map<ChestClickType, String>> callbacks = new LinkedHashMap<>();
        try {
            List<ItemStack> items = new ArrayList<>(menu.layout().size() * 9);
            java.util.Iterator<MenuEntry> entries = page.entries().iterator();
            boolean emptyDisplayRendered = false;
            int slot = 0;
            for (String row : menu.layout()) {
                for (char symbol : row.toCharArray()) {
                    ChestButtonDefinition button = menu.buttons().get(symbol);
                    MenuEntry menuEntry = button != null && button.dynamic() && entries.hasNext()
                            ? entries.next()
                            : null;
                    java.util.Set<String> availableActionGroups = menuEntry == null
                            ? null
                            : menuEntry.availableActionGroups();
                    if (button != null && button.dynamic() && menuEntry == null
                            && !emptyDisplayRendered && page.totalItems() == 0 && button.emptyDisplay() != null) {
                        items.add(buildItem(player, button.emptyDisplay(), variables));
                        emptyDisplayRendered = true;
                        slot++;
                        continue;
                    }
                    if (button == null
                            || (button.dynamic() && menuEntry == null)
                            || (button.hiddenWhenDisabled()
                            && !buttonAvailable(player, button, availableActionGroups, page))) {
                        items.add(null);
                        slot++;
                        continue;
                    }
                    Map<String, String> itemVariables = entryVariables(variables, menuEntry);
                    items.add(buildItem(player, button.display(), itemVariables));
                    Map<ChestClickType, String> slotCallbacks = registerCallbacks(
                            player, menu, route, menuRevision, itemVariables, button,
                            menuEntry == null ? Map.of() : menuEntry.trustedArguments(),
                            availableActionGroups, page);
                    if (!slotCallbacks.isEmpty()) {
                        callbacks.put(slot, slotCallbacks);
                    }
                    slot++;
                }
            }

            CuiInventoryHolder holder = new CuiInventoryHolder(
                    player.getUniqueId(), UUID.randomUUID(), route, menuRevision, callbacks);
            Inventory inventory = Bukkit.createInventory(holder, items.size(), format(player, menu.title(), variables));
            holder.bindInventory(inventory);
            for (int index = 0; index < items.size(); index++) {
                inventory.setItem(index, items.get(index));
            }
            player.openInventory(inventory);
        } catch (RuntimeException exception) {
            sessions.invalidateTokens(callbacks.values().stream()
                    .flatMap(clicks -> clicks.values().stream())
                    .toList());
            throw exception;
        }
    }

    private Map<ChestClickType, String> registerCallbacks(Player player,
                                                          ChestMenuDefinition menu,
                                                          MenuRoute route,
                                                          long menuRevision,
                                                          Map<String, String> variables,
                                                          ChestButtonDefinition button,
                                                          Map<String, String> trustedArguments,
                                                          java.util.Set<String> availableActionGroups,
                                                          PageSlice page) {
        if (!button.permission().isBlank() && !player.hasPermission(button.permission())) {
            return Map.of();
        }
        if (!capabilityAvailable(button)) {
            return Map.of();
        }
        Map<ChestClickType, String> callbacks = new LinkedHashMap<>();
        for (Map.Entry<ChestClickType, ChestClickDefinition> entry : button.clicks().entrySet()) {
            ChestClickDefinition click = entry.getValue();
            if ((availableActionGroups != null && !availableActionGroups.contains(click.actionId()))
                    || !pageActionAvailable(click.actions(), page)) {
                continue;
            }
            String token = sessions.registerActionsToken(
                    player,
                    route,
                    menuRevision,
                    click.actionId(),
                    click.actions(),
                    menu.shared().actionGroups(),
                    variables,
                    trustedArguments,
                    UiSurface.CUI
            );
            callbacks.put(entry.getKey(), token);
        }
        return Map.copyOf(callbacks);
    }

    private Map<String, String> createVariables(Player player,
                                                ChestMenuDefinition menu,
                                                MenuRoute route,
                                                PageSlice page) {
        Map<String, String> variables = new LinkedHashMap<>(menu.variables());
        variables.put("player.name", player.getName());
        variables.put("menu.id", menu.menuId());
        variables.put("page.current", Integer.toString(page.currentPage()));
        variables.put("page.total", Integer.toString(page.totalPages()));
        variables.put("page.items", Integer.toString(page.totalItems()));
        route.arguments().forEach((key, value) -> variables.put("route." + key, value));
        return Map.copyOf(variables);
    }

    private Map<String, String> entryVariables(Map<String, String> base, MenuEntry entry) {
        if (entry == null) {
            return base;
        }
        Map<String, String> variables = new LinkedHashMap<>(base);
        variables.put("entry.id", entry.id());
        entry.displayVariables().forEach((key, value) -> variables.put("entry." + key, value));
        return Map.copyOf(variables);
    }

    private boolean pageActionAvailable(List<ActionSpec> actions, PageSlice page) {
        for (ActionSpec action : actions) {
            if (!action.type().equals("page")) {
                continue;
            }
            if (action.argument().equalsIgnoreCase("previous")) {
                return page.hasPrevious();
            }
            if (action.argument().equalsIgnoreCase("next")) {
                return page.hasNext();
            }
        }
        return true;
    }

    private boolean buttonAvailable(Player player,
                                    ChestButtonDefinition button,
                                    java.util.Set<String> availableActionGroups,
                                    PageSlice page) {
        if (!button.permission().isBlank() && !player.hasPermission(button.permission())) {
            return false;
        }
        if (!capabilityAvailable(button)) {
            return false;
        }
        if (button.clicks().isEmpty()) {
            return true;
        }
        return button.clicks().values().stream().anyMatch(click ->
                (availableActionGroups == null || availableActionGroups.contains(click.actionId()))
                        && pageActionAvailable(click.actions(), page));
    }

    private boolean capabilityAvailable(ChestButtonDefinition button) {
        return button.capability().isBlank()
                || CuiCapability.fromConfigKey(button.capability()).enabled();
    }

    private ItemStack buildItem(Player player,
                                ChestDisplayDefinition display,
                                Map<String, String> variables) {
        String materialName = resolveVariables(display.material(), variables).toUpperCase(java.util.Locale.ROOT);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            throw new IllegalStateException("Unknown dynamic CUI material: " + materialName);
        }
        ItemStack item = new ItemStack(material, display.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("ItemMeta is unavailable for configured CUI material " + display.material());
        }
        String name = format(player, display.name(), variables);
        List<String> lore = display.lore().stream()
                .map(line -> format(player, line, variables))
                .toList();
        if (Misc.isPaper()) {
            meta.displayName(component(name));
            meta.lore(lore.stream().map(this::component).toList());
        } else {
            meta.setDisplayName(name);
            meta.setLore(lore);
        }
        if (display.customData() != null) {
            meta.setCustomModelData(display.customData());
        }
        CuiItemModelAdapter.apply(meta, display.itemModel());
        if (display.glow()) {
            Enchantment glow = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
            if (glow == null) {
                throw new IllegalStateException("Bukkit enchantment registry has no luck_of_the_sea entry");
            }
            meta.addEnchant(glow, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component component(String value) {
        return LegacyToMiniMessage.parse(value).decoration(TextDecoration.ITALIC, false);
    }

    private String format(Player player, String value, Map<String, String> variables) {
        return ColorParser.getBukkitType(setPlaceholder(player, resolveVariables(value, variables)));
    }

    /**
     * Resolves known namespaced display variables while preserving unknown placeholders literally.
     */
    static String resolveVariables(String value, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String replacement = variables.get(matcher.group(1));
            matcher.appendReplacement(resolved,
                    Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
