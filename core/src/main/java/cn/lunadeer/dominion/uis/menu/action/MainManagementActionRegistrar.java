package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.CuboidDTO;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.commands.AdministratorCommand;
import cn.lunadeer.dominion.commands.DominionOperateCommand;
import cn.lunadeer.dominion.configuration.Configuration;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.events.dominion.modify.DominionSetMessageEvent;
import cn.lunadeer.dominion.inputters.CreateDominionInputter;
import cn.lunadeer.dominion.inputters.EditMessageInputter;
import cn.lunadeer.dominion.inputters.RenameDominionInputter;
import cn.lunadeer.dominion.inputters.SetMapColorInputter;
import cn.lunadeer.dominion.providers.DominionProvider;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.menu.tui.ConfiguredTuiManager;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toColor;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Others.autoPoints;

/**
 * Registers fixed main-menu and dominion-management action groups.
 */
public final class MainManagementActionRegistrar {

    private final UiCallbackSessionManager sessions;

    /**
     * Creates fixed menu action groups that can retire callbacks before legacy input adapters.
     */
    public MainManagementActionRegistrar(UiCallbackSessionManager sessions) {
        this.sessions = sessions;
    }

    /**
     * Registers main navigation, administrator, and dominion management action groups.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("start-create-dominion-input", this::createDominionInput, this::requireNoArguments);
        registry.register("open-dominion-list", (context, action) -> openSimple(context, "dominion_list"), this::requireNoArguments);
        registry.register("open-title-list", (context, action) -> openSimple(context, "title_list"), this::requireNoArguments);
        registry.register("open-template-list", (context, action) -> openSimple(context, "template_list"), this::requireNoArguments);
        registry.register("open-migrate-list", this::openMigrateList, this::requireNoArguments);
        registry.register("open-all-dominions", this::openAllDominions, this::requireNoArguments);
        registry.register("reload-cache", this::reloadCache, this::requireNoArguments);
        registry.register("reload-config", this::reloadConfig, this::requireNoArguments);
        registry.register("switch-to-cui", this::switchToCui, this::requireNoArguments);
        registry.register("switch-to-tui", this::switchToTui, this::requireNoArguments);
        registry.register("switch-to-dui", this::switchToDui, this::requireNoArguments);
        registry.register("open-ui-preferences", (context, action) -> openSimple(context, "ui_preferences"),
                this::requireNoArguments);
        registry.register("open-language-preferences",
                (context, action) -> openSimple(context, "language_preferences"), this::requireNoArguments);
        registry.register("set-language", this::setLanguage, this::requireNoArguments);

        registry.register("open-size-menu", (context, action) -> openDominionMenu(context, "set_size"), this::requireNoArguments);
        registry.register("open-env-flags", (context, action) -> openDominionMenu(context, "env_flags"), this::requireNoArguments);
        registry.register("open-guest-flags", (context, action) -> openDominionMenu(context, "guest_flags"), this::requireNoArguments);
        registry.register("open-member-list", (context, action) -> openDominionMenu(context, "member_list"), this::requireNoArguments);
        registry.register("open-group-list", (context, action) -> openDominionMenu(context, "group_list"), this::requireNoArguments);
        registry.register("open-copy-menu", (context, action) -> openDominionMenu(context, "copy_menu"), this::requireNoArguments);
        registry.register("set-dominion-tp", this::setTp, this::requireNoArguments);
        registry.register("start-rename-dominion-input", this::renameInput, this::requireNoArguments);
        registry.register("start-enter-message-input", this::enterMessageInput, this::requireNoArguments);
        registry.register("start-leave-message-input", this::leaveMessageInput, this::requireNoArguments);
        registry.register("start-map-color-input", this::mapColorInput, this::requireNoArguments);
        registry.register("back-dominion-list", (context, action) -> openSimple(context, "dominion_list"), this::requireNoArguments);
    }

    private CompletableFuture<ActionResult> createDominionInput(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            if (!context.submittedInputs().isEmpty()) {
                return createDominion(context);
            }
            sessions.invalidateForTransition(context.player());
            closeChestBeforeLegacyInput(context);
            CreateDominionInputter.createOn(context.player());
            return completed(closeForLegacyInput(context));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Creates an automatically sized dominion from a validated Dialog input and waits for persistence to finish.
     */
    private CompletableFuture<ActionResult> createDominion(ActionContext context) {
        try {
            requireDefaultPermission(context);
            Player player = context.player();
            Location[] points = autoPoints(player);
            CuboidDTO cuboid = new CuboidDTO(points[0], points[1]);
            return DominionProvider.getInstance().createDominion(
                            player, context.requireInput("dominion_name"), player.getUniqueId(),
                            player.getWorld(), cuboid, null, false)
                    .thenApply(created -> created == null
                            ? ActionResult.stop() : ActionResult.open(MenuRoute.of("dominion_list")));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> openSimple(ActionContext context, String menuId) {
        try {
            requireDefaultPermission(context);
            return completed(ActionResult.open(MenuRoute.of(menuId)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> openMigrateList(ActionContext context, ActionSpec action) {
        if (!Configuration.residenceMigration) {
            return completed(ActionResult.failure("Residence migration is disabled."));
        }
        return openSimple(context, "migrate_list");
    }

    private CompletableFuture<ActionResult> openAllDominions(ActionContext context, ActionSpec action) {
        if (!context.player().hasPermission(Dominion.adminPermission)) {
            return completed(ActionResult.failure(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.adminPermission)));
        }
        return completed(ActionResult.open(MenuRoute.of("all_dominions")));
    }

    private CompletableFuture<ActionResult> reloadCache(ActionContext context, ActionSpec action) {
        if (!context.player().hasPermission(Dominion.adminPermission)) {
            return completed(ActionResult.failure("No permission."));
        }
        sessions.invalidateForTransition(context.player());
        AdministratorCommand.reloadCache(context.player(), false);
        return completed(ActionResult.open(MenuRoute.of("main_menu")));
    }

    private CompletableFuture<ActionResult> reloadConfig(ActionContext context, ActionSpec action) {
        if (!context.player().hasPermission(Dominion.adminPermission)) {
            return completed(ActionResult.failure("No permission."));
        }
        sessions.invalidateForTransition(context.player());
        AdministratorCommand.reloadConfig(context.player(), false);
        return completed(ActionResult.open(MenuRoute.of("main_menu")));
    }

    private CompletableFuture<ActionResult> switchToCui(ActionContext context, ActionSpec action) {
        return switchUi(context, PlayerDTO.UI_TYPE.CUI, UiSurface.CUI);
    }

    /**
     * Selects the text renderer from the shared interface preference menu.
     */
    private CompletableFuture<ActionResult> switchToTui(ActionContext context, ActionSpec action) {
        return switchUi(context, PlayerDTO.UI_TYPE.TUI, UiSurface.TUI);
    }

    /**
     * Selects the Dialog renderer from the shared interface preference menu.
     */
    private CompletableFuture<ActionResult> switchToDui(ActionContext context, ActionSpec action) {
        return switchUi(context, PlayerDTO.UI_TYPE.DUI, UiSurface.DIALOG);
    }

    /**
     * Persists one renderer preference while keeping same-surface and cross-surface transitions distinct.
     */
    private CompletableFuture<ActionResult> switchUi(ActionContext context,
                                                       PlayerDTO.UI_TYPE preference,
                                                       UiSurface targetSurface) {
        try {
            requireDefaultPermission(context);
            PlayerDTO player = CacheManager.instance.getPlayer(context.player().getUniqueId());
            if (player == null) throw new IllegalStateException("Player data not found in cache");
            player.setUiPreference(preference);
            sessions.invalidateForTransition(context.player());
            if (context.surface() == targetSurface) {
                return completed(ActionResult.open(MenuRoute.of("main_menu")));
            }
            if (context.surface() == UiSurface.CUI) {
                context.player().closeInventory();
            }
            MainMenu.show(context.player(), "1");
            return completed(ActionResult.close());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Persists a repository-provided locale and reopens the main menu on the current UI surface.
     */
    private CompletableFuture<ActionResult> setLanguage(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            ConfiguredTuiManager.getInstance().setLanguage(
                    context.player(), context.requireTrustedArgument("locale.id"));
            return completed(ActionResult.open(MenuRoute.of("main_menu")));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    // Legacy chat input and text rendering must not remain hidden behind a configured chest Inventory.
    private void closeChestBeforeLegacyInput(ActionContext context) {
        if (context.surface() == UiSurface.CUI) {
            context.player().closeInventory();
        }
    }

    private CompletableFuture<ActionResult> openDominionMenu(ActionContext context, String menuId) {
        try {
            DominionDTO dominion = requireDominion(context);
            return completed(ActionResult.open(new MenuRoute(
                    menuId, 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> setTp(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            DominionOperateCommand.setTp(context.player(), dominion.getName());
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> renameInput(ActionContext context, ActionSpec action) {
        if (!context.submittedInputs().isEmpty()) {
            return renameDominion(context);
        }
        return startDominionInput(context, input -> RenameDominionInputter.createOn(context.player(), input));
    }

    /**
     * Persists a validated Dialog rename and rebuilds the management route with the new identity.
     */
    private CompletableFuture<ActionResult> renameDominion(ActionContext context) {
        try {
            DominionDTO dominion = requireDominion(context);
            return DominionProvider.getInstance().renameDominion(
                            context.player(), dominion, context.requireInput("dominion_new_name"))
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.open(new MenuRoute(
                            "dominion_manage", 1, Map.of("dominion.name", updated.getName()))));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> enterMessageInput(ActionContext context, ActionSpec action) {
        if (!context.submittedInputs().isEmpty()) {
            return setDominionMessage(context, DominionSetMessageEvent.TYPE.ENTER, "dominion_enter_message");
        }
        return startDominionInput(context, input -> EditMessageInputter.createEnterOn(context.player(), input));
    }

    private CompletableFuture<ActionResult> leaveMessageInput(ActionContext context, ActionSpec action) {
        if (!context.submittedInputs().isEmpty()) {
            return setDominionMessage(context, DominionSetMessageEvent.TYPE.LEAVE, "dominion_leave_message");
        }
        return startDominionInput(context, input -> EditMessageInputter.createLeaveOn(context.player(), input));
    }

    /**
     * Persists one validated Dialog message and refreshes the current management form after completion.
     */
    private CompletableFuture<ActionResult> setDominionMessage(ActionContext context,
                                                                DominionSetMessageEvent.TYPE type,
                                                                String inputKey) {
        try {
            DominionDTO dominion = requireDominion(context);
            return DominionProvider.getInstance().setDominionMessage(
                            context.player(), dominion, type, context.requireInput(inputKey))
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> mapColorInput(ActionContext context, ActionSpec action) {
        if (!context.submittedInputs().isEmpty()) {
            return setMapColor(context);
        }
        return startDominionInput(context, input -> SetMapColorInputter.createOn(context.player(), input));
    }

    /**
     * Parses a validated Dialog color and waits for the map-color update before refreshing.
     */
    private CompletableFuture<ActionResult> setMapColor(ActionContext context) {
        try {
            DominionDTO dominion = requireDominion(context);
            return DominionProvider.getInstance().setDominionMapColor(
                            context.player(), dominion, toColor(context.requireInput("dominion_map_color")))
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> startDominionInput(ActionContext context,
                                                                java.util.function.Consumer<String> starter) {
        try {
            DominionDTO dominion = requireDominion(context);
            sessions.invalidateForTransition(context.player());
            closeChestBeforeLegacyInput(context);
            starter.accept(dominion.getName());
            return completed(closeForLegacyInput(context));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private DominionDTO requireDominion(ActionContext context) throws Exception {
        requireDefaultPermission(context);
        DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
        assertDominionAdmin(context.player(), dominion);
        return dominion;
    }

    private ActionResult closeForLegacyInput(ActionContext context) {
        return context.surface() == UiSurface.DIALOG ? ActionResult.close() : ActionResult.stop();
    }

    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) throw new IllegalArgumentException("Fixed menu operation does not accept arguments at " + action.configPath());
    }

    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
