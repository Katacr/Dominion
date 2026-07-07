package cn.lunadeer.dominion.utils.stui.components.buttons;

import net.kyori.adventure.text.event.ClickEvent;

public class CommandButton extends Button {

    public CommandButton(String text, String command) {
        super(text);
        ClickEvent event = legacyClickEvent("RUN_COMMAND", command);
        this.clickEvent = event != null ? event : ClickEvent.runCommand(command);
    }
}
