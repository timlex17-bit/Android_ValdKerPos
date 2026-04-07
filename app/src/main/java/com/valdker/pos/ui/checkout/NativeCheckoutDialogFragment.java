package com.valdker.pos.ui.checkout;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NativeCheckoutDialogFragment extends DialogFragment {

    private static final String TAG = "NATIVE_CHECKOUT";

    public interface Listener {
        void onConfirm(@NonNull String paymentMethod,
                       double cashReceived,
                       double changeAmount,
                       @NonNull String tableNumber,
                       @NonNull String deliveryAddress,
                       double deliveryFee,
                       @Nullable Integer customerId,
                       @NonNull String customerName,
                       long customerPoints,
                       @NonNull List<CheckoutItem> items);
    }

    public interface BankListener {
        void onConfirmBank(@NonNull BankCheckoutResult result);
    }

    public static class CustomerOption {
        public final int id;
        @NonNull public final String name;
        public final long points;

        public CustomerOption(int id, @NonNull String name, long points) {
            this.id = id;
            this.name = name;
            this.points = points;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    public static class CheckoutItem {
        public final long productId;
        @NonNull public final String productName;
        public final int quantity;
        public final double unitPrice;
        public final double lineTotal;

        public CheckoutItem(long productId,
                            @NonNull String productName,
                            int quantity,
                            double unitPrice,
                            double lineTotal) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.lineTotal = lineTotal;
        }
    }

    public static class BankCheckoutResult {
        @NonNull public String paymentMethodCode;
        @Nullable public Integer paymentMethodId;
        @Nullable public Integer bankAccountId;
        @Nullable public Integer customerId;
        @NonNull public String customerName;
        public long customerPoints;
        @NonNull public String bankAccountLabel;
        @NonNull public String referenceNumber;
        @NonNull public String paymentNote;

        public double subtotal;
        public double deliveryFee;
        public double totalAmount;
        public double cashReceived;
        public double changeAmount;

        @NonNull public String tableNumber;
        @NonNull public String deliveryAddress;
        @NonNull public List<CheckoutItem> items;

        public BankCheckoutResult(@NonNull String paymentMethodCode,
                                  @Nullable Integer paymentMethodId,
                                  @Nullable Integer bankAccountId,
                                  @Nullable Integer customerId,
                                  @NonNull String customerName,
                                  long customerPoints,
                                  @NonNull String bankAccountLabel,
                                  @NonNull String referenceNumber,
                                  @NonNull String paymentNote,
                                  double subtotal,
                                  double deliveryFee,
                                  double totalAmount,
                                  double cashReceived,
                                  double changeAmount,
                                  @NonNull String tableNumber,
                                  @NonNull String deliveryAddress,
                                  @NonNull List<CheckoutItem> items) {
            this.paymentMethodCode = paymentMethodCode;
            this.paymentMethodId = paymentMethodId;
            this.bankAccountId = bankAccountId;
            this.customerId = customerId;
            this.customerName = customerName;
            this.customerPoints = customerPoints;
            this.bankAccountLabel = bankAccountLabel;
            this.referenceNumber = referenceNumber;
            this.paymentNote = paymentNote;
            this.subtotal = subtotal;
            this.deliveryFee = deliveryFee;
            this.totalAmount = totalAmount;
            this.cashReceived = cashReceived;
            this.changeAmount = changeAmount;
            this.tableNumber = tableNumber;
            this.deliveryAddress = deliveryAddress;
            this.items = items;
        }
    }

    public static class PaymentMethodOption {
        public final int id;
        @NonNull public final String code;
        @NonNull public final String label;
        public final boolean requiresBankAccount;

        public PaymentMethodOption(int id,
                                   @NonNull String code,
                                   @NonNull String label,
                                   boolean requiresBankAccount) {
            this.id = id;
            this.code = code;
            this.label = label;
            this.requiresBankAccount = requiresBankAccount;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    public static class BankAccountOption {
        public final int id;
        @NonNull public final String label;

        public BankAccountOption(int id, @NonNull String label) {
            this.id = id;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
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
    private BankListener bankListener;

    private final List<CustomerOption> customerOptions = new ArrayList<>();
    private final List<PaymentMethodOption> paymentOptions = new ArrayList<>();
    private final List<BankAccountOption> bankOptions = new ArrayList<>();
    private final List<CheckoutItem> checkoutItems = new ArrayList<>();

    public void setListener(@Nullable Listener l) {
        this.listener = l;
    }

    public void setBankListener(@Nullable BankListener l) {
        this.bankListener = l;
    }

    public void setCustomerOptions(@Nullable List<CustomerOption> items) {
        customerOptions.clear();
        if (items != null) customerOptions.addAll(items);
    }

    public void setPaymentOptions(@Nullable List<PaymentMethodOption> items) {
        paymentOptions.clear();
        if (items != null) paymentOptions.addAll(items);
    }

    public void setBankOptions(@Nullable List<BankAccountOption> items) {
        bankOptions.clear();
        if (items != null) bankOptions.addAll(items);
    }

    public void setCheckoutItems(@Nullable List<CheckoutItem> items) {
        checkoutItems.clear();
        if (items != null) checkoutItems.addAll(items);
    }

    private void ensureDefaultOptions() {
        if (customerOptions.isEmpty()) {
            customerOptions.add(new CustomerOption(0, "Walk-in Customer", 0));
        }

        if (paymentOptions.isEmpty()) {
            paymentOptions.add(new PaymentMethodOption(-1, "", "Select payment method", false));
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        ensureDefaultOptions();

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_native_checkout, null, false);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        final double baseSubtotal = args.getDouble(ARG_TOTAL, 0.0);
        final boolean needTable = args.getBoolean(ARG_NEED_TABLE, false);
        final boolean needDelivery = args.getBoolean(ARG_NEED_DELIVERY, false);

        final NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

        TextView tvTotal = view.findViewById(R.id.tvTotalAmount);

        Spinner spCustomer = view.findViewById(R.id.spCustomer);
        TextView tvCustomerPointsInfo = view.findViewById(R.id.tvCustomerPointsInfo);

        Spinner spPaymentMethod = view.findViewById(R.id.spPaymentMethod);
        TextView tvBankLabel = view.findViewById(R.id.tvBankLabel);
        Spinner spBankAccount = view.findViewById(R.id.spBankAccount);

        EditText etCash = view.findViewById(R.id.etCashReceived);
        TextView tvChange = view.findViewById(R.id.tvChange);

        EditText etReferenceNumber = view.findViewById(R.id.etReferenceNumber);
        EditText etPaymentNote = view.findViewById(R.id.etPaymentNote);

        EditText etTable = view.findViewById(R.id.etTable);
        EditText etAddr = view.findViewById(R.id.etDeliveryAddress);
        EditText etFee = view.findViewById(R.id.etDeliveryFee);

        if (etTable != null) etTable.setVisibility(needTable ? View.VISIBLE : View.GONE);
        if (etAddr != null) etAddr.setVisibility(needDelivery ? View.VISIBLE : View.GONE);
        if (etFee != null) etFee.setVisibility(needDelivery ? View.VISIBLE : View.GONE);

        ArrayAdapter<CustomerOption> customerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                customerOptions
        );
        customerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCustomer.setAdapter(customerAdapter);

        ArrayAdapter<PaymentMethodOption> paymentAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                paymentOptions
        );
        paymentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPaymentMethod.setAdapter(paymentAdapter);

        int defaultIndex = 0;
        for (int i = 0; i < paymentOptions.size(); i++) {
            if ("CASH".equalsIgnoreCase(paymentOptions.get(i).code)) {
                defaultIndex = i;
                break;
            }
        }
        spPaymentMethod.setSelection(defaultIndex);

        ArrayAdapter<BankAccountOption> bankAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                bankOptions
        );
        bankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBankAccount.setAdapter(bankAdapter);

        final Runnable updateCustomerInfo = () -> {
            CustomerOption customer = getSelectedCustomer(spCustomer);
            if (tvCustomerPointsInfo != null) {
                if (customer != null) {
                    tvCustomerPointsInfo.setText("Points: " + customer.points);
                } else {
                    tvCustomerPointsInfo.setText("Points: 0");
                }
            }
        };

        final Runnable updateTotals = () -> {
            double fee = (needDelivery && etFee != null) ? parseMoney(safe(etFee.getText())) : 0.0;
            fee = Math.max(0.0, fee);

            double totalNow = baseSubtotal + fee;

            if (tvTotal != null) {
                tvTotal.setText(usd.format(totalNow));
            }

            PaymentMethodOption selectedMethod = getSelectedPaymentMethod(spPaymentMethod);
            boolean isCash = selectedMethod != null && "CASH".equalsIgnoreCase(selectedMethod.code);

            if (isCash && tvChange != null && etCash != null) {
                double cash = parseMoney(safe(etCash.getText()));

                if (cash <= 0) {
                    tvChange.setText("Change: " + usd.format(0.0));
                } else if (cash < totalNow) {
                    double shortage = totalNow - cash;
                    tvChange.setText("Shortage: " + usd.format(shortage));
                } else {
                    double change = cash - totalNow;
                    tvChange.setText("Change: " + usd.format(change));
                }
            }
        };

        final Runnable applyPaymentUi = () -> {
            PaymentMethodOption selectedMethod = getSelectedPaymentMethod(spPaymentMethod);
            boolean isCash = selectedMethod != null && "CASH".equalsIgnoreCase(selectedMethod.code);
            boolean requiresBank = selectedMethod != null && selectedMethod.requiresBankAccount;

            if (etCash != null) etCash.setVisibility(isCash ? View.VISIBLE : View.GONE);
            if (tvChange != null) tvChange.setVisibility(isCash ? View.VISIBLE : View.GONE);

            if (tvBankLabel != null) tvBankLabel.setVisibility(requiresBank ? View.VISIBLE : View.GONE);
            if (spBankAccount != null) spBankAccount.setVisibility(requiresBank ? View.VISIBLE : View.GONE);
            if (etReferenceNumber != null) etReferenceNumber.setVisibility(requiresBank ? View.VISIBLE : View.GONE);
            if (etPaymentNote != null) etPaymentNote.setVisibility(requiresBank ? View.VISIBLE : View.GONE);

            updateTotals.run();
        };

        spCustomer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                updateCustomerInfo.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateCustomerInfo.run();
            }
        });

        spPaymentMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                applyPaymentUi.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                applyPaymentUi.run();
            }
        });

        if (etCash != null) {
            etCash.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateTotals.run();
                }
            });
        }

        if (needDelivery && etFee != null) {
            etFee.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateTotals.run();
                }
            });
        }

        if (tvTotal != null) tvTotal.setText(usd.format(baseSubtotal));
        updateCustomerInfo.run();
        applyPaymentUi.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Checkout")
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Confirm", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

                CustomerOption selectedCustomer = getSelectedCustomer(spCustomer);
                PaymentMethodOption selectedMethod = getSelectedPaymentMethod(spPaymentMethod);
                BankAccountOption selectedBank = getSelectedBankAccount(spBankAccount);

                if (selectedMethod.id <= 0) {
                    Log.w(TAG, "Payment method ID is invalid/not loaded yet.");
                    return;
                }

                double deliveryFee = (needDelivery && etFee != null) ? parseMoney(safe(etFee.getText())) : 0.0;
                deliveryFee = Math.max(0.0, deliveryFee);

                double totalNow = baseSubtotal + deliveryFee;

                double cashReceived = parseMoney(etCash != null ? safe(etCash.getText()) : "");
                double changeAmount = "CASH".equalsIgnoreCase(selectedMethod.code)
                        ? Math.max(0.0, cashReceived - totalNow)
                        : 0.0;

                String table = (needTable && etTable != null) ? safe(etTable.getText()) : "";
                String addr = (needDelivery && etAddr != null) ? safe(etAddr.getText()) : "";
                String referenceNumber = etReferenceNumber != null ? safe(etReferenceNumber.getText()) : "";
                String paymentNote = etPaymentNote != null ? safe(etPaymentNote.getText()) : "";

                if (needTable && table.isEmpty()) {
                    if (etTable != null) {
                        etTable.setError("Table number is required.");
                        etTable.requestFocus();
                    }
                    return;
                }

                if (needDelivery && addr.isEmpty()) {
                    if (etAddr != null) {
                        etAddr.setError("Delivery address is required.");
                        etAddr.requestFocus();
                    }
                    return;
                }

                if ("CASH".equalsIgnoreCase(selectedMethod.code)) {
                    if (cashReceived <= 0) {
                        if (etCash != null) {
                            etCash.setError("Cash received is required.");
                            etCash.requestFocus();
                        }
                        return;
                    }

                    if (cashReceived < totalNow) {
                        if (etCash != null) {
                            etCash.setError("Cash received cannot be less than total.");
                            etCash.requestFocus();
                            etCash.setSelection(etCash.getText().length());
                        }
                        return;
                    }
                }

                if (selectedMethod.requiresBankAccount) {
                    if (selectedBank == null) {
                        Log.w(TAG, "Bank account is required.");
                        return;
                    }
                    if (referenceNumber.isEmpty()) {
                        if (etReferenceNumber != null) {
                            etReferenceNumber.setError("Reference number is required.");
                            etReferenceNumber.requestFocus();
                        }
                        return;
                    }
                }

                Integer customerId = (selectedCustomer != null && selectedCustomer.id > 0)
                        ? selectedCustomer.id
                        : null;
                String customerName = selectedCustomer != null ? selectedCustomer.name : "";
                long customerPoints = selectedCustomer != null ? selectedCustomer.points : 0L;

                List<CheckoutItem> safeItems = new ArrayList<>(checkoutItems);

                if (listener != null) {
                    listener.onConfirm(
                            selectedMethod.code,
                            cashReceived,
                            changeAmount,
                            table,
                            addr,
                            deliveryFee,
                            customerId,
                            customerName,
                            customerPoints,
                            safeItems
                    );
                }

                if (bankListener != null) {
                    BankCheckoutResult result = new BankCheckoutResult(
                            selectedMethod.code,
                            selectedMethod.id > 0 ? selectedMethod.id : null,
                            selectedMethod.requiresBankAccount && selectedBank != null ? selectedBank.id : null,
                            customerId,
                            customerName,
                            customerPoints,
                            selectedMethod.requiresBankAccount && selectedBank != null ? selectedBank.label : "",
                            referenceNumber,
                            paymentNote,
                            baseSubtotal,
                            deliveryFee,
                            totalNow,
                            cashReceived,
                            changeAmount,
                            table,
                            addr,
                            safeItems
                    );
                    bankListener.onConfirmBank(result);
                }

                dialog.dismiss();
            });
        });

        return dialog;
    }

    @Nullable
    private CustomerOption getSelectedCustomer(@Nullable Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) return null;
        Object obj = spinner.getSelectedItem();
        if (obj instanceof CustomerOption) return (CustomerOption) obj;
        return null;
    }

    @Nullable
    private PaymentMethodOption getSelectedPaymentMethod(@Nullable Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) return null;
        Object obj = spinner.getSelectedItem();
        if (obj instanceof PaymentMethodOption) return (PaymentMethodOption) obj;
        return null;
    }

    @Nullable
    private BankAccountOption getSelectedBankAccount(@Nullable Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) return null;
        Object obj = spinner.getSelectedItem();
        if (obj instanceof BankAccountOption) return (BankAccountOption) obj;
        return null;
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