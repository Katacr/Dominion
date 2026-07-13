package cn.lunadeer.dominion.uis.dominion.copy;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.configuration.uis.ChestUserInterface;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.uis.AbstractUI;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.dominion.DominionList;
import cn.lunadeer.dominion.uis.dominion.DominionManage;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.tui.ConfiguredTuiManager;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.configuration.ConfigurationPart;
import cn.lunadeer.dominion.utils.scui.ChestButton;
import cn.lunadeer.dominion.utils.scui.ChestUserInterfaceManager;
import cn.lunadeer.dominion.utils.scui.ChestView;
import cn.lunadeer.dominion.utils.scui.configuration.ButtonConfiguration;
import cn.lunadeer.dominion.utils.stui.ListView;
import cn.lunadeer.dominion.utils.stui.components.Line;
import cn.lunadeer.dominion.utils.stui.components.buttons.ListViewButton;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;

import static cn.lunadeer.dominion.Dominion.defaultPermission;
import static cn.lunadeer.dominion.misc.Asserts.assertDominionAdmin;
import static cn.lunadeer.dominion.misc.Converts.toDominionDTO;
import static cn.lunadeer.dominion.misc.Converts.toIntegrity;
import static cn.lunadeer.dominion.utils.Misc.formatString;

public class CopyMenu extends AbstractUI {

    public static void show(CommandSender sender, String toDominionName, String pageStr) {
        new CopyMenu().displayByPreference(sender, toDominionName, pageStr);
    }

    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ TUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class CopyMenuTuiText extends ConfigurationPart {
        public String button = "COPY";
        public String description = "Copy Privilege Settings From Other Dominion.";
        public String title = "Select Copy Type";
    }

    public static ListViewButton button(CommandSender sender, String toDominionName) {
        return (ListViewButton) new ListViewButton(TextUserInterface.copyMenuTuiText.button) {
            @Override
            public void function(String pageStr) {
                show(sender, toDominionName, pageStr);
            }
        }.needPermission(defaultPermission);
    }

    @Override
    protected void showTUI(Player player, String... args) throws Exception {
        String toDominionName = args[0];
        int page = toIntegrity(args[1]);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasMenu("copy_menu")) {
            ConfiguredTuiManager.getInstance().show(player, new MenuRoute(
                    "copy_menu", page, Map.of("dominion.name", toDominionName)));
            return;
        }
        DominionDTO dominion = toDominionDTO(toDominionName);
        assertDominionAdmin(player, dominion);

