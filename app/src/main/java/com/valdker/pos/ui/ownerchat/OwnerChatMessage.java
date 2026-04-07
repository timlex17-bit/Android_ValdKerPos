package com.valdker.pos.ui.ownerchat;

public class OwnerChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_BOT = 2;

    public final int type;
    public final String text;
    public final long timeMs;

    private OwnerChatMessage(int type, String text) {
        this.type = type;
        this.text = text;
        this.timeMs = System.currentTimeMillis();
    }

    public static OwnerChatMessage user(String text) {
        return new OwnerChatMessage(TYPE_USER, text);
    }

    public static OwnerChatMessage bot(String text) {
        return new OwnerChatMessage(TYPE_BOT, text);
    }
}