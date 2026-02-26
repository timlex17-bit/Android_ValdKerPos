package com.example.valdker.offline.db;

import android.content.Context;

import androidx.room.Room;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DbProvider {

    private static final String DB_NAME = "valdker_offline.db";

    private static volatile AppDatabase INSTANCE;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    public static ExecutorService executor() {
        return EXECUTOR;
    }

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (DbProvider.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .fallbackToDestructiveMigration() // ✅ dev-safe
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}