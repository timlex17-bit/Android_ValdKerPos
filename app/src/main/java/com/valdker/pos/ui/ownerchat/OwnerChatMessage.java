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
    /**
     * Mapped business data for a tool-backed answer, or null for every
     * other message (user messages, plain conversational replies, errors).
     * Never the raw {@code structured_data} JSON - see StructuredDataMapper.
     */
    @Nullable
    public final StructuredDataView businessData;

    private OwnerChatMessage(int type,
                             String text,
                             @Nullable List<OwnerChatResponse.Link> links,
                             @Nullable StructuredDataView businessData) {
        this.type = type;
        this.text = text;
        this.timeMs = System.currentTimeMillis();
        this.links = links != null ? new ArrayList<>(links) : new ArrayList<>();
        this.businessData = businessData;
    }

    public static OwnerChatMessage user(String text) {
        return new OwnerChatMessage(TYPE_USER, text, null, null);
    }

    public static OwnerChatMessage bot(String text) {
        return new OwnerChatMessage(TYPE_BOT, text, null, null);
    }

    public static OwnerChatMessage bot(String text, @Nullable List<OwnerChatResponse.Link> links) {
        return new OwnerChatMessage(TYPE_BOT, text, links, null);
    }

    public static OwnerChatMessage bot(String text,
                                        @Nullable List<OwnerChatResponse.Link> links,
                                        @Nullable StructuredDataView businessData) {
        return new OwnerChatMessage(TYPE_BOT, text, links, businessData);
    }
}
