package com.example.valdker.ui.productreturns;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.example.valdker.models.OrderLite;
import com.example.valdker.models.OrderItemLite;
import com.example.valdker.models.ProductReturn;
import com.example.valdker.repositories.OrderDetailRepository;
import com.example.valdker.repositories.ProductDetailRepository;
import com.example.valdker.repositories.ProductReturnRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductReturnAddDialog extends DialogFragment {

    public interface DoneCallback {
        void onCreated();
    }

    private Spinner spOrder, spOrderItem;
    private TextView tvCustomerAuto, tvItemsPreview;
    private EditText etNote, etQty, etUnitPrice;
    private Button btnAddItem;

    private final List<OrderLite> orders;
    private final DoneCallback callback;

    // loaded from order detail
    private final List<OrderItemLite> invoiceItems = new ArrayList<>();
    private ArrayAdapter<OrderItemLite> invoiceItemsAdapter;

    // items that will be returned (POST)
    private final List<ProductReturnRepository.CreateItem> returnItems = new ArrayList<>();

    // current order info
    private Integer currentCustomerId = null; // may be null
    private String currentCustomerName = null;

    public ProductReturnAddDialog(
            @NonNull List<OrderLite> orders,
            @NonNull DoneCallback callback
    ) {
        this.orders = orders;
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_product_return_add, null);

        spOrder = v.findViewById(R.id.spOrder);
        spOrderItem = v.findViewById(R.id.spOrderItem);

        tvCustomerAuto = v.findViewById(R.id.tvCustomerAuto);
        tvItemsPreview = v.findViewById(R.id.tvItemsPreview);

        etNote = v.findViewById(R.id.etNote);
        etQty = v.findViewById(R.id.etQty);
        etUnitPrice = v.findViewById(R.id.etUnitPrice);

        btnAddItem = v.findViewById(R.id.btnAddItem);

        // Order spinner
        spOrder.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, orders));

        // Invoice items adapter (start empty)
        invoiceItemsAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, invoiceItems);
        spOrderItem.setAdapter(invoiceItemsAdapter);

        // when order selected -> fetch detail and populate customer+items
        spOrder.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= orders.size()) return;
                OrderLite o = orders.get(position);
                loadOrderDetail(o.id);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // default: if there is at least one order, load detail
        if (!orders.isEmpty()) {
            loadOrderDetail(orders.get(0).id);
        }

        btnAddItem.setOnClickListener(view -> addReturnItem());

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Product Return")
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        androidx.appcompat.app.AlertDialog dlg = (androidx.appcompat.app.AlertDialog) getDialog();
        if (dlg != null) {
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> submit());
        }
    }

    private void loadOrderDetail(int orderId) {
        tvCustomerAuto.setText("Customer: Loading...");
        invoiceItems.clear();
        if (invoiceItemsAdapter != null) invoiceItemsAdapter.notifyDataSetChanged();

        // reset return items when changing invoice (safer)
        returnItems.clear();
        renderPreview();

        OrderDetailRepository.fetch(requireContext(), orderId, new OrderDetailRepository.Callback() {
            @Override
            public void onSuccess(@NonNull OrderDetailRepository.OrderDetail detail) {
                if (!isAdded()) return;

                currentCustomerId = detail.customerId;
                currentCustomerName = detail.customerName;

                String custLabel = (currentCustomerName != null && !currentCustomerName.trim().isEmpty())
                        ? currentCustomerName
                        : "Guest";
                tvCustomerAuto.setText("Customer: " + custLabel);

                invoiceItems.clear();
                invoiceItems.addAll(detail.items);
                invoiceItemsAdapter.notifyDataSetChanged();

                // auto fill price from first item if exists
                if (!invoiceItems.isEmpty()) {
                    OrderItemLite it = invoiceItems.get(0);
                    etUnitPrice.setText(it.price != null ? it.price : "");
                }

                // Optional: enrich product names by fetching product detail for each item
                enrichItemNames();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                tvCustomerAuto.setText("Customer: -");
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Fetch product detail for each productId so spinner shows product name/code
    private void enrichItemNames() {
        for (int i = 0; i < invoiceItems.size(); i++) {
            OrderItemLite it = invoiceItems.get(i);
            final int idx = i;
            ProductDetailRepository.fetch(requireContext(), it.productId, new ProductDetailRepository.Callback() {
                @Override public void onSuccess(@NonNull ProductDetailRepository.ProductMini p) {
                    if (!isAdded()) return;
                    OrderItemLite target = invoiceItems.get(idx);
                    target.productName = p.name;
                    target.productCode = p.code;
                    target.productSku = p.sku;
                    invoiceItemsAdapter.notifyDataSetChanged();
                }
                @Override public void onError(@NonNull String message) {
                    // ignore per-item error, still usable
                }
            });
        }
    }

    private void addReturnItem() {
        if (spOrderItem.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "Invoice item kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyStr = etQty.getText().toString().trim();
        String priceStr = etUnitPrice.getText().toString().trim();

        if (TextUtils.isEmpty(qtyStr) || TextUtils.isEmpty(priceStr)) {
            Toast.makeText(requireContext(), "Qty & Unit Price wajib", Toast.LENGTH_SHORT).show();
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyStr);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Qty tidak valid", Toast.LENGTH_SHORT).show();
            return;
        }

        if (qty <= 0) {
            Toast.makeText(requireContext(), "Qty harus > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderItemLite invIt = (OrderItemLite) spOrderItem.getSelectedItem();

        // validate qty return <= qty purchased
        if (invIt.quantity > 0 && qty > invIt.quantity) {
            Toast.makeText(requireContext(),
                    "Qty return tidak boleh lebih dari qty invoice (" + invIt.quantity + ")",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // add create item for API
        returnItems.add(new ProductReturnRepository.CreateItem(invIt.productId, qty, priceStr));

        etQty.setText("");
        renderPreview();
    }

    private void renderPreview() {
        if (returnItems.isEmpty()) {
            tvItemsPreview.setText("No items yet");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < returnItems.size(); i++) {
            ProductReturnRepository.CreateItem it = returnItems.get(i);
            sb.append(String.format(Locale.US, "%d) product=%d, qty=%d, price=%s\n",
                    i + 1, it.productId, it.quantity, it.unitPrice));
        }
        tvItemsPreview.setText(sb.toString().trim());
    }

    private void submit() {
        if (spOrder.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "Invoice wajib", Toast.LENGTH_SHORT).show();
            return;
        }
        if (returnItems.isEmpty()) {
            Toast.makeText(requireContext(), "Minimal 1 item return", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderLite order = (OrderLite) spOrder.getSelectedItem();
        String note = etNote.getText().toString().trim();

        // customerId bisa null -> API kamu sebelumnya butuh customerId int.
        // Jadi: kalau customer null, kirim 0 / omit? (tergantung backend)
        // Untuk aman: kita kirim 0 jika null.
        int customerId = (currentCustomerId != null) ? currentCustomerId : 0;

        ProductReturnRepository.create(
                requireContext(),
                order.id,
                customerId,
                note,
                null,
                returnItems,
                new ProductReturnRepository.CreateCallback() {
                    @Override
                    public void onSuccess(@NonNull ProductReturn created) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Return created", Toast.LENGTH_SHORT).show();
                        dismiss();
                        if (callback != null) callback.onCreated();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }
}