package com.example.valdker.offline.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SyncScheduler {

    private static final String WORK_NAME = "valdker_sync_orders";

    public static void enqueue(@NonNull Context ctx) {
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(OrderSyncWorker.class)
                .setConstraints(c)
                .build();

        WorkManager.getInstance(ctx.getApplicationContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, req);
    }
}