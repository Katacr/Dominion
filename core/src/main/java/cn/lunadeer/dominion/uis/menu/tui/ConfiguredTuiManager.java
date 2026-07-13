package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.configuration.Configuration;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.menu.PlayerMenuOnboarding;
import cn.lunadeer.dominion.uis.menu.action.ActionContext;
import cn.lunadeer.dominion.uis.menu.action.ActionExecutor;
import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;
import cn.lunadeer.dominion.uis.menu.action.BuiltInActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.DominionActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ToastSender;
import cn.lunadeer.dominion.uis.menu.action.TemplateMenuActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.MemberTemplateActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.MemberFlagActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.PrivilegeFlagActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.MembershipMenuActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.UtilityMenuActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.MainManagementActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.EnvironmentFlagActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.DominionTreeActionRegistrar;
import cn.lunadeer.dominion.uis.menu.data.AllDominionProvider;
import cn.lunadeer.dominion.uis.menu.data.DominionListProvider;
import cn.lunadeer.dominion.uis.menu.data.EnvironmentFlagProvider;
import cn.lunadeer.dominion.uis.menu.data.MemberFlagProvider;
import cn.lunadeer.dominion.uis.menu.data.GuestFlagProvider;
import cn.lunadeer.dominion.uis.menu.data.GroupFlagProvider;
import cn.lunadeer.dominion.uis.menu.data.GroupListProvider;
import cn.lunadeer.dominion.uis.menu.data.GroupMemberProvider;
import cn.lunadeer.dominion.uis.menu.data.MemberListProvider;
import cn.lunadeer.dominion.uis.menu.data.MenuLocaleProvider;
import cn.lunadeer.dominion.uis.menu.data.PlayerSelectionProvider;
import cn.lunadeer.dominion.uis.menu.data.UngroupedMemberProvider;
import cn.lunadeer.dominion.uis.menu.data.DominionCopySourceProvider;
import cn.lunadeer.dominion.uis.menu.data.TitleListProvider;
import cn.lunadeer.dominion.uis.menu.data.PlayerDominionProvider;
import cn.lunadeer.dominion.uis.menu.data.ResidenceMigrationProvider;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.TemplateFlagProvider;
import cn.lunadeer.dominion.uis.menu.data.TemplateListProvider;
import cn.lunadeer.dominion.uis.menu.data.TemplateSelectionProvider;
import cn.lunadeer.dominion.uis.menu.cui.CuiInventoryHolder;
import cn.lunadeer.dominion.uis.menu.cui.CuiListener;
import cn.lunadeer.dominion.uis.menu.cui.CuiRenderer;
import cn.lunadeer.dominion.uis.menu.cui.CuiRouter;
import cn.lunadeer.dominion.uis.menu.input.ChatInputWorkflow;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuResolver;
import cn.lunadeer.dominion.uis.menu.dialog.DialogPlatformAdapter;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiRouter;
import cn.lunadeer.dominion.uis.menu.route.UiResultRouter;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackDispatcher;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.Misc;
import cn.lunadeer.dominion.utils.XLogger;
import cn.lunadeer.dominion.utils.command.Argument;
import cn.lunadeer.dominion.utils.command.SecondaryCommand;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the first configured TUI runtime, commands, actions, and callback sessions.
 */
public final class ConfiguredTuiManager {

    private static ConfiguredTuiManager instance;

    private final TuiMenuRepository repository;
    private final UiCallbackSessionManager sessions;
    private final ActionExecutor executor;
    private final UiRouter router;
    private final CuiRouter cuiRouter;
    private final UiResultRouter results;
    private final UiCallbackDispatcher callbacks;
    private final DialogPlatformAdapter dialogAdapter;
    private final DialogMenuResolver dialogResolver;

    /**
     * Initializes the configured TUI slice after the legacy command manager exists.
     */
    public ConfiguredTuiManager(JavaPlugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("ConfiguredTuiManager is already initialized");
        }

