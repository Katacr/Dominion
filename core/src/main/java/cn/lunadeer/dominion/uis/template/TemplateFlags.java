package cn.lunadeer.dominion.uis.template;

import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.commands.TemplateCommand;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.configuration.uis.ChestUserInterface;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.doos.TemplateDOO;
import cn.lunadeer.dominion.inputters.RenameTemplateInputter;
import cn.lunadeer.dominion.uis.AbstractUI;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
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
import cn.lunadeer.dominion.utils.stui.components.buttons.FunctionalButton;
import cn.lunadeer.dominion.utils.stui.components.buttons.ListViewButton;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;

import static cn.lunadeer.dominion.Dominion.defaultPermission;
import static cn.lunadeer.dominion.misc.Converts.toIntegrity;
import static cn.lunadeer.dominion.utils.Misc.*;

public class TemplateFlags extends AbstractUI {

    public static void show(CommandSender sender, String templateName, String pageStr) {
        new TemplateFlags().displayByPreference(sender, templateName, pageStr);
    }

    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ TUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class TemplateSettingTuiText extends ConfigurationPart {
        public String title = "Template Setting";
        public String button = "SETTING";
        public String notFound = "Template {0} not found.";
    }

    public static ListViewButton button(CommandSender sender, String templateName) {
        return (ListViewButton) new ListViewButton(TextUserInterface.templateSettingTuiText.button) {
            @Override
            public void function(String pageStr) {
                show(sender, templateName, pageStr);
            }
        }.needPermission(defaultPermission);
    }

    @Override
    protected void showTUI(Player player, String... args) throws Exception {
        String pageStr = args.length > 1 ? args[1] : "1";
        String templateName = args[0];
        int page = toIntegrity(pageStr);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasMenu("template_flags")) {
            // Keep the legacy entry point while route state remains owned by the configured TUI.
            ConfiguredTuiManager.getInstance().show(player, new MenuRoute(
                    "template_flags", page, Map.of("template.name", templateName)));
            return;
        }

        TemplateDOO template = TemplateDOO.select(player.getUniqueId(), templateName);
        if (template == null) {
            Notification.error(player, TextUserInterface.templateSettingTuiText.notFound, templateName);
            return;
        }

        ListView view = ListView.create(10, button(player, templateName));
        view.title(TextUserInterface.templateSettingTuiText.title);
        view.navigator(Line.create()
                .append(MainMenu.button(player).build())
                .append(TemplateList.button(player).build())
                .append(TextUserInterface.templateSettingTuiText.button)
        );

