package com.valdker.pos;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;

public class BarcodeScannerDialogFragment extends DialogFragment {

    public interface Listener {
        void onBarcode(@NonNull String barcode);
    }

    private static final long SAME_CODE_COOLDOWN_MS = 1200L;
    private static final long DELIVER_LOCK_MS = 700L;

    @Nullable
    private Listener listener;

    @Nullable
    private DecoratedBarcodeView barcodeView;

    @Nullable
    private TextView tvLast;

    @Nullable
    private ToneGenerator toneGenerator;

    @NonNull
    private String lastCode = "";

    private long lastTime = 0L;
    private boolean deliveringResult = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    public void setListener(@Nullable Listener l) {
        this.listener = l;
    }

    @Override
    public int getTheme() {
        return android.R.style.Theme_Translucent_NoTitleBar;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.dialog_barcode_scanner, container, false);

        barcodeView = v.findViewById(R.id.barcodeView);
        tvLast = v.findViewById(R.id.tvLast);

        if (barcodeView != null) barcodeView.setStatusText("");

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Exception ignored) {
            toneGenerator = null;
        }

        ImageButton btnClose = v.findViewById(R.id.btnCloseScanner);
        if (btnClose != null) {
            btnClose.setOnClickListener(x -> dismissAllowingStateLoss());
        }

        if (barcodeView != null) {

            barcodeView.decodeContinuous(result -> {

                if (!isAdded()) return;
                if (deliveringResult) return;
                if (result == null || result.getText() == null) return;

                String code = result.getText().trim();
                if (code.isEmpty()) return;

                long now = System.currentTimeMillis();

                if (code.equals(lastCode) && (now - lastTime) < SAME_CODE_COOLDOWN_MS) {
                    return;
                }

                lastCode = code;
                lastTime = now;
                deliveringResult = true;

                if (tvLast != null) {
                    tvLast.setText("Last: " + code);
                }

                beep();
                vibrate();

                if (listener != null) {
                    listener.onBarcode(code);
                }

                handler.postDelayed(() -> deliveringResult = false, DELIVER_LOCK_MS);
            });
        }

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        deliveringResult = false;
        if (barcodeView != null) barcodeView.resume();
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);
        if (barcodeView != null) barcodeView.pause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {

        handler.removeCallbacksAndMessages(null);

        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Exception ignored) {}
            toneGenerator = null;
        }

        barcodeView = null;
        tvLast = null;

        super.onDestroyView();
    }

    private void beep() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            }
        } catch (Exception ignored) {}
    }

    private void vibrate() {
        try {

            Context ctx = getContext();
            if (ctx == null) return;

            Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(50);
            }

        } catch (Exception ignored) {}
    }
}