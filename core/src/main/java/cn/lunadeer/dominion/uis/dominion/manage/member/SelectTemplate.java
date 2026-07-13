package cn.lunadeer.dominion.uis.dominion.manage.member;

import cn.lunadeer.dominion.commands.TemplateCommand;
import cn.lunadeer.dominion.configuration.uis.ChestUserInterface;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.doos.TemplateDOO;
import cn.lunadeer.dominion.uis.AbstractUI;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.tui.ConfiguredTuiManager;
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
import static cn.lunadeer.dominion.utils.Misc.formatString;

public class SelectTemplate extends AbstractUI {

    public static void show(CommandSender sender, String dominionName, String playerName, String pageStr) {
        new SelectTemplate().displayByPreference(sender, dominionName, playerName, pageStr);
    }

    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ TUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class SelectTemplateTuiText extends ConfigurationPart {
        public String title = "Select Template";
        public String description = "Select a template to apply to this member.";
        public String button = "SELECT TEMPLATE";
        public String back = "BACK";
        public String apply = "APPLY";
    }

    public static ListViewButton button(CommandSender sender, String dominionName, String playerName) {
        return (ListViewButton) new ListViewButton(TextUserInterface.selectTemplateTuiText.button) {
            @Override
            public void function(String pageStr) {
                show(sender, dominionName, playerName, pageStr);
            }
        }.needPermission(defaultPermission).setHoverText(TextUserInterface.selectTemplateTuiText.description);
    }

    @Override
    protected void showTUI(Player player, String... args) throws Exception {
        String dominionName = args[0];
        String playerName = args[1];
        String pageStr = args[2];

        int page = toIntegrity(pageStr);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasMenu("select_template")) {
            // Preserve the legacy entry point while keeping route targets in server-owned state.
            ConfiguredTuiManager.getInstance().show(player, new MenuRoute(
                    "select_template",
                    page,
                    Map.of("dominion.name", dominionName, "member.name", playerName)
            ));
            return;
        }
        List<TemplateDOO> templates = TemplateDOO.selectAll(player.getUniqueId());

        ListView view = ListView.create(10, button(player, dominionName, playerName));
        view.title(TextUserInterface.selectTemplateTuiText.title);
        Line sub = Line.create()
                .append(MemberFlags.button(player, dominionName, playerName).setText(TextUserInterface.selectTemplateTuiText.back).build());
        view.subtitle(sub);

        for (TemplateDOO template : templates) {
            view.add(Line.create()
                    .append(new FunctionalButton(TextUserInterface.selectTemplateTuiText.apply) {
                        @Override
                        public void function() {
                            TemplateCommand.memberApplyTemplate(player, dominionName, playerName, template.getName());
                        }
                    }.build())
                    .append(Component.text(template.getName())));
        }
        view.showOn(player, page);
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ TUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ CUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class SelectTemplateCui extends ConfigurationPart {
        public String title = "§6✦ §5§lSelect Template for {0} §6✦";
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

        public ButtonConfiguration backButton = ButtonConfiguration.createMaterial(
                '<', Material.BARRIER,
                "§c« Back to Member Settings",
                List.of(
                        "§7Return to the member settings",
                        "§7without applying a template.",
                        "",
                        "§e▶ Click to go back"
                )
        );

        public ButtonConfiguration templateButton = ButtonConfiguration.createMaterial(
                'i', Material.WRITABLE_BOOK,
                "§a⚙ §2{0}",
                List.of(
                        "§7Apply this permission template",
                        "§7to member {0}.",
                        "",
                        "§7Template: §e{1}",
                        "",
                        "§2▶ Click to apply template",
                        "",
                        "§8This will override member's",
                        "§8current permissions."
                )
        );
    }

    @Override
    protected void showCUI(Player player, String... args) throws Exception {
        String dominionName = args[0];
        String playerName = args[1];
        int page = toIntegrity(args.length > 2 ? args[2] : "1", 1);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasChestMenu("select_template")) {
            ConfiguredTuiManager.getInstance().showCui(player, new MenuRoute(
                    "select_template", page, Map.of("dominion.name", dominionName, "member.name", playerName)));
            return;
        }

        ChestListView view = ChestUserInterfaceManager.getInstance().getListViewOf(player);
        view.setTitle(formatString(ChestUserInterface.selectTemplateCui.title, playerName));
        view.applyListConfiguration(ChestUserInterface.selectTemplateCui.listConfiguration, toIntegrity(args[2]));

        view.setButton(ChestUserInterface.selectTemplateCui.backButton.getSymbol(),
                new ChestButton(ChestUserInterface.selectTemplateCui.backButton) {
                    @Override
                    public void onClick(ClickType type) {
                        MemberFlags.show(player, dominionName, playerName, "1");
                    }
                }
        );

        List<TemplateDOO> templates = TemplateDOO.selectAll(player.getUniqueId());
        for (TemplateDOO template : templates) {
            ChestButton templateChest = new ChestButton(ChestUserInterface.selectTemplateCui.templateButton) {
                @Override
                public void onClick(ClickType type) {
                    TemplateCommand.memberApplyTemplate(player, dominionName, playerName, template.getName());
                    MemberFlags.show(player, dominionName, playerName, "1");
                }
            }.setDisplayNameArgs(template.getName()).setLoreArgs(playerName, template.getName());
            view.addItem(templateChest);
        }

        view.open();
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ CUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ Console ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    @Override
    protected void showConsole(CommandSender sender, String... args) throws Exception {
    }
}