        view.add(Line.create().append(RenameTemplateInputter.createTuiButtonOn(player, templateName, pageStr).build()));
        for (PriFlag flag : Flags.getAllPriFlagsEnable()) {
            view.add(createOption(player, flag, template.getFlagValue(flag), template.getName(), pageStr));
        }
        view.showOn(player, toIntegrity(pageStr));
    }

    private static Line createOption(Player player, PriFlag flag, boolean value, String templateName, String pageStr) {
        if (value) {
            return Line.create()
                    .append(new FunctionalButton("☑") {
                        @Override
                        public void function() {
                            TemplateCommand.setTemplateFlag(player, templateName, flag.getFlagName(), "false", pageStr);
                        }
                    }.needPermission(defaultPermission).green().build())
                    .append(Component.text(flag.getDisplayName()).hoverEvent(Component.text(flag.getDescription())));
        } else {
            return Line.create()
                    .append(new FunctionalButton("☐") {
                        @Override
                        public void function() {
                            TemplateCommand.setTemplateFlag(player, templateName, flag.getFlagName(), "true", pageStr);
                        }
                    }.needPermission(defaultPermission).red().build())
                    .append(Component.text(flag.getDisplayName()).hoverEvent(Component.text(flag.getDescription())));
        }
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ TUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ CUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class TemplateSettingCui extends ConfigurationPart {
        public String title = "§6✦ §3§lTemplate: {0} §6✦";
        public ListViewConfiguration listConfiguration = new ListViewConfiguration(
                'i',
                List.of(
                        "<#####RD#",
                        "#iiiiiii#",
                        "#iiiiiii#",
                        "#iiiiiii#",
                        "p#######n"
                )
        );

        public ButtonConfiguration backButton = ButtonConfiguration.createMaterial(
                '<', Material.RED_STAINED_GLASS_PANE,
                "§c« Back to Template List",
                List.of(
                        "§7Return to the template",
                        "§7list menu.",
                        "",
                        "§e▶ Click to go back"
                )
        );

        public ButtonConfiguration renameButton = ButtonConfiguration.createMaterial(
                'R', Material.NAME_TAG,
                "§eRename Template",
                List.of(
                        "§7Rename this template.",
                        "",
                        "§e▶ Click to rename this template"
                )
        );

        public ButtonConfiguration deleteButton = ButtonConfiguration.createMaterial(
                'D', Material.RED_DYE,
                "§cDelete Template",
                List.of(
                        "§7Delete this template.",
                        "§7This action cannot be undone.",
                        "",
                        "§e▶ Click to delete this template"
                )
        );

        public String flagItemName = "§6⚙️ §e{0}";
        public String flagItemStateTrue = "§a§l✓ ENABLED";
        public String flagItemStateFalse = "§c§l✗ DISABLED";
        public List<String> flagItemLore = List.of(
                "§7Status: {0}",
                "",
                "§7Description:",
                "§f{1}",
                "§f{2}",
                "",
                "§e▶ Click to toggle this setting",
                "§8Will not take effect until the",
                "§8next time the template is applied."
        );
    }

    @Override
    protected void showCUI(Player player, String... args) throws Exception {
        String templateName = args[0];
        String pageStr = args.length > 1 ? args[1] : "1";
        int page = toIntegrity(pageStr, 1);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasChestMenu("template_flags")) {
            ConfiguredTuiManager.getInstance().showCui(player, new MenuRoute(
                    "template_flags", page, Map.of("template.name", templateName)));
            return;
        }

        TemplateDOO template = TemplateDOO.select(player.getUniqueId(), templateName);
        if (template == null) {
            Notification.error(player, TextUserInterface.templateSettingTuiText.notFound, templateName);
            return;
        }

        ChestListView view = ChestUserInterfaceManager.getInstance().getListViewOf(player);
        view.setTitle(formatString(ChestUserInterface.templateSettingCui.title, templateName));
        view.applyListConfiguration(ChestUserInterface.templateSettingCui.listConfiguration, page);

        view.setButton(ChestUserInterface.templateSettingCui.backButton.getSymbol(),
                new ChestButton(ChestUserInterface.templateSettingCui.backButton) {
                    @Override
                    public void onClick(ClickType type) {
                        TemplateList.show(player, "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.templateSettingCui.deleteButton.getSymbol(),
                new ChestButton(ChestUserInterface.templateSettingCui.deleteButton) {
                    @Override
                    public void onClick(ClickType type) {
                        TemplateCommand.deleteTemplate(player, templateName, "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.templateSettingCui.renameButton.getSymbol(),
                new ChestButton(ChestUserInterface.templateSettingCui.renameButton) {
                    @Override
                    public void onClick(ClickType type) {
                        RenameTemplateInputter.createOn(player, templateName, "1");
                        view.close();
                    }
                }
        );

        for (int i = 0; i < Flags.getAllPriFlags().size(); i++) {
            PriFlag flag = Flags.getAllPriFlags().get(i);
            Integer itemPage = (int) Math.ceil((double) (i + 1) / view.getPageSize());
            String flagState = template.getFlagValue(flag) ? ChestUserInterface.templateSettingCui.flagItemStateTrue : ChestUserInterface.templateSettingCui.flagItemStateFalse;
            String flagName = formatString(ChestUserInterface.templateSettingCui.flagItemName, flag.getDisplayName());
            List<String> descriptions = foldLore2Line(flag.getDescription(), 30);
            List<String> flagLore = formatStringList(ChestUserInterface.templateSettingCui.flagItemLore, flagState, descriptions.get(0), descriptions.get(1));
            ButtonConfiguration btnConfig = ButtonConfiguration.createMaterial(
                    ChestUserInterface.templateSettingCui.listConfiguration.itemSymbol.charAt(0),
                    flag.getMaterial(),
                    flagName,
                    flagLore
            );
            view.addItem(new ChestButton(btnConfig) {
                @Override
                public void onClick(ClickType type) {
                    TemplateCommand.setTemplateFlag(player, templateName, flag.getFlagName(),
                            String.valueOf(!template.getFlagValue(flag)), itemPage.toString());
                }
            });
        }

        view.open();
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ CUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ Console ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    @Override
    protected void showConsole(CommandSender sender, String... args) throws Exception {
        Notification.warn(sender, Language.consoleText.inGameOnly);
    }
}
