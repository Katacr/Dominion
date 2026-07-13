package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.Dominion;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.doos.TemplateDOO;
import cn.lunadeer.dominion.uis.menu.input.ChatInputWorkflow;
import cn.lunadeer.dominion.uis.menu.input.InputFieldSpec;
import cn.lunadeer.dominion.uis.menu.input.InputSchema;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.session.UiCallbackSessionManager;
import cn.lunadeer.dominion.utils.Misc;
import cn.lunadeer.dominion.utils.Notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static cn.lunadeer.dominion.misc.Converts.toPriFlag;

/**
 * Registers trusted Dominion action groups used by configured template menus.
 */
public final class TemplateMenuActionRegistrar {

    private static final InputSchema CREATE_TEMPLATE_SCHEMA = new InputSchema(List.of(
            InputFieldSpec.requiredText("template_name", 1, 64, "\\S+")
    ));
    private static final InputSchema RENAME_TEMPLATE_SCHEMA = new InputSchema(List.of(
            InputFieldSpec.requiredText("template_new_name", 1, 64, "\\S+")
    ));

    private final UiCallbackSessionManager sessions;
    private final ChatInputWorkflow inputWorkflow;

    /**
     * Creates template action groups that retire visible callbacks before starting chat input.
     */
    public TemplateMenuActionRegistrar(UiCallbackSessionManager sessions, ChatInputWorkflow inputWorkflow) {
        this.sessions = sessions;
        this.inputWorkflow = inputWorkflow;
    }

    /**
     * Adds template action groups to the Dominion action namespace.
     */
    public void registerInto(DominionActionRegistry registry) {
        registry.register("open-template", this::openTemplate, this::requireNoArguments);
        registry.register("delete-template", this::deleteTemplate, this::requireNoArguments);
        registry.register("create-template-input", this::createTemplateInput, this::requireNoArguments);
        registry.register("toggle-template-flag", this::toggleTemplateFlag, this::requireNoArguments);
        registry.register("rename-template-input", this::renameTemplateInput, this::requireNoArguments);
        registry.register("back-template-list", this::backToTemplateList, this::requireNoArguments);
    }

