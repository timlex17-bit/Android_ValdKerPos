package com.example.valdker.ui.checkout;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.Locale;

public class NativeCheckoutDialogFragment extends DialogFragment {

    public interface Listener {
        void onConfirm(String paymentMethod,
                       double cashReceived,
                       double changeAmount,
                       String tableNumber,
                       String deliveryAddress,
                       double deliveryFee);
    }

    private static final String ARG_TOTAL = "arg_total";
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
        return f;
    }

    private Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_native_checkout, null, false);

        double total = getArguments().getDouble(ARG_TOTAL);
        boolean needTable = getArguments().getBoolean(ARG_NEED_TABLE);
        boolean needDelivery = getArguments().getBoolean(ARG_NEED_DELIVERY);

        NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

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

        tvTotal.setText(usd.format(total));
        radioCash.setChecked(true);

        if (needTable) etTable.setVisibility(View.VISIBLE);
        if (needDelivery) {
            etAddr.setVisibility(View.VISIBLE);
            etFee.setVisibility(View.VISIBLE);
        }

        radioPayment.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioCash) {
                etCash.setVisibility(View.VISIBLE);
                tvChange.setVisibility(View.VISIBLE);
            } else {
                etCash.setVisibility(View.GONE);
                tvChange.setVisibility(View.GONE);
            }
        });

        etCash.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                double cash = parseMoney(s.toString());
                double change = cash - total;
                tvChange.setText("Change: " + usd.format(change < 0 ? 0 : change));
            }
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Checkout")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (d, w) -> {

                    String paymentMethod = "CASH";
                    if (radioQris.isChecked()) paymentMethod = "QRIS";
                    if (radioTransfer.isChecked()) paymentMethod = "TRANSFER";

                    double cashReceived = parseMoney(etCash.getText().toString());
                    double changeAmount = paymentMethod.equals("CASH")
                            ? Math.max(0, cashReceived - total)
                            : 0;

                    String table = needTable ? etTable.getText().toString().trim() : "";
                    String addr = needDelivery ? etAddr.getText().toString().trim() : "";
                    double fee = needDelivery ? parseMoney(etFee.getText().toString()) : 0;

                    if (listener != null) {
                        listener.onConfirm(paymentMethod,
                                cashReceived,
                                changeAmount,
                                table,
                                addr,
                                fee);
                    }
                })
                .create();
    }

    private double parseMoney(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return 0;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}