        ListView view = ListView.create(10, button(player, toDominionName));
        view.title(formatString(TextUserInterface.copyMenuTuiText.title));
        view.navigator(
                Line.create()
                        .append(MainMenu.button(player).build())
                        .append(DominionList.button(player).build())
                        .append(DominionManage.button(player, toDominionName).build())
                        .append(TextUserInterface.copyMenuTuiText.button)
        );
        view.add(Line.create()
                .append(DominionCopy.button(player, toDominionName, DominionCopy.CopyType.ENVIRONMENT).build())
                .append(TextUserInterface.dominionCopyTuiText.envDescription));
        view.add(Line.create()
                .append(DominionCopy.button(player, toDominionName, DominionCopy.CopyType.GUEST).build())
                .append(TextUserInterface.dominionCopyTuiText.guestDescription));
        view.add(Line.create()
                .append(DominionCopy.button(player, toDominionName, DominionCopy.CopyType.MEMBER).build())
                .append(TextUserInterface.dominionCopyTuiText.memberDescription));
        view.add(Line.create()
                .append(DominionCopy.button(player, toDominionName, DominionCopy.CopyType.GROUP).build())
                .append(TextUserInterface.dominionCopyTuiText.groupDescription));
        view.showOn(player, page);
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ TUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ CUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class CopyMenuCui extends ConfigurationPart {
        public String title = "§6✦ §a§lCopy Settings §6✦";
        public List<String> layout = List.of(
                "<########",
                "#E#G#M#P#",
                "#########"
        );

        public ButtonConfiguration backButton = ButtonConfiguration.createMaterial(
                '<', Material.RED_STAINED_GLASS_PANE,
                "§c« Back to Dominion Management",
                List.of(
                        "§7Return to dominion management",
                        "§7to access other settings.",
                        "",
                        "§e▶ Click to go back"
                )
        );

        public ButtonConfiguration envCopyButton = ButtonConfiguration.createMaterial(
                'E', Material.GRASS_BLOCK,
                "§2🌍 §aEnvironment Copy",
                List.of(
                        "§7Copy environment settings",
                        "§7from another dominion.",
                        "",
                        "§a▶ Click to copy environment",
                        "",
                        "§8Weather, time, mob spawning..."
                )
        );

        public ButtonConfiguration guestCopyButton = ButtonConfiguration.createMaterial(
                'G', Material.OAK_DOOR,
                "§e🚪 §6Guest Copy",
                List.of(
                        "§7Copy guest permissions",
                        "§7from another dominion.",
                        "",
                        "§6▶ Click to copy guest settings",
                        "",
                        "§8Build, interact, use items..."
                )
        );

        public ButtonConfiguration memberCopyButton = ButtonConfiguration.createMaterial(
                'M', Material.PLAYER_HEAD,
                "§b👥 §3Member Copy",
                List.of(
                        "§7Copy member permissions",
                        "§7from another dominion.",
                        "",
                        "§3▶ Click to copy member settings",
                        "",
                        "§8Individual player permissions..."
                )
        );

        public ButtonConfiguration groupCopyButton = ButtonConfiguration.createMaterial(
                'P', Material.CHEST,
                "§d📦 §5Group Copy",
                List.of(
                        "§7Copy group settings",
                        "§7from another dominion.",
                        "",
                        "§5▶ Click to copy group settings",
                        "",
                        "§8Permission groups and roles..."
                )
        );
    }

    @Override
    protected void showCUI(Player player, String... args) throws Exception {
        String toDominionName = args[0];
        int page = toIntegrity(args.length > 1 ? args[1] : "1", 1);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasChestMenu("copy_menu")) {
            ConfiguredTuiManager.getInstance().showCui(player, new MenuRoute(
                    "copy_menu", page, Map.of("dominion.name", toDominionName)));
            return;
        }

        DominionDTO dominion = toDominionDTO(toDominionName);
        assertDominionAdmin(player, dominion);

        ChestView view = ChestUserInterfaceManager.getInstance().getViewOf(player);
        view.setTitle(ChestUserInterface.copyMenuCui.title);
        view.setLayout(ChestUserInterface.copyMenuCui.layout);

        view.setButton(ChestUserInterface.copyMenuCui.backButton.getSymbol(),
                new ChestButton(ChestUserInterface.copyMenuCui.backButton) {
                    @Override
                    public void onClick(ClickType type) {
                        DominionManage.show(player, toDominionName, "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.copyMenuCui.envCopyButton.getSymbol(),
                new ChestButton(ChestUserInterface.copyMenuCui.envCopyButton) {
                    @Override
                    public void onClick(ClickType type) {
                        DominionCopy.show(player, toDominionName, DominionCopy.CopyType.ENVIRONMENT, "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.copyMenuCui.guestCopyButton.getSymbol(),
                new ChestButton(ChestUserInterface.copyMenuCui.guestCopyButton) {
                    @Override
                    public void onClick(ClickType type) {
                        DominionCopy.show(player, toDominionName, DominionCopy.CopyType.GUEST, "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.copyMenuCui.memberCopyButton.getSymbol(),
                new ChestButton(ChestUserInterface.copyMenuCui.memberCopyButton) {
                    @Override
                    public void onClick(ClickType type) {
                        DominionCopy.show(player, toDominionName, DominionCopy.CopyType.MEMBER, "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.copyMenuCui.groupCopyButton.getSymbol(),
                new ChestButton(ChestUserInterface.copyMenuCui.groupCopyButton) {
                    @Override
                    public void onClick(ClickType type) {
                        DominionCopy.show(player, toDominionName, DominionCopy.CopyType.GROUP, "1");
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