    private CompletableFuture<ActionResult> openTemplate(ActionContext context, ActionSpec action) {
        try {
            String templateName = requireOwnedTemplate(context);
            return completed(ActionResult.open(templateRoute(templateName)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    private CompletableFuture<ActionResult> deleteTemplate(ActionContext context, ActionSpec action) {
        try {
            String templateName = requireOwnedTemplate(context);
            TemplateDOO.delete(context.player().getUniqueId(), templateName);
            Notification.info(context.player(), Language.templateCommandText.deleteTemplateSuccess, templateName);
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(Misc.formatString(
                    Language.templateCommandText.deleteTemplateFail, exception.getMessage()), exception));
        }
    }

    private CompletableFuture<ActionResult> createTemplateInput(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            if (!context.submittedInputs().isEmpty()) {
                return createTemplate(context);
            }
            sessions.invalidateForTransition(context.player());
            inputWorkflow.start(context, CREATE_TEMPLATE_SCHEMA,
                    Language.createTemplateInputterText.hint, this::createTemplate);
            return completed(context.surface() == cn.lunadeer.dominion.uis.menu.route.UiSurface.DIALOG
                    ? ActionResult.close() : ActionResult.stop());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    private CompletableFuture<ActionResult> createTemplate(ActionContext context) {
        try {
            requireDefaultPermission(context);
            String templateName = context.requireInput("template_name");
            if (TemplateDOO.selectAll(context.player().getUniqueId()).stream()
                    .anyMatch(template -> template.getName().equals(templateName))) {
                throw new IllegalStateException(Misc.formatString(
                        Language.templateCommandText.templateNameExist, templateName));
            }
            TemplateDOO.create(context.player().getUniqueId(), templateName);
            Notification.info(context.player(), Language.templateCommandText.createTemplateSuccess, templateName);
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(Misc.formatString(
                    Language.templateCommandText.createTemplateFail, exception.getMessage()), exception));
        }
    }

    /**
     * Revalidates and updates one route-bound template flag.
     */
    private CompletableFuture<ActionResult> toggleTemplateFlag(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            String templateName = context.requireRouteArgument("template.name");
            String flagName = context.requireTrustedArgument("flag.name");
            boolean value = parseBoolean(context.requireTrustedArgument("flag.next-value"));
            TemplateDOO template = requireOwnedTemplate(context.player().getUniqueId(), templateName);
            PriFlag flag = toPriFlag(flagName);
            if (!Flags.getAllPriFlagsEnable().contains(flag)) {
                throw new IllegalStateException("Privilege flag is not enabled: " + flagName);
            }
            template.setFlagValue(flag, value);
            Notification.info(context.player(), Language.templateCommandText.setFlagSuccess,
                    flagName, templateName, Boolean.toString(value));
            return completed(ActionResult.refresh());
        } catch (Exception exception) {
            return completed(ActionResult.failure(Misc.formatString(
                    Language.templateCommandText.setFlagFail, exception.getMessage()), exception));
        }
    }

    /**
     * Starts normalized chat input for the current template's new name.
     */
    private CompletableFuture<ActionResult> renameTemplateInput(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            requireOwnedTemplate(context.player().getUniqueId(),
                    context.requireRouteArgument("template.name"));
            if (!context.submittedInputs().isEmpty()) {
                return renameTemplate(context);
            }
            sessions.invalidateForTransition(context.player());
            inputWorkflow.start(context, RENAME_TEMPLATE_SCHEMA,
                    Language.renameTemplateInputterText.hint, this::renameTemplate);
            return completed(context.surface() == cn.lunadeer.dominion.uis.menu.route.UiSurface.DIALOG
                    ? ActionResult.close() : ActionResult.stop());
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage(), exception));
        }
    }

    /**
     * Applies a validated name and opens a route carrying the updated identity.
     */
    private CompletableFuture<ActionResult> renameTemplate(ActionContext context) {
        try {
            requireDefaultPermission(context);
            String oldName = context.requireRouteArgument("template.name");
            String newName = context.requireInput("template_new_name");
            TemplateDOO template = requireOwnedTemplate(context.player().getUniqueId(), oldName);
            if (!oldName.equals(newName)
                    && TemplateDOO.select(context.player().getUniqueId(), newName) != null) {
                throw new IllegalStateException(Misc.formatString(
                        Language.templateCommandText.templateNameExist, newName));
            }
            template.setName(newName);
            Notification.info(context.player(), Language.templateCommandText.renameTemplateSuccess, newName);
            return completed(ActionResult.open(templateRoute(newName)));
        } catch (Exception exception) {
            return completed(ActionResult.failure(Misc.formatString(
                    Language.templateCommandText.renameTemplateFail, exception.getMessage()), exception));
        }
    }

    /**
     * Returns to the configured template list even if the prior detail was removed concurrently.
     */
    private CompletableFuture<ActionResult> backToTemplateList(ActionContext context, ActionSpec action) {
        try {
            requireDefaultPermission(context);
            return completed(ActionResult.open(MenuRoute.of("template_list")));
        } catch (Exception exception) {
            return completed(ActionResult.failure(exception.getMessage()));
        }
    }

    // Trusted callback arguments identify a candidate, but ownership is always rechecked against storage.
    private String requireOwnedTemplate(ActionContext context) throws Exception {
        requireDefaultPermission(context);
        String templateName = context.requireTrustedArgument("template.name");
        requireOwnedTemplate(context.player().getUniqueId(), templateName);
        return templateName;
    }

    /**
     * Resolves a template only when it still belongs to the triggering player.
     */
    private TemplateDOO requireOwnedTemplate(UUID playerId, String templateName) throws Exception {
        TemplateDOO template = TemplateDOO.select(playerId, templateName);
        if (template == null) {
            throw new IllegalStateException(Misc.formatString(
                    Language.templateCommandText.templateNotExist, templateName));
        }
        return template;
    }

    /**
     * Creates the canonical first-page route for template settings.
     */
    private MenuRoute templateRoute(String templateName) {
        return new MenuRoute("template_flags", 1, Map.of("template.name", templateName));
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

    private void requireDefaultPermission(ActionContext context) {
        if (!context.player().hasPermission(Dominion.defaultPermission)) {
            throw new IllegalStateException(Misc.formatString(
                    Language.commandExceptionText.noPermission, Dominion.defaultPermission));
        }
    }

    private CompletableFuture<ActionResult> completed(ActionResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private void requireNoArguments(ActionSpec action) {
        if (!action.argument().isBlank()) {
            throw new IllegalArgumentException("Template operation does not accept arguments at "
                    + action.configPath());
        }
    }
}
