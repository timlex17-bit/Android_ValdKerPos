package com.valdker.pos.drafts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PosDraftDao {

    @Query("SELECT * FROM pos_drafts ORDER BY createdAt ASC")
    List<PosDraftEntity> getAllDrafts();

    @Query("SELECT * FROM pos_drafts WHERE posType = :posType ORDER BY createdAt ASC")
    List<PosDraftEntity> getAllDrafts(@NonNull String posType);

    @Query("SELECT * FROM pos_drafts WHERE isActive = 1 LIMIT 1")
    @Nullable
    PosDraftEntity getActiveDraft();

    @Query("SELECT * FROM pos_drafts WHERE posType = :posType AND isActive = 1 LIMIT 1")
    @Nullable
    PosDraftEntity getActiveDraft(@NonNull String posType);

    @Insert
    long insertDraft(@NonNull PosDraftEntity draft);

    @Update
    void updateDraft(@NonNull PosDraftEntity draft);

    @Delete
    void deleteDraft(@NonNull PosDraftEntity draft);

    @Query("DELETE FROM pos_drafts WHERE id = :draftId")
    void deleteDraft(long draftId);

    @Query("UPDATE pos_drafts SET isActive = 0, updatedAt = :updatedAt WHERE posType = :posType")
    void clearActiveDraft(@NonNull String posType, long updatedAt);

    @Query("UPDATE pos_drafts SET isActive = 0, updatedAt = :updatedAt")
    void clearActiveDraft(long updatedAt);

    @Query("UPDATE pos_drafts SET isActive = 1, updatedAt = :updatedAt WHERE id = :draftId")
    void markDraftActive(long draftId, long updatedAt);

    @Query("UPDATE pos_drafts SET updatedAt = :updatedAt WHERE id = :draftId")
    void touchDraft(long draftId, long updatedAt);

    @Transaction
    default void setActiveDraft(long draftId, @NonNull String posType, long updatedAt) {
        clearActiveDraft(posType, updatedAt);
        markDraftActive(draftId, updatedAt);
    }

    @Query("SELECT * FROM pos_draft_items WHERE draftId = :draftId ORDER BY createdAt ASC")
    List<PosDraftItemEntity> getItemsByDraft(long draftId);

    @Query("SELECT * FROM pos_draft_items WHERE draftId = :draftId AND productId = :productId AND itemType = :itemType LIMIT 1")
    @Nullable
    PosDraftItemEntity getDraftItem(long draftId, @NonNull String productId, @NonNull String itemType);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertDraftItem(@NonNull PosDraftItemEntity item);

    @Update
    void updateDraftItem(@NonNull PosDraftItemEntity item);

    @Transaction
    default void insertOrUpdateDraftItem(@NonNull PosDraftItemEntity item) {
        PosDraftItemEntity existing = getDraftItem(item.draftId, item.productId, item.itemType);
        long now = item.updatedAt > 0L ? item.updatedAt : System.currentTimeMillis();
        if (existing == null) {
            if (item.createdAt <= 0L) item.createdAt = now;
            item.updatedAt = now;
            insertDraftItem(item);
            return;
        }

        existing.productName = item.productName;
        existing.barcode = item.barcode;
        existing.quantity = item.quantity;
        existing.price = item.price;
        existing.subtotal = item.subtotal;
        existing.shopId = item.shopId;
        existing.imageUrl = item.imageUrl;
        existing.updatedAt = now;
        updateDraftItem(existing);
    }

    @Query("UPDATE pos_draft_items SET quantity = :quantity, subtotal = :subtotal, updatedAt = :updatedAt WHERE id = :itemId")
    void updateItemQuantity(long itemId, int quantity, double subtotal, long updatedAt);

    @Query("DELETE FROM pos_draft_items WHERE id = :itemId")
    void deleteDraftItem(long itemId);

    @Query("DELETE FROM pos_draft_items WHERE draftId = :draftId AND productId = :productId AND itemType = :itemType")
    void deleteDraftItem(long draftId, @NonNull String productId, @NonNull String itemType);

    @Query("DELETE FROM pos_draft_items WHERE draftId = :draftId")
    void deleteItemsByDraft(long draftId);

    @Transaction
    default void deleteDraftWithItems(long draftId) {
        deleteItemsByDraft(draftId);
        deleteDraft(draftId);
    }

    @Query("SELECT draftId, COALESCE(SUM(quantity), 0) AS totalQuantity FROM pos_draft_items WHERE draftId IN (:draftIds) GROUP BY draftId")
    List<PosDraftItemCount> getItemCountsByDrafts(@NonNull List<Long> draftIds);
}
