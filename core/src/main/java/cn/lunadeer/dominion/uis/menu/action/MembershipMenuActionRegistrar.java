package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.GroupDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.doos.PlayerDOO;
import cn.lunadeer.dominion.providers.GroupProvider;
import cn.lunadeer.dominion.providers.MemberProvider;
import cn.lunadeer.dominion.uis.dominion.DominionManage;
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
import static cn.lunadeer.dominion.misc.Converts.toMemberDTO;
import static cn.lunadeer.dominion.misc.Converts.toPlayerDTO;

/**
 * Registers member and group list action groups for configured management menus.
 */
public final class MembershipMenuActionRegistrar {

    private static final InputSchema CREATE_GROUP_SCHEMA = new InputSchema(List.of(
            InputFieldSpec.requiredText("group_name", 1, 64, null)
    ));

    private final UiCallbackSessionManager sessions;
    private final ChatInputWorkflow inputWorkflow;

    /**
     * Creates membership action groups with shared chat input and legacy back transitions.
     */
    public MembershipMenuActionRegistrar(UiCallbackSessionManager sessions, ChatInputWorkflow inputWorkflow) {
        this.sessions = sessions;
        this.inputWorkflow = inputWorkflow;
    }

    /**
     * Registers member, group, and selection action groups.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("open-member-flags", this::openMemberFlags, this::requireNoArguments);
        registry.register("remove-member", this::removeMember, this::requireNoArguments);
        registry.register("open-player-selection", this::openPlayerSelection, this::requireNoArguments);
        registry.register("add-member", this::addMember, this::requireNoArguments);
        registry.register("back-member-list-route", this::backMemberList, this::requireNoArguments);
        registry.register("back-members-dominion-manage", this::backDominionManage, this::requireNoArguments);

        registry.register("open-group", this::openGroup, this::requireNoArguments);
        registry.register("delete-group", this::deleteGroup, this::requireNoArguments);
        registry.register("create-group-input", this::createGroupInput, this::requireNoArguments);
        registry.register("open-group-flags", this::openGroupFlags, this::requireNoArguments);
        registry.register("open-group-member-selection", this::openGroupMemberSelection, this::requireNoArguments);
        registry.register("remove-group-member", this::removeGroupMember, this::requireNoArguments);
        registry.register("add-group-member", this::addGroupMember, this::requireNoArguments);
        registry.register("back-group-list-route", this::backGroupList, this::requireNoArguments);
        registry.register("back-group-manage", this::backGroupManage, this::requireNoArguments);
    }

    private CompletableFuture<ActionResult> openMemberFlags(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            MemberDTO member = requireMember(context, dominion);
            return completed(ActionResult.open(memberFlagsRoute(dominion, member)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> removeMember(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            MemberDTO member = requireMember(context, dominion);
            return MemberProvider.getInstance().removeMember(context.player(), dominion, member)
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> openPlayerSelection(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            return completed(ActionResult.open(route("select_player", dominion.getName())));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> addMember(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            String playerName = context.requireTrustedArgument("player.name");
            PlayerDTO player;
            try {
                player = toPlayerDTO(playerName);
            } catch (Exception exception) {
                org.bukkit.entity.Player online = Dominion.instance.getServer().getPlayer(playerName);
                if (online == null) throw exception;
                player = PlayerDOO.create(online);
            }
            return MemberProvider.getInstance().addMember(context.player(), dominion, player)
                    .thenApply(updated -> updated == null
                            ? ActionResult.stop()
                            : ActionResult.open(route("member_list", dominion.getName())));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> backMemberList(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            return completed(ActionResult.open(route("member_list", dominion.getName())));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> backDominionManage(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            return completed(ActionResult.open(new MenuRoute(
                    "dominion_manage", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> openGroup(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = toGroupDTO(dominion, context.requireTrustedArgument("group.name"));
            return completed(ActionResult.open(groupRoute("group_manage", dominion, group)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> deleteGroup(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            String groupName = context.trustedArgument("group.name");
            if (groupName == null) groupName = context.requireRouteArgument("group.name");
            GroupDTO group = toGroupDTO(dominion, groupName);
            return GroupProvider.getInstance().deleteGroup(context.player(), dominion, group)
                    .thenApply(updated -> {
                        if (updated == null) return ActionResult.stop();
                        return context.menuId().equals("group_manage")
                                ? ActionResult.open(route("group_list", dominion.getName()))
                                : ActionResult.refresh();
                    });
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> createGroupInput(ActionContext context, ActionSpec action) {
        try {
            requireDominion(context);
            if (!context.submittedInputs().isEmpty()) {
                return createGroup(context);
            }
            sessions.invalidateForTransition(context.player());
            inputWorkflow.start(context, CREATE_GROUP_SCHEMA,
                    Language.createGroupInputterText.hint, this::createGroup);
            return completed(context.surface() == cn.lunadeer.dominion.uis.menu.route.UiSurface.DIALOG
                    ? ActionResult.close() : ActionResult.stop());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> createGroup(ActionContext context) {
        try {
            DominionDTO dominion = requireDominion(context);
            return GroupProvider.getInstance().createGroup(
                            context.player(), dominion, context.requireInput("group_name"))
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> openGroupFlags(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireRouteGroup(context, dominion);
            return completed(ActionResult.open(groupRoute("group_flags", dominion, group)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> openGroupMemberSelection(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireRouteGroup(context, dominion);
            return completed(ActionResult.open(groupRoute("select_member", dominion, group)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> removeGroupMember(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireRouteGroup(context, dominion);
            MemberDTO member = toMemberDTO(dominion, context.requireTrustedArgument("member.name"));
            return GroupProvider.getInstance().removeMember(context.player(), dominion, group, member)
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> addGroupMember(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireRouteGroup(context, dominion);
            MemberDTO member = toMemberDTO(dominion, context.requireTrustedArgument("member.name"));
            return GroupProvider.getInstance().addMember(context.player(), dominion, group, member)
                    .thenApply(updated -> updated == null
                            ? ActionResult.stop()
                            : ActionResult.open(groupRoute("group_manage", dominion, group)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> backGroupList(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            return completed(ActionResult.open(route("group_list", dominion.getName())));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> backGroupManage(ActionContext context, ActionSpec action) {
        try {
            DominionDTO dominion = requireDominion(context);
            GroupDTO group = requireRouteGroup(context, dominion);
            return completed(ActionResult.open(groupRoute("group_manage", dominion, group)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private DominionDTO requireDominion(ActionContext context) throws Exception {
        requireDefaultPermission(context);
        DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
        assertDominionAdmin(context.player(), dominion);
        return dominion;
    }

    private MemberDTO requireMember(ActionContext context, DominionDTO dominion) throws Exception {
        return toMemberDTO(dominion, context.requireTrustedArgument("member.name"));
    }

    private GroupDTO requireRouteGroup(ActionContext context, DominionDTO dominion) throws Exception {
        return toGroupDTO(dominion, context.requireRouteArgument("group.name"));
    }

    private MenuRoute route(String menuId, String dominionName) {
        return new MenuRoute(menuId, 1, Map.of("dominion.name", dominionName));
    }

    private MenuRoute memberFlagsRoute(DominionDTO dominion, MemberDTO member) {
        return new MenuRoute("member_flags", 1, Map.of(
                "dominion.name", dominion.getName(),
                "member.name", member.getPlayer().getLastKnownName()
        ));
    }

    private MenuRoute groupRoute(String menuId, DominionDTO dominion, GroupDTO group) {
        return new MenuRoute(menuId, 1, Map.of(
                "dominion.name", dominion.getName(),
                "group.name", group.getNamePlain()
        ));
    }

    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException("Membership operation does not accept arguments at "
                    + action.configPath());
        }
    }

    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
