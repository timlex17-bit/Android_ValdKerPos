package com.valdker.pos.ui.shift;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.valdker.pos.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.math.BigDecimal;

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

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setCancelable(false)
                .setPositiveButton("Open Shift", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String cash = etCash.getText() != null ? etCash.getText().toString().trim() : "";
            String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

            if (cash.isEmpty()) {
                etCash.setError(getString(R.string.msg_opening_cash_required));
                etCash.requestFocus();
                return;
            }

            if (!isValidMoneyAmount(cash)) {
                etCash.setError(getString(R.string.msg_invalid_money_amount));
                etCash.requestFocus();
                return;
            }

            if (listener != null) listener.onSubmit(cash, note);
            dialog.dismiss();
        }));

        return dialog;
    }

    private boolean isValidMoneyAmount(@NonNull String value) {
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            return amount.compareTo(BigDecimal.ZERO) >= 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
