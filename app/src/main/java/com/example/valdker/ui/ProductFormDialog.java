package com.example.valdker.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.BarcodeScannerDialogFragment;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class ProductFormDialog extends DialogFragment {

    public interface DoneCallback {
        void onDone(@NonNull Product saved);
    }

    private DoneCallback cb;

    private TextInputLayout tilProdSku, tilProdCode;
    private TextInputEditText etProdSku, etProdCode;

    // Edit mode
    private boolean isEdit = false;
    private Product editing;

    // Image
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
        EditText etSku = v.findViewById(R.id.etProdSku);
        EditText etCode = v.findViewById(R.id.etProdCode);
        Spinner spCategory = v.findViewById(R.id.spProdCategory);

        EditText etDesc = v.findViewById(R.id.etProdDesc);
        EditText etStock = v.findViewById(R.id.etProdStock);
        EditText etBuy = v.findViewById(R.id.etProdBuy);
        EditText etSell = v.findViewById(R.id.etProdSell);
        EditText etWeight = v.findViewById(R.id.etProdWeight);

        tilProdSku = v.findViewById(R.id.tilProdSku);
        tilProdCode = v.findViewById(R.id.tilProdCode);
        etProdSku = v.findViewById(R.id.etProdSku);
        etProdCode = v.findViewById(R.id.etProdCode);

        Spinner spUnit = v.findViewById(R.id.spProdUnit);
        Spinner spSupplier = v.findViewById(R.id.spProdSupplier);

        MaterialButton btnSelectImage = v.findViewById(R.id.btnSelectImage);
        ImageView imgPreview = v.findViewById(R.id.imgPreview);
        ProgressBar progress = v.findViewById(R.id.progressProdForm);

        tilProdSku.setEndIconOnClickListener(v1 -> openScannerForField(true));
        tilProdCode.setEndIconOnClickListener(v12 -> openScannerForField(false));

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

        // Spinner data
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
            if (etSku != null) etSku.setText(editing.sku != null ? editing.sku : "");
            etCode.setText(editing.barcode);
            etStock.setText(String.valueOf(editing.stock));
            etBuy.setText(editing.buyPrice != null ? editing.buyPrice : "");
            etSell.setText(editing.sellPrice != null ? editing.sellPrice : "");
            etWeight.setText(editing.weight != null ? editing.weight : "");

            // Stock must be view-only in edit mode (backend locks it)
            etStock.setEnabled(false);
            etStock.setFocusable(false);
            etStock.setClickable(false);
            etStock.setLongClickable(false);
        } else {
            // Stock is allowed in add mode (if backend accepts initial stock)
            etStock.setEnabled(true);
            etStock.setFocusable(true);
            etStock.setClickable(true);
            etStock.setLongClickable(true);
        }

        // Load dropdown data
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

                    // Auto select for Edit
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

        android.widget.TextView tvTitle = v.findViewById(R.id.tvTitleProductForm);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null);

        androidx.appcompat.app.AlertDialog dialog = b.create();

        dialog.setOnShowListener(dlg -> {
            View positiveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            View negativeBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);

            if (positiveBtn != null) {
                ((android.widget.TextView) positiveBtn).setTextColor(0xFF22C55E);
            }

            if (negativeBtn != null) {
                ((android.widget.TextView) negativeBtn).setTextColor(0xFF22C55E);
            }

            if (positiveBtn != null && positiveBtn.getParent() instanceof View) {
                View buttonBar = (View) positiveBtn.getParent();
                buttonBar.setBackgroundColor(0xFFF9FAFB);
            }

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(btn -> {

                        String name = etName.getText().toString().trim();

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
                        if (code.isEmpty()) { toast("Code required"); return; }
                        if (spCategory.getSelectedItem() == null) { toast("Category required"); return; }

                        if (!isEdit && stockStr.isEmpty()) { toast("Stock required"); return; }

                        if (buy.isEmpty()) { toast("Buy price required"); return; }
                        if (sell.isEmpty()) { toast("Sell price required"); return; }
                        if (weight.isEmpty()) { toast("Weight required"); return; }
                        if (spUnit.getSelectedItem() == null) { toast("Unit required"); return; }
                        if (spSupplier.getSelectedItem() == null) { toast("Supplier required"); return; }

                        int stock = 0;
                        if (!isEdit) {
                            try {
                                stock = Integer.parseInt(stockStr);
                            } catch (Exception e) {
                                toast("Stock must be number");
                                return;
                            }
                        } else {
                            try {
                                stock = Integer.parseInt(stockStr.isEmpty() ? "0" : stockStr);
                            } catch (Exception ignored) {
                                stock = 0;
                            }
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

                            int productId;

                            try {
                                productId = Integer.parseInt(editing.id);
                            } catch (Exception e) {
                                progress.setVisibility(View.GONE);
                                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                toast("Invalid product ID");
                                return;
                            }

                            repo.updateProduct(
                                    token2,
                                    productId,
                                    name,
                                    sku,
                                    code,
                                    cat.id,
                                    desc,
                                    stock,
                                    buy,
                                    sell,
                                    weight,
                                    unit.id,
                                    sup.id,
                                    selectedImageUri,
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
                                            toast("Update failed: " + statusCode + " - " + message);
                                        }
                                    }
                            );
                        } else {
                            repo.createProduct(
                                    token2,
                                    name,
                                    sku,
                                    code,
                                    cat.id,
                                    desc,
                                    stock,
                                    buy,
                                    sell,
                                    weight,
                                    unit.id,
                                    sup.id,
                                    selectedImageUri,
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
                                            toast("Create failed: " + statusCode + " - " + message);
                                        }
                                    }
                            );
                        }
                    });
                });

        return dialog;
    }

    private static final int CAMERA_REQUEST = 2101;
    private boolean pendingScanForSku = false;

    private void openScannerForField(boolean forSku) {
        pendingScanForSku = forSku;

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }

        openBarcodeScannerDialog();
    }

    private void openBarcodeScannerDialog() {
        BarcodeScannerDialogFragment dlg = new BarcodeScannerDialogFragment();

        dlg.setListener(barcode -> {
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (barcode == null) return;

                String value = barcode.trim();
                if (value.isEmpty()) return;

                if (pendingScanForSku) {
                    etProdSku.setText(value);
                    etProdSku.setSelection(value.length());
                } else {
                    etProdCode.setText(value);
                    etProdCode.setSelection(value.length());
                }

                if (dlg.isAdded()) {
                    dlg.dismissAllowingStateLoss();
                }
            });
        });

        dlg.setCancelable(true);
        dlg.show(getParentFragmentManager(), "BARCODE_DIALOG_PRODUCT");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openBarcodeScannerDialog();
            } else {
                Toast.makeText(requireContext(),
                        "Camera permission required to scan barcode",
                        Toast.LENGTH_LONG).show();
            }
        }
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