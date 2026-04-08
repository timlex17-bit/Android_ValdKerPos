package com.valdker.pos.ui.productreturns;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;
import com.valdker.pos.models.OrderItemLite;
import com.valdker.pos.models.OrderLite;
import com.valdker.pos.models.ProductLite;
import com.valdker.pos.models.ProductReturn;
import com.valdker.pos.repositories.LiteRepository;
import com.valdker.pos.repositories.OrderDetailRepository;
import com.valdker.pos.repositories.ProductDetailRepository;
import com.valdker.pos.repositories.ProductReturnRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductReturnAddDialog extends DialogFragment {

    public interface DoneCallback {
        void onCreated();
    }

    private enum ReturnMode {
        BY_INVOICE,
        MANUAL
    }

    // Header / mode
    private RadioGroup rgReturnMode;
    private RadioButton rbByInvoice;
    private RadioButton rbManual;

    // Sections
    private LinearLayout layoutInvoiceSection;
    private LinearLayout layoutManualSection;

    // Invoice mode
    private Spinner spOrder;
    private Spinner spOrderItem;
    private TextView tvCustomerAuto;
    private EditText etQty;
    private EditText etUnitPrice;
    private Button btnAddItem;

    // Manual mode
    private Spinner spManualProduct;
    private EditText etManualQty;
    private EditText etManualUnitPrice;
    private Button btnAddManualItem;
    private TextView tvManualHint;

    // Shared
    private EditText etNote;
    private RecyclerView rvPreview;
    private TextView tvPreviewEmpty;

    private PreviewAdapter previewAdapter;

    private final List<OrderLite> orders;
    private final DoneCallback callback;

    // Invoice detail items
    private final List<OrderItemLite> invoiceItems = new ArrayList<>();
    private ArrayAdapter<OrderItemLite> invoiceItemsAdapter;

    // Manual products
    private final List<ProductLite> manualProducts = new ArrayList<>();
    private ArrayAdapter<ProductLite> manualProductsAdapter;

    // Return draft items
    private final List<ProductReturnRepository.CreateItem> returnItems = new ArrayList<>();

    // Current invoice-linked customer
    private Integer currentCustomerId = null;
    private String currentCustomerName = null;

    private ReturnMode currentMode = ReturnMode.BY_INVOICE;
    private boolean isLoadingManualProducts = false;

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
        View v = requireActivity().getLayoutInflater()
                .inflate(R.layout.dialog_product_return_add, null);

        bindViews(v);
        setupStaticUi();
        setupPreview();
        setupModeSwitcher();
        setupInvoiceMode();
        setupManualMode();

        TextView tvTitle = v.findViewById(R.id.tvTitleProductReturnAdd);
        if (tvTitle != null) {
            tvTitle.setText("Product Return");
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

    private void bindViews(@NonNull View v) {
        rgReturnMode = v.findViewById(R.id.rgReturnMode);
        rbByInvoice = v.findViewById(R.id.rbByInvoice);
        rbManual = v.findViewById(R.id.rbManual);

        layoutInvoiceSection = v.findViewById(R.id.layoutInvoiceSection);
        layoutManualSection = v.findViewById(R.id.layoutManualSection);

        spOrder = v.findViewById(R.id.spOrder);
        spOrderItem = v.findViewById(R.id.spOrderItem);
        tvCustomerAuto = v.findViewById(R.id.tvCustomerAuto);
        etQty = v.findViewById(R.id.etQty);
        etUnitPrice = v.findViewById(R.id.etUnitPrice);
        btnAddItem = v.findViewById(R.id.btnAddItem);

        spManualProduct = v.findViewById(R.id.spManualProduct);
        etManualQty = v.findViewById(R.id.etManualQty);
        etManualUnitPrice = v.findViewById(R.id.etManualUnitPrice);
        btnAddManualItem = v.findViewById(R.id.btnAddManualItem);
        tvManualHint = v.findViewById(R.id.tvManualHint);

        etNote = v.findViewById(R.id.etNote);
        rvPreview = v.findViewById(R.id.rvPreview);
        tvPreviewEmpty = v.findViewById(R.id.tvPreviewEmpty);
    }

    private void setupStaticUi() {
        if (etUnitPrice != null) {
            etUnitPrice.setEnabled(false);
            etUnitPrice.setFocusable(false);
            etUnitPrice.setClickable(false);
            etUnitPrice.setLongClickable(false);
        }

        if (etManualUnitPrice != null) {
            etManualUnitPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }

        if (tvCustomerAuto != null) {
            tvCustomerAuto.setText("Customer: -");
        }

        if (tvManualHint != null) {
            tvManualHint.setText("Manual return does not require invoice or customer.");
        }
    }

    private void setupPreview() {
        previewAdapter = new PreviewAdapter();
        rvPreview.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPreview.setAdapter(previewAdapter);
        syncPreviewUi();
    }

    private void setupModeSwitcher() {
        currentMode = ReturnMode.BY_INVOICE;

        if (rbByInvoice != null) {
            rbByInvoice.setChecked(true);
        }

        updateModeUi();

        if (rgReturnMode != null) {
            rgReturnMode.setOnCheckedChangeListener((group, checkedId) -> {
                ReturnMode newMode = checkedId == R.id.rbManual
                        ? ReturnMode.MANUAL
                        : ReturnMode.BY_INVOICE;

                if (currentMode == newMode) return;

                currentMode = newMode;
                clearTransientInputsOnly();
                updateModeUi();

                if (currentMode == ReturnMode.MANUAL) {
                    ensureManualProductsLoaded();
                } else if (!orders.isEmpty()) {
                    loadOrderDetail(orders.get(0).id);
                }
            });
        }
    }

    private void updateModeUi() {
        if (layoutInvoiceSection != null) {
            layoutInvoiceSection.setVisibility(currentMode == ReturnMode.BY_INVOICE ? View.VISIBLE : View.GONE);
        }

        if (layoutManualSection != null) {
            layoutManualSection.setVisibility(currentMode == ReturnMode.MANUAL ? View.VISIBLE : View.GONE);
        }

        if (tvPreviewEmpty != null && returnItems.isEmpty()) {
            if (currentMode == ReturnMode.BY_INVOICE) {
                tvPreviewEmpty.setText("No return items yet. Add items from invoice.");
            } else {
                tvPreviewEmpty.setText("No return items yet. Add products manually.");
            }
        }
    }

    private void clearTransientInputsOnly() {
        if (etQty != null) etQty.setText("");
        if (etUnitPrice != null) etUnitPrice.setText("");
        if (etManualQty != null) etManualQty.setText("");
        if (etManualUnitPrice != null) etManualUnitPrice.setText("");
    }

    private void setupInvoiceMode() {
        if (spOrder == null || spOrderItem == null) return;

        spOrder.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                orders
        ));

        invoiceItemsAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                invoiceItems
        );
        spOrderItem.setAdapter(invoiceItemsAdapter);

        spOrder.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (currentMode != ReturnMode.BY_INVOICE) return;
                if (position < 0 || position >= orders.size()) return;

                OrderLite o = orders.get(position);
                loadOrderDetail(o.id);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        spOrderItem.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (currentMode != ReturnMode.BY_INVOICE) return;
                updateUnitPriceFromSelectedInvoiceItem();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                if (etUnitPrice != null) etUnitPrice.setText("");
            }
        });

        if (btnAddItem != null) {
            btnAddItem.setOnClickListener(view -> addInvoiceReturnItem());
        }

        if (!orders.isEmpty()) {
            loadOrderDetail(orders.get(0).id);
        }
    }

    private void setupManualMode() {
        if (spManualProduct != null) {
            manualProductsAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    manualProducts
            );
            spManualProduct.setAdapter(manualProductsAdapter);

            spManualProduct.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (currentMode != ReturnMode.MANUAL) return;
                    updateManualPriceFromSelectedProduct();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }

        if (btnAddManualItem != null) {
            btnAddManualItem.setOnClickListener(v -> addManualReturnItem());
        }
    }

    private void ensureManualProductsLoaded() {
        if (isLoadingManualProducts) return;
        if (!manualProducts.isEmpty()) return;

        isLoadingManualProducts = true;

        LiteRepository.fetchProductsLite(requireContext(), new LiteRepository.LiteCallback<ProductLite>() {
            @Override
            public void onSuccess(@NonNull List<ProductLite> items) {
                if (!isAdded()) return;

                isLoadingManualProducts = false;
                manualProducts.clear();
                manualProducts.addAll(items);

                if (manualProductsAdapter != null) {
                    manualProductsAdapter.notifyDataSetChanged();
                }

                if (!manualProducts.isEmpty() && spManualProduct != null) {
                    spManualProduct.setSelection(0);
                    updateManualPriceFromSelectedProduct();
                }
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                isLoadingManualProducts = false;
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadOrderDetail(int orderId) {
        if (tvCustomerAuto != null) tvCustomerAuto.setText("Customer: Loading...");
        if (etUnitPrice != null) etUnitPrice.setText("");
        if (etQty != null) etQty.setText("");

        invoiceItems.clear();
        if (invoiceItemsAdapter != null) invoiceItemsAdapter.notifyDataSetChanged();

        OrderDetailRepository.fetch(requireContext(), orderId, new OrderDetailRepository.Callback() {
            @Override
            public void onSuccess(@NonNull OrderDetailRepository.OrderDetail detail) {
                if (!isAdded()) return;

                currentCustomerId = detail.customerId;
                currentCustomerName = detail.customerName;

                String custLabel = (currentCustomerName != null && !currentCustomerName.trim().isEmpty())
                        ? currentCustomerName
                        : "Guest";
                if (tvCustomerAuto != null) {
                    tvCustomerAuto.setText("Customer: " + custLabel);
                }

                invoiceItems.clear();
                if (detail.items != null) invoiceItems.addAll(detail.items);
                if (invoiceItemsAdapter != null) invoiceItemsAdapter.notifyDataSetChanged();

                if (!invoiceItems.isEmpty() && spOrderItem != null) {
                    spOrderItem.setSelection(0);
                    updateUnitPriceFromSelectedInvoiceItem();
                } else if (etUnitPrice != null) {
                    etUnitPrice.setText("");
                    Toast.makeText(requireContext(), "This invoice has no items.", Toast.LENGTH_SHORT).show();
                }

                enrichInvoiceItemNames();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;

                currentCustomerId = null;
                currentCustomerName = null;

                if (tvCustomerAuto != null) tvCustomerAuto.setText("Customer: -");
                if (etUnitPrice != null) etUnitPrice.setText("");

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void enrichInvoiceItemNames() {
        for (int i = 0; i < invoiceItems.size(); i++) {
            final int idx = i;
            OrderItemLite it = invoiceItems.get(i);

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

                    if (invoiceItemsAdapter != null) invoiceItemsAdapter.notifyDataSetChanged();
                    if (previewAdapter != null) previewAdapter.notifyDataSetChanged();
                }

                @Override
                public void onError(@NonNull String message) {
                    // ignore per-item enrich error
                }
            });
        }
    }

    private void updateUnitPriceFromSelectedInvoiceItem() {
        Object sel = spOrderItem != null ? spOrderItem.getSelectedItem() : null;
        if (!(sel instanceof OrderItemLite)) {
            if (etUnitPrice != null) etUnitPrice.setText("");
            return;
        }

        OrderItemLite it = (OrderItemLite) sel;
        String price = (it.price != null) ? it.price.trim() : "";
        if (etUnitPrice != null) etUnitPrice.setText(price);
    }

    private void updateManualPriceFromSelectedProduct() {
        Object sel = spManualProduct != null ? spManualProduct.getSelectedItem() : null;
        if (!(sel instanceof ProductLite)) {
            if (etManualUnitPrice != null) etManualUnitPrice.setText("");
            return;
        }

        ProductLite p = (ProductLite) sel;

        String price = "";
        try {
            if (p.sellPrice != null) {
                price = String.valueOf(p.sellPrice);
            }
        } catch (Exception ignored) {
        }

        if (etManualUnitPrice != null) {
            etManualUnitPrice.setText(price);
        }
    }

    private void addInvoiceReturnItem() {
        Object sel = spOrderItem != null ? spOrderItem.getSelectedItem() : null;
        if (!(sel instanceof OrderItemLite)) {
            Toast.makeText(requireContext(), "Invoice item is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderItemLite invIt = (OrderItemLite) sel;

        String qtyStr = etQty != null ? etQty.getText().toString().trim() : "";
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

        String priceStr = (invIt.price != null) ? invIt.price.trim() : "";
        if (TextUtils.isEmpty(priceStr)) {
            Toast.makeText(requireContext(), "Invoice price is missing.", Toast.LENGTH_LONG).show();
            return;
        }

        String normalized = priceStr.replace(",", ".");
        if (!isValidNonNegativeDecimal(normalized)) {
            Toast.makeText(requireContext(), "Invalid invoice price.", Toast.LENGTH_LONG).show();
            return;
        }

        int existingIndex = findReturnItemIndex(invIt.productId);
        int newQty = addQty;

        if (existingIndex >= 0) {
            ProductReturnRepository.CreateItem existing = returnItems.get(existingIndex);
            newQty = existing.quantity + addQty;
        }

        int maxQty = invIt.quantity;
        if (maxQty > 0 && newQty > maxQty) {
            Toast.makeText(
                    requireContext(),
                    "Return qty cannot exceed invoice qty (" + maxQty + ").",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        ProductReturnRepository.CreateItem newItem = new ProductReturnRepository.CreateItem(
                invIt.productId,
                newQty,
                normalized
        );

        if (existingIndex >= 0) {
            returnItems.set(existingIndex, newItem);
        } else {
            returnItems.add(newItem);
        }

        if (etQty != null) etQty.setText("");
        updateUnitPriceFromSelectedInvoiceItem();
        syncPreviewUi();
    }

    private void addManualReturnItem() {
        Object sel = spManualProduct != null ? spManualProduct.getSelectedItem() : null;
        if (!(sel instanceof ProductLite)) {
            Toast.makeText(requireContext(), "Product is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductLite p = (ProductLite) sel;

        String qtyStr = etManualQty != null ? etManualQty.getText().toString().trim() : "";
        String unitPriceStr = etManualUnitPrice != null ? etManualUnitPrice.getText().toString().trim() : "";

        if (TextUtils.isEmpty(qtyStr)) {
            Toast.makeText(requireContext(), "Qty is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(unitPriceStr)) {
            Toast.makeText(requireContext(), "Unit price is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyStr);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Invalid qty.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (qty <= 0) {
            Toast.makeText(requireContext(), "Qty must be greater than 0.", Toast.LENGTH_SHORT).show();
            return;
        }

        String normalizedPrice = unitPriceStr.replace(",", ".");
        if (!isValidNonNegativeDecimal(normalizedPrice)) {
            Toast.makeText(requireContext(), "Invalid unit price.", Toast.LENGTH_SHORT).show();
            return;
        }

        int existingIndex = findReturnItemIndex(p.id);
        int newQty = qty;

        if (existingIndex >= 0) {
            ProductReturnRepository.CreateItem existing = returnItems.get(existingIndex);
            newQty = existing.quantity + qty;
        }

        ProductReturnRepository.CreateItem newItem = new ProductReturnRepository.CreateItem(
                p.id,
                newQty,
                normalizedPrice
        );

        if (existingIndex >= 0) {
            returnItems.set(existingIndex, newItem);
        } else {
            returnItems.add(newItem);
        }

        if (etManualQty != null) etManualQty.setText("");
        syncPreviewUi();
    }

    private int findReturnItemIndex(int productId) {
        for (int i = 0; i < returnItems.size(); i++) {
            if (returnItems.get(i).productId == productId) return i;
        }
        return -1;
    }

    private boolean isValidNonNegativeDecimal(@NonNull String value) {
        try {
            BigDecimal bd = new BigDecimal(value);
            return bd.compareTo(BigDecimal.ZERO) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void syncPreviewUi() {
        if (previewAdapter != null) {
            previewAdapter.notifyDataSetChanged();
        }

        if (tvPreviewEmpty != null) {
            tvPreviewEmpty.setVisibility(returnItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private OrderItemLite findInvoiceItemByProductId(int productId) {
        for (OrderItemLite it : invoiceItems) {
            if (it != null && it.productId == productId) return it;
        }
        return null;
    }

    private ProductLite findManualProductById(int productId) {
        for (ProductLite p : manualProducts) {
            if (p != null && p.id == productId) return p;
        }
        return null;
    }

    private String bestProductLabel(int productId) {
        OrderItemLite inv = findInvoiceItemByProductId(productId);
        if (inv != null) {
            String label = safe(inv.productName);
            if (label.isEmpty()) label = safe(inv.productCode);
            if (label.isEmpty()) label = safe(inv.productSku);
            if (label.isEmpty()) label = "Product #" + productId;
            return label;
        }

        ProductLite p = findManualProductById(productId);
        if (p != null) {
            String label = safe(p.name);
            if (label.isEmpty()) label = safe(p.code);
            if (label.isEmpty()) label = safe(p.sku);
            if (label.isEmpty()) label = "Product #" + productId;
            return label;
        }

        return "Product #" + productId;
    }

    private String safe(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private void showEditQtyDialog(int position) {
        if (position < 0 || position >= returnItems.size()) return;

        ProductReturnRepository.CreateItem it = returnItems.get(position);
        OrderItemLite inv = findInvoiceItemByProductId(it.productId);
        int maxQty = inv != null ? inv.quantity : 0;

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(it.quantity));

        String msg = maxQty > 0
                ? "Enter new qty (max " + maxQty + ")"
                : "Enter new qty";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Qty")
                .setMessage(msg)
                .setView(input)
                .setNegativeButton("Cancel", (d, w) -> {
                })
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

                    returnItems.set(
                            position,
                            new ProductReturnRepository.CreateItem(
                                    it.productId,
                                    newQty,
                                    it.unitPrice
                            )
                    );

                    syncPreviewUi();
                })
                .show();
    }

    private void removePreviewItem(int position) {
        if (position < 0 || position >= returnItems.size()) return;
        returnItems.remove(position);
        syncPreviewUi();
    }

    private void submit() {
        if (returnItems.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least 1 return item.", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = etNote != null ? etNote.getText().toString().trim() : "";

        if (currentMode == ReturnMode.BY_INVOICE) {
            OrderLite order = (spOrder != null && spOrder.getSelectedItem() instanceof OrderLite)
                    ? (OrderLite) spOrder.getSelectedItem()
                    : null;

            if (order == null) {
                Toast.makeText(requireContext(), "Invoice is required in invoice mode.", Toast.LENGTH_SHORT).show();
                return;
            }

            Integer customerId = currentCustomerId != null ? currentCustomerId : null;

            ProductReturnRepository.create(
                    requireContext(),
                    Integer.valueOf(order.id),
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
            return;
        }

        ProductReturnRepository.create(
                requireContext(),
                (Integer) null,
                (Integer) null,
                note,
                null,
                returnItems,
                new ProductReturnRepository.CreateCallback() {
                    @Override
                    public void onSuccess(@NonNull ProductReturn created) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Manual return created.", Toast.LENGTH_SHORT).show();
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

            h.tvLine1.setText(bestProductLabel(it.productId));

            OrderItemLite inv = findInvoiceItemByProductId(it.productId);
            int maxQty = inv != null ? inv.quantity : 0;

            String line2 = maxQty > 0
                    ? "Qty: " + it.quantity + " • Unit: " + it.unitPrice + " • Max: " + maxQty
                    : "Qty: " + it.quantity + " • Unit: " + it.unitPrice;

            h.tvLine2.setText(line2);

            h.btnEditQty.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showEditQtyDialog(pos);
                }
            });

            h.btnRemove.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    removePreviewItem(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return returnItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvLine1;
            TextView tvLine2;
            Button btnEditQty;
            Button btnRemove;

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