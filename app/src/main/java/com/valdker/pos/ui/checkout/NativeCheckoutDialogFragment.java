package com.valdker.pos.ui.checkout;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.money.OrderTotals;
import com.valdker.pos.money.Money;
import com.valdker.pos.utils.Toast;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NativeCheckoutDialogFragment extends DialogFragment {

    private static final String TAG = "NATIVE_CHECKOUT";

    @Nullable
    private TextView tvBreakdown;
    @Nullable
    private EditText etDiscountValue;
    /**
     * Wadah kolom diskon. Pesan kesalahan dipasang di sini, bukan di
     * EditText-nya: pada TextInputLayout, setError() milik EditText
     * memunculkan gelembung merah lama yang menutupi kolom, sedangkan
     * setError() milik wadahnya menulis pesan di bawah kolom seperti dialog
     * formulir lain di aplikasi ini.
     */
    @Nullable
    private TextInputLayout tilDiscountValue;
    @Nullable
    private Spinner spDiscountMode;
    @Nullable
    private LinearLayout containerSplit;
    @Nullable
    private TextView tvSplitRemaining;
    @Nullable
    private Button btnAddSplit;

    /** Baris pembayaran tambahan yang sedang ditampilkan. */
    private final List<View> splitRows = new ArrayList<>();

    private static final int DISCOUNT_MODE_AMOUNT = 0;
    private static final int DISCOUNT_MODE_PERCENT = 1;

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

    /** Satu baris pembayaran tambahan pada order yang dibayar terbagi. */
    public static class SplitPayment {
        @Nullable public final Integer paymentMethodId;
        @NonNull public final String methodCode;
        @NonNull public final Money amount;

        public SplitPayment(@Nullable Integer paymentMethodId,
                            @NonNull String methodCode,
                            @NonNull Money amount) {
            this.paymentMethodId = paymentMethodId;
            this.methodCode = methodCode;
            this.amount = amount;
        }
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

        /** Nilai otoritatif; field double di atas hanya turunannya. */
        @NonNull public final Money unitPriceMoney;
        @NonNull public final Money lineTotalMoney;

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
            this.unitPriceMoney = Money.ofDouble(unitPrice);
            this.lineTotalMoney = Money.ofDouble(lineTotal);
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

        /** Nilai otoritatif; field double di atas hanya turunannya. */
        @NonNull public Money subtotalMoney = Money.zero();
        @NonNull public Money discountMoney = Money.zero();
        @NonNull public Money taxMoney = Money.zero();
        @NonNull public Money deliveryFeeMoney = Money.zero();
        @NonNull public Money totalAmountMoney = Money.zero();
        @NonNull public Money cashReceivedMoney = Money.zero();
        @NonNull public Money changeAmountMoney = Money.zero();

        /**
         * Pembayaran tambahan di luar metode utama. Kosong berarti order ini
         * dibayar dengan satu metode saja - perilaku sebelum split payment ada.
         * Metode utama menanggung sisanya: total - jumlah baris di sini.
         */
        @NonNull public List<SplitPayment> splitPayments = new ArrayList<>();

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
            this.subtotalMoney = Money.ofDouble(subtotal);
            this.deliveryFeeMoney = Money.ofDouble(deliveryFee);
            this.totalAmountMoney = Money.ofDouble(totalAmount);
            this.cashReceivedMoney = Money.ofDouble(cashReceived);
            this.changeAmountMoney = Money.ofDouble(changeAmount);
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

    /**
     * @param needTable tampilkan kolom "Nomor meja" dan wajibkan isinya.
     *
     *                  <p>Pemanggil yang memutuskan, bukan dialog ini. Untuk
     *                  keranjang restoran, kolom ini hanya diminta ketika meja
     *                  BELUM dipilih dari grid: kalau sudah dipilih, server
     *                  mengisi ulang {@code table_number} dari {@code Table.name}
     *                  begitu {@code table_id} terkirim, sehingga apa pun yang
     *                  diketik di sini dijamin dibuang. Menanyakannya dua kali
     *                  hanya membuat kasir mengisi kolom yang hasilnya hilang.
     */
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
    @Nullable
    private Runnable onDismissCallback;

    private final List<CustomerOption> customerOptions = new ArrayList<>();
    private final List<PaymentMethodOption> paymentOptions = new ArrayList<>();
    private final List<BankAccountOption> bankOptions = new ArrayList<>();
    private final List<CheckoutItem> checkoutItems = new ArrayList<>();

    @Nullable
    private ArrayAdapter<CustomerOption> customerAdapter;
    @Nullable
    private ArrayAdapter<PaymentMethodOption> paymentAdapter;
    @Nullable
    private ArrayAdapter<BankAccountOption> bankAdapter;
    @Nullable
    private Integer preselectedCustomerId;
    @NonNull
    private String preselectedCustomerName = "";
    private long preselectedCustomerPoints = 0L;

    public void setListener(@Nullable Listener l) {
        this.listener = l;
    }

    public void setBankListener(@Nullable BankListener l) {
        this.bankListener = l;
    }

    public void setOnDismissCallback(@Nullable Runnable callback) {
        this.onDismissCallback = callback;
    }

    public void setCustomerOptions(@Nullable List<CustomerOption> items) {
        customerOptions.clear();
        if (items != null) customerOptions.addAll(items);
        ensurePreselectedCustomerOption();
        if (customerAdapter != null) customerAdapter.notifyDataSetChanged();
    }

    public void setPreselectedCustomer(@Nullable Integer customerId,
                                       @Nullable String customerName,
                                       long customerPoints) {
        preselectedCustomerId = customerId != null && customerId > 0 ? customerId : null;
        preselectedCustomerName = customerName != null ? customerName.trim() : "";
        preselectedCustomerPoints = Math.max(0L, customerPoints);
        ensurePreselectedCustomerOption();
        if (customerAdapter != null) customerAdapter.notifyDataSetChanged();
    }

    public void setPaymentOptions(@Nullable List<PaymentMethodOption> items) {
        paymentOptions.clear();
        if (items != null) paymentOptions.addAll(items);
        if (paymentAdapter != null) paymentAdapter.notifyDataSetChanged();
    }

    public void setBankOptions(@Nullable List<BankAccountOption> items) {
        bankOptions.clear();
        if (items != null) bankOptions.addAll(items);
        if (bankAdapter != null) bankAdapter.notifyDataSetChanged();
    }

    public void setCheckoutItems(@Nullable List<CheckoutItem> items) {
        checkoutItems.clear();
        if (items != null) checkoutItems.addAll(items);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }

    private void ensureDefaultOptions() {
        if (customerOptions.isEmpty()) {
            customerOptions.add(new CustomerOption(0, getString(R.string.workshop_walk_in_customer), 0));
        }
        ensurePreselectedCustomerOption();

        if (paymentOptions.isEmpty()) {
            paymentOptions.add(new PaymentMethodOption(-1, "", getString(R.string.payment_select_method), false));
        }
    }

    private void ensurePreselectedCustomerOption() {
        if (preselectedCustomerId == null || preselectedCustomerId <= 0) return;

        for (CustomerOption option : customerOptions) {
            if (option != null && option.id == preselectedCustomerId) {
                return;
            }
        }

        String name = preselectedCustomerName.isEmpty()
                ? "Customer #" + preselectedCustomerId
                : preselectedCustomerName;
        int insertIndex = customerOptions.isEmpty() ? 0 : Math.min(1, customerOptions.size());
        customerOptions.add(insertIndex, new CustomerOption(
                preselectedCustomerId,
                name,
                preselectedCustomerPoints
        ));
    }

    private int findPreselectedCustomerIndex() {
        if (preselectedCustomerId == null || preselectedCustomerId <= 0) return -1;
        for (int i = 0; i < customerOptions.size(); i++) {
            CustomerOption option = customerOptions.get(i);
            if (option != null && option.id == preselectedCustomerId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Menampilkan pesan kesalahan di bawah kolom dan memindahkan fokus ke
     * sana. Kalau wadahnya tidak ada, pesan tetap dipasang pada EditText
     * supaya tidak pernah ada penolakan yang diam-diam.
     */
    private void showFieldError(@Nullable TextInputLayout container,
                                @Nullable EditText field,
                                @NonNull String message) {
        if (container != null) {
            container.setError(message);
        } else if (field != null) {
            field.setError(message);
        }
        if (field != null) field.requestFocus();
    }

    private void clearFieldError(@Nullable TextInputLayout container) {
        if (container == null) return;
        container.setError(null);
        container.setErrorEnabled(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        ensureDefaultOptions();

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_native_checkout, null, false);

        Bundle args = getArguments() != null ? getArguments() : new Bundle();

        final double baseSubtotal = args.getDouble(ARG_TOTAL, 0.0);
        final Money baseSubtotalMoney = Money.ofDouble(baseSubtotal);
        // POSSettings.tax_percent milik toko; nol berarti tidak ada baris pajak.
        final BigDecimal taxPercent = new SessionManager(requireContext()).getTaxPercent();
        final boolean needTable = args.getBoolean(ARG_NEED_TABLE, false);
        final boolean needDelivery = args.getBoolean(ARG_NEED_DELIVERY, false);


        TextView tvTotal = view.findViewById(R.id.tvTotalAmount);
        tvBreakdown = view.findViewById(R.id.tvTotalBreakdown);
        etDiscountValue = view.findViewById(R.id.etDiscountValue);
        tilDiscountValue = view.findViewById(R.id.tilDiscountValue);
        spDiscountMode = view.findViewById(R.id.spDiscountMode);
        containerSplit = view.findViewById(R.id.containerSplitPayments);
        tvSplitRemaining = view.findViewById(R.id.tvSplitRemaining);
        btnAddSplit = view.findViewById(R.id.btnAddSplitPayment);

        Spinner spCustomer = view.findViewById(R.id.spCustomer);
        TextView tvCustomerPointsInfo = view.findViewById(R.id.tvCustomerPointsInfo);

        Spinner spPaymentMethod = view.findViewById(R.id.spPaymentMethod);
        Spinner spBankAccount = view.findViewById(R.id.spBankAccount);

        EditText etCash = view.findViewById(R.id.etCashReceived);
        TextView tvChange = view.findViewById(R.id.tvChange);

        EditText etReferenceNumber = view.findViewById(R.id.etReferenceNumber);
        EditText etPaymentNote = view.findViewById(R.id.etPaymentNote);

        EditText etTable = view.findViewById(R.id.etTable);
        EditText etAddr = view.findViewById(R.id.etDeliveryAddress);
        EditText etFee = view.findViewById(R.id.etDeliveryFee);

        // Yang disembunyikan adalah wadah TextInputLayout, bukan EditText di
        // dalamnya: menyembunyikan EditText saja menyisakan kotak bergaris
        // kosong yang tetap memakan satu baris penuh.
        TextInputLayout tilCash = view.findViewById(R.id.tilCashReceived);
        TextInputLayout tilTable = view.findViewById(R.id.tilTable);
        TextInputLayout tilAddr = view.findViewById(R.id.tilDeliveryAddress);
        TextInputLayout tilFee = view.findViewById(R.id.tilDeliveryFee);
        TextInputLayout tilReference = view.findViewById(R.id.tilReferenceNumber);

        View groupCash = view.findViewById(R.id.groupCash);
        View groupBank = view.findViewById(R.id.groupBank);
        View cardOrderDetails = view.findViewById(R.id.cardOrderDetails);

        if (tilTable != null) tilTable.setVisibility(needTable ? View.VISIBLE : View.GONE);
        if (tilAddr != null) tilAddr.setVisibility(needDelivery ? View.VISIBLE : View.GONE);
        if (tilFee != null) tilFee.setVisibility(needDelivery ? View.VISIBLE : View.GONE);

        // Kartu "Detail pesanan" hanya berisi meja dan pengantaran; tanpa
        // keduanya ia menyusut jadi kartu berisi judul saja.
        if (cardOrderDetails != null) {
            cardOrderDetails.setVisibility(needTable || needDelivery ? View.VISIBLE : View.GONE);
        }

        // Layout isi Spinner milik aplikasi, bukan android.R.layout.simple_*.
        // Layout sistem membawa ukuran huruf dan padding dari tema perangkat,
        // sehingga isi Spinner di popup ini tampil lebih besar dan tidak
        // sebaris dengan kolom Material tepat di sebelahnya. Lihat
        // res/layout/item_field_spinner.xml.
        customerAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_field_spinner,
                customerOptions
        );
        customerAdapter.setDropDownViewResource(R.layout.item_field_spinner_dropdown);
        spCustomer.setAdapter(customerAdapter);
        int preselectedIndex = findPreselectedCustomerIndex();
        if (preselectedIndex >= 0) {
            spCustomer.setSelection(preselectedIndex);
        }

        paymentAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_field_spinner,
                paymentOptions
        );
        paymentAdapter.setDropDownViewResource(R.layout.item_field_spinner_dropdown);
        spPaymentMethod.setAdapter(paymentAdapter);

        int defaultIndex = 0;
        for (int i = 0; i < paymentOptions.size(); i++) {
            if ("CASH".equalsIgnoreCase(paymentOptions.get(i).code)) {
                defaultIndex = i;
                break;
            }
        }
        spPaymentMethod.setSelection(defaultIndex);

        bankAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_field_spinner,
                bankOptions
        );
        bankAdapter.setDropDownViewResource(R.layout.item_field_spinner_dropdown);
        spBankAccount.setAdapter(bankAdapter);

        if (spDiscountMode != null) {
            ArrayAdapter<String> discountModeAdapter = new ArrayAdapter<>(
                    requireContext(),
                    R.layout.item_field_spinner,
                    new String[]{
                            getString(R.string.checkout_discount_mode_amount),
                            getString(R.string.checkout_discount_mode_percent)
                    }
            );
            discountModeAdapter.setDropDownViewResource(R.layout.item_field_spinner_dropdown);
            spDiscountMode.setAdapter(discountModeAdapter);
        }

        final Runnable updateCustomerInfo = () -> {
            CustomerOption customer = getSelectedCustomer(spCustomer);
            if (tvCustomerPointsInfo != null) {
                if (customer != null) {
                    tvCustomerPointsInfo.setText(getString(R.string.checkout_points_format, customer.points));
                } else {
                    tvCustomerPointsInfo.setText(getString(R.string.checkout_points_zero));
                }
            }
        };

        final Runnable updateTotals = () -> {
            Money fee = (needDelivery && etFee != null)
                    ? Money.of(safe(etFee.getText())).orZeroIfNegative()
                    : Money.zero();

            OrderTotals totals = OrderTotals.of(
                    baseSubtotalMoney, currentDiscount(baseSubtotalMoney), fee, taxPercent);
            Money totalNow = totals.total();

            if (tvTotal != null) {
                tvTotal.setText(totalNow.format());
            }
            renderBreakdown(totals, taxPercent);
            renderSplitRemaining(totals.total(), getSelectedPaymentMethod(spPaymentMethod));

            PaymentMethodOption selectedMethod = getSelectedPaymentMethod(spPaymentMethod);
            boolean isCash = selectedMethod != null && "CASH".equalsIgnoreCase(selectedMethod.code);

            if (isCash && tvChange != null && etCash != null) {
                Money cash = Money.of(safe(etCash.getText()));
                Money due = totalNow.minus(splitPaymentsTotal());

                if (!cash.isPositive()) {
                    tvChange.setText(getString(R.string.checkout_change_format, Money.zero().format()));
                } else if (cash.isLessThan(due)) {
                    tvChange.setText(getString(R.string.checkout_shortage_format,
                            due.minus(cash).format()));
                } else {
                    tvChange.setText(getString(R.string.checkout_change_format,
                            cash.minus(due).format()));
                }
            }
        };

        final Runnable applyPaymentUi = () -> {
            PaymentMethodOption selectedMethod = getSelectedPaymentMethod(spPaymentMethod);
            boolean isCash = selectedMethod != null && "CASH".equalsIgnoreCase(selectedMethod.code);
            boolean requiresBank = selectedMethod != null && selectedMethod.requiresBankAccount;

            // Satu wadah per kondisi, bukan empat view yang disembunyikan
            // sendiri-sendiri: label rekening, pemilih rekening, nomor
            // referensi, dan catatan selalu muncul dan hilang bersamaan.
            if (groupCash != null) groupCash.setVisibility(isCash ? View.VISIBLE : View.GONE);
            if (groupBank != null) groupBank.setVisibility(requiresBank ? View.VISIBLE : View.GONE);

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

        if (etDiscountValue != null) {
            etDiscountValue.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateTotals.run();
                }
            });
        }
        if (spDiscountMode != null) {
            spDiscountMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    updateTotals.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        if (btnAddSplit != null && containerSplit != null) {
            btnAddSplit.setOnClickListener(v -> {
                View row = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_split_payment, containerSplit, false);

                Spinner sp = row.findViewById(R.id.spSplitMethod);
                ArrayAdapter<PaymentMethodOption> rowAdapter = new ArrayAdapter<>(
                        requireContext(), R.layout.item_field_spinner, paymentOptions);
                rowAdapter.setDropDownViewResource(R.layout.item_field_spinner_dropdown);
                sp.setAdapter(rowAdapter);

                EditText amount = row.findViewById(R.id.etSplitAmount);
                amount.addTextChangedListener(new SimpleTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable e) {
                        updateTotals.run();
                    }
                });

                row.findViewById(R.id.btnRemoveSplit).setOnClickListener(x -> {
                    containerSplit.removeView(row);
                    splitRows.remove(row);
                    updateTotals.run();
                });

                containerSplit.addView(row);
                splitRows.add(row);
                updateTotals.run();
            });
        }

        if (tvTotal != null) tvTotal.setText(baseSubtotalMoney.format());
        updateCustomerInfo.run();
        updateTotals.run();
        applyPaymentUi.run();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.action_checkout))
                .setView(view)
                .setNegativeButton(getString(R.string.action_cancel), (d, w) -> d.dismiss())
                .setPositiveButton(getString(R.string.action_confirm), null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

                // Pesan kesalahan bertahan sampai dihapus. Tanpa baris ini,
                // peringatan dari percobaan sebelumnya masih tertulis di bawah
                // kolom yang sudah dibetulkan kasir.
                clearFieldError(tilDiscountValue);
                clearFieldError(tilCash);
                clearFieldError(tilTable);
                clearFieldError(tilAddr);
                clearFieldError(tilReference);

                CustomerOption selectedCustomer = getSelectedCustomer(spCustomer);
                PaymentMethodOption selectedMethod = getSelectedPaymentMethod(spPaymentMethod);
                BankAccountOption selectedBank = getSelectedBankAccount(spBankAccount);

                if (selectedMethod == null || selectedMethod.id <= 0) {
                    Log.w(TAG, "Payment method ID is invalid/not loaded yet.");
                    Toast.makeText(requireContext(), getString(R.string.msg_payment_method_missing), Toast.LENGTH_LONG).show();
                    return;
                }

                Money deliveryFeeMoney = (needDelivery && etFee != null)
                        ? Money.of(safe(etFee.getText())).orZeroIfNegative()
                        : Money.zero();

                // Diskon yang diketik lebih besar dari subtotal ditolak, bukan
                // dijepit diam-diam: kasir harus melihat angkanya salah.
                Money typedDiscount = currentDiscount(baseSubtotalMoney);
                if (typedDiscount.isGreaterThanOrEqual(baseSubtotalMoney)
                        && !typedDiscount.equals(baseSubtotalMoney)) {
                    showFieldError(tilDiscountValue, etDiscountValue,
                            getString(R.string.msg_discount_exceeds_subtotal));
                    return;
                }

                OrderTotals confirmTotals = OrderTotals.of(
                        baseSubtotalMoney, typedDiscount, deliveryFeeMoney, taxPercent);
                Money totalNowMoney = confirmTotals.total();

                // Dengan pembayaran terbagi, tunai hanya menanggung porsi metode
                // utama - bukan seluruh total. Membandingkannya dengan total
                // penuh akan menolak pembayaran yang sebenarnya sudah lunas.
                Money primaryShare = totalNowMoney.minus(splitPaymentsTotal());

                Money cashReceivedMoney = Money.of(etCash != null ? safe(etCash.getText()) : "");
                Money changeAmountMoney = "CASH".equalsIgnoreCase(selectedMethod.code)
                        ? cashReceivedMoney.minus(primaryShare).orZeroIfNegative()
                        : Money.zero();

                double deliveryFee = deliveryFeeMoney.toDouble();
                double totalNow = totalNowMoney.toDouble();
                double cashReceived = cashReceivedMoney.toDouble();
                double changeAmount = changeAmountMoney.toDouble();

                String table = (needTable && etTable != null) ? safe(etTable.getText()) : "";
                String addr = (needDelivery && etAddr != null) ? safe(etAddr.getText()) : "";
                String referenceNumber = etReferenceNumber != null ? safe(etReferenceNumber.getText()) : "";
                String paymentNote = etPaymentNote != null ? safe(etPaymentNote.getText()) : "";

                if (needTable && table.isEmpty()) {
                    showFieldError(tilTable, etTable,
                            getString(R.string.msg_table_number_required));
                    return;
                }

                if (needDelivery && addr.isEmpty()) {
                    showFieldError(tilAddr, etAddr,
                            getString(R.string.msg_delivery_address_required));
                    return;
                }

                // Pembayaran terbagi: setiap baris wajib berisi nominal, dan
                // jumlahnya tidak boleh melewati total - metode utama yang
                // menanggung sisanya, jadi sisa negatif berarti salah input.
                if (!splitRows.isEmpty()) {
                    if (hasEmptySplitRow()) {
                        Toast.makeText(requireContext(),
                                getString(R.string.msg_split_amount_required),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    // Metode yang mewajibkan rekening bank belum didukung pada
                    // baris terbagi - barisnya tidak punya pemilih rekening.
                    // Diblokir di sini supaya kasir dapat pesan yang jelas
                    // alih-alih penolakan mentah dari server.
                    if (hasSplitRowRequiringBank()) {
                        Toast.makeText(requireContext(),
                                getString(R.string.msg_split_bank_unsupported),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (splitPaymentsTotal().isGreaterThanOrEqual(totalNowMoney)
                            && !splitPaymentsTotal().equals(totalNowMoney)) {
                        Toast.makeText(requireContext(),
                                getString(R.string.msg_split_exceeds_total),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                if ("CASH".equalsIgnoreCase(selectedMethod.code)) {
                    if (!cashReceivedMoney.isPositive()) {
                        showFieldError(tilCash, etCash,
                                getString(R.string.msg_cash_received_required));
                        return;
                    }

                    // Perbandingan eksak: dengan double, uang pas untuk total
                    // hasil 0.1+0.2 tampak kurang dan pembayaran ditolak.
                    if (cashReceivedMoney.isLessThan(primaryShare)) {
                        showFieldError(tilCash, etCash,
                                getString(R.string.msg_cash_received_less_total));
                        if (etCash != null && etCash.getText() != null) {
                            etCash.setSelection(etCash.getText().length());
                        }
                        return;
                    }
                }

                if (selectedMethod.requiresBankAccount) {
                    if (selectedBank == null) {
                        Log.w(TAG, "Bank account is required.");
                        Toast.makeText(requireContext(), getString(R.string.error_required), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (referenceNumber.isEmpty()) {
                        showFieldError(tilReference, etReferenceNumber,
                                getString(R.string.msg_reference_number_required));
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
                    // Nilai eksak, bukan hasil bolak-balik lewat double.
                    result.subtotalMoney = confirmTotals.subtotal();
                    result.discountMoney = confirmTotals.discount();
                    result.taxMoney = confirmTotals.tax();
                    result.deliveryFeeMoney = confirmTotals.deliveryFee();
                    result.totalAmountMoney = totalNowMoney;
                    result.cashReceivedMoney = cashReceivedMoney;
                    result.changeAmountMoney = changeAmountMoney;
                    result.splitPayments = collectSplitPayments();
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

    /**
     * Diskon yang sedang diketik kasir, dalam nominal maupun persen.
     * Nilainya tidak dijepit di sini - {@link OrderTotals} yang menjepit, dan
     * konfirmasi menolak kalau melebihi subtotal, supaya kasir melihat
     * kesalahannya alih-alih angkanya diam-diam diganti.
     */
    @NonNull
    private Money currentDiscount(@NonNull Money subtotal) {
        if (etDiscountValue == null) return Money.zero();
        String raw = safe(etDiscountValue.getText());
        if (raw.isEmpty()) return Money.zero();

        if (isPercentDiscountMode()) {
            try {
                return OrderTotals.discountFromPercent(subtotal, new BigDecimal(raw));
            } catch (NumberFormatException e) {
                return Money.zero();
            }
        }
        return Money.of(raw).orZeroIfNegative();
    }

    /**
     * Sisa yang ditanggung metode utama: total dikurangi seluruh baris
     * tambahan. Baris ini hanya muncul kalau kasir memang membagi pembayaran.
     */
    private void renderSplitRemaining(@NonNull Money total, @Nullable PaymentMethodOption primary) {
        if (tvSplitRemaining == null) return;
        if (splitRows.isEmpty()) {
            tvSplitRemaining.setVisibility(View.GONE);
            return;
        }
        Money remaining = total.minus(splitPaymentsTotal());
        String label = primary != null ? primary.label : "-";
        tvSplitRemaining.setText(getString(R.string.checkout_split_remaining, label, remaining.format()));
        tvSplitRemaining.setVisibility(View.VISIBLE);
    }

    /** Jumlah seluruh baris pembayaran tambahan yang terisi. */
    @NonNull
    private Money splitPaymentsTotal() {
        Money sum = Money.zero();
        for (View row : splitRows) {
            EditText amount = row.findViewById(R.id.etSplitAmount);
            sum = sum.plus(Money.of(safe(amount.getText())).orZeroIfNegative());
        }
        return sum;
    }

    /** Baris pembayaran tambahan yang terisi, siap dikirim ke payload. */
    @NonNull
    private List<SplitPayment> collectSplitPayments() {
        List<SplitPayment> out = new ArrayList<>();
        for (View row : splitRows) {
            Spinner sp = row.findViewById(R.id.spSplitMethod);
            EditText amount = row.findViewById(R.id.etSplitAmount);
            Money value = Money.of(safe(amount.getText()));
            if (!value.isPositive()) continue;
            Object sel = sp.getSelectedItem();
            if (sel instanceof PaymentMethodOption) {
                PaymentMethodOption m = (PaymentMethodOption) sel;
                out.add(new SplitPayment(m.id > 0 ? m.id : null, m.code, value));
            }
        }
        return out;
    }

    /** True kalau ada baris terbagi memakai metode yang butuh rekening bank. */
    private boolean hasSplitRowRequiringBank() {
        for (View row : splitRows) {
            Spinner sp = row.findViewById(R.id.spSplitMethod);
            Object sel = sp.getSelectedItem();
            if (sel instanceof PaymentMethodOption && ((PaymentMethodOption) sel).requiresBankAccount) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEmptySplitRow() {
        for (View row : splitRows) {
            EditText amount = row.findViewById(R.id.etSplitAmount);
            if (!Money.of(safe(amount.getText())).isPositive()) return true;
        }
        return false;
    }

    private boolean isPercentDiscountMode() {
        return spDiscountMode != null
                && spDiscountMode.getSelectedItemPosition() == DISCOUNT_MODE_PERCENT;
    }

    /** Rincian di bawah angka total: subtotal, diskon, dan pajak bila ada. */
    private void renderBreakdown(@NonNull OrderTotals totals, @NonNull BigDecimal taxPercent) {
        if (tvBreakdown == null) return;
        if (!totals.hasTax() && !totals.hasDiscount() && !totals.deliveryFee().isPositive()) {
            tvBreakdown.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.checkout_line_subtotal)).append(' ')
                .append(totals.subtotal().format());
        if (totals.hasDiscount()) {
            sb.append("  ·  ").append(getString(R.string.checkout_line_discount)).append(' ')
                    .append(totals.discount().format());
        }
        if (totals.deliveryFee().isPositive()) {
            sb.append("  ·  ").append(getString(R.string.checkout_line_delivery)).append(' ')
                    .append(totals.deliveryFee().format());
        }
        if (totals.hasTax()) {
            sb.append("  ·  ").append(getString(R.string.checkout_line_tax))
                    .append(' ').append(taxPercent.stripTrailingZeros().toPlainString()).append("%  ")
                    .append(totals.tax().format());
        }
        tvBreakdown.setText(sb.toString());
        tvBreakdown.setVisibility(View.VISIBLE);
    }

    @NonNull
    private String safe(@Nullable CharSequence cs) {
        if (cs == null) return "";
        return cs.toString().trim();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
