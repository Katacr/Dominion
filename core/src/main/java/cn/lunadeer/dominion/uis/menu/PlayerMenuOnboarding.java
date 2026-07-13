package cn.lunadeer.dominion.uis.menu;

import cn.lunadeer.dominion.utils.command.CommandManager;
import cn.lunadeer.dominion.utils.stui.TextUserInterfaceManager;
import cn.lunadeer.dominion.utils.stui.components.buttons.CommandButton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sends the first-use language and UI preference choices through cross-platform clickable chat components.
 */
public final class PlayerMenuOnboarding {

    private static final int LANGUAGES_PER_LINE = 6;

    private PlayerMenuOnboarding() {
    }

    /**
     * Sends every locale discovered from the plugin UI directory as a clickable command.
     */
    public static void showLanguageSelection(Player player, Set<String> locales) {
        TextUserInterfaceManager messages = TextUserInterfaceManager.getInstance();
        messages.sendMessage(player, Component.text("Welcome to Dominion / 欢迎使用 Dominion", NamedTextColor.GOLD));
        messages.sendMessage(player, Component.text("Select your language / 请选择语言", NamedTextColor.GRAY));
        List<String> sorted = locales.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        for (int start = 0; start < sorted.size(); start += LANGUAGES_PER_LINE) {
            List<Component> buttons = new ArrayList<>();
            for (String locale : sorted.subList(start, Math.min(start + LANGUAGES_PER_LINE, sorted.size()))) {
                buttons.add(new CommandButton(locale,
                        CommandManager.getRootCommand() + " language " + locale)
                        .setHoverText("Use " + locale)
                        .build());
            }
            messages.sendMessage(player, join(buttons));
        }
    }

    /**
     * Sends the three persisted renderer choices after a language has been selected.
     */
    public static void showUiSelection(Player player) {
        TextUserInterfaceManager messages = TextUserInterfaceManager.getInstance();
        messages.sendMessage(player, Component.text("Select UI / 选择菜单界面", NamedTextColor.GRAY));
        List<Component> buttons = List.of(
                uiButton("TUI", "Clickable text / 可点击文本"),
                uiButton("CUI", "Chest menu / 箱子菜单"),
                uiButton("DUI", "Dialog menu / 对话框菜单")
        );
        messages.sendMessage(player, join(buttons));
    }

    private static Component uiButton(String type, String tooltip) {
        return new CommandButton(type, CommandManager.getRootCommand() + " switch_ui " + type)
                .setHoverText(tooltip)
                .build();
    }

    private static Component join(List<Component> components) {
        Component line = Component.empty();
        for (int index = 0; index < components.size(); index++) {
            if (index > 0) {
                line = line.append(Component.space());
            }
            line = line.append(components.get(index));
        }
        return line;
    }
}
