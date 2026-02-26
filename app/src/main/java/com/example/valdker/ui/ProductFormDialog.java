package com.example.valdker.ui;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.CategoryLite;
import com.example.valdker.models.Product;
import com.example.valdker.models.SupplierLite;
import com.example.valdker.models.UnitLite;
import com.example.valdker.repositories.CategoryRepository;
import com.example.valdker.repositories.ProductRepository;
import com.example.valdker.repositories.SupplierRepository;
import com.example.valdker.repositories.UnitRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ProductFormDialog extends DialogFragment {

    public interface DoneCallback {
        void onDone(@NonNull Product saved);
    }

    private DoneCallback cb;

    // edit mode
    private boolean isEdit = false;
    private Product editing;

    // image
    private Uri selectedImageUri;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public static ProductFormDialog newAdd(@NonNull DoneCallback cb) {
        ProductFormDialog f = new ProductFormDialog();
        f.cb = cb;
        f.isEdit = false;
        f.editing = null;
        return f;
    }

    public static ProductFormDialog newEdit(@NonNull Product editing, @NonNull DoneCallback cb) {
        ProductFormDialog f = new ProductFormDialog();
        f.cb = cb;
        f.isEdit = true;
        f.editing = editing;
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View v = requireActivity().getLayoutInflater()
                .inflate(R.layout.dialog_product_form, null);

        EditText etName = v.findViewById(R.id.etProdName);

        // ✅ NEW: SKU field (make sure dialog_product_form.xml has etProdSku)
        EditText etSku = v.findViewById(R.id.etProdSku);

        EditText etCode = v.findViewById(R.id.etProdCode);
        Spinner spCategory = v.findViewById(R.id.spProdCategory);

        EditText etDesc = v.findViewById(R.id.etProdDesc);
        EditText etStock = v.findViewById(R.id.etProdStock);
        EditText etBuy = v.findViewById(R.id.etProdBuy);
        EditText etSell = v.findViewById(R.id.etProdSell);
        EditText etWeight = v.findViewById(R.id.etProdWeight);

        Spinner spUnit = v.findViewById(R.id.spProdUnit);
        Spinner spSupplier = v.findViewById(R.id.spProdSupplier);

        MaterialButton btnSelectImage = v.findViewById(R.id.btnSelectImage);
        ImageView imgPreview = v.findViewById(R.id.imgPreview);
        ProgressBar progress = v.findViewById(R.id.progressProdForm);

        // ✅ register image picker BEFORE return dialog
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                res -> {
                    if (res.getResultCode() == android.app.Activity.RESULT_OK && res.getData() != null) {
                        Uri uri = res.getData().getData();
                        if (uri != null) {
                            selectedImageUri = uri;
                            imgPreview.setVisibility(View.VISIBLE);
                            imgPreview.setImageURI(uri);
                        }
                    }
                }
        );

        btnSelectImage.setOnClickListener(b -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            imagePickerLauncher.launch(Intent.createChooser(i, "Select Image"));
        });

        // spinner data
        List<CategoryLite> categories = new ArrayList<>();
        List<UnitLite> units = new ArrayList<>();
        List<SupplierLite> suppliers = new ArrayList<>();

        ArrayAdapter<CategoryLite> catAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                categories
        );
        spCategory.setAdapter(catAdapter);

        ArrayAdapter<UnitLite> unitAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                units
        );
        spUnit.setAdapter(unitAdapter);

        ArrayAdapter<SupplierLite> supAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                suppliers
        );
        spSupplier.setAdapter(supAdapter);

        // Prefill when edit
        if (isEdit && editing != null) {
            etName.setText(editing.name);

            // ✅ NEW: prefill sku
            if (etSku != null) etSku.setText(editing.sku != null ? editing.sku : "");

            etCode.setText(editing.barcode);
            etStock.setText(String.valueOf(editing.stock));

            // ✅ now these fields exist because ProductRepository parses them
            etBuy.setText(editing.buyPrice != null ? editing.buyPrice : "");
            etSell.setText(editing.sellPrice != null ? editing.sellPrice : "");
            etWeight.setText(editing.weight != null ? editing.weight : "");
        }

        // load dropdown data
        SessionManager session = new SessionManager(requireContext());
        String token = session.getToken();

        if (token == null || token.trim().isEmpty()) {
            toast("Token empty. Please login again.");
        } else {
            progress.setVisibility(View.VISIBLE);

            UnitRepository unitRepo = new UnitRepository(requireContext());
            SupplierRepository supRepo = new SupplierRepository(requireContext());

            final int[] done = {0};
            final int total = 3;

            Runnable markDone = () -> {
                done[0]++;
                if (done[0] >= total) {
                    progress.setVisibility(View.GONE);
                    catAdapter.notifyDataSetChanged();
                    unitAdapter.notifyDataSetChanged();
                    supAdapter.notifyDataSetChanged();

                    // ✅ Auto select for Edit
                    if (isEdit && editing != null) {
                        selectCategory(spCategory, categories, editing.categoryId);
                        selectUnit(spUnit, units, editing.unitId);
                        selectSupplier(spSupplier, suppliers, editing.supplierId);
                    }
                }
            };

            CategoryRepository.fetchLite(requireContext(), token, new CategoryRepository.LiteCallback() {
                @Override
                public void onSuccess(@NonNull List<CategoryLite> list) {
                    categories.clear();
                    categories.addAll(list);
                    markDone.run();
                }

                @Override
                public void onError(int code, @NonNull String message) {
                    toast("Categories failed: " + code);
                    markDone.run();
                }
            });

            unitRepo.fetchUnits(token, new UnitRepository.Callback() {
                @Override
                public void onSuccess(@NonNull List<UnitLite> list) {
                    units.clear();
                    units.addAll(list);
                    markDone.run();
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    toast("Units failed: " + statusCode);
                    markDone.run();
                }
            });

            supRepo.fetchSupplierLite(token, new SupplierRepository.LiteCallback() {
                @Override
                public void onSuccess(@NonNull List<SupplierLite> list) {
                    suppliers.clear();
                    suppliers.addAll(list);
                    markDone.run();
                }

                @Override
                public void onError(int code, @NonNull String message) {
                    toast("Suppliers failed: " + code);
                    markDone.run();
                }
            });
        }

        String title = isEdit ? "Edit Product" : "Add Product";

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null);

        androidx.appcompat.app.AlertDialog dialog = b.create();

        dialog.setOnShowListener(dlg -> dialog
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(btn -> {

                    String name = etName.getText().toString().trim();

                    // ✅ NEW: sku (optional)
                    String sku = "";
                    if (etSku != null && etSku.getText() != null) {
                        sku = etSku.getText().toString().trim();
                    }

                    String code = etCode.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();

                    String stockStr = etStock.getText().toString().trim();
                    String buy = etBuy.getText().toString().trim();
                    String sell = etSell.getText().toString().trim();
                    String weight = etWeight.getText().toString().trim();

                    if (name.isEmpty()) { toast("Name required"); return; }

                    // SKU optional -> no required validation

                    if (code.isEmpty()) { toast("Code required"); return; }
                    if (spCategory.getSelectedItem() == null) { toast("Category required"); return; }
                    if (stockStr.isEmpty()) { toast("Stock required"); return; }
                    if (buy.isEmpty()) { toast("Buy price required"); return; }
                    if (sell.isEmpty()) { toast("Sell price required"); return; }
                    if (weight.isEmpty()) { toast("Weight required"); return; }
                    if (spUnit.getSelectedItem() == null) { toast("Unit required"); return; }
                    if (spSupplier.getSelectedItem() == null) { toast("Supplier required"); return; }

                    int stock;
                    try {
                        stock = Integer.parseInt(stockStr);
                    } catch (Exception e) {
                        toast("Stock must be number");
                        return;
                    }

                    CategoryLite cat = (CategoryLite) spCategory.getSelectedItem();
                    UnitLite unit = (UnitLite) spUnit.getSelectedItem();
                    SupplierLite sup = (SupplierLite) spSupplier.getSelectedItem();

                    SessionManager session2 = new SessionManager(requireContext());
                    String token2 = session2.getToken();

                    if (token2 == null || token2.trim().isEmpty()) {
                        toast("Token empty");
                        return;
                    }

                    progress.setVisibility(View.VISIBLE);
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                    ProductRepository repo = new ProductRepository(requireContext());

                    if (isEdit && editing != null) {
                        repo.updateProduct(
                                token2,
                                editing.id,
                                name,
                                sku,      // ✅ NEW
                                code,
                                cat.id,
                                desc,
                                stock,
                                buy,
                                sell,
                                weight,
                                unit.id,
                                sup.id,
                                new ProductRepository.ItemCallback() {
                                    @Override
                                    public void onSuccess(@NonNull Product saved) {
                                        progress.setVisibility(View.GONE);
                                        if (cb != null) cb.onDone(saved);
                                        dismiss();
                                    }

                                    @Override
                                    public void onError(int statusCode, @NonNull String message) {
                                        progress.setVisibility(View.GONE);
                                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                        toast("Update failed: " + statusCode);
                                    }
                                }
                        );
                    } else {
                        repo.createProduct(
                                token2,
                                name,
                                sku,     // ✅ NEW
                                code,
                                cat.id,
                                desc,
                                stock,
                                buy,
                                sell,
                                weight,
                                unit.id,
                                sup.id,
                                new ProductRepository.ItemCallback() {
                                    @Override
                                    public void onSuccess(@NonNull Product saved) {
                                        progress.setVisibility(View.GONE);
                                        if (cb != null) cb.onDone(saved);
                                        dismiss();
                                    }

                                    @Override
                                    public void onError(int statusCode, @NonNull String message) {
                                        progress.setVisibility(View.GONE);
                                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                        toast("Create failed: " + statusCode);
                                    }
                                }
                        );
                    }
                }));

        return dialog;
    }

    private void selectCategory(@NonNull Spinner sp, @NonNull List<CategoryLite> list, @Nullable String categoryIdStr) {
        if (categoryIdStr == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (String.valueOf(list.get(i).id).equals(categoryIdStr)) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private void selectUnit(@NonNull Spinner sp, @NonNull List<UnitLite> list, @Nullable String unitIdStr) {
        if (unitIdStr == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (String.valueOf(list.get(i).id).equals(unitIdStr)) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private void selectSupplier(@NonNull Spinner sp, @NonNull List<SupplierLite> list, @Nullable String supplierIdStr) {
        if (supplierIdStr == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (String.valueOf(list.get(i).id).equals(supplierIdStr)) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private void toast(@NonNull String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
