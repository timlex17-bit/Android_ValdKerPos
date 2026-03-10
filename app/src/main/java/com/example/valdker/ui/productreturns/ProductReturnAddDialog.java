package com.example.valdker.ui.productreturns;

import android.app.Dialog;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.OrderItemLite;
import com.example.valdker.models.OrderLite;
import com.example.valdker.models.ProductReturn;
import com.example.valdker.repositories.OrderDetailRepository;
import com.example.valdker.repositories.ProductDetailRepository;
import com.example.valdker.repositories.ProductReturnRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;

public class ProductReturnAddDialog extends DialogFragment {

    public interface DoneCallback {
        void onCreated();
    }

    private Spinner spOrder, spOrderItem;
    private TextView tvCustomerAuto;
    private EditText etNote, etQty, etUnitPrice;
    private Button btnAddItem;

    private RecyclerView rvPreview;
    private TextView tvPreviewEmpty;
    private PreviewAdapter previewAdapter;

    private final java.util.List<OrderLite> orders;
    private final DoneCallback callback;

    // Loaded from order detail
    private final java.util.List<OrderItemLite> invoiceItems = new ArrayList<>();
    private ArrayAdapter<OrderItemLite> invoiceItemsAdapter;

    // Items that will be returned (POST)
    private final java.util.List<ProductReturnRepository.CreateItem> returnItems = new ArrayList<>();

    // Current order info
    private Integer currentCustomerId = null;
    private String currentCustomerName = null;

