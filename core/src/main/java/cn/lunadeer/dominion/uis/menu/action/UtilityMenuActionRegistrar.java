package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.commands.CopyCommand;
import cn.lunadeer.dominion.commands.GroupTitleCommand;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.events.dominion.modify.DominionReSizeEvent;
import cn.lunadeer.dominion.inputters.ResizeDominionInputter;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.dominion.DominionManage;
import cn.lunadeer.dominion.uis.dominion.copy.DominionCopy;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Asserts.assertDominionOwner;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;

/**
 * Registers copy, title, and resize action groups used by small configured menus.
 */
public final class UtilityMenuActionRegistrar {

    private final UiCallbackSessionManager sessions;

    /**
     * Creates utility action groups with callback cleanup before legacy input transitions.
     */
    public UtilityMenuActionRegistrar(UiCallbackSessionManager sessions) {
        this.sessions = sessions;
    }

    /**
     * Registers fixed copy types, title toggles, and validated resize input.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("open-copy-environment", (context, action) -> openCopy(context, DominionCopy.CopyType.ENVIRONMENT), this::requireNoArguments);
        registry.register("open-copy-guest", (context, action) -> openCopy(context, DominionCopy.CopyType.GUEST), this::requireNoArguments);
        registry.register("open-copy-member", (context, action) -> openCopy(context, DominionCopy.CopyType.MEMBER), this::requireNoArguments);
        registry.register("open-copy-group", (context, action) -> openCopy(context, DominionCopy.CopyType.GROUP), this::requireNoArguments);
        registry.register("copy-from-dominion", this::copyFromDominion, this::requireNoArguments);
        registry.register("back-copy-menu", this::backCopyMenu, this::requireNoArguments);
        registry.register("toggle-title", this::toggleTitle, this::requireNoArguments);
        registry.register("back-title-main-menu", this::backMainMenu, this::requireNoArguments);
        registry.register("resize-input", this::resizeInput, this::validateResizeArguments);
        registry.register("back-size-dominion-manage", this::backDominionManage, this::requireNoArguments);
    }

    private CompletableFuture<ActionResult> openCopy(ActionContext context, DominionCopy.CopyType type) {
        try {
            DominionDTO dominion = requireAdminDominion(context);
            return completed(ActionResult.open(copyRoute(dominion.getName(), type)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> copyFromDominion(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO target = requireAdminDominion(context);
            String source = context.requireTrustedArgument("source.name");
            DominionCopy.CopyType type = DominionCopy.CopyType.valueOf(context.requireRouteArgument("copy.type"));
            toDominionDTO(source);
            sessions.invalidateForTransition(context.player());
            switch (type) {
                case ENVIRONMENT -> CopyCommand.copyEnvironment(context.player(), source, target.getName(), false);
                case GUEST -> CopyCommand.copyGuest(context.player(), source, target.getName(), false);
                case MEMBER -> CopyCommand.copyMember(context.player(), source, target.getName(), false);
                case GROUP -> CopyCommand.copyGroup(context.player(), source, target.getName(), false);
            }
            return completed(ActionResult.open(copyRoute(target.getName(), type)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> backCopyMenu(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireAdminDominion(context);
            return completed(ActionResult.open(new MenuRoute(
                    "copy_menu", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> toggleTitle(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            String titleId = context.requireTrustedArgument("title.id");
            sessions.invalidateForTransition(context.player());
            GroupTitleCommand.useTitle(context.player(), titleId, Integer.toString(context.route().page()), false);
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> backMainMenu(ActionContext context, ActionSpec action) {
        return completed(ActionResult.open(MenuRoute.of("main_menu")));
    }

    private CompletableFuture<ActionResult> resizeInput(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
            assertDominionOwner(context.player(), dominion);
            String[] arguments = action.argument().split("\\s+");
            DominionReSizeEvent.TYPE type = DominionReSizeEvent.TYPE.valueOf(arguments[0]);
            DominionReSizeEvent.DIRECTION direction = DominionReSizeEvent.DIRECTION.valueOf(arguments[1]);
            sessions.invalidateForTransition(context.player());
            if (context.surface() == UiSurface.CUI) {
                context.player().closeInventory();
            }
            if (type == DominionReSizeEvent.TYPE.EXPAND) {
                ResizeDominionInputter.createExpandOn(context.player(), dominion.getName(), direction);
            } else {
                ResizeDominionInputter.createContractOn(context.player(), dominion.getName(), direction);
            }
            return completed(context.surface() == UiSurface.DIALOG
                    ? ActionResult.close() : ActionResult.stop());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> backDominionManage(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
            assertDominionAdmin(context.player(), dominion);
            return completed(ActionResult.open(new MenuRoute(
                    "dominion_manage", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private DominionDTO requireAdminDominion(ActionContext context) throws Exception {
        requireDefaultPermission(context);
        DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
        assertDominionAdmin(context.player(), dominion);
        return dominion;
    }

    private MenuRoute copyRoute(String dominionName, DominionCopy.CopyType type) {
        return new MenuRoute("dominion_copy", 1, Map.of(
                "dominion.name", dominionName,
                "copy.type", type.name()
        ));
    }

    private void validateResizeArguments(ActionSpec action) {
        String[] arguments = action.argument().split("\\s+");
        if (arguments.length != 2) throw new IllegalArgumentException("Resize operation requires type and direction at " + action.configPath());
        DominionReSizeEvent.TYPE.valueOf(arguments[0]);
        DominionReSizeEvent.DIRECTION.valueOf(arguments[1]);
    }

    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) throw new IllegalArgumentException("Utility operation does not accept arguments at " + action.configPath());
    }

    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
