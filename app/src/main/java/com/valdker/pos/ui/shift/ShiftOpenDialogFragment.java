package com.valdker.pos.ui.shift;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.valdker.pos.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ShiftOpenDialogFragment extends DialogFragment {

    public interface Listener {
        void onSubmit(@NonNull String openingCash, @NonNull String note);
    }

    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shift_open, null, false);
        EditText etCash = v.findViewById(R.id.etOpeningCash);
        EditText etNote = v.findViewById(R.id.etNote);

        setCancelable(false);

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setCancelable(false)
                .setPositiveButton("Open Shift", (d, which) -> {
                    String cash = etCash.getText().toString().trim();
                    String note = etNote.getText().toString().trim();
                    if (cash.isEmpty()) cash = "0";
                    if (listener != null) listener.onSubmit(cash, note);
                })
                .create();
    }
}