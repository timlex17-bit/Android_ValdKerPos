package com.valdker.pos.ui.purchases;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.ProductLite;
import com.valdker.pos.models.SupplierLite;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.repositories.LiteRepository;
import com.valdker.pos.repositories.PurchaseRepository;
import com.valdker.pos.repositories.SupplierRepository;
import com.valdker.pos.utils.InsetsHelper;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PurchaseAddDialog extends androidx.fragment.app.DialogFragment {

    public interface Listener {
        void onSaved();
    }

    private static final String NO_EXPIRED_DATE = "Expired Date";

    private Listener listener;

    private Spinner spSupplier;
    private Spinner spProduct;
    private View btnSave;

    private EditText etInvoice;
    private EditText etNote;

    private TextView tvPurchaseDate;
    private TextView tvExpiredDate;

    private EditText etQty;
    private EditText etCost;

    private MaterialButton btnAddItem;

    private ProgressBar progress;

    private androidx.recyclerview.widget.RecyclerView rvDraft;
    private PurchaseItemDraftAdapter draftAdapter;

    private final List<SupplierLite> suppliers = new ArrayList<>();
    private final List<ProductLite> products = new ArrayList<>();
    private final List<PurchaseItemDraft> drafts = new ArrayList<>();

    private final SimpleDateFormat apiDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private Runnable onDismiss;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void addOnDismissListener(@NonNull Runnable r) {
        onDismiss = r;
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismiss != null) onDismiss.run();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null || getDialog().getWindow() == null) return;

        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.78f);

        getDialog().getWindow().setLayout(w, h);
        getDialog().getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.dialog_purchase_add, container, false);

        View scroll = v.findViewById(R.id.scrollPurchaseAdd);
        if (scroll != null) InsetsHelper.applyScrollInsets(scroll);

        spSupplier = v.findViewById(R.id.spSupplier);
        spProduct = v.findViewById(R.id.spProduct);

        etInvoice = v.findViewById(R.id.etInvoice);
        etNote = v.findViewById(R.id.etNote);

        tvPurchaseDate = v.findViewById(R.id.tvPurchaseDate);
        tvExpiredDate = v.findViewById(R.id.tvExpiredDate);

        etQty = v.findViewById(R.id.etQty);
        etCost = v.findViewById(R.id.etCost);

        btnAddItem = v.findViewById(R.id.btnAddItem);
        btnSave = v.findViewById(R.id.btnSavePurchase);
        if (btnSave != null) btnSave.setOnClickListener(x -> onSave());

        View btnCancel = v.findViewById(R.id.btnCancelPurchase);
        if (btnCancel != null) btnCancel.setOnClickListener(x -> dismiss());

        progress = v.findViewById(R.id.progressCreatePurchase);

        rvDraft = v.findViewById(R.id.rvDraftItems);
        rvDraft.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rvDraft.setHasFixedSize(true);

        draftAdapter = new PurchaseItemDraftAdapter(drafts, position -> {
            if (position >= 0 && position < drafts.size()) {
                drafts.remove(position);
                draftAdapter.notifyDataSetChanged();
                updateSaveEnabled();
            }
        });
        rvDraft.setAdapter(draftAdapter);

        String today = apiDate.format(Calendar.getInstance().getTime());
        tvPurchaseDate.setText(today);
        tvExpiredDate.setText(NO_EXPIRED_DATE);

        tvPurchaseDate.setOnClickListener(x -> pickDateInto(tvPurchaseDate, false));
        tvExpiredDate.setOnClickListener(x -> pickDateInto(tvExpiredDate, true));

        btnAddItem.setOnClickListener(x -> onAddDraftItem());

        loadSuppliersAndProducts();
        updateSaveEnabled();

        return v;
    }

    private void setButtonText(@NonNull View v, @NonNull String text) {
        if (v instanceof MaterialButton) {
            ((MaterialButton) v).setText(text);
        } else if (v instanceof TextView) {
            ((TextView) v).setText(text);
        }
    }

    private String getButtonText(@NonNull View v) {
        if (v instanceof MaterialButton) return String.valueOf(((MaterialButton) v).getText());
        if (v instanceof TextView) return String.valueOf(((TextView) v).getText());
        return "";
    }

    private void loadSuppliersAndProducts() {
        showLoading(true);

        String token = new SessionManager(requireContext()).getToken();

        SupplierRepository supRepo = new SupplierRepository(requireContext());
        supRepo.fetchSupplierLite(token, new SupplierRepository.LiteCallback() {
            @Override
            public void onSuccess(@NonNull List<SupplierLite> list) {
                suppliers.clear();
                suppliers.addAll(list);
                bindSuppliersSpinner();

                LiteRepository.fetchProductsLite(requireContext(), new LiteRepository.LiteCallback<ProductLite>() {
                    @Override
                    public void onSuccess(@NonNull List<ProductLite> items) {
                        products.clear();
                        products.addAll(items);
                        bindProductsSpinner();
                        showLoading(false);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        showLoading(false);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(int code, @NonNull String message) {
                showLoading(false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindSuppliersSpinner() {
        List<Object> data = new ArrayList<>();
        data.add("— No Supplier —");
        data.addAll(suppliers);

        ArrayAdapter<Object> ad = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                data
        );
        spSupplier.setAdapter(ad);
        spSupplier.setSelection(0);
    }

    private void bindProductsSpinner() {
        ArrayAdapter<ProductLite> ad = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                products
        );
        spProduct.setAdapter(ad);
        if (!products.isEmpty()) spProduct.setSelection(0);
    }

    private SupplierLite getSelectedSupplierOrNull() {
        Object sel = spSupplier.getSelectedItem();
        if (sel instanceof SupplierLite) return (SupplierLite) sel;
        return null;
    }

    private ProductLite getSelectedProductOrNull() {
        Object sel = spProduct.getSelectedItem();
        if (sel instanceof ProductLite) return (ProductLite) sel;
        return null;
    }

    private void pickDateInto(@NonNull TextView target, boolean isExpired) {
        Calendar c = Calendar.getInstance();

        DatePickerDialog dlg = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar x = Calendar.getInstance();
                    x.set(Calendar.YEAR, year);
                    x.set(Calendar.MONTH, month);
                    x.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    String date = apiDate.format(x.getTime());
                    target.setText(date);
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );

        dlg.show();
    }

    private void onAddDraftItem() {
        if (products.isEmpty()) {
            Toast.makeText(requireContext(), "Products not loaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductLite p = getSelectedProductOrNull();
        if (p == null || p.id <= 0) {
            Toast.makeText(requireContext(), "Select a product.", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyStr = (etQty.getText() != null) ? etQty.getText().toString().trim() : "";
        String costStr = (etCost.getText() != null) ? etCost.getText().toString().trim() : "";

        if (TextUtils.isEmpty(qtyStr)) {
            etQty.setError("Qty required");
            etQty.requestFocus();
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyStr);
        } catch (Exception e) {
            etQty.setError("Invalid qty");
            etQty.requestFocus();
            return;
        }

        if (qty <= 0) {
            etQty.setError("Qty must be > 0");
            etQty.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(costStr)) {
            etCost.setError("Cost price required");
            etCost.requestFocus();
            return;
        }

        String expText = getButtonText(tvExpiredDate).trim();
        String exp = "";

        if (!TextUtils.isEmpty(expText)
                && !NO_EXPIRED_DATE.equalsIgnoreCase(expText)
                && !"Expired Date".equalsIgnoreCase(expText)
                && !"—".equals(expText)) {
            exp = expText;
        }

        boolean merged = false;
        for (int i = 0; i < drafts.size(); i++) {
            PurchaseItemDraft existing = drafts.get(i);

            if (existing.productId == p.id) {
                String e1 = existing.expiredDate == null ? "" : existing.expiredDate.trim();
                String e2 = exp == null ? "" : exp.trim();

                if (e1.equals(e2) && String.valueOf(existing.costPrice).trim().equals(costStr.trim())) {
                    int newQty = existing.qty + qty;

                    PurchaseItemDraft replaced = new PurchaseItemDraft(
                            existing.productId,
                            existing.productName,
                            newQty,
                            existing.costPrice,
                            existing.expiredDate
                    );

                    drafts.set(i, replaced);
                    merged = true;
                    break;
                }
            }
        }

        if (!merged) {
            drafts.add(new PurchaseItemDraft(p.id, p.name, qty, costStr, exp));
        }

        draftAdapter.notifyDataSetChanged();

        etQty.setText("");
        tvExpiredDate.setText(NO_EXPIRED_DATE);

        updateSaveEnabled();
    }

    private void updateSaveEnabled() {
        if (btnSave == null) return;

        boolean enabled = !drafts.isEmpty() && (progress == null || progress.getVisibility() != View.VISIBLE);
        btnSave.setEnabled(enabled);
        btnSave.setAlpha(enabled ? 1f : 0.35f);
    }

    private void onSave() {
        if (drafts.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least one item.", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = new SessionManager(requireContext()).getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JSONObject body = new JSONObject();

            SupplierLite selectedSup = getSelectedSupplierOrNull();
            if (selectedSup != null) body.put("supplier", selectedSup.id);
            else body.put("supplier", JSONObject.NULL);

            String invoice = etInvoice.getText() != null ? etInvoice.getText().toString().trim() : "";
            String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

            body.put("invoice_id", invoice);
            body.put("purchase_date", getButtonText(tvPurchaseDate).trim());
            body.put("note", note);

            JSONArray items = new JSONArray();
            for (PurchaseItemDraft d : drafts) {
                JSONObject it = new JSONObject();
                it.put("product", d.productId);
                it.put("quantity", d.qty);
                it.put("cost_price", d.costPrice);

                if (d.expiredDate == null
                        || d.expiredDate.trim().isEmpty()
                        || NO_EXPIRED_DATE.equalsIgnoreCase(d.expiredDate.trim())
                        || "Expired Date".equalsIgnoreCase(d.expiredDate.trim())
                        || "—".equals(d.expiredDate)) {
                    it.put("expired_date", JSONObject.NULL);
                } else {
                    it.put("expired_date", d.expiredDate.trim());
                }

                items.put(it);
            }
            body.put("items", items);

            showLoading(true);
            if (btnSave != null) btnSave.setEnabled(false);

            PurchaseRepository repo = new PurchaseRepository(requireContext());
            repo.createPurchase(body, new PurchaseRepository.CreateCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    if (!isAdded()) return;
                    showLoading(false);
                    Toast.makeText(requireContext(), "Purchase saved.", Toast.LENGTH_SHORT).show();
                    dismissAllowingStateLoss();
                    if (listener != null) listener.onSaved();
                }

                @Override
                public void onError(int code, @NonNull String message) {
                    if (!isAdded()) return;
                    showLoading(false);
                    btnSave.setEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Build payload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        if (btnAddItem != null) btnAddItem.setEnabled(!loading);
        if (btnSave != null) btnSave.setEnabled(!loading && !drafts.isEmpty());

        if (spSupplier != null) spSupplier.setEnabled(!loading);
        if (spProduct != null) spProduct.setEnabled(!loading);

        if (!loading) updateSaveEnabled();
    }

    @Override
    public void onStop() {
        super.onStop();
        ApiClient.getInstance(requireContext()).cancelAll(PurchaseRepository.TAG_CREATE);
    }
}