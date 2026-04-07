package com.valdker.pos.utils;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * InsetsHelper
 * - Prevents list/RecyclerView content from being cut off by system bars (gesture/navigation bar).
 * - Keeps floating buttons above system bars.
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

    /**
     * Adds extra bottom padding to a scrollable root so content can scroll fully above system bars.
     * Note: Call this on the scroll container (e.g., NestedScrollView, ScrollView).
     */
    public static void applyScrollBottomInsets(@NonNull View root, @Nullable String logTag) {
        final int startL = root.getPaddingLeft();
        final int startT = root.getPaddingTop();
        final int startR = root.getPaddingRight();
        final int startB = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(
                    startL,
                    startT,
                    startR,
                    startB + bars.bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    // -------------------------------------------------
    // FAB / view margin inset
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

        ViewGroup.LayoutParams params = fab.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            if (logTag != null) Log.w(logTag, "applyFabMarginInsets(): layout params are not MarginLayoutParams.");
            return;
        }

        final int baseMarginPx = dp(fab, baseMarginDp);

        ViewGroup.MarginLayoutParams lp0 = (ViewGroup.MarginLayoutParams) params;
        final int startBottomMargin = lp0.bottomMargin;
        final int startRightMargin = lp0.rightMargin;
        final int startLeftMargin = lp0.leftMargin;
        final int startTopMargin = lp0.topMargin;

        final View parent = (fab.getParent() instanceof View) ? (View) fab.getParent() : null;
        final int parentPadBottom = parent != null ? parent.getPaddingBottom() : 0;
        final int parentPadRight = parent != null ? parent.getPaddingRight() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) p;

                lp.leftMargin = startLeftMargin;
                lp.topMargin = startTopMargin;

                lp.bottomMargin = startBottomMargin + baseMarginPx + parentPadBottom + sys.bottom;
                lp.rightMargin = startRightMargin + baseMarginPx + parentPadRight + sys.right;

                v.setLayoutParams(lp);
            }

            return insets;
        });

        ViewCompat.requestApplyInsets(fab);
    }

    // -------------------------------------------------
    // Recycler bottom inset with FAB spacing
    // -------------------------------------------------

    public static void applyRecyclerBottomInsetsWithFab(
            @NonNull View insetsRoot,
            @Nullable RecyclerView recycler,
            @Nullable View fab,
            int extraSpaceDp,
            @Nullable String logTag
    ) {
        if (recycler == null) {
            if (logTag != null) Log.w(logTag, "applyRecyclerBottomInsetsWithFab(): recycler is null.");
            return;
        }

        if (fab == null) {
            applyRecyclerBottomInsets(insetsRoot, recycler, logTag);
            return;
        }

        final int startL = recycler.getPaddingLeft();
        final int startT = recycler.getPaddingTop();
        final int startR = recycler.getPaddingRight();
        final int startB = recycler.getPaddingBottom();
        final int extraPx = dp(recycler, extraSpaceDp);

        recycler.setClipToPadding(false);

        fab.post(() -> {
            final int fabH = fab.getHeight() > 0 ? fab.getHeight() : dp(fab, 56);

            ViewCompat.setOnApplyWindowInsetsListener(insetsRoot, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                int extraBottom = sys.bottom + fabH + extraPx;

                recycler.setPadding(startL, startT, startR, startB + extraBottom);

                return insets;
            });

            ViewCompat.requestApplyInsets(insetsRoot);
        });
    }

    private static int dp(@NonNull View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------
    // ScrollView system bars inset (safe, no padding inflation)
    // -------------------------------------------------

    public static void applyScrollInsets(@NonNull View scrollView) {

        final int startL = scrollView.getPaddingLeft();
        final int startT = scrollView.getPaddingTop();
        final int startR = scrollView.getPaddingRight();
        final int startB = scrollView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {

            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Apply top and bottom system bar padding
            // while preserving original paddings.
            v.setPadding(
                    startL,
                    startT + sys.top,
                    startR,
                    startB + sys.bottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(scrollView);
    }

    private void applyTopInset(View target) {
        if (target == null) return;

        final int initialLeft = target.getPaddingLeft();
        final int initialTop = target.getPaddingTop();
        final int initialRight = target.getPaddingRight();
        final int initialBottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(
                    initialLeft,
                    initialTop + topInset,
                    initialRight,
                    initialBottom
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(target);
    }
}