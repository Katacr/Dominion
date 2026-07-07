package cn.lunadeer.dominion.utils.stui.components.buttons;

import net.kyori.adventure.text.event.ClickEvent;

public class UrlButton extends Button {
    public UrlButton(String text, String url) {
        super(text);
        ClickEvent event = legacyClickEvent("OPEN_URL", url);
        this.clickEvent = event != null ? event : ClickEvent.openUrl(url);
    }
}
