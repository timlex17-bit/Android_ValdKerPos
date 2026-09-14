package com.valdker.pos.base;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.valdker.pos.utils.ErrorHandler;

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

    /*
      Dulu di sini ada applyFabBottomInset(fab, extraBottomDp) dan
      applyFabEndAndBottomInsets(...). Keduanya dihapus, bukan diperbaiki:
      jarak FAB bukan urusan tiap layar. Sebelas fragment memanggilnya dengan
      angka ajaib 56dp - dimaksudkan untuk menghindari bilah navigasi aplikasi,
      padahal wadah fragment-nya memang sudah berhenti di atas bilah itu - jadi
      FAB-nya mengambang 56dp terlalu tinggi, sementara layar yang tidak
      memanggil apa pun menaruh FAB-nya di belakang bilah navigasi sistem.

      Sekarang ValoraFab yang menghitung sendiri seberapa banyak inset sistem
      yang benar-benar menutupinya. Lihat com.valdker.pos.ui.common.ValoraFab.
    */

    protected int dp(int value) {
        if (getContext() == null) return value;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    protected void showApiError(@Nullable Throwable throwable) {
        if (getContext() == null) return;
        ErrorHandler.handleApiError(requireContext(), throwable);
    }

    protected void showApiError(@Nullable String message) {
        if (getContext() == null) return;
        ErrorHandler.handleApiError(requireContext(), message);
    }
}
