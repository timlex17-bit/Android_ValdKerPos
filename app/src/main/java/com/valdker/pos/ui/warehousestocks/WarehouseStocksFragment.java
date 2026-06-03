package com.valdker.pos.ui.warehousestocks;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.WarehouseStock;
import com.valdker.pos.network.WarehouseStockApi;
import com.valdker.pos.utils.InsetsHelper;
import com.valdker.pos.repositories.AdminMasterCacheRepository;
import com.valdker.pos.utils.NetworkUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import com.valdker.pos.BarcodeScannerDialogFragment;
import com.valdker.pos.models.ProductOption;
import com.valdker.pos.models.ProductScanResponse;
import com.valdker.pos.models.ProductUnit;
import com.valdker.pos.models.Warehouse;
import com.valdker.pos.network.ProductOptionApi;
import com.valdker.pos.network.WarehouseApi;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WarehouseStocksFragment extends BaseFragment {
    private static final int CAMERA_REQUEST_BARCODE = 3101;

    private RecyclerView rvWarehouseStocks;
    private EditText etSearchWarehouseStock;
    private ProgressBar progress;
    private LinearLayout emptyState;
    private ImageView btnBack;
    private ImageView ivRefreshWarehouseStock;
    private FloatingActionButton fabAddWarehouseStock;

    private SessionManager sessionManager;
    private AdminMasterCacheRepository cacheRepo;
    private WarehouseStockAdapter adapter;
    private boolean warehouseStockDialogShowing = false;
    private boolean isLoading = false;
    @Nullable
    private BarcodeScannerDialogFragment.Listener pendingBarcodeScannerListener;
    private final List<Warehouse> warehouseOptions = new ArrayList<>();
    private final List<ProductOption> productOptions = new ArrayList<>();
    private final DecimalFormat quantityFormat = new DecimalFormat("#.##");

    private final List<WarehouseStock> allStocks = new ArrayList<>();

    public WarehouseStocksFragment() {
        super(R.layout.fragment_warehouse_stocks);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        sessionManager = new SessionManager(requireContext());
        cacheRepo = new AdminMasterCacheRepository(requireContext());

        initViews(view);

        InsetsHelper.applyRecyclerBottomInsets(view, rvWarehouseStocks, "WAREHOUSE_STOCKS");
        applyFabBottomInset(fabAddWarehouseStock, 56);

        setupRecyclerView();
        setupListeners();
        loadDropdownOptions();
        loadWarehouseStocks();
    }

    private void loadDropdownOptions() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            return;
        }
        WarehouseApi.getWarehouses(requireContext(), getSessionManager(), new WarehouseApi.WarehouseListCallback() {
            @Override
            public void onSuccess(List<Warehouse> warehouses) {
                if (!isAdded()) return;

                warehouseOptions.clear();
                if (warehouses != null) {
                    warehouseOptions.addAll(warehouses);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showApiError(getString(R.string.msg_load_warehouses_failed, message));
            }
        });

        ProductOptionApi.getProducts(requireContext(), getSessionManager(), new ProductOptionApi.ProductListCallback() {
            @Override
            public void onSuccess(List<ProductOption> products) {
                if (!isAdded()) return;

                productOptions.clear();
                if (products != null) {
                    productOptions.addAll(products);
                }
                Log.d("PRODUCTS", "Warehouse products loaded=" + productOptions.size());
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showApiError(getString(R.string.msg_load_products_failed, message));
            }
        });
    }

    private void initViews(View view) {
        rvWarehouseStocks = view.findViewById(R.id.rvWarehouseStocks);
        etSearchWarehouseStock = view.findViewById(R.id.etSearchWarehouseStock);
        progress = view.findViewById(R.id.progress);
        emptyState = view.findViewById(R.id.emptyState);
        btnBack = view.findViewById(R.id.btnBack);
        ivRefreshWarehouseStock = view.findViewById(R.id.ivRefreshWarehouseStock);
        fabAddWarehouseStock = view.findViewById(R.id.fabAddWarehouseStock);
    }

    private void setupRecyclerView() {
        adapter = new WarehouseStockAdapter(new WarehouseStockAdapter.OnWarehouseStockActionListener() {
            @Override
            public void onEdit(WarehouseStock stock) {
                showWarehouseStockDialog(stock);
            }

            @Override
            public void onDelete(WarehouseStock stock) {
                confirmDelete(stock);
            }
        });

        rvWarehouseStocks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvWarehouseStocks.setHasFixedSize(false);
        rvWarehouseStocks.setClipToPadding(false);
        rvWarehouseStocks.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed()
        );

        ivRefreshWarehouseStock.setOnClickListener(v -> loadWarehouseStocks());

        fabAddWarehouseStock.setOnClickListener(v -> showWarehouseStockDialog(null));

        fabAddWarehouseStock.post(() -> {
            if (fabAddWarehouseStock == null) return;
            fabAddWarehouseStock.bringToFront();
            fabAddWarehouseStock.setElevation(100f);
            fabAddWarehouseStock.setTranslationZ(100f);
        });

        etSearchWarehouseStock.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterWarehouseStocks(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private SessionManager getSessionManager() {
        if (sessionManager == null) {
            sessionManager = new SessionManager(requireContext());
        }
        return sessionManager;
    }

    private void loadWarehouseStocks() {
        if (!isAdded() || isLoading) return;
        isLoading = true;
        showLoading(true);

        cacheRepo.loadWarehouseStocks(localStocks -> {
            if (!isAdded()) return;
            boolean hasLocal = !localStocks.isEmpty();
            if (hasLocal) {
                allStocks.clear();
                allStocks.addAll(localStocks);
                filterWarehouseStocks(etSearchWarehouseStock == null ? "" : etSearchWarehouseStock.getText().toString());
            }

            if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                isLoading = false;
                showLoading(false);
                if (!hasLocal) {
                    showLocalEmpty();
                }
                return;
            }

            fetchWarehouseStocksFromApi(hasLocal);
        });
    }

    private void fetchWarehouseStocksFromApi(boolean hadLocalData) {
        WarehouseStockApi.getWarehouseStocks(requireContext(), getSessionManager(), new WarehouseStockApi.WarehouseStockListCallback() {
            @Override
            public void onSuccess(List<WarehouseStock> stocks) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);

                cacheRepo.saveWarehouseStocks(stocks == null ? new ArrayList<>() : stocks, cachedStocks -> {
                    if (!isAdded()) return;
                    allStocks.clear();
                    allStocks.addAll(cachedStocks);
                    filterWarehouseStocks(etSearchWarehouseStock == null ? "" : etSearchWarehouseStock.getText().toString());
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);
                showApiError(message);
                if (!hadLocalData && allStocks.isEmpty()) {
                    showLocalEmpty();
                } else {
                    updateEmptyState(allStocks.isEmpty());
                }
            }
        });
    }

    private void filterWarehouseStocks(String keyword) {
        String query = keyword == null ? "" : keyword.toLowerCase(Locale.US).trim();

        if (query.isEmpty()) {
            adapter.setData(allStocks);
            updateEmptyState(allStocks.isEmpty());
            return;
        }

        List<WarehouseStock> filtered = new ArrayList<>();

        for (WarehouseStock stock : allStocks) {
            String productName = safeLower(stock.getProductName());
            String productCode = safeLower(stock.getProductCode());
            String productSku = safeLower(stock.getProductSku());
            String warehouseName = safeLower(stock.getWarehouseName());
            String warehouseCode = safeLower(stock.getWarehouseCode());
            String quantity = String.valueOf(stock.getQuantity());
            String minStock = String.valueOf(stock.getMinStock());

            if (productName.contains(query)
                    || productCode.contains(query)
                    || productSku.contains(query)
                    || warehouseName.contains(query)
                    || warehouseCode.contains(query)
                    || quantity.contains(query)
                    || minStock.contains(query)) {
                filtered.add(stock);
            }
        }

        adapter.setData(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private void showWarehouseStockDialog(@Nullable WarehouseStock stock) {
        if (warehouseStockDialogShowing) {
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        warehouseStockDialogShowing = true;

        boolean isEdit = stock != null;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_warehouse_stock_form, null, false);

        AutoCompleteTextView actWarehouse = dialogView.findViewById(R.id.actWarehouse);
        AutoCompleteTextView actProduct = dialogView.findViewById(R.id.actProduct);
        AutoCompleteTextView actUnit = dialogView.findViewById(R.id.actUnit);
        EditText etBarcode = dialogView.findViewById(R.id.etBarcode);
        EditText etQuantity = dialogView.findViewById(R.id.etQuantity);
        EditText etMinStock = dialogView.findViewById(R.id.etMinStock);
        Button btnScanBarcode = dialogView.findViewById(R.id.btnScanBarcode);
        TextView tvQuantityPreview = dialogView.findViewById(R.id.tvQuantityPreview);

        final int[] selectedWarehouseId = {isEdit ? stock.getWarehouse() : 0};
        final int[] selectedProductId = {isEdit ? stock.getProduct() : 0};
        final int[] selectedProductUnitId = {isEdit ? stock.getProductUnit() : 0};
        final ProductOption[] selectedProduct = {findProductById(selectedProductId[0])};
        final ProductUnit[] selectedUnit = {null};
        final List<ProductUnit> unitOptions = new ArrayList<>();

        ArrayAdapter<Warehouse> warehouseAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                warehouseOptions
        );

        ArrayAdapter<ProductUnit> unitAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        actWarehouse.setAdapter(warehouseAdapter);
        actUnit.setAdapter(unitAdapter);

        actWarehouse.setThreshold(0);
        preparePickerField(actProduct);
        preparePickerField(actUnit);

        actWarehouse.setOnClickListener(v -> actWarehouse.showDropDown());
        actUnit.setOnClickListener(v -> actUnit.showDropDown());

        actWarehouse.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actWarehouse.showDropDown();
        });

        actWarehouse.setOnItemClickListener((parent, view, position, id) -> {
            Warehouse selected = (Warehouse) parent.getItemAtPosition(position);
            selectedWarehouseId[0] = selected.getId();
            actWarehouse.setText(selected.getDisplayName(), false);
        });

        actProduct.setOnClickListener(v -> showProductPicker(
                getString(R.string.dialog_select_product_title),
                actProduct,
                selected -> {
                    selectedProductId[0] = selected.getId();
                    selectedProduct[0] = selected;
                    selectedProductUnitId[0] = 0;
                    selectedUnit[0] = null;
                    Log.d("UNITS", "Product=" + selected.getDisplayName()
                            + " units=" + selected.getProductUnits().size());
                    actProduct.setError(null);
                    actProduct.setText(selected.getDisplayName(), false);
                    bindUnitOptions(
                            selected,
                            unitOptions,
                            unitAdapter,
                            actUnit,
                            selectedProductUnitId,
                            selectedUnit,
                            null
                    );
                    updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);
                }
        ));

        actUnit.setOnItemClickListener((parent, view, position, id) -> {
            ProductUnit selected = (ProductUnit) parent.getItemAtPosition(position);
            selectedProductUnitId[0] = selected.getId();
            selectedUnit[0] = selected;
            actUnit.setText(selected.getDisplayName(), false);
            updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);
        });

        btnScanBarcode.setOnClickListener(v -> {
            String manualCode = etBarcode.getText().toString().trim();
            if (!manualCode.isEmpty()) {
                scanProductIntoWarehouseForm(
                        manualCode,
                        actProduct,
                        actUnit,
                        unitOptions,
                        unitAdapter,
                        selectedProductId,
                        selectedProductUnitId,
                        selectedProduct,
                        selectedUnit,
                        etQuantity,
                        tvQuantityPreview
                );
                return;
            }

            openBarcodeScanner(code -> {
                etBarcode.setText(code);
                scanProductIntoWarehouseForm(
                        code,
                        actProduct,
                        actUnit,
                        unitOptions,
                        unitAdapter,
                        selectedProductId,
                        selectedProductUnitId,
                        selectedProduct,
                        selectedUnit,
                        etQuantity,
                        tvQuantityPreview
                );
            });
        });

        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            String code = etBarcode.getText().toString().trim();
            if (!code.isEmpty()) {
                scanProductIntoWarehouseForm(
                        code,
                        actProduct,
                        actUnit,
                        unitOptions,
                        unitAdapter,
                        selectedProductId,
                        selectedProductUnitId,
                        selectedProduct,
                        selectedUnit,
                        etQuantity,
                        tvQuantityPreview
                );
            }
            return false;
        });

        TextWatcher previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        etQuantity.addTextChangedListener(previewWatcher);

        if (isEdit) {
            actWarehouse.setText(
                    getString(
                            R.string.warehouse_display_code_format,
                            safeText(stock.getWarehouseName()),
                            safeText(stock.getWarehouseCode())
                    ),
                    false
            );

            actProduct.setText(
                    getString(
                            R.string.product_sku_display_format,
                            safeText(stock.getProductName()),
                            safeText(stock.getProductSku())
                    ),
                    false
            );

            etQuantity.setText(String.valueOf(stock.getQuantity()));
            etMinStock.setText(String.valueOf(stock.getMinStock()));
            if (selectedProduct[0] != null) {
                bindUnitOptions(
                        selectedProduct[0],
                        unitOptions,
                        unitAdapter,
                        actUnit,
                        selectedProductUnitId,
                        selectedUnit,
                        null
                );
            }
        } else {
            etQuantity.setText("0");
            etMinStock.setText("0");
        }

        updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? getString(R.string.dialog_edit_warehouse_stock_title) : getString(R.string.dialog_add_warehouse_stock_title))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(isEdit ? getString(R.string.action_update) : getString(R.string.action_create), null)
                .create();

        dialog.setOnDismissListener(d -> warehouseStockDialogShowing = false);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_green_dark));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String quantityText = etQuantity.getText().toString().trim();
                String minStockText = etMinStock.getText().toString().trim();

                if (selectedWarehouseId[0] <= 0) {
                    actWarehouse.setError(getString(R.string.msg_warehouse_required));
                    actWarehouse.requestFocus();
                    actWarehouse.showDropDown();
                    return;
                }

                if (selectedProductId[0] <= 0) {
                    actProduct.setError(getString(R.string.msg_product_required));
                    actProduct.requestFocus();
                    actProduct.showDropDown();
                    return;
                }

                double quantity;
                double minStock;

                try {
                    quantity = TextUtils.isEmpty(quantityText) ? 0 : Double.parseDouble(quantityText);
                    minStock = TextUtils.isEmpty(minStockText) ? 0 : Double.parseDouble(minStockText);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), getString(R.string.msg_invalid_number_format), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (quantity < 0) {
                    etQuantity.setError(getString(R.string.msg_quantity_must_be_non_negative));
                    etQuantity.requestFocus();
                    return;
                }

                if (minStock < 0) {
                    etMinStock.setError(getString(R.string.msg_minimum_stock_must_be_non_negative));
                    etMinStock.requestFocus();
                    return;
                }

                if (selectedProductUnitId[0] > 0 && findUnitById(unitOptions, selectedProductUnitId[0]) == null) {
                    actUnit.setError(getString(R.string.msg_unit_required));
                    actUnit.requestFocus();
                    actUnit.showDropDown();
                    return;
                }

                if (hasDuplicateWarehouseStock(selectedWarehouseId[0], selectedProductId[0], isEdit ? stock.getId() : 0)) {
                    Toast.makeText(requireContext(), getString(R.string.msg_warehouse_stock_duplicate), Toast.LENGTH_LONG).show();
                    return;
                }

                WarehouseStock payload = new WarehouseStock(
                        selectedWarehouseId[0],
                        selectedProductId[0],
                        selectedProductUnitId[0],
                        quantity,
                        minStock
                );

                if (isEdit) {
                    updateWarehouseStock(stock.getId(), payload, dialog);
                } else {
                    if (BuildConfig.DEBUG) {
                        Warehouse selectedWarehouse = findWarehouseById(selectedWarehouseId[0]);
                        Log.d("WAREHOUSE_STOCK_POST", "warehouse=" + selectedWarehouseId[0]
                                + " (" + (selectedWarehouse != null ? selectedWarehouse.getName() : "-")
                                + " / " + (selectedWarehouse != null ? selectedWarehouse.getCode() : "-") + ")"
                                + ", product=" + selectedProductId[0]
                                + ", product_unit=" + selectedProductUnitId[0]
                                + ", quantity=" + quantity
                                + ", minimum_stock=" + minStock);
                    }
                    createWarehouseStock(payload, dialog);
                }
            });
        });

        dialog.show();
    }

    private void scanProductIntoWarehouseForm(
            String code,
            AutoCompleteTextView actProduct,
            AutoCompleteTextView actUnit,
            List<ProductUnit> unitOptions,
            ArrayAdapter<ProductUnit> unitAdapter,
            int[] selectedProductId,
            int[] selectedProductUnitId,
            ProductOption[] selectedProduct,
            ProductUnit[] selectedUnit,
            EditText etQuantity,
            TextView tvQuantityPreview
    ) {
        if (code == null || code.trim().isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_barcode_required), Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        ProductOptionApi.scanProduct(requireContext(), getSessionManager(), code, new ProductOptionApi.ProductScanCallback() {
            @Override
            public void onSuccess(ProductScanResponse response) {
                if (!isAdded()) return;

                showLoading(false);

                ProductOption product = findProductById(response.getProductId());
                if (product == null) {
                    product = response.toProductOption();
                    productOptions.add(product);
                } else if (response.getProductUnits() != null
                        && response.getProductUnits().size() > product.getProductUnits().size()) {
                    product.setProductUnits(response.getProductUnits());
                }

                selectedProduct[0] = product;
                selectedProductId[0] = product.getId();
                selectedProductUnitId[0] = 0;
                selectedUnit[0] = null;
                Log.d("UNITS", "Product=" + product.getDisplayName()
                        + " units=" + product.getProductUnits().size());
                actProduct.setText(product.getDisplayName(), false);
                actProduct.setError(null);

                bindUnitOptions(
                        product,
                        unitOptions,
                        unitAdapter,
                        actUnit,
                        selectedProductUnitId,
                        selectedUnit,
                        response.getMatchedUnit()
                );
                updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(getString(R.string.msg_scan_failed, message));
            }
        });
    }

    private void bindUnitOptions(
            ProductOption product,
            List<ProductUnit> unitOptions,
            ArrayAdapter<ProductUnit> unitAdapter,
            AutoCompleteTextView actUnit,
            int[] selectedProductUnitId,
            ProductUnit[] selectedUnit,
            @Nullable ProductUnit preferredUnit
    ) {
        unitOptions.clear();
        if (product != null && product.getProductUnits() != null) {
            unitOptions.addAll(product.getProductUnits());
        }

        Log.d("UNITS", "Product=" + (product != null ? product.getName() : "-")
                + " units=" + unitOptions.size());
        for (ProductUnit unitOption : unitOptions) {
            Log.d("UNITS", "unit=" + unitOption.getDisplayName()
                    + " conv=" + unitOption.getConversionQty());
        }

        unitAdapter.clear();
        unitAdapter.addAll(unitOptions);
        unitAdapter.notifyDataSetChanged();

        ProductUnit unit = findUnitById(unitOptions, selectedProductUnitId[0]);
        if (preferredUnit != null && preferredUnit.getId() > 0) {
            unit = findUnitById(unitOptions, preferredUnit.getId());
            if (unit == null) {
                unit = preferredUnit;
                unitOptions.add(unit);
                unitAdapter.add(unit);
                unitAdapter.notifyDataSetChanged();
            }
        }
        if (unit == null) unit = findDefaultUnit(unitOptions);

        selectedUnit[0] = unit;
        selectedProductUnitId[0] = unit != null ? unit.getId() : 0;
        actUnit.setText(unit != null ? unit.getDisplayName() : "", false);
        actUnit.setError(null);
    }

    @Nullable
    private ProductUnit findDefaultUnit(List<ProductUnit> units) {
        if (units == null || units.isEmpty()) return null;

        for (ProductUnit unit : units) {
            if (unit.isDefaultPurchaseUnit()) return unit;
        }
        for (ProductUnit unit : units) {
            if (unit.isDefaultSaleUnit()) return unit;
        }
        for (ProductUnit unit : units) {
            if (unit.isBaseUnit()) return unit;
        }

        return units.get(0);
    }

    @Nullable
    private ProductUnit findUnitById(List<ProductUnit> units, int id) {
        if (units == null || id <= 0) return null;
        for (ProductUnit unit : units) {
            if (unit.getId() == id) return unit;
        }
        return null;
    }

    @Nullable
    private ProductOption findProductById(int id) {
        if (id <= 0) return null;
        for (ProductOption product : productOptions) {
            if (product.getId() == id) return product;
        }
        return null;
    }

    private boolean hasDuplicateWarehouseStock(int warehouseId, int productId, int currentStockId) {
        for (WarehouseStock existing : allStocks) {
            if (existing.getWarehouse() == warehouseId
                    && existing.getProduct() == productId
                    && existing.getId() != currentStockId) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Warehouse findWarehouseById(int id) {
        if (id <= 0) return null;
        for (Warehouse warehouse : warehouseOptions) {
            if (warehouse.getId() == id) return warehouse;
        }
        return null;
    }

    private void updateQuantityPreview(EditText etQuantity, TextView tvQuantityPreview, @Nullable ProductUnit unit) {
        if (tvQuantityPreview == null) return;

        if (unit == null || unit.getId() <= 0) {
            tvQuantityPreview.setText("");
            return;
        }

        double quantity;
        try {
            String raw = etQuantity.getText().toString().trim();
            quantity = raw.isEmpty() ? 0 : Double.parseDouble(raw);
        } catch (Exception e) {
            tvQuantityPreview.setText("");
            return;
        }

        double baseQty = quantity * unit.getConversionQty();
        String unitName = unit.getUnitName().trim().isEmpty()
                ? getString(R.string.label_unit_fallback)
                : unit.getUnitName().trim().toUpperCase(Locale.US);

        if (unit.isBaseUnit() || unit.getConversionQty() == 1.0) {
            tvQuantityPreview.setText(getString(
                    R.string.quantity_preview_base_format,
                    quantityFormat.format(quantity),
                    unitName
            ));
            return;
        }

        tvQuantityPreview.setText(
                getString(
                        R.string.quantity_preview_conversion_format,
                        quantityFormat.format(quantity),
                        unitName,
                        quantityFormat.format(baseQty)
                )
        );
    }

    private void openBarcodeScanner(BarcodeScannerDialogFragment.Listener listener) {
        pendingBarcodeScannerListener = listener;

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_REQUEST_BARCODE);
            return;
        }

        showBarcodeScanner(listener);
    }

    private void showBarcodeScanner(BarcodeScannerDialogFragment.Listener listener) {
        BarcodeScannerDialogFragment dlg = new BarcodeScannerDialogFragment();
        dlg.setListener(barcode -> {
            listener.onBarcode(barcode);
            dlg.dismissAllowingStateLoss();
        });
        dlg.show(getParentFragmentManager(), "warehouse_stock_barcode_scanner");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != CAMERA_REQUEST_BARCODE) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            BarcodeScannerDialogFragment.Listener listener = pendingBarcodeScannerListener;
            if (listener != null && isAdded()) {
                showBarcodeScanner(listener);
            }
        } else if (isAdded()) {
            Toast.makeText(requireContext(), getString(R.string.msg_camera_permission_barcode), Toast.LENGTH_LONG).show();
        }
        pendingBarcodeScannerListener = null;
    }

    private void preparePickerField(AutoCompleteTextView field) {
        field.setFocusable(false);
        field.setFocusableInTouchMode(false);
        field.setCursorVisible(false);
        field.setInputType(InputType.TYPE_NULL);
        field.setThreshold(0);
    }

    private interface ProductPickerCallback {
        void onSelected(ProductOption product);
    }

    private void showProductPicker(
            String title,
            AutoCompleteTextView target,
            ProductPickerCallback callback
    ) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);

        EditText search = new EditText(requireContext());
        search.setHint(getString(R.string.hint_search_product));
        search.setSingleLine(true);

        ListView listView = new ListView(requireContext());

        container.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        container.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
        ));

        ArrayAdapter<ProductOption> pickerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                new ArrayList<>(productOptions)
        );

        listView.setAdapter(pickerAdapter);

        AlertDialog pickerDialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(container)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .create();

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                pickerAdapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ProductOption selected = pickerAdapter.getItem(position);
            if (selected == null) return;

            callback.onSelected(selected);
            target.setText(selected.getDisplayName(), false);
            pickerDialog.dismiss();
        });

        pickerDialog.setOnShowListener(d -> search.requestFocus());
        pickerDialog.show();
    }

    private void createWarehouseStock(WarehouseStock payload, AlertDialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        WarehouseStockApi.createWarehouseStock(requireContext(), getSessionManager(), payload, new WarehouseStockApi.WarehouseStockCallback() {
            @Override
            public void onSuccess(WarehouseStock stock) {
                if (!isAdded()) return;

                showLoading(false);
                dialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.msg_warehouse_stock_created), Toast.LENGTH_SHORT).show();
                loadWarehouseStocks();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void updateWarehouseStock(int id, WarehouseStock payload, AlertDialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        WarehouseStockApi.updateWarehouseStock(requireContext(), getSessionManager(), id, payload, new WarehouseStockApi.WarehouseStockCallback() {
            @Override
            public void onSuccess(WarehouseStock stock) {
                if (!isAdded()) return;

                showLoading(false);
                dialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.msg_warehouse_stock_updated), Toast.LENGTH_SHORT).show();
                loadWarehouseStocks();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void confirmDelete(WarehouseStock stock) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_delete_warehouse_stock_title))
                .setMessage(getString(R.string.dialog_delete_warehouse_stock_message, safeText(stock.getProductName())))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> deleteWarehouseStock(stock.getId()))
                .show();
    }

    private void deleteWarehouseStock(int id) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        WarehouseStockApi.deleteWarehouseStock(requireContext(), getSessionManager(), id, new WarehouseStockApi.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                showLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_warehouse_stock_deleted), Toast.LENGTH_SHORT).show();
                loadWarehouseStocks();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void showLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rvWarehouseStocks != null) rvWarehouseStocks.setEnabled(!loading);
        if (ivRefreshWarehouseStock != null) {
            ivRefreshWarehouseStock.setEnabled(!loading);
            ivRefreshWarehouseStock.setAlpha(loading ? 0.5f : 1f);
        }
        if (fabAddWarehouseStock != null) {
            fabAddWarehouseStock.setEnabled(!loading);
            fabAddWarehouseStock.setAlpha(loading ? 0.65f : 1f);
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyState != null) {
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }

        if (rvWarehouseStocks != null) {
            rvWarehouseStocks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void showLocalEmpty() {
        updateEmptyState(true);
        Toast.makeText(requireContext(), AdminMasterCacheRepository.NO_LOCAL_DATA_MESSAGE, Toast.LENGTH_SHORT).show();
    }

    private String safeText(String value) {
        if (value == null) return "-";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        warehouseStockDialogShowing = false;
        isLoading = false;
        rvWarehouseStocks = null;
        etSearchWarehouseStock = null;
        progress = null;
        emptyState = null;
        btnBack = null;
        ivRefreshWarehouseStock = null;
        fabAddWarehouseStock = null;
    }
}
