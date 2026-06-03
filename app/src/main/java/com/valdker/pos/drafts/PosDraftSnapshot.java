package com.valdker.pos.drafts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

public class PosDraftSnapshot {
    public final List<PosDraftEntity> drafts;
    public final Map<Long, Integer> itemCounts;
    @Nullable
    public final PosDraftEntity activeDraft;
    public final List<PosDraftItemEntity> items;

    public PosDraftSnapshot(@NonNull List<PosDraftEntity> drafts,
                            @Nullable PosDraftEntity activeDraft,
                            @NonNull Map<Long, Integer> itemCounts,
                            @NonNull List<PosDraftItemEntity> items) {
        this.drafts = drafts;
        this.activeDraft = activeDraft;
        this.itemCounts = itemCounts;
        this.items = items;
    }
}
