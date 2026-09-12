package com.valdker.pos.drafts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                PosDraftEntity.class,
                PosDraftItemEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class PosDraftDatabase extends RoomDatabase {

    private static volatile PosDraftDatabase instance;

    public abstract PosDraftDao posDraftDao();

    /**
     * Meja dan pelayan dine-in per draft.
     *
     * <p>Ditulis sebagai ALTER TABLE dan bukan rebuild tabel: keempat kolom ini
     * nullable tanpa default, jadi menambahkannya tidak menyentuh baris yang
     * sudah ada. Tidak ada {@code fallbackToDestructiveMigration()} di mana pun
     * pada database ini - kalau ada, upgrade akan menghapus keranjang yang
     * sedang terbuka milik kasir, bukan sekadar memindahkan skema.
     */
    @VisibleForTesting
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pos_drafts ADD COLUMN tableId INTEGER");
            db.execSQL("ALTER TABLE pos_drafts ADD COLUMN tableName TEXT");
            db.execSQL("ALTER TABLE pos_drafts ADD COLUMN waiterId INTEGER");
            db.execSQL("ALTER TABLE pos_drafts ADD COLUMN waiterName TEXT");
        }
    };

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
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
