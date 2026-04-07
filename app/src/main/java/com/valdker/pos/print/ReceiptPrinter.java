package com.valdker.pos.print;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;

public class ReceiptPrinter {

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
        try {
            // Android 12+ permission check
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                cb.onNeedPermission();
                return;
            }

            BluetoothConnection connection = BluetoothPrintersConnections.selectFirstPaired();
            if (connection == null) {
                cb.onNoPairedPrinter();
                return;
            }

            // 58mm common setup: 203dpi, 48mm width, 32 chars
            EscPosPrinter printer = new EscPosPrinter(connection, 203, 48f, 32);

            String receiptText = ReceiptFormatter.build(order);
            printer.printFormattedText(receiptText);

            // cut (if supported)
//            try { printer.cutPaper(); } catch (Exception ignore) {}

            cb.onSuccess();
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }
}
