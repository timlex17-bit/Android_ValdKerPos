package com.example.valdker.offline.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.valdker.offline.db.dao.OrderDao;
import com.example.valdker.offline.db.dao.OrderItemDao;
import com.example.valdker.offline.db.dao.ProductStockDao;
import com.example.valdker.offline.db.dao.ShiftDao;
import com.example.valdker.offline.db.entities.OrderEntity;
import com.example.valdker.offline.db.entities.OrderItemEntity;
import com.example.valdker.offline.db.entities.ProductStockEntity;
import com.example.valdker.offline.db.entities.ShiftEntity;

@Database(
        entities = {OrderEntity.class, OrderItemEntity.class, ProductStockEntity.class, ShiftEntity.class},
        version = 3,
        exportSchema = false
)
public abstract class ValdkerDatabase extends RoomDatabase {

    private static volatile ValdkerDatabase INSTANCE;
    public abstract ShiftDao shiftDao();

    public abstract OrderDao orderDao();
    public abstract OrderItemDao orderItemDao();
    public abstract ProductStockDao productStockDao();

    public static ValdkerDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (ValdkerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    ValdkerDatabase.class,
                                    "valdker_offline.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}