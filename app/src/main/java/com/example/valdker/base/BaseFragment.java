package com.example.valdker.base;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public abstract class BaseFragment extends Fragment {

    public BaseFragment() {
        super();
    }

    public BaseFragment(int layoutId) {
        super(layoutId);
    }

    protected void applyTopInset(@Nullable View topBar) {
        if (topBar == null) return;

        final int baseLeft = topBar.getPaddingLeft();
        final int baseTop = topBar.getPaddingTop();
        final int baseRight = topBar.getPaddingRight();
        final int baseBottom = topBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(
                    baseLeft,
                    baseTop + topInset,
                    baseRight,
                    baseBottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(topBar);
    }

    protected void applyTopInsetWithExtra(@Nullable View topBar, int extraTopPx) {
        if (topBar == null) return;

        final int baseLeft = topBar.getPaddingLeft();
        final int baseTop = topBar.getPaddingTop();
        final int baseRight = topBar.getPaddingRight();
        final int baseBottom = topBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(
                    baseLeft,
                    baseTop + topInset + Math.max(extraTopPx, 0),
                    baseRight,
                    baseBottom
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(topBar);
    }

    protected void applyBottomInset(@Nullable View view) {
        if (view == null) return;

        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(
                    baseLeft,
                    baseTop,
                    baseRight,
                    baseBottom + bottomInset
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(view);
    }

    protected void applyVerticalInsets(@Nullable View view) {
        if (view == null) return;

        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(
                    baseLeft,
                    baseTop + topInset,
                    baseRight,
                    baseBottom + bottomInset
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Universal FAB bottom inset.
     *
     * XML cukup pakai:
     * android:layout_marginEnd="16dp"
     * android:layout_marginBottom="16dp"
     *
     * Lalu di Fragment panggil:
     * applyFabBottomInset(fab, 56);
     */
    protected void applyFabBottomInset(@Nullable View fab, int extraBottomDp) {
        if (fab == null) return;

        ViewGroup.LayoutParams params = fab.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;

        final int baseLeft = lp.leftMargin;
        final int baseTop = lp.topMargin;
        final int baseRight = lp.rightMargin;
        final int baseBottom = lp.bottomMargin;
        final int extraBottomPx = dp(extraBottomDp);

        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, insets) -> {
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            lp.leftMargin = baseLeft;
            lp.topMargin = baseTop;
            lp.rightMargin = baseRight;
            lp.bottomMargin = baseBottom + bottomInset + extraBottomPx;

            v.setLayoutParams(lp);
            return insets;
        });

        ViewCompat.requestApplyInsets(fab);
    }

    /**
     * Kalau ingin FAB ikut aman juga dari sisi kanan pada device tertentu.
     * Optional, dipakai kalau dibutuhkan.
     */
    protected void applyFabEndAndBottomInsets(@Nullable View fab, int extraEndDp, int extraBottomDp) {
        if (fab == null) return;

        ViewGroup.LayoutParams params = fab.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;

        final int baseLeft = lp.leftMargin;
        final int baseTop = lp.topMargin;
        final int baseRight = lp.rightMargin;
        final int baseBottom = lp.bottomMargin;
        final int extraEndPx = dp(extraEndDp);
        final int extraBottomPx = dp(extraBottomDp);

        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, insets) -> {
            int rightInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            lp.leftMargin = baseLeft;
            lp.topMargin = baseTop;
            lp.rightMargin = baseRight + rightInset + extraEndPx;
            lp.bottomMargin = baseBottom + bottomInset + extraBottomPx;

            v.setLayoutParams(lp);
            return insets;
        });

        ViewCompat.requestApplyInsets(fab);
    }

    protected int dp(int value) {
        if (getContext() == null) return value;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}