package com.valdker.pos.print;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BluetoothPrinterManager {

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        PRINTING,
        ERROR
    }

    public enum PrintResult {
        SUCCESS,
        FAILED,
        SKIPPED,
        TIMEOUT
    }

    public interface PrintCallback {
        void onSuccess();
        void onError(@NonNull String message);
        void onSkipped(@NonNull String message);
        default void onTimeout(@NonNull String message) {
            onError(message);
        }
    }

    private static final String TAG = "PRINTER";
    private static final long CONNECT_TIMEOUT_MS = 10_000L;
    private static final long PRINT_TIMEOUT_MS = 20_000L;
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final BluetoothPrinterManager INSTANCE = new BluetoothPrinterManager();

    private final Object lock = new Object();
    private final AtomicBoolean printing = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, "valdker-printer");
            t.setDaemon(true);
            return t;
        }
    });
    private final ExecutorService watchdogExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, "valdker-printer-watchdog");
            t.setDaemon(true);
            return t;
        }
    });

    @Nullable
    private BluetoothSocket socket;
    @Nullable
    private OutputStream outputStream;
    @Nullable
    private String connectedMac;
    private volatile State state = State.DISCONNECTED;

    private BluetoothPrinterManager() {
    }

    @NonNull
    public static BluetoothPrinterManager getInstance() {
        return INSTANCE;
    }

    @NonNull
    public State getState() {
        return state;
    }

    public boolean isPrinting() {
        return printing.get();
    }

    public boolean isConnectedToSavedPrinter(@NonNull Context context) {
        String mac = PrinterPrefs.getMac(context);
        synchronized (lock) {
            return isConnectedLocked(mac);
        }
    }

    @NonNull
    public Future<?> printAsync(@NonNull Context context,
                                @NonNull String formattedText,
                                @Nullable PrintCallback callback) {
        Context appCtx = context.getApplicationContext();
        AtomicBoolean delivered = new AtomicBoolean(false);
        Future<?> task = executor.submit(() -> {
            try {
                printBlocking(appCtx, formattedText);
                deliverSuccess(callback, delivered);
            } catch (PrintAlreadyRunningException e) {
                deliverSkipped(callback, delivered, e.getMessage());
            } catch (PrintTimeoutException e) {
                deliverTimeout(callback, delivered, e.getMessage());
            } catch (Exception e) {
                deliverError(callback, delivered, safeMessage(e));
            }
        });
        watchdogExecutor.execute(() -> {
            try {
                task.get(PRINT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Log.e(TAG, "PRINTER: print timed out after " + PRINT_TIMEOUT_MS + "ms");
                task.cancel(true);
                forceCloseAfterTimeout();
                printing.set(false);
                deliverTimeout(callback, delivered,
                        "Printer connection timed out. Please check printer and try reprint.");
            } catch (Exception ignored) {
                // The worker reports success/failure through the callback.
            }
        });
        return task;
    }

    public void printBlocking(@NonNull Context context, @NonNull String formattedText) throws Exception {
        if (!printing.compareAndSet(false, true)) {
            Log.w(TAG, "PRINTER: print skipped because already printing");
            throw new PrintAlreadyRunningException("Receipt is already printing.");
        }

        try {
            synchronized (lock) {
                state = State.PRINTING;
                Log.i(TAG, "PRINTER: print started");

                ensureConnectedLocked(context.getApplicationContext());

                try {
                    writeReceiptLocked(context, formattedText);
                } catch (IOException first) {
                    Log.w(TAG, "PRINTER: write failed, reconnecting: " + first.getMessage());
                    closeQuietlyLocked();
                    ensureConnectedLocked(context.getApplicationContext());
                    writeReceiptLocked(context, formattedText);
                }

                state = State.CONNECTED;
                Log.i(TAG, "PRINTER: print success");
            }
        } catch (Exception e) {
            synchronized (lock) {
                state = State.ERROR;
                closeQuietlyLocked();
            }
            Log.e(TAG, "PRINTER: print failed: " + safeMessage(e), e);
            throw e;
        } finally {
            printing.set(false);
        }
    }

    public void disconnect() {
        synchronized (lock) {
            closeQuietlyLocked();
        }
    }

    @SuppressLint("MissingPermission")
    @Nullable
    public BluetoothDevice findSavedBondedDevice(@NonNull Context context) throws Exception {
        String mac = PrinterPrefs.getMac(context);
        if (TextUtils.isEmpty(mac)) return null;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            throw new IllegalStateException("Bluetooth is not supported on this device.");
        }
        if (!adapter.isEnabled()) {
            throw new IllegalStateException("Bluetooth is disabled. Please enable Bluetooth first.");
        }
        if (!hasBtConnectPermission(context)) {
            throw new SecurityException("Bluetooth permission is required to connect printer.");
        }

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) return null;

        for (BluetoothDevice device : bonded) {
            if (device != null && mac.equalsIgnoreCase(device.getAddress())) {
                return device;
            }
        }
        return null;
    }

    private void ensureConnectedLocked(@NonNull Context context) throws Exception {
        String mac = PrinterPrefs.getMac(context);
        String name = PrinterPrefs.getName(context);
        if (TextUtils.isEmpty(mac)) {
            throw new IllegalStateException("Printer not connected. Please select printer.");
        }

        if (isConnectedLocked(mac)) {
            return;
        }

        if (socket != null || outputStream != null) {
            Log.i(TAG, "PRINTER: reconnecting");
            closeQuietlyLocked();
        }

        BluetoothDevice device = findSavedBondedDevice(context);
        if (device == null) {
            throw new IllegalStateException("Saved printer was not found in paired Bluetooth devices.");
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            try {
                adapter.cancelDiscovery();
            } catch (SecurityException ignored) {
            }
        }

        state = State.CONNECTING;
        Log.i(TAG, "PRINTER: connecting to " + safeName(name, device) + "/" + mac);

        BluetoothSocket newSocket = connectWithTimeout(device);

        socket = newSocket;
        outputStream = newSocket.getOutputStream();
        connectedMac = mac;
        state = State.CONNECTED;
        Log.i(TAG, "PRINTER: connected");
    }

    @SuppressLint("MissingPermission")
    @NonNull
    private BluetoothSocket connectWithTimeout(@NonNull BluetoothDevice device) throws Exception {
        BluetoothSocket newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        AtomicBoolean done = new AtomicBoolean(false);
        final Exception[] error = new Exception[1];
        Thread connector = new Thread(() -> {
            try {
                newSocket.connect();
            } catch (Exception e) {
                error[0] = e;
            } finally {
                done.set(true);
            }
        }, "valdker-printer-connect");
        connector.setDaemon(true);
        connector.start();
        connector.join(CONNECT_TIMEOUT_MS);
        if (!done.get()) {
            try {
                newSocket.close();
            } catch (Exception ignored) {
            }
            connector.interrupt();
            throw new PrintTimeoutException("Printer connection timed out. Please check printer and try reprint.");
        }
        if (error[0] != null) {
            throw error[0];
        }
        return newSocket;
    }

    private boolean isConnectedLocked(@Nullable String mac) {
        return socket != null
                && outputStream != null
                && socket.isConnected()
                && !TextUtils.isEmpty(mac)
                && mac.equalsIgnoreCase(connectedMac);
    }

    private void writeReceiptLocked(@NonNull Context context, @NonNull String formattedText) throws IOException {
        if (outputStream == null) {
            throw new IOException("Printer output stream is not ready.");
        }

        int paperWidth = PrinterPrefs.getPaperWidthMm(context);
        int chars = PrinterService.profileForMm(paperWidth).chars;
        byte[] data = EscPosTextEncoder.encode(formattedText, chars);
        outputStream.write(data);
        outputStream.flush();
    }

    private void closeQuietlyLocked() {
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {
        }
        outputStream = null;

        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        connectedMac = null;
        state = State.DISCONNECTED;
        Log.i(TAG, "PRINTER: socket closed");
    }

    private void forceCloseAfterTimeout() {
        state = State.ERROR;
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        outputStream = null;
        socket = null;
        connectedMac = null;
        Log.i(TAG, "PRINTER: socket closed after timeout");
    }

    private static boolean hasBtConnectPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private static String safeName(@Nullable String savedName, @NonNull BluetoothDevice device) {
        if (!TextUtils.isEmpty(savedName)) return savedName.trim();
        try {
            String name = device.getName();
            if (!TextUtils.isEmpty(name)) return name.trim();
        } catch (SecurityException ignored) {
        }
        return "Unknown printer";
    }

    @NonNull
    private static String safeMessage(@Nullable Throwable t) {
        if (t == null || TextUtils.isEmpty(t.getMessage())) return "Unknown printer error";
        return t.getMessage();
    }

    private static final class PrintAlreadyRunningException extends Exception {
        PrintAlreadyRunningException(@NonNull String message) {
            super(message);
        }
    }

    private static final class PrintTimeoutException extends Exception {
        PrintTimeoutException(@NonNull String message) {
            super(message);
        }
    }

    private static void deliverSuccess(@Nullable PrintCallback callback, @NonNull AtomicBoolean delivered) {
        if (callback != null && delivered.compareAndSet(false, true)) callback.onSuccess();
    }

    private static void deliverError(@Nullable PrintCallback callback,
                                     @NonNull AtomicBoolean delivered,
                                     @NonNull String message) {
        if (callback != null && delivered.compareAndSet(false, true)) callback.onError(message);
    }

    private static void deliverSkipped(@Nullable PrintCallback callback,
                                       @NonNull AtomicBoolean delivered,
                                       @Nullable String message) {
        if (callback != null && delivered.compareAndSet(false, true)) {
            callback.onSkipped(TextUtils.isEmpty(message) ? "Receipt printing skipped." : message);
        }
    }

    private static void deliverTimeout(@Nullable PrintCallback callback,
                                       @NonNull AtomicBoolean delivered,
                                       @Nullable String message) {
        if (callback != null && delivered.compareAndSet(false, true)) {
            callback.onTimeout(TextUtils.isEmpty(message)
                    ? "Printer connection timed out. Please check printer and try reprint."
                    : message);
        }
    }

    private static final class EscPosTextEncoder {
        private static final byte ESC = 0x1B;

        private EscPosTextEncoder() {
        }

        @NonNull
        static byte[] encode(@NonNull String formattedText, int charsPerLine) throws IOException {
            int width = Math.max(24, charsPerLine);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            out.write(new byte[]{ESC, '@'});
            out.write(new byte[]{ESC, 't', 0});

            String[] lines = formattedText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            for (String raw : lines) {
                Line line = parseLine(raw);
                out.write(new byte[]{ESC, 'a', line.align});
                out.write(new byte[]{ESC, 'E', (byte) (line.bold ? 1 : 0)});
                writeAscii(out, fit(line.text, width));
                out.write('\n');
                if (line.bold) {
                    out.write(new byte[]{ESC, 'E', 0});
                }
            }

            out.write('\n');
            out.write('\n');
            return out.toByteArray();
        }

        @NonNull
        private static Line parseLine(@NonNull String raw) {
            byte align = 0;
            String text = raw;

            if (text.startsWith("[C]")) {
                align = 1;
                text = text.substring(3);
            } else if (text.startsWith("[R]")) {
                align = 2;
                text = text.substring(3);
            } else if (text.startsWith("[L]")) {
                align = 0;
                text = text.substring(3);
            }

            boolean bold = text.contains("<b>") || text.contains("</b>");
            text = text.replace("[L]", "")
                    .replace("[C]", "")
                    .replace("[R]", " ")
                    .replace("<b>", "")
                    .replace("</b>", "");

            return new Line(align, bold, sanitize(text));
        }

        @NonNull
        private static String fit(@NonNull String text, int width) {
            if (text.length() <= width) return text;
            StringBuilder sb = new StringBuilder();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + width, text.length());
                sb.append(text, start, end);
                start = end;
                if (start < text.length()) sb.append('\n');
            }
            return sb.toString();
        }

        private static void writeAscii(@NonNull ByteArrayOutputStream out, @NonNull String text) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                out.write(c <= 0x7F ? c : '?');
            }
        }

        @NonNull
        private static String sanitize(@Nullable String value) {
            if (value == null) return "";
            String clean = Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return clean.replace("\u2705", "*")
                    .replace("\u2014", "-")
                    .replace("\u2013", "-")
                    .replace("\u2022", "-")
                    .replace("\u00e2\u20ac\u201d", "-")
                    .replace("\u00e2\u20ac\u00a2", "-")
                    .replace("\u00e2\u2013\u00a0", "*")
                    .replace("\u00e2\u2013\u00b2", "^")
                    .trim();
        }

        private static final class Line {
            final byte align;
            final boolean bold;
            @NonNull final String text;

            Line(byte align, boolean bold, @NonNull String text) {
                this.align = align;
                this.bold = bold;
                this.text = text;
            }
        }
    }
}
