package cn.lunadeer.dominion.utils.stui.components.buttons;

import cn.lunadeer.dominion.utils.command.CommandManager;
import cn.lunadeer.dominion.utils.command.NoPermissionException;
import cn.lunadeer.dominion.utils.command.SecondaryCommand;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public abstract class FunctionalButton extends PermissionButton {

    public abstract void function();


    public FunctionalButton(String text) {
        super(text);
        UUID uuid = UUID.randomUUID();
        new SecondaryCommand("tui_btn_future_" + uuid) {
            @Override
            public void executeHandler(CommandSender sender) {
                for (String permission : permissions) {
                    if (!sender.hasPermission(permission))
                        throw new NoPermissionException(permission);
                }
                function();
            }
        }.dynamic().register();
        String command = CommandManager.getRootCommand() + " tui_btn_future_" + uuid;
        ClickEvent event = legacyClickEvent("RUN_COMMAND", command);
        this.clickEvent = event != null ? event : ClickEvent.runCommand(command);
    }

}
