package com.valdker.pos.print;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;

public final class PrinterService {

    private PrinterService() {}

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

    @Nullable
    public static BluetoothConnection findSavedPrinter(@NonNull Context ctx) {
        String mac = PrinterPrefs.getMac(ctx);
        if (mac == null || mac.trim().isEmpty()) return null;

        BluetoothPrintersConnections printers = new BluetoothPrintersConnections();
        BluetoothConnection[] list = printers.getList();
        if (list == null) return null;

        for (BluetoothConnection c : list) {
            try {
                if (c != null && c.getDevice() != null) {
                    String addr = c.getDevice().getAddress();
                    if (mac.equalsIgnoreCase(addr)) return c;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static boolean isPrinterReachable(@NonNull Context ctx) {
        return findSavedPrinter(ctx) != null;
    }

    public static void printText(@NonNull Context ctx, @NonNull String formattedText) throws Exception {
        BluetoothConnection conn = findSavedPrinter(ctx);
        if (conn == null) throw new IllegalStateException("Printer seidauk hili. Konfigura Printer iha Settings uluk.");

        int mm = PrinterPrefs.getPaperWidthMm(ctx);
        PaperProfile p = profileForMm(mm);

        EscPosPrinter printer = new EscPosPrinter(conn, p.dpi, p.mmWidth, p.chars);
        printer.printFormattedText(formattedText);

    }
}
