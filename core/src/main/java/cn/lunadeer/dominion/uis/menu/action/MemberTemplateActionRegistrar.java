package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.doos.MemberDOO;
import cn.lunadeer.dominion.doos.TemplateDOO;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.utils.Misc;
import cn.lunadeer.dominion.utils.Notification;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Asserts.assertDominionOwner;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toMemberDTO;

/**
 * Registers trusted action groups for selecting and applying member templates.
 */
public final class MemberTemplateActionRegistrar {

    /**
     * Adds member-template action groups to the Dominion action namespace.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("apply-member-template", this::applyTemplate, this::requireNoArguments);
        registry.register("back-member-flags", this::backToMemberFlags, this::requireNoArguments);
    }

    private CompletableFuture<ActionResult> applyTemplate(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            String dominionName = context.requireRouteArgument("dominion.name");
            String memberName = context.requireRouteArgument("member.name");
            String templateName = context.requireTrustedArgument("template.name");
            DominionDTO dominion = toDominionDTO(dominionName);
            MemberDTO member = toMemberDTO(dominion, memberName);
            TemplateDOO template = TemplateDOO.select(context.player().getUniqueId(), templateName);
            if (template == null) {
                throw new IllegalStateException(Misc.formatString(
                        Language.templateCommandText.templateNotExist, templateName));
            }
            if (template.getFlagValue(Flags.ADMIN)) {
                assertDominionOwner(context.player(), dominion);
            } else {
                assertDominionAdmin(context.player(), dominion);
            }
            ((MemberDOO) member).applyTemplate(template);
            Notification.info(context.player(), Language.templateCommandText.applyTemplateSuccess,
                    templateName, memberName);
            return completed(ActionResult.open(memberFlagsRoute(dominionName, memberName)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(Misc.formatString(
                    Language.templateCommandText.applyTemplateFail, exception.getMessage()), exception));
        }
    }

    private CompletableFuture<ActionResult> backToMemberFlags(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            String dominionName = context.requireRouteArgument("dominion.name");
            String memberName = context.requireRouteArgument("member.name");
            DominionDTO dominion = toDominionDTO(dominionName);
            assertDominionAdmin(context.player(), dominion);
            toMemberDTO(dominion, memberName);
            return completed(ActionResult.open(memberFlagsRoute(dominionName, memberName)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    /**
     * Creates the canonical first-page route for member settings.
     */
    private MenuRoute memberFlagsRoute(String dominionName, String memberName) {
        return new MenuRoute("member_flags", 1, Map.of(
                "dominion.name", dominionName,
                "member.name", memberName
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
            throw new IllegalArgumentException("Member template operation does not accept arguments at "
                    + action.configPath());
        }
    }

    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
