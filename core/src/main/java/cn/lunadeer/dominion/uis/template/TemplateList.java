package cn.lunadeer.dominion.uis.template;

import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.configuration.uis.ChestUserInterface;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.doos.TemplateDOO;
import cn.lunadeer.dominion.inputters.CreateTemplateInputter;
import cn.lunadeer.dominion.uis.AbstractUI;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.menu.tui.ConfiguredTuiManager;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.configuration.ConfigurationPart;
import cn.lunadeer.dominion.utils.scui.ChestButton;
import cn.lunadeer.dominion.utils.scui.ChestListView;
import cn.lunadeer.dominion.utils.scui.ChestUserInterfaceManager;
import cn.lunadeer.dominion.utils.scui.configuration.ButtonConfiguration;
import cn.lunadeer.dominion.utils.scui.configuration.ListViewConfiguration;
import cn.lunadeer.dominion.utils.stui.ListView;
import cn.lunadeer.dominion.utils.stui.components.Line;
import cn.lunadeer.dominion.utils.stui.components.buttons.Button;
import cn.lunadeer.dominion.utils.stui.components.buttons.ListViewButton;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

import static cn.lunadeer.dominion.Dominion.defaultPermission;
import static cn.lunadeer.dominion.commands.TemplateCommand.deleteTemplate;
import static cn.lunadeer.dominion.misc.Converts.toIntegrity;

public class TemplateList extends AbstractUI {

    public static void show(CommandSender sender, String pageStr) {
        new TemplateList().displayByPreference(sender, pageStr);
    }

    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ TUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class TemplateListTuiText extends ConfigurationPart {
        public String title = "Template List";
        public String button = "TEMPLATES";
        public String description = "Templates can be used to quickly setup privileges of member.";
        public String deleteButton = "DELETE";
    }

    public static ListViewButton button(CommandSender sender) {
        return (ListViewButton) new ListViewButton(TextUserInterface.templateListTuiText.button) {
            @Override
            public void function(String pageStr) {
                TemplateList.show(sender, pageStr);
            }
        }.needPermission(defaultPermission);
    }

    @Override
    protected void showTUI(Player player, String... args) throws Exception {
        int page = toIntegrity(args[0], 1);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasMenu("template_list")) {
            // Keep the legacy entry point while routing migrated TUI players through the configured page.
            ConfiguredTuiManager.getInstance().show(player, "template_list", page);
            return;
        }
        List<TemplateDOO> templates = TemplateDOO.selectAll(player.getUniqueId());

        ListView view = ListView.create(10, button(player));
        view.title(TextUserInterface.templateListTuiText.title);
        view.navigator(Line.create()
                .append(MainMenu.button(player).build())
                .append(TextUserInterface.templateListTuiText.button));

        view.add(Line.create()
                .append(CreateTemplateInputter.createTuiButtonOn(player).needPermission(defaultPermission).build()));

        for (TemplateDOO template : templates) {
            Button setting = TemplateFlags.button(player, template.getName()).green();
            Button delete = new ListViewButton(TextUserInterface.templateListTuiText.deleteButton) {
                @Override
                public void function(String pageStr) {
                    deleteTemplate(player, template.getName(), pageStr);
                }
            }.needPermission(defaultPermission).red();
            Line line = Line.create()
                    .append(delete.build())
                    .append(setting.build())
                    .append(template.getName());
            view.add(line);
        }

        view.showOn(player, page);
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ TUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ CUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class TemplateListCui extends ConfigurationPart {
        public String title = "§6✦ §d§lTemplate Management §6✦";
        public ListViewConfiguration listConfiguration = new ListViewConfiguration(
                'i',
                List.of(
                        "<########",
                        "#iiiiiii#",
                        "#iiiiiii#",
                        "#iiiiiii#",
                        "p#######n"
                )
        );

        public ButtonConfiguration templateItemButton = ButtonConfiguration.createMaterial(
                'i', Material.PAPER, "§6✦ §f{0} §6✦",
                List.of(
                        "§7Template for managing permissions",
                        "§8and access controls quickly.",
                        "",
                        "§e▶ Click to edit template"
                )
        );

        public ButtonConfiguration createTemplateButton = ButtonConfiguration.createMaterial(
                'i', Material.LIME_DYE,
                "§a+ Create New Template",
                List.of(
                        "§7Create a new permission template",
                        "§8to quickly setup member privileges.",
                        "",
                        "§e▶ Click to create template",
                        "",
                        "§7Action: §aCreate Template"
                )
        );

        public ButtonConfiguration backButton = ButtonConfiguration.createMaterial(
                '<', Material.RED_STAINED_GLASS_PANE,
                "§c« Back to Main Menu",
                List.of(
                        "§7Return to the main menu",
                        "§8to access other features.",
                        "",
                        "§e▶ Click to go back"
                )
        );
    }

    @Override
    protected void showCUI(Player player, String... args) throws Exception {
        int page = toIntegrity(args[0], 1);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasChestMenu("template_list")) {
            ConfiguredTuiManager.getInstance().showCui(player, "template_list", page);
            return;
        }

        ChestListView view = ChestUserInterfaceManager.getInstance().getListViewOf(player);
        view.setTitle(ChestUserInterface.templateListCui.title);
        view.applyListConfiguration(ChestUserInterface.templateListCui.listConfiguration, page);

        view.addItem(new ChestButton(ChestUserInterface.templateListCui.createTemplateButton) {
            @Override
            public void onClick(ClickType type) {
                CreateTemplateInputter.createOn(player);
                view.close();
            }
        });

        List<TemplateDOO> templates = TemplateDOO.selectAll(player.getUniqueId());

        for (TemplateDOO template : templates) {
            ChestButton btn = new ChestButton(ChestUserInterface.templateListCui.templateItemButton) {
                @Override
                public void onClick(ClickType type) {
                    TemplateFlags.show(player, template.getName(), "1");
                }
            };
            btn = btn.setDisplayNameArgs(template.getName());
            view.addItem(btn);
        }

        view.setButton(ChestUserInterface.templateListCui.backButton.getSymbol(),
                new ChestButton(ChestUserInterface.templateListCui.backButton) {
                    @Override
                    public void onClick(ClickType type) {
                        MainMenu.show(player, "1");
                    }
                }
        );

        view.open();
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ CUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ Console ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    @Override
    protected void showConsole(CommandSender sender, String... args) throws Exception {
        Notification.warn(sender, Language.consoleText.inGameOnly);
    }
}
