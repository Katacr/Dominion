package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionResult;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.action.BuiltInActionRegistrar;
import cn.lunadeer.dominion.uis.menu.action.DominionActionRegistry;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.cui.ChestMenuDefinition;
import cn.lunadeer.dominion.uis.menu.cui.ChestMenuLoader;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.data.PageSlice;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuLoader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TuiResourceTest {

    private static final List<String> BUNDLED_MENU_IDS = List.of(
            "action_demo", "template_list", "select_template", "env_flags",
            "template_flags", "member_flags", "guest_flags", "group_flags",
            "member_list", "select_player", "group_list", "group_manage", "select_member",
            "copy_menu", "dominion_copy", "title_list", "set_size", "main_menu", "ui_preferences",
            "language_preferences", "dominion_manage",
            "dominion_list", "all_dominions", "all_dominions_of_player", "migrate_list"
    );
    private static final List<String> BUNDLED_CHEST_MENU_IDS = List.of(
            "main_menu", "ui_preferences", "language_preferences", "dominion_list", "env_flags",
            "dominion_manage", "copy_menu", "set_size",
            "group_list", "group_manage", "group_flags", "select_member", "guest_flags", "member_list",
            "select_player", "member_flags", "select_template", "template_list", "template_flags",
            "dominion_copy", "title_list", "all_dominions", "all_dominions_of_player", "migrate_list"
    );
    private static final List<String> BUNDLED_DIALOG_MENU_IDS = List.of(
            "action_demo", "main_menu", "ui_preferences", "language_preferences", "dominion_manage", "copy_menu",
            "set_size", "dominion_list",
            "all_dominions", "all_dominions_of_player", "migrate_list", "env_flags", "guest_flags",
            "group_flags", "member_flags", "template_flags", "member_list", "select_player", "group_list",
            "group_manage", "select_member", "select_template", "template_list", "dominion_copy", "title_list"
    );

    @Test
    void bundledTuiResourcesAreValidYaml() throws Exception {
        YamlConfiguration manifest = loadYaml("uis/manifest.yml");
        assertEquals(List.of("en_US", "zh_CN"), manifest.getStringList("locales"));
        assertEquals(BUNDLED_MENU_IDS, manifest.getStringList("text-menus"));
        assertEquals(BUNDLED_CHEST_MENU_IDS, manifest.getStringList("chest-menus"));
        assertEquals(BUNDLED_DIALOG_MENU_IDS, manifest.getStringList("dialog-menus"));
        for (String menuId : BUNDLED_CHEST_MENU_IDS) {
            assertYaml("uis/en_US/chest_menu/" + menuId + ".yml", "Layout");
            assertYaml("uis/zh_CN/chest_menu/" + menuId + ".yml", "Layout");
        }
        assertYaml("uis/_shared/action_demo.yml", "action-groups");
        assertYaml("uis/en_US/text_menu/action_demo.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/action_demo.yml", "Layout");
        for (String menuId : BUNDLED_DIALOG_MENU_IDS) {
            assertYaml("uis/en_US/dialog_menu/" + menuId + ".yml", "Bottom");
            assertYaml("uis/zh_CN/dialog_menu/" + menuId + ".yml", "Bottom");
        }
        assertYaml("uis/_shared/template_list.yml", "data");
        assertYaml("uis/en_US/text_menu/template_list.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/template_list.yml", "Layout");
        assertYaml("uis/_shared/select_template.yml", "route");
        assertYaml("uis/en_US/text_menu/select_template.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/select_template.yml", "Layout");
        assertYaml("uis/_shared/env_flags.yml", "route");
        assertYaml("uis/en_US/text_menu/env_flags.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/env_flags.yml", "Layout");
        assertYaml("uis/_shared/template_flags.yml", "route");
        assertYaml("uis/en_US/text_menu/template_flags.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/template_flags.yml", "Layout");
        assertYaml("uis/_shared/member_flags.yml", "route");
        assertYaml("uis/en_US/text_menu/member_flags.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/member_flags.yml", "Layout");
        assertYaml("uis/_shared/guest_flags.yml", "route");
        assertYaml("uis/en_US/text_menu/guest_flags.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/guest_flags.yml", "Layout");
        assertYaml("uis/_shared/group_flags.yml", "route");
        assertYaml("uis/en_US/text_menu/group_flags.yml", "Layout");
        assertYaml("uis/zh_CN/text_menu/group_flags.yml", "Layout");
        for (String menuId : List.of(
                "dominion_list", "all_dominions", "all_dominions_of_player", "migrate_list")) {
            assertYaml("uis/_shared/" + menuId + ".yml", "data");
            assertYaml("uis/en_US/text_menu/" + menuId + ".yml", "Layout");
            assertYaml("uis/zh_CN/text_menu/" + menuId + ".yml", "Layout");
        }
    }

    @Test
    void bundledMenusPassSemanticLoading() throws Exception {
        ActionRegistry actions = new ActionRegistry();
        new BuiltInActionRegistrar(null).registerInto(actions);
        DominionActionRegistry dominion = new DominionActionRegistry();
        for (String operation : List.of(
                "legacy-main-menu", "open-template", "delete-template", "create-template-input",
                "apply-member-template", "back-member-flags", "toggle-env-flag", "back-dominion-manage",
                "toggle-template-flag", "rename-template-input", "back-template-list",
                "toggle-member-flag", "open-member-template-list", "back-member-list",
                "toggle-guest-flag", "back-guest-dominion-manage", "toggle-group-flag",
                "rename-group-input", "back-group-list", "open-member-flags", "remove-member",
                "open-player-selection", "add-member", "back-member-list-route",
                "back-members-dominion-manage", "open-group", "delete-group", "create-group-input",
                "open-group-flags", "open-group-member-selection", "remove-group-member",
                "add-group-member", "back-group-list-route", "back-group-manage",
                "open-copy-environment", "open-copy-guest", "open-copy-member", "open-copy-group",
                "copy-from-dominion", "back-copy-menu", "toggle-title", "back-title-main-menu",
                "back-size-dominion-manage", "start-create-dominion-input", "open-dominion-list",
                "open-title-list", "open-template-list", "open-migrate-list", "open-all-dominions",
                "reload-cache", "reload-config", "switch-to-cui", "open-size-menu", "open-env-flags",
                "switch-to-tui", "switch-to-dui", "open-ui-preferences", "open-language-preferences", "set-language",
                "open-guest-flags", "open-member-list", "open-group-list", "open-copy-menu",
                "set-dominion-tp", "start-rename-dominion-input", "start-enter-message-input",
                "start-leave-message-input", "start-map-color-input", "back-dominion-list",
                "manage-dominion", "delete-dominion", "teleport-dominion",
                "migrate-residence", "migrate-all-residences", "back-main-menu-route"
        )) {
            dominion.register(operation, (context, action) ->
                    CompletableFuture.completedFuture(ActionResult.stop()), action -> {
                if (!action.argument().isBlank()) {
                    throw new IllegalArgumentException("Unexpected operation arguments");
                }
            });
        }
        dominion.register("resize-input", (context, action) ->
                CompletableFuture.completedFuture(ActionResult.stop()));
        actions.register("dominion", dominion, dominion::validate);
        MenuDataRegistry data = new MenuDataRegistry();
        data.register("templates.owned", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("templates.member-apply-candidates", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("flags.environment", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("flags.template", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("flags.member", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("flags.guest", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("flags.group", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        data.register("menus.locales", (context, page, pageSize) ->
                CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        for (String provider : List.of("members.by-dominion", "players.member-candidates", "groups.by-dominion",
                "members.by-group", "members.ungrouped", "dominions.copy-sources", "titles.available",
                "dominions.managed", "dominions.all", "dominions.by-player", "residences.migration")) {
            data.register(provider, (context, page, pageSize) ->
                    CompletableFuture.completedFuture(PageSlice.paginate(List.of(), page, pageSize)));
        }
        TuiMenuRepository repository = new TuiMenuRepository(null, new ActionParser(), actions, data);
        ChestMenuLoader chestLoader = new ChestMenuLoader(new ActionParser(), actions, data);
        DialogMenuLoader dialogLoader = new DialogMenuLoader(new ActionParser(), actions);

        for (String menuId : BUNDLED_MENU_IDS) {
            SharedMenuDefinition shared = loadShared(menuId, actions);
            for (String locale : List.of("en_US", "zh_CN")) {
                TuiMenuDefinition menu = repository.loadTui(menuId, locale,
                        loadYaml("uis/" + locale + "/text_menu/" + menuId + ".yml"), shared);
                assertEquals(menuId, menu.menuId());
                if (menuId.equals("select_template")) {
                    assertEquals(Set.of("dominion.name", "member.name"),
                            menu.shared().requiredRouteArguments());
                } else if (menuId.equals("env_flags")) {
                    assertEquals(Set.of("dominion.name"), menu.shared().requiredRouteArguments());
                } else if (menuId.equals("template_flags")) {
                    assertEquals(Set.of("template.name"), menu.shared().requiredRouteArguments());
                } else if (menuId.equals("member_flags")) {
                    assertEquals(Set.of("dominion.name", "member.name"),
                            menu.shared().requiredRouteArguments());
                } else if (menuId.equals("guest_flags")) {
                    assertEquals(Set.of("dominion.name"), menu.shared().requiredRouteArguments());
                } else if (menuId.equals("group_flags")) {
                    assertEquals(Set.of("dominion.name", "group.name"),
                            menu.shared().requiredRouteArguments());
                } else if (Set.of("member_list", "select_player", "group_list").contains(menuId)) {
                    assertEquals(Set.of("dominion.name"), menu.shared().requiredRouteArguments());
                } else if (Set.of("group_manage", "select_member").contains(menuId)) {
                    assertEquals(Set.of("dominion.name", "group.name"),
                            menu.shared().requiredRouteArguments());
                } else if (Set.of("copy_menu", "set_size", "dominion_manage").contains(menuId)) {
                    assertEquals(Set.of("dominion.name"), menu.shared().requiredRouteArguments());
                } else if (menuId.equals("dominion_copy")) {
                    assertEquals(Set.of("dominion.name", "copy.type"),
                            menu.shared().requiredRouteArguments());
                } else if (menuId.equals("all_dominions_of_player")) {
                    assertEquals(Set.of("player.name"), menu.shared().requiredRouteArguments());
                }
            }
        }
        for (String menuId : BUNDLED_CHEST_MENU_IDS) {
            SharedMenuDefinition shared = loadShared(menuId, actions);
            for (String locale : List.of("en_US", "zh_CN")) {
                ChestMenuDefinition menu = chestLoader.load(menuId, locale,
                        loadYaml("uis/" + locale + "/chest_menu/" + menuId + ".yml"), shared);
                assertEquals(menuId, menu.menuId());
                if (Set.of("dominion_list", "env_flags").contains(menuId)) {
                    assertEquals(28, menu.dynamicPageSize());
                }
            }
        }
        for (String menuId : BUNDLED_DIALOG_MENU_IDS) {
            SharedMenuDefinition dialogShared = loadShared(menuId, actions);
            for (String locale : List.of("en_US", "zh_CN")) {
                DialogMenuDefinition dialog = dialogLoader.load(menuId, locale,
                        loadYaml("uis/" + locale + "/dialog_menu/" + menuId + ".yml"), dialogShared);
                assertEquals(menuId, dialog.menuId());
                if (menuId.equals("action_demo")) {
                    assertEquals(1, dialog.inputs().size());
                    assertEquals(4, dialog.bottom().buttons().size());
                } else if (!dialogShared.dataSources().isEmpty()) {
                    assertNotNull(dialog.dynamic());
                }
            }
        }
    }

    private void assertYaml(String path, String requiredKey) throws Exception {
        YamlConfiguration yaml = loadYaml(path);
        assertFalse(yaml.get(requiredKey) == null, path + " missing " + requiredKey);
    }

    private SharedMenuDefinition loadShared(String menuId, ActionRegistry registry) throws Exception {
        YamlConfiguration yaml = loadYaml("uis/_shared/" + menuId + ".yml");
        Map<String, String> sources = new LinkedHashMap<>();
        ConfigurationSection data = yaml.getConfigurationSection("data");
        if (data != null) {
            data.getKeys(false).forEach(key -> sources.put(key, data.getString(key + ".provider")));
        }
        Map<String, List<ActionSpec>> actionGroups = new LinkedHashMap<>();
        ConfigurationSection actionGroupsSection = yaml.getConfigurationSection("action-groups");
        assertNotNull(actionGroupsSection);
        ActionParser parser = new ActionParser();
        for (String actionGroup : actionGroupsSection.getKeys(false)) {
            List<ActionSpec> parsed = parser.parseAll(
                    yaml.getStringList("action-groups." + actionGroup + ".actions"),
                    "uis/_shared/" + menuId + ".yml.action-groups." + actionGroup + ".actions");
            parsed.forEach(registry::validate);
            actionGroups.put(actionGroup, parsed);
        }
        return new SharedMenuDefinition(menuId, Set.copyOf(yaml.getStringList("route.required")),
                Set.copyOf(yaml.getStringList("route.optional")), sources, actionGroups);
    }

    private YamlConfiguration loadYaml(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }
}
