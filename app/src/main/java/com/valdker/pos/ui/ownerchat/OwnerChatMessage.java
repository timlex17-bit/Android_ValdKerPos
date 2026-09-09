package com.valdker.pos.ui.ownerchat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class OwnerChatMessage {
    public static final int TYPE_USER = 1;
    public static final int TYPE_BOT = 2;

    public final int type;
    public final String text;
    public final long timeMs;
    @NonNull
    public final List<OwnerChatResponse.Link> links;

    private OwnerChatMessage(int type,
                             String text,
                             @Nullable List<OwnerChatResponse.Link> links) {
        this.type = type;
        this.text = text;
        this.timeMs = System.currentTimeMillis();
        this.links = links != null ? new ArrayList<>(links) : new ArrayList<>();
    }

    public static OwnerChatMessage user(String text) {
        return new OwnerChatMessage(TYPE_USER, text, null);
    }

    public static OwnerChatMessage bot(String text) {
        return new OwnerChatMessage(TYPE_BOT, text, null);
    }

    public static OwnerChatMessage bot(String text, @Nullable List<OwnerChatResponse.Link> links) {
        return new OwnerChatMessage(TYPE_BOT, text, links);
    }
}
