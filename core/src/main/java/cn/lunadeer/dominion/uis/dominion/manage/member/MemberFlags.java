package cn.lunadeer.dominion.uis.dominion.manage.member;

import cn.lunadeer.dominion.api.dtos.DominionDTO;
import cn.lunadeer.dominion.api.dtos.MemberDTO;
import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.api.dtos.flag.PriFlag;
import cn.lunadeer.dominion.commands.MemberCommand;
import cn.lunadeer.dominion.configuration.Language;
import cn.lunadeer.dominion.configuration.uis.ChestUserInterface;
import cn.lunadeer.dominion.configuration.uis.TextUserInterface;
import cn.lunadeer.dominion.misc.CommandArguments;
import cn.lunadeer.dominion.uis.AbstractUI;
import cn.lunadeer.dominion.uis.MainMenu;
import cn.lunadeer.dominion.uis.dominion.DominionList;
import cn.lunadeer.dominion.uis.dominion.DominionManage;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.tui.ConfiguredTuiManager;
import cn.lunadeer.dominion.utils.Notification;
import cn.lunadeer.dominion.utils.command.SecondaryCommand;
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
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;

import static cn.lunadeer.dominion.Dominion.defaultPermission;
import static cn.lunadeer.dominion.misc.Converts.*;
import static cn.lunadeer.dominion.utils.Misc.*;


public class MemberFlags extends AbstractUI {

    public static void show(CommandSender sender, String dominionName, String playerName, String pageStr) {
        new MemberFlags().displayByPreference(sender, dominionName, playerName, pageStr);
    }

    public static SecondaryCommand flags = new SecondaryCommand("member_flags", List.of(
            new CommandArguments.RequiredDominionArgument(),
            new CommandArguments.RequiredPlayerArgument(),
            new CommandArguments.OptionalPageArgument()
    ), Language.uiCommandsDescription.memberFlags) {
        @Override
        public void executeHandler(CommandSender sender) {
            show(sender, getArgumentValue(0), getArgumentValue(1), getArgumentValue(2));
        }
    }.needPermission(defaultPermission).register();

    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ TUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class MemberSettingTuiText extends ConfigurationPart {
        public String title = "{0} Member Setting";
        public String description = "Set member's privilege of dominion.";
        public String button = "SETTING";
    }

    public static ListViewButton button(CommandSender sender, String dominionName, String playerName) {
        return (ListViewButton) new ListViewButton(TextUserInterface.memberSettingTuiText.button) {
            @Override
            public void function(String pageStr) {
                show(sender, dominionName, playerName, pageStr);
            }
        }.needPermission(defaultPermission).setHoverText(TextUserInterface.memberSettingTuiText.description);
    }

