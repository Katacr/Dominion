package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.flag.EnvFlag;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.providers.DominionProvider;
import cn.lunadeer.dominion.uis.dominion.DominionManage;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toEnvFlag;

/**
 * Registers trusted action groups for configured environment flag menus.
 */
public final class EnvironmentFlagActionRegistrar {

    private final UiCallbackSessionManager sessions;

    /**
     * Creates environment action groups with access to legacy transition cleanup.
     */
    public EnvironmentFlagActionRegistrar(UiCallbackSessionManager sessions) {
        this.sessions = sessions;
    }

    /**
     * Adds environment flag action groups to the Dominion action namespace.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("toggle-env-flag", this::toggleFlag, this::requireNoArguments);
        registry.register("back-dominion-manage", this::backToDominionManage, this::requireNoArguments);
    }

    private CompletableFuture<ActionResult> toggleFlag(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            String dominionName = context.requireRouteArgument("dominion.name");
            String flagName = context.requireTrustedArgument("flag.name");
            String nextValue = context.requireTrustedArgument("flag.next-value");
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            EnvFlag flag = toEnvFlag(flagName);
            if (!Flags.getAllEnvFlagsEnable().contains(flag)) {
                throw new IllegalStateException("Environment flag is not enabled: " + flagName);
            }
            boolean value = parseBoolean(nextValue);
            return DominionProvider.getInstance().setDominionEnvFlag(context.player(), dominion, flag, value)
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> backToDominionManage(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            String dominionName = context.requireRouteArgument("dominion.name");
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            return CompletableFuture.completedFuture(ActionResult.open(new MenuRoute(
                    "dominion_manage", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage()));
        }
    }

    private boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalStateException("Invalid trusted boolean value: " + value);
    }

    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException("Environment flag operation does not accept arguments at "
                    + action.configPath());
        }
    }
}
