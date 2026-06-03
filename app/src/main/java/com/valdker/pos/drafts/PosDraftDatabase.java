package com.valdker.pos.drafts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                PosDraftEntity.class,
                PosDraftItemEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class PosDraftDatabase extends RoomDatabase {

    private static volatile PosDraftDatabase instance;

    public abstract PosDraftDao posDraftDao();

    @NonNull
    public static PosDraftDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (PosDraftDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    PosDraftDatabase.class,
                                    "valdker_pos_drafts.db"
                            )
                            .build();
                }
            }
        }
        return instance;
    }
}
