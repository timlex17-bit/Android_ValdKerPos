package com.example.valdker.ui.customers;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfirmDeleteDialog {

    public interface YesCallback { void onYes(); }

    public static void show(@NonNull Context ctx,
                            @NonNull String title,
                            @NonNull String message,
                            @NonNull YesCallback yes) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Delete", (d, w) -> yes.onYes())
                .show();
    }
}