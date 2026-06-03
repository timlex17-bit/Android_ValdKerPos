package com.valdker.pos.drafts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.models.CartItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PosDraftRepository {

    public static class CreatedDraftResult {
        public final String draftName;
        public final PosDraftSnapshot snapshot;

        CreatedDraftResult(@NonNull String draftName, @NonNull PosDraftSnapshot snapshot) {
            this.draftName = draftName;
            this.snapshot = snapshot;
        }
    }

    public static class CloseDraftResult {
        public final boolean closed;
        public final PosDraftSnapshot snapshot;

        CloseDraftResult(boolean closed, @NonNull PosDraftSnapshot snapshot) {
            this.closed = closed;
            this.snapshot = snapshot;
        }
    }

    private final PosDraftDao dao;

    public PosDraftRepository(@NonNull Context context) {
        this.dao = PosDraftDatabase.getInstance(context).posDraftDao();
    }

    @NonNull
    public PosDraftSnapshot loadSnapshot(@NonNull String posType, boolean includeItems) {
        return ensureSnapshot(posType, includeItems);
    }

    @NonNull
    public PosDraftSnapshot activateDraft(@NonNull String posType,
                                          long draftId,
                                          boolean includeItems) {
        if (draftId > 0L) {
            dao.setActiveDraft(draftId, posType, System.currentTimeMillis());
        }
        return ensureSnapshot(posType, includeItems);
    }

    @NonNull
    public PosDraftSnapshot activateDraftSavingCurrent(@NonNull String posType,
                                                       long oldDraftId,
                                                       long newDraftId,
                                                       @NonNull List<CartItem> currentItems) {
        if (oldDraftId > 0L) {
            replaceDraftItems(oldDraftId, currentItems);
        }
        return activateDraft(posType, newDraftId, true);
    }

    @NonNull
    public CreatedDraftResult createAndActivateDraft(@NonNull String posType) {
        long now = System.currentTimeMillis();
        List<PosDraftEntity> drafts = dao.getAllDrafts(posType);

        PosDraftEntity draft = new PosDraftEntity();
        draft.posType = posType;
        draft.name = nextDraftName(drafts);
        draft.isActive = false;
        draft.createdAt = now;
        draft.updatedAt = now;

        long id = dao.insertDraft(draft);
        dao.setActiveDraft(id, posType, now);
        return new CreatedDraftResult(draft.name, ensureSnapshot(posType, true));
    }

    @NonNull
    public CreatedDraftResult createAndActivateDraftSavingCurrent(@NonNull String posType,
                                                                  long oldDraftId,
                                                                  @NonNull List<CartItem> currentItems) {
        if (oldDraftId > 0L) {
            replaceDraftItems(oldDraftId, currentItems);
        }
        return createAndActivateDraft(posType);
    }

    public void replaceDraftItems(long draftId, @NonNull List<CartItem> items) {
        long now = System.currentTimeMillis();
        dao.deleteItemsByDraft(draftId);

        for (CartItem item : items) {
            PosDraftItemEntity entity = PosDraftMapper.fromCartItem(draftId, item, now);
            if (entity != null) dao.insertDraftItem(entity);
        }

        dao.touchDraft(draftId, now);
    }

    public void upsertDraftItem(@NonNull PosDraftItemEntity item) {
        long now = item.updatedAt > 0L ? item.updatedAt : System.currentTimeMillis();
        item.updatedAt = now;
        dao.insertOrUpdateDraftItem(item);
        dao.touchDraft(item.draftId, now);
    }

    @NonNull
    public PosDraftSnapshot deleteDraftItem(@NonNull String posType,
                                            long draftId,
                                            @NonNull String productId,
                                            @NonNull String itemType) {
        dao.deleteDraftItem(draftId, productId, CartItem.normalizeItemType(itemType));
        dao.touchDraft(draftId, System.currentTimeMillis());
        return ensureSnapshot(posType, false);
    }

    @NonNull
    public PosDraftSnapshot clearDraftItems(@NonNull String posType, long draftId) {
        dao.deleteItemsByDraft(draftId);
        dao.touchDraft(draftId, System.currentTimeMillis());
        return ensureSnapshot(posType, true);
    }

    @NonNull
    public CloseDraftResult closeActiveDraftIfPossible(@NonNull String posType, long draftId) {
        List<PosDraftEntity> drafts = dao.getAllDrafts(posType);
        if (drafts == null || drafts.size() <= 1) {
            return new CloseDraftResult(false, ensureSnapshot(posType, true));
        }

        dao.deleteDraftWithItems(draftId);
        return new CloseDraftResult(true, ensureSnapshot(posType, true));
    }

    @NonNull
    public PosDraftSnapshot completeActiveDraft(@NonNull String posType, long draftId) {
        if (draftId > 0L) {
            dao.deleteDraftWithItems(draftId);
        }
        return ensureSnapshot(posType, true);
    }

    @NonNull
    public PosDraftSnapshot cleanupActiveDraftAfterCheckout(@NonNull String posType, long draftId) {
        return completeActiveDraft(posType, draftId);
    }

    @NonNull
    private PosDraftSnapshot ensureSnapshot(@NonNull String posType, boolean includeItems) {
        long now = System.currentTimeMillis();
        List<PosDraftEntity> drafts = dao.getAllDrafts(posType);

        if (drafts == null || drafts.isEmpty()) {
            PosDraftEntity draft = new PosDraftEntity();
            draft.posType = posType;
            draft.name = "A";
            draft.isActive = true;
            draft.createdAt = now;
            draft.updatedAt = now;
            dao.insertDraft(draft);
            drafts = dao.getAllDrafts(posType);
        }

        PosDraftEntity active = dao.getActiveDraft(posType);
        if (active == null && drafts != null && !drafts.isEmpty()) {
            dao.setActiveDraft(drafts.get(0).id, posType, now);
            drafts = dao.getAllDrafts(posType);
            active = dao.getActiveDraft(posType);
        }

        List<Long> ids = new ArrayList<>();
        if (drafts != null) {
            for (PosDraftEntity draft : drafts) {
                if (draft != null) ids.add(draft.id);
            }
        }

        Map<Long, Integer> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            List<PosDraftItemCount> rows = dao.getItemCountsByDrafts(ids);
            if (rows != null) {
                for (PosDraftItemCount row : rows) {
                    if (row != null) counts.put(row.draftId, row.totalQuantity);
                }
            }
        }

        List<PosDraftItemEntity> items = new ArrayList<>();
        if (includeItems && active != null) {
            List<PosDraftItemEntity> activeItems = dao.getItemsByDraft(active.id);
            if (activeItems != null) items = activeItems;
        }

        return new PosDraftSnapshot(drafts != null ? drafts : new ArrayList<>(), active, counts, items);
    }

    @NonNull
    private static String nextDraftName(@Nullable List<PosDraftEntity> drafts) {
        boolean[] used = new boolean[26];
        if (drafts != null) {
            for (PosDraftEntity draft : drafts) {
                if (draft == null || draft.name == null || draft.name.length() != 1) continue;
                char c = Character.toUpperCase(draft.name.charAt(0));
                if (c >= 'A' && c <= 'Z') used[c - 'A'] = true;
            }
        }
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) return String.valueOf((char) ('A' + i));
        }
        int next = drafts == null ? 1 : drafts.size() + 1;
        return "Walk-in " + next;
    }
}
