package com.example.valdker.utils;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * InsetsHelper
 * - Prevent list/RecyclerView content from being cut off by system bars (gesture/nav bar).
 * - Keep FAB above system bars.
 */
public final class InsetsHelper {

    private InsetsHelper() {}

    // -------------------------------------------------
    // Recycler bottom inset
    // -------------------------------------------------

    public static void applyRecyclerBottomInsets(
            @NonNull View insetsRoot,
            @Nullable View target
    ) {
        applyRecyclerBottomInsets(insetsRoot, target, null);
    }

    public static void applyRecyclerBottomInsets(
            @NonNull View insetsRoot,
            @Nullable View target,
            @Nullable String logTag
    ) {
        if (target == null) {
            if (logTag != null) Log.w(logTag, "applyRecyclerBottomInsets(): target is null.");
            return;
        }

        final int startL = target.getPaddingLeft();
        final int startT = target.getPaddingTop();
        final int startR = target.getPaddingRight();
        final int startB = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(insetsRoot, (v, insets) -> {

            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            target.setPadding(
                    startL,
                    startT,
                    startR,
                    startB + sys.bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(insetsRoot);
    }

    // -------------------------------------------------
    // Top + bottom inset
    // -------------------------------------------------

    public static void applySystemBarsPadding(
            @NonNull View insetsRoot,
            @Nullable View target
    ) {
        applySystemBarsPadding(insetsRoot, target, null);
    }

    public static void applySystemBarsPadding(
            @NonNull View insetsRoot,
            @Nullable View target,
            @Nullable String logTag
    ) {
        if (target == null) {
            if (logTag != null) Log.w(logTag, "applySystemBarsPadding(): target is null.");
            return;
        }

        final int startL = target.getPaddingLeft();
        final int startT = target.getPaddingTop();
        final int startR = target.getPaddingRight();
        final int startB = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(insetsRoot, (v, insets) -> {

            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            target.setPadding(
                    startL,
                    startT + sys.top,
                    startR,
                    startB + sys.bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(insetsRoot);
    }

    // -------------------------------------------------
    // FAB inset
    // -------------------------------------------------

    public static void applyFabMarginInsets(
            @Nullable View fab,
            int baseMarginDp,
            @Nullable String logTag
    ) {
        if (fab == null) {
            if (logTag != null) Log.w(logTag, "applyFabMarginInsets(): fab is null.");
            return;
        }

        final int baseMarginPx = dp(fab, baseMarginDp);

        ViewGroup.LayoutParams params = fab.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            if (logTag != null) Log.w(logTag, "applyFabMarginInsets(): not MarginLayoutParams.");
            return;
        }

        ViewGroup.MarginLayoutParams lp0 = (ViewGroup.MarginLayoutParams) params;
        final int startBottomMargin = lp0.bottomMargin;
        final int startRightMargin = lp0.rightMargin;

        final View parent = (fab.getParent() instanceof View) ? (View) fab.getParent() : null;
        final int parentPadBottom = parent != null ? parent.getPaddingBottom() : 0;
        final int parentPadRight = parent != null ? parent.getPaddingRight() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, insets) -> {

            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) p;

                lp.bottomMargin =
                        startBottomMargin + baseMarginPx + parentPadBottom + sys.bottom;

                lp.rightMargin =
                        startRightMargin + baseMarginPx + parentPadRight + sys.right;

                v.setLayoutParams(lp);
            }

            return insets;
        });

        ViewCompat.requestApplyInsets(fab);
    }

    private static int dp(@NonNull View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }
}