package com.valdker.pos.ui.customers;

import android.content.Context;
import com.valdker.pos.R;

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
                .setNegativeButton(ctx.getString(R.string.action_cancel), (d, w) -> d.dismiss())
                .setPositiveButton(ctx.getString(R.string.action_delete), (d, w) -> yes.onYes())
                .show();
    }
}