        ActionRegistry actionRegistry = new ActionRegistry();
        DominionActionRegistry dominionActions = new DominionActionRegistry();
        new BuiltInActionRegistrar(new ToastSender(plugin)).registerInto(actionRegistry);
        actionRegistry.register("dominion", dominionActions, dominionActions::validate);

        sessions = new UiCallbackSessionManager();
        results = new UiResultRouter();
        registerDominionActions(dominionActions);

        MenuDataRegistry dataRegistry = new MenuDataRegistry();
        dataRegistry.register("templates.owned", new TemplateListProvider());
        dataRegistry.register("templates.member-apply-candidates", new TemplateSelectionProvider());
        dataRegistry.register("flags.environment", new EnvironmentFlagProvider());
        dataRegistry.register("flags.template", new TemplateFlagProvider());
        dataRegistry.register("flags.member", new MemberFlagProvider());
        dataRegistry.register("flags.guest", new GuestFlagProvider());
        dataRegistry.register("flags.group", new GroupFlagProvider());
        dataRegistry.register("members.by-dominion", new MemberListProvider());
        dataRegistry.register("players.member-candidates", new PlayerSelectionProvider());
        dataRegistry.register("groups.by-dominion", new GroupListProvider());
        dataRegistry.register("members.by-group", new GroupMemberProvider());
        dataRegistry.register("members.ungrouped", new UngroupedMemberProvider());
        dataRegistry.register("dominions.copy-sources", new DominionCopySourceProvider());
        dataRegistry.register("titles.available", new TitleListProvider());
        dataRegistry.register("dominions.managed", new DominionListProvider());
        dataRegistry.register("dominions.all", new AllDominionProvider());
        dataRegistry.register("dominions.by-player", new PlayerDominionProvider());
        dataRegistry.register("residences.migration", new ResidenceMigrationProvider());
        repository = new TuiMenuRepository(plugin, new ActionParser(), actionRegistry, dataRegistry);
        dataRegistry.register("menus.locales", new MenuLocaleProvider(repository::localeIds));
        dialogResolver = new DialogMenuResolver(dataRegistry);
        repository.load();
        sessions.start(plugin);
        sessions.activateRevision(repository.revision());
        executor = new ActionExecutor(actionRegistry);
        callbacks = new UiCallbackDispatcher(sessions, executor, results);
        TuiRenderer renderer = new TuiRenderer(sessions, dataRegistry);
        router = new UiRouter(repository, renderer);
        cuiRouter = new CuiRouter(repository, new CuiRenderer(sessions, dataRegistry));
        results.register(UiSurface.TUI, this::handleResult);
        results.register(UiSurface.CUI, this::handleCuiResult);
        dialogAdapter = createDialogAdapter(plugin);
        if (dialogAdapter != null) {
            results.register(UiSurface.DIALOG, this::handleDialogResult);
        }
        plugin.getServer().getPluginManager().registerEvents(new CuiListener(sessions, callbacks), plugin);
        registerCommands();
        instance = this;
    }

    /**
     * Returns whether the configured runtime completed plugin startup.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Returns the configured TUI runtime singleton.
     */
    public static ConfiguredTuiManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfiguredTuiManager is not initialized");
        }
        return instance;
    }

    /**
     * Opens a validated configured menu for a player.
     */
    public void show(Player player, String menuId) {
        show(player, menuId, 1);
    }

    /**
     * Opens a validated configured menu on a requested page.
     */
    public void show(Player player, String menuId, int page) {
        show(player, new MenuRoute(menuId, page, java.util.Map.of()));
    }

    /**
     * Opens a prevalidated route with server-owned arguments.
     */
    public void show(Player player, MenuRoute route) {
        if (usesDialogPreference(player) && hasDialogMenu(route.menuId())) {
            showDialog(player, route);
            return;
        }
        router.open(player, route);
    }

    /**
     * Resolves the effective persisted UI preference without changing callback-owned surface routing.
     */
    private boolean usesDialogPreference(Player player) {
        PlayerDTO.UI_TYPE configured = PlayerDTO.UI_TYPE.valueOf(Configuration.defaultUiType);
        if (configured == PlayerDTO.UI_TYPE.DUI) {
            return true;
        }
        if (configured != PlayerDTO.UI_TYPE.BY_PLAYER) {
            return false;
        }
        PlayerDTO playerData = CacheManager.instance.getPlayer(player.getUniqueId());
        return playerData != null && playerData.getUiPreference() == PlayerDTO.UI_TYPE.DUI;
    }

    /**
     * Returns whether a configured menu is available for a legacy fallback point.
     */
    public boolean hasMenu(String menuId) {
        return repository.find(menuId) != null;
    }

    /**
     * Returns whether a configured chest presentation is available for a legacy fallback point.
     */
    public boolean hasChestMenu(String menuId) {
        return repository.findChest(menuId) != null;
    }

    /**
     * Returns whether the active platform and repository can display a native Dialog menu.
     */
    public boolean hasDialogMenu(String menuId) {
        return dialogAdapter != null && repository.findDialog(menuId) != null;
    }

    /**
     * Shows first-use language choices and reports whether normal menu rendering should stop.
     */
    public boolean showOnboardingIfRequired(Player player) {
        PlayerDTO playerData = CacheManager.instance.getPlayer(player.getUniqueId());
        if (playerData == null || !playerData.getLanguage().equalsIgnoreCase("NONE")) {
            return false;
        }
        PlayerMenuOnboarding.showLanguageSelection(player, repository.localeIds());
        return true;
    }

    /**
     * Persists one discovered locale and advances the player to UI selection.
     */
    public void selectLanguage(Player player, String locale) throws Exception {
        boolean firstSelection = setLanguage(player, locale);
        if (firstSelection) {
            PlayerMenuOnboarding.showUiSelection(player);
        } else {
            MainMenu.show(player, "1");
        }
    }

    /**
     * Persists one discovered locale and reports whether it completed first-use language selection.
     */
    public boolean setLanguage(Player player, String locale) throws Exception {
        PlayerDTO playerData = CacheManager.instance.getPlayer(player.getUniqueId());
        if (playerData == null) {
            throw new IllegalStateException("Player data not found in cache");
        }
        boolean firstSelection = playerData.getLanguage().equalsIgnoreCase("NONE");
        playerData.setLanguage(repository.requireLocale(locale));
        return firstSelection;
    }

    /**
     * Opens a validated configured chest menu for a player.
     */
    public void showCui(Player player, String menuId, int page) {
        cuiRouter.open(player, new MenuRoute(menuId, page, java.util.Map.of()));
    }

    /**
     * Opens a prevalidated configured chest route with server-owned arguments.
     */
    public void showCui(Player player, MenuRoute route) {
        cuiRouter.open(player, route);
    }

    /**
     * Opens one configured native Dialog through the selected platform adapter.
     */
    public void showDialog(Player player, MenuRoute route) {
        if (dialogAdapter == null) {
            Notification.error(player, "Native Dialog is unavailable on this server platform.");
            return;
        }
        DialogMenuDefinition menu = repository.findDialog(route.menuId(), repository.localeFor(player));
        if (menu == null) {
            Notification.error(player, "Configured Dialog menu is unavailable: {0}", route.menuId());
            return;
        }
        if (!route.arguments().keySet().containsAll(menu.shared().requiredRouteArguments())) {
            Notification.error(player, "Missing route arguments for configured Dialog menu: {0}", route.menuId());
            return;
        }
        java.util.Set<String> allowed = new java.util.HashSet<>(menu.shared().requiredRouteArguments());
        allowed.addAll(menu.shared().optionalRouteArguments());
        if (!allowed.containsAll(route.arguments().keySet())) {
            Notification.error(player, "Unknown route arguments for configured Dialog menu: {0}", route.menuId());
            return;
        }
        sessions.beginSession(player);
        long revision = repository.revision();
        dialogResolver.resolve(player, menu, route).whenComplete((resolved, throwable) ->
                Scheduler.runEntityTask(() -> {
                    if (!player.isOnline() || revision != repository.revision()) {
                        return;
                    }
                    if (throwable != null || resolved == null) {
                        Notification.error(player, "Failed to load configured Dialog menu: {0}", route.menuId());
                        XLogger.error(throwable == null
                                ? new IllegalStateException("Dialog resolver returned no menu") : throwable);
                        return;
                    }
                    dialogAdapter.open(player, resolved, resolved.effectiveRoute(route), revision, sessions);
                }, player));
    }

    /**
     * Atomically reloads configured menus and invalidates callbacks from the previous revision.
     */
    public void reload() {
        repository.load();
        sessions.activateRevision(repository.revision());
    }

    /**
     * Clears all callback state during plugin shutdown.
     */
    public void close() {
        if (dialogAdapter != null) {
            dialogAdapter.shutdown();
        }
        sessions.clear();
        instance = null;
    }

    private void registerCommands() {
        Argument menuArgument = new Argument("menu", true,
                (sender, previous) -> repository.menuIds().stream().sorted().toList());
        new SecondaryCommand("ui_menu", List.of(menuArgument), "Opens a configured TUI menu.") {
            @Override
            public void executeHandler(CommandSender sender) {
                if (!(sender instanceof Player player)) {
                    Notification.error(sender, "Configured TUI menus can only be opened by players.");
                    return;
                }
                router.open(player, MenuRoute.of(getArgumentValue(0)));
            }
        }.needPermission(Dominion.adminPermission).register();

        new SecondaryCommand("ui_callback", List.of(new Argument("token", true))) {
            @Override
            public void executeHandler(CommandSender sender) {
                if (!(sender instanceof Player player)) {
                    return;
                }
                callbacks.dispatch(player, getArgumentValue(0));
            }
        }.hidden().register();

        new SecondaryCommand("ui_dialog_callback", List.of(new Argument("token", true))) {
            @Override
            public void executeHandler(CommandSender sender) {
                if (sender instanceof Player player && dialogAdapter != null
                        && !dialogAdapter.dispatchInline(player, getArgumentValue(0))) {
                    Notification.warn(player, "This Dialog action has expired. Please reopen the menu.");
                }
            }
        }.hidden().register();

        new SecondaryCommand("language", List.of(new Argument("locale", true,
                (sender, previous) -> repository.localeIds().stream().sorted().toList())),
                "Selects the language used by Dominion menus.") {
            @Override
            public void executeHandler(CommandSender sender) {
                if (!(sender instanceof Player player)) {
                    Notification.error(sender, "Menu language can only be selected by players.");
                    return;
                }
                try {
                    selectLanguage(player, getArgumentValue(0));
                } catch (Exception exception) {
                    Notification.error(player, exception.getMessage());
                }
            }
        }.needPermission(Dominion.defaultPermission).register();

        Argument dialogArgument = new Argument("menu", true,
                (sender, previous) -> repository.dialogMenuIds().stream().sorted().toList());
        new SecondaryCommand("ui_dialog", List.of(dialogArgument), "Opens a configured native Dialog menu.") {
            @Override
            public void executeHandler(CommandSender sender) {
                if (!(sender instanceof Player player)) {
                    Notification.error(sender, "Configured Dialog menus can only be opened by players.");
                    return;
                }
                showDialog(player, MenuRoute.of(getArgumentValue(0)));
            }
        }.needPermission(Dominion.adminPermission).register();
    }

    /**
     * Loads the Spigot adapter only when its platform event API is actually available.
     */
    private DialogPlatformAdapter createDialogAdapter(JavaPlugin plugin) {
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            String adapterName;
            if (Misc.isPaper()) {
                Class.forName("io.papermc.paper.dialog.Dialog", false, classLoader);
                Class.forName("io.papermc.paper.dialog.DialogResponseView", false, classLoader);
                adapterName = "cn.lunadeer.dominion.dialog.paper.PaperDialogPlatformAdapter";
            } else {
                Class.forName("org.bukkit.event.player.PlayerCustomClickEvent", false, classLoader);
                Class.forName("net.md_5.bungee.api.dialog.Dialog", false, classLoader);
                adapterName = "cn.lunadeer.dominion.dialog.spigot.SpigotDialogPlatformAdapter";
            }
            Class<?> adapterClass = Class.forName(adapterName, true, classLoader);
            DialogPlatformAdapter adapter = (DialogPlatformAdapter) adapterClass.getDeclaredConstructor().newInstance();
            adapter.initialize(plugin, callbacks);
            XLogger.info("Configured Dialog adapter initialized: {0}", adapterClass.getSimpleName());
            return adapter;
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            XLogger.error("Failed to initialize Dialog adapter: {0}", exception.getMessage());
            XLogger.error(exception);
            return null;
        }
    }

    /**
     * Routes Dialog action outcomes without exposing platform API types to core.
     */
    private void handleDialogResult(Player player,
                                    MenuRoute currentRoute,
                                    ActionResult result,
                                    Throwable throwable) {
        if (throwable != null || result == null || result.kind() == ActionResult.Kind.FAILURE) {
            Notification.error(player, result != null && result.message() != null
                    ? result.message() : "Dialog action execution failed.");
            if (throwable != null) {
                XLogger.error(throwable);
            } else if (result != null && result.cause() != null) {
                XLogger.error(result.cause());
            }
            reopenDialogNextTick(player, currentRoute);
            return;
        }
        if (result.kind() == ActionResult.Kind.OPEN && result.route() != null) {
            if (repository.findDialog(result.route().menuId()) != null) {
                reopenDialogNextTick(player, result.route());
            } else {
                runUiNextTick(player, () -> router.open(player, result.route()));
            }
        } else if (result.kind() == ActionResult.Kind.REFRESH) {
            reopenDialogNextTick(player, currentRoute);
        } else if (result.kind() == ActionResult.Kind.STOP) {
            reopenDialogNextTick(player, currentRoute);
        } else if (result.kind() == ActionResult.Kind.CLOSE) {
            sessions.invalidateForTransition(player);
            dialogAdapter.close(player);
        }
    }

    private void handleResult(Player player, MenuRoute currentRoute, ActionResult result, Throwable throwable) {
        if (throwable != null) {
            Notification.error(player, "UI action execution failed.");
            XLogger.error(throwable);
            return;
        }
        if (result == null) {
            Notification.error(player, "UI action returned no result.");
            return;
        }
        if (result.kind() == ActionResult.Kind.FAILURE) {
            Notification.error(player, result.message() == null ? "UI action failed." : result.message());
            if (result.cause() != null) {
                XLogger.error(result.cause());
            }
        } else if (result.kind() == ActionResult.Kind.OPEN) {
            if (result.route() == null) {
                Notification.error(player, "UI navigation returned no route.");
            } else {
                router.open(player, result.route());
            }
        } else if (result.kind() == ActionResult.Kind.REFRESH) {
            router.open(player, currentRoute);
        } else if (result.kind() == ActionResult.Kind.CLOSE) {
            sessions.invalidateForTransition(player);
        }
    }

    private void handleCuiResult(Player player,
                                 MenuRoute currentRoute,
                                 ActionResult result,
                                 Throwable throwable) {
        if (throwable != null) {
            Notification.error(player, "UI action execution failed.");
            XLogger.error(throwable);
            refreshCurrentCui(player, currentRoute);
            return;
        }
        if (result == null) {
            Notification.error(player, "UI action returned no result.");
            refreshCurrentCui(player, currentRoute);
            return;
        }
        if (result.kind() == ActionResult.Kind.FAILURE) {
            Notification.error(player, result.message() == null ? "UI action failed." : result.message());
            if (result.cause() != null) {
                XLogger.error(result.cause());
            }
            refreshCurrentCui(player, currentRoute);
        } else if (result.kind() == ActionResult.Kind.OPEN) {
            if (result.route() == null) {
                Notification.error(player, "UI navigation returned no route.");
                refreshCurrentCui(player, currentRoute);
            } else if (repository.findChest(result.route().menuId()) != null) {
                runUiNextTick(player, () -> cuiRouter.open(player, result.route()));
            } else {
                // Until the target CUI is migrated, keep the operation usable through its configured TUI route.
                player.closeInventory();
                runUiNextTick(player, () -> router.open(player, result.route()));
            }
        } else if (result.kind() == ActionResult.Kind.REFRESH) {
            refreshCurrentCui(player, currentRoute);
        } else if (result.kind() == ActionResult.Kind.CLOSE) {
            sessions.invalidateForTransition(player);
            player.closeInventory();
        } else if (result.kind() == ActionResult.Kind.STOP) {
            refreshCurrentCui(player, currentRoute);
        }
    }

    private void refreshCurrentCui(Player player, MenuRoute route) {
        runUiNextTick(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CuiInventoryHolder holder
                    && holder.ownerId().equals(player.getUniqueId())
                    && holder.route().equals(route)) {
                cuiRouter.open(player, route);
            }
        });
    }

    /**
     * Replaces the current Dialog one tick after its callback and cache work complete.
     */
    private void reopenDialogNextTick(Player player, MenuRoute route) {
        runUiNextTick(player, () -> showDialog(player, route));
    }

    /**
     * Schedules one UI transition on the owning player's next entity tick.
     */
    private void runUiNextTick(Player player, Runnable transition) {
        Scheduler.runEntityTaskLater(() -> {
            if (player.isOnline()) {
                transition.run();
            }
        }, player, 1L);
    }

    // Legacy transitions remain explicit while their target pages have not been migrated.
    private void registerDominionActions(DominionActionRegistry registry) {
        registry.register("legacy-main-menu", (context, action) -> {
            sessions.invalidateForTransition(context.player());
            MainMenu.show(context.player(), "1");
            return CompletableFuture.completedFuture(ActionResult.stop());
        }, this::requireNoDominionArguments);
        new TemplateMenuActionRegistrar(
                sessions,
                new ChatInputWorkflow(this::handleInputWorkflowResult)
        ).registerInto(registry);
        new MemberTemplateActionRegistrar().registerInto(registry);
        new MemberFlagActionRegistrar(sessions).registerInto(registry);
        new PrivilegeFlagActionRegistrar(
                sessions,
                new ChatInputWorkflow(this::handleInputWorkflowResult)
        ).registerInto(registry);
        new MembershipMenuActionRegistrar(
                sessions,
                new ChatInputWorkflow(this::handleInputWorkflowResult)
        ).registerInto(registry);
        new UtilityMenuActionRegistrar(sessions).registerInto(registry);
        new MainManagementActionRegistrar(sessions).registerInto(registry);
        new DominionTreeActionRegistrar(sessions).registerInto(registry);
        new EnvironmentFlagActionRegistrar(sessions).registerInto(registry);
    }

    private void requireNoDominionArguments(cn.lunadeer.dominion.uis.menu.action.ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException("Dominion operation does not accept arguments at "
                    + action.configPath());
        }
    }

    private void handleInputWorkflowResult(ActionContext context, ActionResult result, Throwable throwable) {
        if (!results.dispatch(context, result, throwable)) {
            Notification.error(context.player(), "UI result surface is unavailable: {0}", context.surface());
        }
    }
}
