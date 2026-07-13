package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.cache.server.ServerCache;
import cn.lunadeer.dominion.commands.DominionOperateCommand;
import cn.lunadeer.dominion.commands.MigrationCommand;
import cn.lunadeer.dominion.configuration.Configuration;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.providers.DominionProvider;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.managers.TeleportManager.teleportToDominion;
import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;

/**
 * Registers trusted dominion-tree and Residence migration action groups.
 */
public final class DominionTreeActionRegistrar {

    private final UiCallbackSessionManager sessions;

    /**
     * Creates action groups that can retire callbacks before legacy migration transitions.
     */
    public DominionTreeActionRegistrar(UiCallbackSessionManager sessions) {
        this.sessions = sessions;
    }

    /**
     * Registers navigation, deletion confirmation, teleportation, and migration action groups.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("manage-dominion", this::manageDominion, this::requireNoArguments);
        registry.register("delete-dominion", this::deleteDominion, this::requireNoArguments);
        registry.register("teleport-dominion", this::teleportDominion, this::requireNoArguments);
        registry.register("migrate-residence", this::migrateResidence, this::requireNoArguments);
        registry.register("migrate-all-residences", this::migrateAllResidences, this::requireNoArguments);
        registry.register("back-main-menu-route", (context, action) ->
                completed(ActionResult.open(MenuRoute.of("main_menu"))), this::requireNoArguments);
    }

    /**
     * Opens management for a currently resolvable local dominion.
     */
    private CompletableFuture<ActionResult> manageDominion(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireLocalDominion(context);
            assertDominionAdmin(context.player(), dominion);
            return completed(ActionResult.open(new MenuRoute(
                    "dominion_manage", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    /**
     * Starts the existing deletion confirmation flow for a local dominion.
     */
    private CompletableFuture<ActionResult> deleteDominion(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireLocalDominion(context);
            if (context.surface() == UiSurface.DIALOG && context.confirmed()) {
                return DominionProvider.getInstance().deleteDominion(context.player(), dominion, false, true)
                        .thenApply(deleted -> deleted == null ? ActionResult.stop() : ActionResult.refresh());
            }
            if (context.surface() == UiSurface.CUI) {
                sessions.invalidateForTransition(context.player());
                context.player().closeInventory();
            }
            DominionOperateCommand.delete(context.player(), dominion.getName(), "");
            return completed(ActionResult.stop());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Delegates local or cross-server teleportation to the existing manager.
     */
    private CompletableFuture<ActionResult> teleportDominion(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            teleportToDominion(context.player(), requireTrustedDominion(context));
            return completed(context.surface() == UiSurface.DIALOG
                    ? ActionResult.refresh() : ActionResult.stop());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Migrates one trusted root Residence through the legacy domain command.
     */
    private CompletableFuture<ActionResult> migrateResidence(ActionContext context, ActionSpec action) {
        try {
            requireMigrationEnabled(context);
            String residence = context.requireTrustedArgument("residence.name");
            sessions.invalidateForTransition(context.player());
            MigrationCommand.migrate(context.player(), residence, Integer.toString(context.route().page()), false);
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Runs the administrator-only bulk Residence migration.
     */
    private CompletableFuture<ActionResult> migrateAllResidences(ActionContext context, ActionSpec action) {
        try {
            requireMigrationEnabled(context);
            requireAdministrator(context);
            sessions.invalidateForTransition(context.player());
            MigrationCommand.migrateAll(context.player());
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Resolves a trusted dominion and rejects remote management action groups.
     */
    private DominionDTO requireLocalDominion(ActionContext context) {
        requireDefaultPermission(context);
        DominionDTO dominion = requireTrustedDominion(context);
        if (dominion.getServerId() != Configuration.multiServer.serverId) {
            throw new IllegalStateException("Remote dominions cannot be managed from this server.");
        }
        return dominion;
    }

    /**
     * Re-resolves callback identity from the current server-specific cache.
     */
    private DominionDTO requireTrustedDominion(ActionContext context) {
        int serverId = Integer.parseInt(context.requireTrustedArgument("server.id"));
        int dominionId = Integer.parseInt(context.requireTrustedArgument("dominion.id"));
        ServerCache server = CacheManager.instance.getCache(serverId);
        if (server == null) {
            throw new IllegalStateException("Dominion server cache is unavailable: " + serverId);
        }
        DominionDTO dominion = server.getDominionCache().getDominion(dominionId);
        if (dominion == null || dominion.getServerId() != serverId) {
            throw new IllegalStateException("Dominion is no longer available: " + dominionId);
        }
        return dominion;
    }

    /**
     * Revalidates default permission and the optional migration capability.
     */
    private void requireMigrationEnabled(ActionContext context) {
        requireDefaultPermission(context);
        if (!Configuration.residenceMigration) {
            throw new IllegalStateException(TextUserInterface.migrateListTuiText.notEnabled);
        }
    }

    /**
     * Requires the administrator permission for bulk action groups.
     */
    private void requireAdministrator(ActionContext context) {
        if (!context.player().hasPermission(Dominion.adminPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.adminPermission));
        }
    }

    /**
     * Requires the standard Dominion player permission.
     */
    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    /**
     * Keeps resource targets out of configurable action arguments.
     */
    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException(
                    "Dominion tree operation does not accept arguments at " + action.configPath());
        }
    }

    /**
     * Wraps immediate UI outcomes in the asynchronous action contract.
     */
    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
