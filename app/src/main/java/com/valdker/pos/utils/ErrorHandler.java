package com.valdker.pos.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.android.volley.NoConnectionError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.valdker.pos.LoginActivity;
import com.valdker.pos.SessionManager;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ErrorHandler {

    private static final String DEFAULT_API_ERROR = "Request error";
    private static final String ACCESS_DENIED = "Access denied.";
    private static final String SERVER_ERROR = "Server error, please try again.";
    private static final String SESSION_EXPIRED = "Session expired. Please login again.";
    private static final String NO_INTERNET = "No internet connection.";
    private static long lastNoInternetShownAt = 0L;
    private static long lastAuthRedirectAt = 0L;
    private static final long NO_INTERNET_DIALOG_GUARD_MS = 1200L;
    private static final long AUTH_REDIRECT_GUARD_MS = 1200L;

    private ErrorHandler() {
    }

    public static void handleApiError(@Nullable Context context, @Nullable Throwable throwable) {
        if (context == null) return;

        if (!NetworkUtils.isNetworkAvailable(context) || isNetworkError(throwable)) {
            showNoInternet(context, null);
            return;
        }

        int statusCode = getStatusCode(throwable);
        if (statusCode > 0) {
            handleHttpStatus(context, statusCode, getResponseBody(throwable));
            return;
        }

        String message = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : DEFAULT_API_ERROR;
        showToast(context, sanitizeMessage(message));
    }

    public static void handleApiError(@Nullable Context context, @Nullable String message) {
        if (context == null) return;

        String safeMessage = message == null ? "" : message.trim();
        if (isDeviceTimeValidationError(400, safeMessage)) {
            showDeviceTimeDialog(context);
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(context) || isNetworkError(safeMessage)) {
            showNoInternet(context, null);
            return;
        }

        int statusCode = extractStatusCode(safeMessage);
        if (statusCode > 0) {
            handleHttpStatus(context, statusCode, safeMessage);
            return;
        }

        showToast(context, sanitizeMessage(safeMessage.isEmpty() ? DEFAULT_API_ERROR : safeMessage));
    }

    private static void handleHttpStatus(@NonNull Context context, int statusCode, @Nullable String detail) {
        if (isDeviceTimeValidationError(statusCode, detail)) {
            showDeviceTimeDialog(context);
            return;
        }

        if (statusCode == 401) {
            redirectToLogin(context);
            return;
        }

        if (statusCode == 403) {
            showToast(context, ACCESS_DENIED);
            return;
        }

        if (statusCode >= 500) {
            showToast(context, SERVER_ERROR);
            return;
        }

        showToast(context, sanitizeMessage(detail));
    }

    public static boolean isDeviceTimeValidationError(int statusCode, @Nullable String message) {
        if (statusCode != 400 || message == null) return false;

        String m = message.trim().toLowerCase(Locale.US);
        if (m.isEmpty()) return false;

        return m.contains("device date/time is not synchronized with server time")
                || m.contains("time_drift_seconds")
                || m.contains("device_time");
    }

    public static void showDeviceTimeDialog(@Nullable Context context) {
        if (context == null) return;

        Runnable action = () -> {
            FragmentActivity activity = findFragmentActivity(context);
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Toast.makeText(context, "Waktu perangkat tidak sesuai", Toast.LENGTH_LONG).show();
                return;
            }

            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Waktu Perangkat Tidak Sesuai")
                    .setMessage("Tanggal atau waktu HP/tablet tidak sesuai dengan server. Aktifkan tanggal & waktu otomatis di pengaturan perangkat, lalu coba lagi.")
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .show();
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    public static boolean isNetworkError(@Nullable Throwable throwable) {
        if (throwable == null) return false;

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof IOException
                    || current instanceof TimeoutError
                    || current instanceof NoConnectionError) {
                return true;
            }

            if (current instanceof VolleyError) {
                VolleyError volleyError = (VolleyError) current;
                if (volleyError.networkResponse == null && volleyError.getCause() == null) {
                    return true;
                }
            }

            if (isNetworkError(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isNetworkError(@Nullable String message) {
        if (message == null) return false;

        String m = message.trim().toLowerCase(Locale.US);
        if (m.isEmpty()) return false;

        return m.contains("no internet")
                || m.contains("tidak ada koneksi")
                || m.contains("koneksi internet")
                || m.contains("network")
                || m.contains("internet")
                || m.contains("network error")
                || m.contains("fetch failed")
                || m.contains("request error")
                || m.contains("unable")
                || m.contains("unable to resolve host")
                || m.contains("no address associated with hostname")
                || m.contains("unknownhostexception")
                || m.contains("sockettimeoutexception")
                || m.contains("connectexception")
                || m.contains("ioexception")
                || m.contains("timeout")
                || m.contains("timed out")
                || m.contains("failed to connect")
                || m.contains("failed to load")
                || m.contains("gagal memuat")
                || m.contains("gagal load")
                || m.contains("load failed")
                || m.contains("connection");
    }

    public static void showNoInternet(@Nullable Context context, @Nullable Runnable retryCallback) {
        if (context == null) return;

        Runnable action = () -> showNoInternetOnMain(context, retryCallback);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    private static void showNoInternetOnMain(@NonNull Context context, @Nullable Runnable retryCallback) {
        FragmentActivity activity = findFragmentActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Toast.makeText(context, NO_INTERNET, Toast.LENGTH_LONG).show();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNoInternetShownAt < NO_INTERNET_DIALOG_GUARD_MS) {
            return;
        }

        FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(NoInternetDialog.TAG) != null) {
            return;
        }

        lastNoInternetShownAt = now;
        NoInternetDialog dialog = NoInternetDialog.newInstance();
        dialog.setRetryCallback(retryCallback);
        dialog.show(fm, NoInternetDialog.TAG);
    }

    private static int getStatusCode(@Nullable Throwable throwable) {
        if (throwable instanceof VolleyError) {
            VolleyError volleyError = (VolleyError) throwable;
            if (volleyError.networkResponse != null) {
                return volleyError.networkResponse.statusCode;
            }
        }
        return 0;
    }

    @Nullable
    private static String getResponseBody(@Nullable Throwable throwable) {
        if (throwable instanceof VolleyError) {
            VolleyError volleyError = (VolleyError) throwable;
            if (volleyError.networkResponse != null && volleyError.networkResponse.data != null) {
                return new String(volleyError.networkResponse.data, StandardCharsets.UTF_8).trim();
            }
        }
        return throwable != null ? throwable.getMessage() : null;
    }

    private static int extractStatusCode(@Nullable String message) {
        if (message == null) return 0;
        String m = message.toLowerCase(Locale.US);
        if (m.contains("http 401") || m.contains("(401)") || m.contains(" 401 ")) return 401;
        if (m.contains("http 403") || m.contains("(403)") || m.contains(" 403 ")) return 403;
        if (m.contains("http 500") || m.contains("(500)") || m.contains(" 500 ")) return 500;
        if (m.contains("http 502") || m.contains("(502)") || m.contains(" 502 ")) return 502;
        if (m.contains("http 503") || m.contains("(503)") || m.contains(" 503 ")) return 503;
        if (m.contains("http 504") || m.contains("(504)") || m.contains(" 504 ")) return 504;
        return 0;
    }

    @NonNull
    private static String sanitizeMessage(@Nullable String message) {
        if (message == null) return DEFAULT_API_ERROR;
        String m = message.trim();
        if (m.isEmpty()) return DEFAULT_API_ERROR;
        String lower = m.toLowerCase(Locale.US);
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<body")) {
            return SERVER_ERROR;
        }
        return m.length() > 180 ? m.substring(0, 180).trim() : m;
    }

    private static void showToast(@NonNull Context context, @Nullable String message) {
        Toast.makeText(context, sanitizeMessage(message), Toast.LENGTH_LONG).show();
    }

    private static void redirectToLogin(@NonNull Context context) {
        long now = System.currentTimeMillis();
        if (now - lastAuthRedirectAt < AUTH_REDIRECT_GUARD_MS) {
            return;
        }
        lastAuthRedirectAt = now;

        new SessionManager(context).clearAuth();
        Toast.makeText(context, SESSION_EXPIRED, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        Activity activity = findActivity(context);
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
    }

    @Nullable
    private static FragmentActivity findFragmentActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof FragmentActivity) {
                return (FragmentActivity) current;
            }
            if (current instanceof Activity && !(current instanceof FragmentActivity)) {
                return null;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
