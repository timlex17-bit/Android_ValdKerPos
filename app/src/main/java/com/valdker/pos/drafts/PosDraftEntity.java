package com.valdker.pos.drafts;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pos_drafts",
        indices = {
                @Index("posType"),
                @Index("isActive")
        }
)
public class PosDraftEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String posType = "retail";
    public String name = "";
    public boolean isActive = false;
    public long createdAt = 0L;
    public long updatedAt = 0L;
}
