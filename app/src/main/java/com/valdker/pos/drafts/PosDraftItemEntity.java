package com.valdker.pos.drafts;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pos_draft_items",
        foreignKeys = @ForeignKey(
                entity = PosDraftEntity.class,
                parentColumns = "id",
                childColumns = "draftId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("draftId"),
                @Index(value = {"draftId", "productId", "itemType"}, unique = true)
        }
)
public class PosDraftItemEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long draftId;
    public String productId = "";
    public String productName = "";
    @Nullable
    public String barcode;
    public int quantity = 0;
    public double price = 0d;
    public double subtotal = 0d;
    public String itemType = "PRODUCT";
    public int shopId = 0;
    @Nullable
    public String imageUrl;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
