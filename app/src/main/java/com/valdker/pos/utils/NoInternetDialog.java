package com.valdker.pos.utils;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.valdker.pos.R;

public class NoInternetDialog extends DialogFragment {

    public static final String TAG = "NO_INTERNET_DIALOG";

    @Nullable
    private Runnable retryCallback;

    public static NoInternetDialog newInstance() {
        return new NoInternetDialog();
    }

    public void setRetryCallback(@Nullable Runnable retryCallback) {
        this.retryCallback = retryCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setTitle("No internet connection.")
                .setIcon(android.R.drawable.stat_notify_error)
                .setMessage("Please check your connection and try again.")
                .setPositiveButton("Retry", (dialog, which) -> {
                    if (retryCallback != null) {
                        retryCallback.run();
                    }
                })
                .setNegativeButton("Close", null)
                .create();
    }
}
