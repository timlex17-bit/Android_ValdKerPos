package com.valdker.pos.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.R;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class Toast {

    public static final int LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = android.widget.Toast.LENGTH_LONG;
    private static final long DUPLICATE_WINDOW_MS = 4000L;
    private static final long NOISY_ERROR_WINDOW_MS = 9000L;
    private static final long NOISY_GLOBAL_WINDOW_MS = 1400L;

    @Nullable
    private static android.widget.Toast currentToast;
    @NonNull
    private static final Map<String, Long> lastMessageShownAt = new HashMap<>();
    private static long lastNoisyShownAt = 0L;

    @Nullable
    private final Context context;
    @NonNull
    private final CharSequence message;
    private final int duration;

    private Toast(@Nullable Context context, @NonNull CharSequence message, int duration) {
        this.context = context;
        this.message = message;
        this.duration = duration;
    }

    @NonNull
    public static Toast makeText(@Nullable Context context, @Nullable CharSequence message, int duration) {
        return new Toast(context, message == null ? "" : message, duration);
    }

    @NonNull
    public static Toast makeText(@Nullable Context context, int resId, int duration) {
        CharSequence message = "";
        if (context != null) {
            try {
                message = context.getText(resId);
            } catch (Exception ignored) {
            }
        }
        return new Toast(context, message, duration);
    }

    public void show() {
        if (context == null || message.length() == 0) return;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOnMainThread();
        } else {
            new Handler(Looper.getMainLooper()).post(this::showOnMainThread);
        }
    }

    public void cancel() {
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
    }

    private void showOnMainThread() {
        try {
            String normalizedMessage = normalizeMessage(message);
            if (shouldSuppress(normalizedMessage)) {
                return;
            }

            if (currentToast != null) {
                currentToast.cancel();
            }

            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.view_app_popup_toast, null, false);
            TextView tvMessage = view.findViewById(R.id.tvPopupToastMessage);
            tvMessage.setText(message);

            android.widget.Toast toast = new android.widget.Toast(context.getApplicationContext());
            toast.setDuration(duration);
            toast.setView(view);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(context, 88));
            currentToast = toast;
            toast.show();
        } catch (Exception e) {
            android.widget.Toast.makeText(context, message, duration).show();
        }
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static String normalizeMessage(@NonNull CharSequence raw) {
        return raw.toString()
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.US);
    }

    private static boolean shouldSuppress(@NonNull String normalizedMessage) {
        long now = System.currentTimeMillis();
        pruneOldMessages(now);

        Long lastSame = lastMessageShownAt.get(normalizedMessage);
        boolean noisy = isNoisyError(normalizedMessage);
        long sameMessageWindow = noisy ? NOISY_ERROR_WINDOW_MS : DUPLICATE_WINDOW_MS;

        if (lastSame != null && now - lastSame < sameMessageWindow) {
            return true;
        }

        if (noisy && now - lastNoisyShownAt < NOISY_GLOBAL_WINDOW_MS) {
            return true;
        }

        lastMessageShownAt.put(normalizedMessage, now);
        if (noisy) {
            lastNoisyShownAt = now;
        }
        return false;
    }

    private static boolean isNoisyError(@NonNull String message) {
        return message.contains("network")
                || message.contains("timeout")
                || message.contains("failed to load")
                || message.contains("load failed")
                || message.contains("gagal memuat")
                || message.contains("gagal load")
                || message.contains("gagal mengambil")
                || message.contains("gagal sinkron")
                || message.contains("failed:")
                || message.contains("error:");
    }

    private static void pruneOldMessages(long now) {
        if (lastMessageShownAt.size() < 80) return;

        Iterator<Map.Entry<String, Long>> iterator = lastMessageShownAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > 30000L) {
                iterator.remove();
            }
        }
    }
}
