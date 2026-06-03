package com.valdker.pos.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.valdker.pos.BuildConfig;
import com.valdker.pos.R;

public final class SystemBarsFix {

    private SystemBarsFix() {
    }

    public static void applyForcedDetailSafeArea(Activity activity, View root, String screenName) {
        if (activity == null || root == null) {
            if (BuildConfig.DEBUG) {
                Log.e("SYSTEM_BARS", screenName + ": root is null, safe area not applied");
            }
            return;
        }

        Window window = activity.getWindow();
        if (window == null) return;

        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        WindowCompat.setDecorFitsSystemWindows(window, false);

        int statusBarGreen = ContextCompat.getColor(activity, R.color.status_bar_green);
        window.setStatusBarColor(statusBarGreen);
        window.setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
            controller.show(WindowInsetsCompat.Type.navigationBars());
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
        }

        final int originalLeft = root.getPaddingLeft();
        final int originalTop = root.getPaddingTop();
        final int originalRight = root.getPaddingRight();
        final int originalBottom = root.getPaddingBottom();

        final int fallbackStatus = getStatusBarHeight(activity);
        final int fallbackNav = getNavigationBarHeight(activity);

        root.setPadding(
                originalLeft,
                originalTop + fallbackStatus,
                originalRight,
                originalBottom + fallbackNav
        );

        if (BuildConfig.DEBUG) {
            Log.d("STATUS_BAR_THEME", "screen=" + screenName
                    + " color=status_bar_green icons=dark");
            Log.d("SYSTEM_BARS", screenName
                    + ": helper attached"
                    + " fallbackStatus=" + fallbackStatus
                    + " fallbackNav=" + fallbackNav
                    + " initialFinalPaddingTop=" + root.getPaddingTop()
                    + " rootClass=" + root.getClass().getSimpleName()
                    + " rootId=" + root.getId());
        }

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            int top = bars.top > 0 ? bars.top : fallbackStatus;
            int bottom = bars.bottom > 0 ? bars.bottom : fallbackNav;

            v.setPadding(
                    originalLeft,
                    originalTop + top,
                    originalRight,
                    originalBottom + bottom
            );

            if (BuildConfig.DEBUG) {
                Log.d("SYSTEM_BARS", screenName
                        + ": applied safe area"
                        + " statusTop=" + top
                        + " navBottom=" + bottom
                        + " finalPaddingTop=" + v.getPaddingTop()
                        + " finalPaddingBottom=" + v.getPaddingBottom());
            }

            return insets;
        });

        root.post(() -> ViewCompat.requestApplyInsets(root));
    }

    private static int getStatusBarHeight(Context context) {
        int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            return context.getResources().getDimensionPixelSize(resId);
        }
        return (int) (24 * context.getResources().getDisplayMetrics().density);
    }

    private static int getNavigationBarHeight(Context context) {
        int resId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) {
            return context.getResources().getDimensionPixelSize(resId);
        }
        return 0;
    }
}