    @Override
    protected void showTUI(Player player, String... args) throws Exception {
        String dominionName = args[0];
        String playerName = args[1];
        String pageStr = args.length > 2 ? args[2] : "1";
        int page = toIntegrity(pageStr);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasMenu("member_flags")) {
            // Preserve the legacy command while keeping member identity in server-owned route state.
            ConfiguredTuiManager.getInstance().show(player, new MenuRoute(
                    "member_flags", page, Map.of(
                    "dominion.name", dominionName,
                    "member.name", playerName
            )));
            return;
        }
        DominionDTO dominion = toDominionDTO(dominionName);
        MemberDTO member = toMemberDTO(dominion, playerName);
        ListView view = ListView.create(10, button(player, dominionName, playerName));
        view.title(formatString(TextUserInterface.memberSettingTuiText.title, playerName));
        view.navigator(
                Line.create()
                        .append(MainMenu.button(player).build())
                        .append(DominionList.button(player).build())
                        .append(DominionManage.button(player, dominionName).build())
                        .append(MemberList.button(player, dominionName).build())
                        .append(TextUserInterface.memberSettingTuiText.button)
        );
        view.add(Line.create().append(SelectTemplate.button(player, dominionName, playerName).build()));
        if (member.getFlagValue(Flags.ADMIN)) {
            view.add(createOption(player, Flags.ADMIN, true, playerName, dominion.getName(), page));
        } else {
            for (PriFlag flag : Flags.getAllPriFlagsEnable()) {
                view.add(createOption(player, flag, member.getFlagValue(flag), playerName, dominion.getName(), page));
            }
        }
        view.showOn(player, page);
    }

    private static Line createOption(Player player, PriFlag flag, boolean value, String player_name, String dominion_name, int page) {
        if (value) {
            return Line.create()
                    .append(new FunctionalButton("☑") {
                        @Override
                        public void function() {
                            MemberCommand.setMemberPrivilege(player, dominion_name, player_name, flag.getFlagName(), "false", String.valueOf(page));
                        }
                    }.needPermission(defaultPermission).green().build())
                    .append(Component.text(flag.getDisplayName()).hoverEvent(Component.text(flag.getDescription()))
                    );
        } else {
            return Line.create()
                    .append(new FunctionalButton("☐") {
                        @Override
                        public void function() {
                            MemberCommand.setMemberPrivilege(player, dominion_name, player_name, flag.getFlagName(), "true", String.valueOf(page));
                        }
                    }.needPermission(defaultPermission).red().build())
                    .append(Component.text(flag.getDisplayName()).hoverEvent(Component.text(flag.getDescription()))
                    );
        }
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ TUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ CUI ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    public static class MemberSettingCui extends ConfigurationPart {
        public String title = "§6✦ §f§lMember {0} Settings §6✦";
        public ListViewConfiguration listConfiguration = new ListViewConfiguration(
                'i',
                List.of(
                        "<######T#",
                        "#iiiiiii#",
                        "#iiiiiii#",
                        "#iiiiiii#",
                        "p#######n"
                )
        );

        public ButtonConfiguration backButton = ButtonConfiguration.createMaterial(
                '<', Material.RED_STAINED_GLASS_PANE,
                "Back",
                List.of(
                        "Go back to the",
                        "member list."
                )
        );

        public ButtonConfiguration templateButton = ButtonConfiguration.createMaterial(
                'T', Material.PAPER,
                "§e✏ §6Select Template",
                List.of(
                        "§7Select a template to apply",
                        "§7to this member's privileges.",
                        "",
                        "§6▶ Click to select template"
                )
        );

        public String flagItemName = "&7Flag: &9{0}";
        public String flagItemStateTrue = "&a&l[ENABLED]";
        public String flagItemStateFalse = "&c&l[DISABLED]";
        public List<String> flagItemLore = List.of(
                "&7State: {0}",
                "&7Des: &f{1}",
                "       &f{2}",
                "",
                "&7Click to toggle this flag."
        );
    }

    @Override
    protected void showCUI(Player player, String... args) throws Exception {
        String dominionName = args[0];
        String memberName = args[1];
        int configuredPage = toIntegrity(args.length > 2 ? args[2] : "1", 1);
        if (ConfiguredTuiManager.isInitialized()
                && ConfiguredTuiManager.getInstance().hasChestMenu("member_flags")) {
            ConfiguredTuiManager.getInstance().showCui(player, new MenuRoute(
                    "member_flags", configuredPage,
                    Map.of("dominion.name", dominionName, "member.name", memberName)));
            return;
        }
        DominionDTO dominion = toDominionDTO(args[0]);
        MemberDTO member = toMemberDTO(dominion, args[1]);
        ChestListView view = ChestUserInterfaceManager.getInstance().getListViewOf(player);
        view.setTitle(formatString(ChestUserInterface.memberSettingCui.title, args[1]));
        view.applyListConfiguration(ChestUserInterface.memberSettingCui.listConfiguration, toIntegrity(args[2]));

        view.setButton(ChestUserInterface.memberSettingCui.backButton.getSymbol(),
                new ChestButton(ChestUserInterface.memberSettingCui.backButton) {
                    @Override
                    public void onClick(ClickType type) {
                        MemberList.show(player, dominion.getName(), "1");
                    }
                }
        );

        view.setButton(ChestUserInterface.memberSettingCui.templateButton.getSymbol(),
                new ChestButton(ChestUserInterface.memberSettingCui.templateButton) {
                    @Override
                    public void onClick(ClickType type) {
                        SelectTemplate.show(player, dominion.getName(), args[1], "1");
                    }
                }
        );

        for (int i = 0; i < Flags.getAllPriFlagsEnable().size(); i++) {
            PriFlag flag = Flags.getAllPriFlagsEnable().get(i);
            Integer page = (int) Math.ceil((double) (i + 1) / view.getPageSize());
            String flagState = member.getFlagValue(flag) ? ChestUserInterface.memberSettingCui.flagItemStateTrue : ChestUserInterface.memberSettingCui.flagItemStateFalse;
            String flagName = formatString(ChestUserInterface.memberSettingCui.flagItemName, flag.getDisplayName());
            List<String> descriptions = foldLore2Line(flag.getDescription(), 30);
            List<String> flagLore = formatStringList(ChestUserInterface.memberSettingCui.flagItemLore, flagState, descriptions.get(0), descriptions.get(1));
            ButtonConfiguration btnConfig = ButtonConfiguration.createMaterial(
                    ChestUserInterface.memberSettingCui.listConfiguration.itemSymbol.charAt(0),
                    flag.getMaterial(),
                    flagName,
                    flagLore
            );
            view.addItem(new ChestButton(btnConfig) {
                @Override
                public void onClick(ClickType type) {
                    boolean newValue = !member.getFlagValue(flag);
                    MemberCommand.setMemberPrivilege(player, dominion.getName(), args[1], flag.getFlagName(), String.valueOf(newValue), String.valueOf(page));
                }
            });
        }

        view.open();
    }

    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑ CUI ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓ Console ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

    @Override
    protected void showConsole(CommandSender sender, String... args) throws Exception {
        Notification.info(sender, ChestUserInterface.memberSettingCui.title);
        // command
        Notification.info(sender, MemberCommand.setMemberPrivilege.getUsage());
        Notification.info(sender, Language.consoleText.descPrefix, MemberCommand.setMemberPrivilege.getDescription());
        // items
        DominionDTO dominion = toDominionDTO(args[0]);
        MemberDTO member = toMemberDTO(dominion, args[1]);
        int page = toIntegrity(args[2], 1);
        Triple<Integer, Integer, Integer> pageInfo = pageUtil(page, 8, Flags.getAllPriFlagsEnable().size());
        for (int i = pageInfo.getLeft(); i < pageInfo.getMiddle(); i++) {
            PriFlag flag = Flags.getAllPriFlagsEnable().get(i);
            String flagState = member.getFlagValue(flag) ? ChestUserInterface.memberSettingCui.flagItemStateTrue : ChestUserInterface.memberSettingCui.flagItemStateFalse;
            String flagName = formatString(ChestUserInterface.memberSettingCui.flagItemName, flag.getDisplayName());
            Notification.info(sender, "§6▶ {0} §7(§b{1}§7)", flagName, flag.getFlagName());
            Notification.info(sender, "§6  \t{0}\t&7{1}", flagState, flag.getDescription());
        }
        // page info
        Notification.info(sender, Language.consoleText.pageInfo, page, pageInfo.getRight(), Flags.getAllPriFlagsEnable().size());
    }
}
