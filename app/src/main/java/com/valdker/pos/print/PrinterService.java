package com.valdker.pos.print;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

public final class PrinterService {

    private PrinterService() {}

    /**
     * Handler bilah utama, dibuat saat pertama dipakai.
     *
     * <p>Dulu field static final yang langsung memanggil
     * Looper.getMainLooper(). Itu membuat kelas ini mustahil dimuat di unit
     * test JVM - android.jar untuk pengujian hanyalah stub yang melempar
     * "not mocked" - padahal profileForMm() di bawah adalah data murni yang
     * justru perlu diuji: lebar kertas 58mm dan 80mm menentukan perataan
     * setiap kolom pada struk.
     */
    @Nullable
    private static volatile Handler mainHandler;

    @NonNull
    private static Handler main() {
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (PrinterService.class) {
                handler = mainHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                    mainHandler = handler;
                }
            }
        }
        return handler;
    }

    public static class PaperProfile {
        public final int dpi;
        public final float mmWidth;
        public final int chars;

        public PaperProfile(int dpi, float mmWidth, int chars) {
            this.dpi = dpi;
            this.mmWidth = mmWidth;
            this.chars = chars;
        }
    }

    public static PaperProfile profileForMm(int mm) {
        // umum:
        // 58mm -> printable ~48mm, 32 chars
        // 80mm -> printable ~72mm, 48 chars
        if (mm >= 80) return new PaperProfile(203, 72f, 48);
        return new PaperProfile(203, 48f, 32);
    }

    public static boolean hasBtPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean isPrinterReachable(@NonNull Context ctx) {
        try {
            return BluetoothPrinterManager.getInstance().findSavedBondedDevice(ctx) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void printText(@NonNull Context ctx, @NonNull String formattedText) throws Exception {
        BluetoothPrinterManager.getInstance().printBlocking(ctx, formattedText);
    }

    public static void printTextAsync(@NonNull Context ctx,
                                      @NonNull String formattedText,
                                      @NonNull BluetoothPrinterManager.PrintCallback callback) {
        BluetoothPrinterManager.getInstance().printAsync(ctx, formattedText, new BluetoothPrinterManager.PrintCallback() {
            @Override
            public void onSuccess() {
                main().post(callback::onSuccess);
            }

            @Override
            public void onError(@NonNull String message) {
                main().post(() -> callback.onError(message));
            }

            @Override
            public void onSkipped(@NonNull String message) {
                main().post(() -> callback.onSkipped(message));
            }

            @Override
            public void onTimeout(@NonNull String message) {
                main().post(() -> callback.onTimeout(message));
            }
        });
    }
}
