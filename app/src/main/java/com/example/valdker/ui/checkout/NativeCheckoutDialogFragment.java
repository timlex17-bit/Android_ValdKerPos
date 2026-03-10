package com.example.valdker.ui.checkout;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.Locale;

public class NativeCheckoutDialogFragment extends DialogFragment {

    private static final String TAG = "NATIVE_CHECKOUT";

    public interface Listener {
        void onConfirm(@NonNull String paymentMethod,
                       double cashReceived,
                       double changeAmount,
                       @NonNull String tableNumber,
                       @NonNull String deliveryAddress,
                       double deliveryFee);
    }

    private static final String ARG_TOTAL = "arg_total";          // subtotal
    private static final String ARG_NEED_TABLE = "arg_need_table";
    private static final String ARG_NEED_DELIVERY = "arg_need_delivery";

    public static NativeCheckoutDialogFragment newInstance(double total,
                                                           boolean needTable,
                                                           boolean needDelivery) {
        NativeCheckoutDialogFragment f = new NativeCheckoutDialogFragment();
        Bundle b = new Bundle();
        b.putDouble(ARG_TOTAL, total);
        b.putBoolean(ARG_NEED_TABLE, needTable);
        b.putBoolean(ARG_NEED_DELIVERY, needDelivery);
        f.setArguments(b);

        Log.i(TAG, "newInstance(subtotal=" + total + ", needTable=" + needTable + ", needDelivery=" + needDelivery + ")");
        return f;
    }

    private Listener listener;

    public void setListener(@Nullable Listener l) {
        this.listener = l;
        Log.i(TAG, "setListener(): " + (l != null ? "OK" : "NULL"));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_native_checkout, null, false);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        final double baseSubtotal = args.getDouble(ARG_TOTAL, 0.0);
        final boolean needTable = args.getBoolean(ARG_NEED_TABLE, false);
        final boolean needDelivery = args.getBoolean(ARG_NEED_DELIVERY, false);

        final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

        TextView tvTotal = view.findViewById(R.id.tvTotalAmount);

        RadioGroup radioPayment = view.findViewById(R.id.radioPayment);
        RadioButton radioCash = view.findViewById(R.id.radioCash);
        RadioButton radioQris = view.findViewById(R.id.radioQris);
        RadioButton radioTransfer = view.findViewById(R.id.radioTransfer);

        EditText etCash = view.findViewById(R.id.etCashReceived);
        TextView tvChange = view.findViewById(R.id.tvChange);

        EditText etTable = view.findViewById(R.id.etTable);
        EditText etAddr = view.findViewById(R.id.etDeliveryAddress);
        EditText etFee = view.findViewById(R.id.etDeliveryFee);

        // Show/hide fields immediately based on flags
        if (etTable != null) etTable.setVisibility(needTable ? View.VISIBLE : View.GONE);
        if (etAddr != null) etAddr.setVisibility(needDelivery ? View.VISIBLE : View.GONE);
        if (etFee != null) etFee.setVisibility(needDelivery ? View.VISIBLE : View.GONE);

        // Helper: compute total with delivery fee
        final Runnable updateTotals = () -> {
            double fee = (needDelivery && etFee != null) ? parseMoney(safe(etFee.getText())) : 0.0;
            fee = Math.max(0.0, fee);

            double totalNow = baseSubtotal + fee;

            if (tvTotal != null) tvTotal.setText(usd.format(totalNow));

            boolean isCash = (radioCash != null && radioCash.isChecked());
            if (isCash && tvChange != null && etCash != null) {
                double cash = parseMoney(safe(etCash.getText()));
                double change = Math.max(0.0, cash - totalNow);
                tvChange.setText("Change: " + usd.format(change));
            }
        };

        // Helper: apply payment UI immediately
        final java.util.function.IntConsumer applyPaymentUi = checkedId -> {
            boolean isCash = (checkedId == R.id.radioCash);

            if (etCash != null) etCash.setVisibility(isCash ? View.VISIBLE : View.GONE);
            if (tvChange != null) tvChange.setVisibility(isCash ? View.VISIBLE : View.GONE);

            updateTotals.run();
        };

        // Default selection
        if (radioCash != null) radioCash.setChecked(true);

        // IMPORTANT: apply initial UI state (fixes your bug #1)
        applyPaymentUi.accept(R.id.radioCash);

        if (radioPayment != null) {
            radioPayment.setOnCheckedChangeListener((group, checkedId) -> {
                Log.i(TAG, "Payment method changed -> " + checkedId);
                applyPaymentUi.accept(checkedId);
            });
        }

        // Watch cash input to update change
        if (etCash != null) {
            etCash.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateTotals.run();
                }
            });
        }

        // Watch delivery fee to update total + change
        if (needDelivery && etFee != null) {
            etFee.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateTotals.run();
                }
            });
        }

        // Initial total display (subtotal; fee may update later)
        if (tvTotal != null) tvTotal.setText(usd.format(baseSubtotal));
        updateTotals.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Checkouts")
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Confirm", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

                String paymentMethod = "CASH";
                if (radioQris != null && radioQris.isChecked()) paymentMethod = "QRIS";
                if (radioTransfer != null && radioTransfer.isChecked()) paymentMethod = "TRANSFER";

                double deliveryFee = (needDelivery && etFee != null) ? parseMoney(safe(etFee.getText())) : 0.0;
                deliveryFee = Math.max(0.0, deliveryFee);

                double totalNow = baseSubtotal + deliveryFee;

                double cashReceived = parseMoney(etCash != null ? safe(etCash.getText()) : "");
                double changeAmount = "CASH".equals(paymentMethod)
                        ? Math.max(0.0, cashReceived - totalNow)
                        : 0.0;

                String table = (needTable && etTable != null) ? safe(etTable.getText()) : "";
                String addr = (needDelivery && etAddr != null) ? safe(etAddr.getText()) : "";

                // Validation
                if (needTable && table.isEmpty()) {
                    if (etTable != null) {
                        etTable.setError("Table number is required.");
                        etTable.requestFocus();
                    }
                    return;
                }

                if (needDelivery) {
                    if (addr.isEmpty()) {
                        if (etAddr != null) {
                            etAddr.setError("Delivery address is required.");
                            etAddr.requestFocus();
                        }
                        return;
                    }
                }

                // Optional cash validation (prevent confirm with empty cash)
                if ("CASH".equals(paymentMethod)) {
                    if (cashReceived <= 0) {
                        if (etCash != null) {
                            etCash.setError("Cash received is required.");
                            etCash.requestFocus();
                        }
                        return;
                    }
                }

                if (listener != null) {
                    listener.onConfirm(paymentMethod, cashReceived, changeAmount, table, addr, deliveryFee);
                } else {
                    Log.w(TAG, "Listener is NULL -> no action after confirm.");
                }

                dialog.dismiss();
            });
        });

        return dialog;
    }

    @NonNull
    private String safe(@Nullable CharSequence cs) {
        if (cs == null) return "";
        return cs.toString().trim();
    }

    private double parseMoney(@Nullable String s) {
        try {
            if (s == null) return 0.0;
            String t = s.trim();
            if (t.isEmpty()) return 0.0;
            // Accept inputs like "$1.00" just in case
            t = t.replace("$", "").replace(",", "");
            return Double.parseDouble(t);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}