package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.providers.MemberProvider;
import cn.lunadeer.dominion.uis.dominion.manage.member.MemberList;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toMemberDTO;
import static cn.lunadeer.dominion.misc.Converts.toPriFlag;

/**
 * Registers trusted action groups for configured dominion member flag menus.
 */
public final class MemberFlagActionRegistrar {

    private final UiCallbackSessionManager sessions;

    /**
     * Creates member flag action groups with explicit cleanup for the remaining legacy back target.
     */
    public MemberFlagActionRegistrar(UiCallbackSessionManager sessions) {
        this.sessions = sessions;
    }

    /**
     * Adds member flag and navigation action groups to the Dominion action namespace.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("toggle-member-flag", this::toggleFlag, this::requireNoArguments);
        registry.register("open-member-template-list", this::openTemplateList, this::requireNoArguments);
        registry.register("back-member-list", this::backToMemberList, this::requireNoArguments);
    }

    /**
     * Revalidates route and callback state before delegating a member flag update.
     */
    private CompletableFuture<ActionResult> toggleFlag(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            MemberDTO member = requireMember(context, dominion);
            String flagName = context.requireTrustedArgument("flag.name");
            PriFlag flag = toPriFlag(flagName);
            validateVisibleFlag(member, flag);
            boolean value = parseBoolean(context.requireTrustedArgument("flag.next-value"));
            return MemberProvider.getInstance().setMemberFlag(
                            context.player(), dominion, member, flag, value)
                    .thenApply(updated -> updated == null ? ActionResult.stop() : ActionResult.refresh());
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Opens template selection with the same trusted member route identity.
     */
    private CompletableFuture<ActionResult> openTemplateList(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            MemberDTO member = requireMember(context, dominion);
            return CompletableFuture.completedFuture(ActionResult.open(new MenuRoute(
                    "select_template", 1, memberRouteArguments(dominion, member))));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage()));
        }
    }

    /**
     * Invalidates configured callbacks before returning to the legacy member list.
     */
    private CompletableFuture<ActionResult> backToMemberList(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            DominionDTO dominion = requireDominion(context);
            return CompletableFuture.completedFuture(ActionResult.open(new MenuRoute(
                    "member_list", 1, Map.of("dominion.name", dominion.getName()))));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(ActionResult.failure(exception.getMessage()));
        }
    }

    /**
     * Resolves the route dominion and requires current administrator access.
     */
    private DominionDTO requireDominion(ActionContext context) throws Exception {
        DominionDTO dominion = toDominionDTO(context.requireRouteArgument("dominion.name"));
        assertDominionAdmin(context.player(), dominion);
        return dominion;
    }

    /**
     * Resolves the route member inside the already validated dominion.
     */
    private MemberDTO requireMember(ActionContext context, DominionDTO dominion) throws Exception {
        return toMemberDTO(dominion, context.requireRouteArgument("member.name"));
    }

    /**
     * Rejects callbacks for flags that the current member state would not render.
     */
    private void validateVisibleFlag(MemberDTO member, PriFlag flag) {
        if (member.getFlagValue(Flags.ADMIN)) {
            if (!flag.equals(Flags.ADMIN)) {
                throw new IllegalStateException("Only the ADMIN flag is editable for administrator members");
            }
            return;
        }
        if (!Flags.getAllPriFlagsEnable().contains(flag)) {
            throw new IllegalStateException("Privilege flag is not enabled: " + flag.getFlagName());
        }
    }

    /**
     * Builds canonical server-owned arguments shared by both member pages.
     */
    private Map<String, String> memberRouteArguments(DominionDTO dominion, MemberDTO member) {
        return Map.of(
                "dominion.name", dominion.getName(),
                "member.name", member.getPlayer().getLastKnownName()
        );
    }

    /**
     * Parses a trusted callback boolean without accepting ambiguous values.
     */
    private boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalStateException("Invalid trusted boolean value: " + value);
    }

    /**
     * Applies the same base permission used by legacy member controls.
     */
    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    /**
     * Prevents YAML from supplying domain parameters to trusted action groups.
     */
    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException("Member flag operation does not accept arguments at "
                    + action.configPath());
        }
    }
}
