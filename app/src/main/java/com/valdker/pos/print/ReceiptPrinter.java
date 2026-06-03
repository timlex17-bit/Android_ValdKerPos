package com.valdker.pos.print;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class ReceiptPrinter {
    private static final String TAG = "PRINTER";

    public interface Callback {
        void onSuccess();
        void onError(String message);
        void onNeedPermission();
        void onNoPairedPrinter();
    }

    private final Activity activity;

    public ReceiptPrinter(Activity activity) {
        this.activity = activity;
    }

    public void print(OrderData order, Callback cb) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
            cb.onNeedPermission();
            return;
        }

        String mac = PrinterPrefs.getMac(activity);
        if (mac == null || mac.trim().isEmpty()) {
            cb.onNoPairedPrinter();
            return;
        }

        String receiptText = ReceiptFormatter.build(order);
        PrinterService.printTextAsync(activity, receiptText, new BluetoothPrinterManager.PrintCallback() {
            @Override
            public void onSuccess() {
                activity.runOnUiThread(cb::onSuccess);
            }

            @Override
            public void onError(@androidx.annotation.NonNull String message) {
                Log.e(TAG, "PRINTER: receipt printer failed: " + message);
                activity.runOnUiThread(() -> cb.onError(message));
            }

            @Override
            public void onSkipped(@androidx.annotation.NonNull String message) {
                Log.w(TAG, "PRINTER: receipt printer skipped: " + message);
                activity.runOnUiThread(() -> cb.onError(message));
            }
        });
    }
}
