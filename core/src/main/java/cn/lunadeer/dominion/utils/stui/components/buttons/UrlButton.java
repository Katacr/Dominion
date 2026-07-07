package cn.lunadeer.dominion.utils.stui.components.buttons;

import net.kyori.adventure.text.event.ClickEvent;

public class UrlButton extends Button {
    public UrlButton(String text, String url) {
        super(text);
        this.clickEvent = ClickEvent.openUrl(url);
    }
}