    public ProductReturnAddDialog(
            @NonNull java.util.List<OrderLite> orders,
            @NonNull DoneCallback callback
    ) {
        this.orders = orders;
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View v = requireActivity().getLayoutInflater()
                .inflate(R.layout.dialog_product_return_add, null);

        spOrder = v.findViewById(R.id.spOrder);
        spOrderItem = v.findViewById(R.id.spOrderItem);

        tvCustomerAuto = v.findViewById(R.id.tvCustomerAuto);

        etNote = v.findViewById(R.id.etNote);
        etQty = v.findViewById(R.id.etQty);
        etUnitPrice = v.findViewById(R.id.etUnitPrice);

        btnAddItem = v.findViewById(R.id.btnAddItem);

        rvPreview = v.findViewById(R.id.rvPreview);
        tvPreviewEmpty = v.findViewById(R.id.tvPreviewEmpty);

        // Unit price must be read-only (extra safety besides XML settings)
        etUnitPrice.setEnabled(false);
        etUnitPrice.setFocusable(false);
        etUnitPrice.setClickable(false);

        // Preview list setup
        previewAdapter = new PreviewAdapter();
        rvPreview.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPreview.setAdapter(previewAdapter);

        // Order spinner
        spOrder.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                orders
        ));

        // Invoice items spinner (starts empty)
        invoiceItemsAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                invoiceItems
        );
        spOrderItem.setAdapter(invoiceItemsAdapter);

        // When order selected -> fetch detail and populate customer + invoice items
        spOrder.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= orders.size()) return;
                OrderLite o = orders.get(position);
                loadOrderDetail(o.id);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        // When invoice item selected -> auto update unit price
        spOrderItem.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateUnitPriceFromSelectedItem();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                etUnitPrice.setText("");
            }
        });

        btnAddItem.setOnClickListener(view -> addReturnItem());

        // Default behavior
        if (!orders.isEmpty()) {
            loadOrderDetail(orders.get(0).id);
        }

        TextView tvTitle = v.findViewById(R.id.tvTitleProductReturnAdd);
        if (tvTitle != null) {
            tvTitle.setText("Add Product Return");
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            View positiveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            View negativeBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);

            if (positiveBtn instanceof TextView) {
                ((TextView) positiveBtn).setTextColor(0xFF22C55E);
            }
            if (negativeBtn instanceof TextView) {
                ((TextView) negativeBtn).setTextColor(0xFF22C55E);
            }
            if (positiveBtn != null && positiveBtn.getParent() instanceof View) {
                ((View) positiveBtn.getParent()).setBackgroundColor(0xFFF9FAFB);
            }
        });

        return dialog;
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
        etUnitPrice.setText("");
        etQty.setText("");

        invoiceItems.clear();
        if (invoiceItemsAdapter != null) invoiceItemsAdapter.notifyDataSetChanged();

        // Reset return items when changing invoice (prevents mixing invoices)
        returnItems.clear();
        syncPreviewUI();

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
                if (detail.items != null) invoiceItems.addAll(detail.items);
                invoiceItemsAdapter.notifyDataSetChanged();

                if (!invoiceItems.isEmpty()) {
                    spOrderItem.setSelection(0);
                    updateUnitPriceFromSelectedItem();
                } else {
                    etUnitPrice.setText("");
                    Toast.makeText(requireContext(), "This invoice has no items.", Toast.LENGTH_SHORT).show();
                }

                // Optional: enrich labels with product name/code/sku
                enrichItemNames();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                currentCustomerId = null;
                currentCustomerName = null;
                tvCustomerAuto.setText("Customer: -");
                etUnitPrice.setText("");
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUnitPriceFromSelectedItem() {
        Object sel = spOrderItem.getSelectedItem();
        if (!(sel instanceof OrderItemLite)) {
            etUnitPrice.setText("");
            return;
        }
        OrderItemLite it = (OrderItemLite) sel;
        String price = (it.price != null) ? it.price.trim() : "";
        etUnitPrice.setText(price);
    }

    private void enrichItemNames() {
        for (int i = 0; i < invoiceItems.size(); i++) {
            OrderItemLite it = invoiceItems.get(i);
            final int idx = i;

            if (it == null || it.productId <= 0) continue;

            ProductDetailRepository.fetch(requireContext(), it.productId, new ProductDetailRepository.Callback() {
                @Override
                public void onSuccess(@NonNull ProductDetailRepository.ProductMini p) {
                    if (!isAdded()) return;
                    if (idx < 0 || idx >= invoiceItems.size()) return;

                    OrderItemLite target = invoiceItems.get(idx);
                    target.productName = p.name;
                    target.productCode = p.code;
                    target.productSku = p.sku;

                    invoiceItemsAdapter.notifyDataSetChanged();
                    previewAdapter.notifyDataSetChanged();
                }

                @Override
                public void onError(@NonNull String message) {
                    // Ignore per-item error; spinner and preview are still usable
                }
            });
        }
    }

    private void addReturnItem() {
        Object sel = spOrderItem.getSelectedItem();
        if (!(sel instanceof OrderItemLite)) {
            Toast.makeText(requireContext(), "Invoice item is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderItemLite invIt = (OrderItemLite) sel;

        String qtyStr = etQty.getText().toString().trim();
        if (TextUtils.isEmpty(qtyStr)) {
            Toast.makeText(requireContext(), "Qty is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        int addQty;
        try {
            addQty = Integer.parseInt(qtyStr);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Invalid qty.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (addQty <= 0) {
            Toast.makeText(requireContext(), "Qty must be greater than 0.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Unit price must come from invoice item, never from user input
        String priceStr = (invIt.price != null) ? invIt.price.trim() : "";
        if (priceStr.isEmpty()) {
            Toast.makeText(requireContext(), "Invoice price is missing.", Toast.LENGTH_LONG).show();
            return;
        }

        // Normalize numeric string (handles comma decimal separators)
        String normalized = priceStr.replace(",", ".");
        if (!isValidNonNegativeDecimal(normalized)) {
            Toast.makeText(requireContext(), "Invalid invoice price: " + priceStr, Toast.LENGTH_LONG).show();
            return;
        }

        // Merge behavior: if product already exists in return list, increase qty (not duplicates)
        int existingIndex = findReturnItemIndex(invIt.productId);
        int newQty = addQty;

        if (existingIndex >= 0) {
            ProductReturnRepository.CreateItem existing = returnItems.get(existingIndex);
            newQty = existing.quantity + addQty;
        }

        // Validate newQty <= invoice qty
        int maxQty = invIt.quantity;
        if (maxQty > 0 && newQty > maxQty) {
            Toast.makeText(
                    requireContext(),
                    "Return qty cannot exceed invoice qty (" + maxQty + ").",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (existingIndex >= 0) {
            // Replace existing item with new immutable instance
            returnItems.set(
                    existingIndex,
                    new ProductReturnRepository.CreateItem(
                            invIt.productId,
                            newQty,
                            normalized
                    )
            );
        } else {
            // Add a new line
            returnItems.add(
                    new ProductReturnRepository.CreateItem(
                            invIt.productId,
                            newQty,
                            normalized
                    )
            );
        }

        etQty.setText("");
        updateUnitPriceFromSelectedItem();
        syncPreviewUI();
    }

    private int findReturnItemIndex(int productId) {
        for (int i = 0; i < returnItems.size(); i++) {
            if (returnItems.get(i).productId == productId) return i;
        }
        return -1;
    }

    private boolean isValidNonNegativeDecimal(@NonNull String s) {
        try {
            BigDecimal bd = new BigDecimal(s);
            return bd.compareTo(BigDecimal.ZERO) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void syncPreviewUI() {
        previewAdapter.notifyDataSetChanged();
        tvPreviewEmpty.setVisibility(returnItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private OrderItemLite findInvoiceItemByProductId(int productId) {
        for (OrderItemLite it : invoiceItems) {
            if (it != null && it.productId == productId) return it;
        }
        return null;
    }

    private String bestProductLabel(int productId) {
        OrderItemLite inv = findInvoiceItemByProductId(productId);
        if (inv == null) return "Product #" + productId;

        String n = (inv.productName != null && !inv.productName.trim().isEmpty())
                ? inv.productName.trim()
                : "";

        if (n.isEmpty() && inv.productCode != null && !inv.productCode.trim().isEmpty()) {
            n = inv.productCode.trim();
        }
        if (n.isEmpty() && inv.productSku != null && !inv.productSku.trim().isEmpty()) {
            n = inv.productSku.trim();
        }
        if (n.isEmpty()) n = "Product #" + productId;

        return n;
    }

    private void showEditQtyDialog(int position) {
        if (position < 0 || position >= returnItems.size()) return;

        ProductReturnRepository.CreateItem it = returnItems.get(position);
        OrderItemLite inv = findInvoiceItemByProductId(it.productId);
        int maxQty = (inv != null) ? inv.quantity : 0;

        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(it.quantity));

        String msg = (maxQty > 0)
                ? ("Enter new qty (max " + maxQty + ")")
                : "Enter new qty";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Qty")
                .setMessage(msg)
                .setView(input)
                .setNegativeButton("Cancel", (d, w) -> { })
                .setPositiveButton("Update", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (TextUtils.isEmpty(s)) {
                        Toast.makeText(requireContext(), "Qty is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int newQty;
                    try {
                        newQty = Integer.parseInt(s);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Invalid qty.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (newQty <= 0) {
                        Toast.makeText(requireContext(), "Qty must be greater than 0.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (maxQty > 0 && newQty > maxQty) {
                        Toast.makeText(
                                requireContext(),
                                "Return qty cannot exceed invoice qty (" + maxQty + ").",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    // Replace immutable item instance with a new one
                    returnItems.set(
                            position,
                            new ProductReturnRepository.CreateItem(
                                    it.productId,
                                    newQty,
                                    it.unitPrice
                            )
                    );

                    syncPreviewUI();
                })
                .show();
    }

    private void removePreviewItem(int position) {
        if (position < 0 || position >= returnItems.size()) return;
        returnItems.remove(position);
        syncPreviewUI();
    }

    private void submit() {
        if (spOrder.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "Invoice is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (returnItems.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least 1 return item.", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderLite order = (OrderLite) spOrder.getSelectedItem();
        String note = etNote.getText().toString().trim();

        // If customerId is null, send 0 (adjust if backend expects omit instead)
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
                        Toast.makeText(requireContext(), "Return created.", Toast.LENGTH_SHORT).show();
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

    // Preview adapter (dialog-only) to avoid affecting other modules
    private class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = requireActivity().getLayoutInflater()
                    .inflate(R.layout.item_return_preview_row, parent, false);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ProductReturnRepository.CreateItem it = returnItems.get(position);

            String label = bestProductLabel(it.productId);
            h.tvLine1.setText(label);

            OrderItemLite inv = findInvoiceItemByProductId(it.productId);
            int maxQty = (inv != null) ? inv.quantity : 0;

            String line2;
            if (maxQty > 0) {
                line2 = "Qty: " + it.quantity + " • Unit: " + it.unitPrice + " • Max: " + maxQty;
            } else {
                line2 = "Qty: " + it.quantity + " • Unit: " + it.unitPrice;
            }
            h.tvLine2.setText(line2);

            h.btnEditQty.setOnClickListener(v -> showEditQtyDialog(h.getAdapterPosition()));
            h.btnRemove.setOnClickListener(v -> removePreviewItem(h.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return returnItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvLine1, tvLine2;
            Button btnEditQty, btnRemove;

            VH(@NonNull View itemView) {
                super(itemView);
                tvLine1 = itemView.findViewById(R.id.tvLine1);
                tvLine2 = itemView.findViewById(R.id.tvLine2);
                btnEditQty = itemView.findViewById(R.id.btnEditQty);
                btnRemove = itemView.findViewById(R.id.btnRemove);
            }
        }
    }
}