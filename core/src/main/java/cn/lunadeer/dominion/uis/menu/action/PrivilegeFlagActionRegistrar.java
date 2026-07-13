package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.GroupDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.providers.DominionProvider;
import cn.lunadeer.dominion.providers.GroupProvider;
import cn.lunadeer.dominion.uis.dominion.DominionManage;
import cn.lunadeer.dominion.uis.dominion.manage.group.GroupList;
import cn.lunadeer.dominion.uis.menu.input.ChatInputWorkflow;
import cn.lunadeer.dominion.uis.menu.input.InputFieldSpec;
import cn.lunadeer.dominion.uis.menu.input.InputSchema;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toGroupDTO;
import static cn.lunadeer.dominion.misc.Converts.toPriFlag;

/**
 * Registers guest and group privilege action groups shared by configured TUI pages.
 */
public final class PrivilegeFlagActionRegistrar {

    private static final InputSchema RENAME_GROUP_SCHEMA = new InputSchema(List.of(
            InputFieldSpec.requiredText("group_new_name", 1, 64, null)
    ));

    private final UiCallbackSessionManager sessions;
    private final ChatInputWorkflow inputWorkflow;

    /**
     * Creates privilege action groups with chat input and explicit legacy transition cleanup.
     */
    public PrivilegeFlagActionRegistrar(UiCallbackSessionManager sessions, ChatInputWorkflow inputWorkflow) {
        this.sessions = sessions;
        this.inputWorkflow = inputWorkflow;
    }

    /**
     * Registers all guest and group flag action groups in the Dominion namespace.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("toggle-guest-flag", this::toggleGuestFlag, this::requireNoArguments);
        registry.register("back-guest-dominion-manage", this::backGuest, this::requireNoArguments);
        registry.register("toggle-group-flag", this::toggleGroupFlag, this::requireNoArguments);
        registry.register("rename-group-input", this::renameGroupInput, this::requireNoArguments);
        registry.register("back-group-list", this::backGroupList, this::requireNoArguments);
    }

    /**
     * Revalidates and updates one enabled non-ADMIN guest flag.
     */
    private CompletableFuture<ActionResult> toggleGuestFlag(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            PriFlag flag = toPriFlag(context.requireTrustedArgument("flag.name"));
            if (flag.equals(Flags.ADMIN) || !Flags.getAllPriFlagsEnable().contains(flag)) {
                throw new IllegalStateException("Guest flag is not enabled: " + flag.getFlagName());
            }
            boolean value = parseBoolean(context.requireTrustedArgument("flag.next-value"));
            return DominionProvider.getInstance().setDominionGuestFlag(
                            context.player(), dominion, flag, value)
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Returns from guest settings to the remaining legacy dominion management page.
     */
    private CompletableFuture<ActionResult> backGuest(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            return CompletableFuture.completedFuture(ActionResult.open(new MenuRoute(
                    "dominion_manage", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage()));
        }
    }

    /**
     * Revalidates group visibility before updating one trusted flag.
     */
    private CompletableFuture<ActionResult> toggleGroupFlag(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireGroup(context, dominion);
            PriFlag flag = toPriFlag(context.requireTrustedArgument("flag.name"));
            if (group.getFlagValue(Flags.ADMIN)) {
                if (!flag.equals(Flags.ADMIN)) {
                    throw new IllegalStateException("Only ADMIN is editable for administrator groups");
                }
            } else if (!Flags.getAllPriFlagsEnable().contains(flag)) {
                throw new IllegalStateException("Group flag is not enabled: " + flag.getFlagName());
            }
            boolean value = parseBoolean(context.requireTrustedArgument("flag.next-value"));
            return GroupProvider.getInstance().setGroupFlag(context.player(), dominion, group, flag, value)
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Starts normalized chat input for a group rename.
     */
    private CompletableFuture<ActionResult> renameGroupInput(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            requireGroup(context, dominion);
            if (!context.submittedInputs().isEmpty()) {
                return renameGroup(context);
            }
            sessions.invalidateForTransition(context.player());
            inputWorkflow.start(context, RENAME_GROUP_SCHEMA,
                    Language.renameGroupInputterText.hint, this::renameGroup);
            return CompletableFuture.completedFuture(
                    context.surface() == cn.lunadeer.dominion.uis.menu.route.UiSurface.DIALOG
                            ? ActionResult.close() : ActionResult.stop());
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Renames the current group and opens a route containing its new plain name.
     */
    private CompletableFuture<ActionResult> renameGroup(ActionContext context) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireGroup(context, dominion);
            String newName = context.requireInput("group_new_name");
            return GroupProvider.getInstance().renameGroup(context.player(), dominion, group, newName)
                    .thenApply(updated -> updated == null
                            ? ActionResult.stop()
                            : ActionResult.open(groupRoute(dominion.getName(), updated.getNamePlain())));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Returns to the legacy group list while it has not yet been migrated.
     */
    private CompletableFuture<ActionResult> backGroupList(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            return CompletableFuture.completedFuture(ActionResult.open(new MenuRoute(
                    "group_list", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage()));
        }
    }

    private DominionDTO requireDominion(ActionContext context) throws Exception {
        DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
        assertDominionAdmin(context.player(), dominion);
        return dominion;
    }

    private GroupDTO requireGroup(ActionContext context, DominionDTO dominion) throws Exception {
        return toGroupDTO(dominion, context.requireRouteArgument("group.name"));
    }

    private MenuRoute groupRoute(String dominionName, String groupName) {
        return new MenuRoute("group_flags", 1, Map.of(
                "dominion.name", dominionName,
                "group.name", groupName
        ));
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
            throw new IllegalArgumentException("Privilege flag operation does not accept arguments at "
                    + action.configPath());
        }
    }
}
