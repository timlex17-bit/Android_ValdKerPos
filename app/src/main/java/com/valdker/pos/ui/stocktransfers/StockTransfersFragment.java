package com.valdker.pos.ui.stocktransfers;

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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.valdker.pos.BarcodeScannerDialogFragment;
import com.valdker.pos.BuildConfig;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.models.ProductOption;
import com.valdker.pos.models.ProductScanResponse;
import com.valdker.pos.models.ProductUnit;
import com.valdker.pos.models.StockTransfer;
import com.valdker.pos.models.StockTransferItem;
import com.valdker.pos.models.Warehouse;
import com.valdker.pos.models.WarehouseStock;
import com.valdker.pos.network.ProductOptionApi;
import com.valdker.pos.network.StockTransferApi;
import com.valdker.pos.network.WarehouseApi;
import com.valdker.pos.network.WarehouseStockApi;
import com.valdker.pos.repositories.InventoryOperationCacheRepository;
import com.valdker.pos.utils.InsetsHelper;
import com.valdker.pos.utils.NetworkUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StockTransfersFragment extends BaseFragment {
    private static final int CAMERA_REQUEST_BARCODE = 3102;

    private RecyclerView rvStockTransfers;
    private EditText etSearchStockTransfer;
    private ProgressBar progress;
    private LinearLayout emptyState;
    private TextView tvEmpty;
    private TextView tvEmptySub;
    private ImageView btnBack;
    private ImageView ivRefreshStockTransfer;
    private FloatingActionButton fabAddStockTransfer;

    private SessionManager sessionManager;
    private InventoryOperationCacheRepository cacheRepository;
    private StockTransferAdapter adapter;
    private boolean stockTransferDialogShowing = false;
    @Nullable
    private BarcodeScannerDialogFragment.Listener pendingBarcodeScannerListener;

    private final List<StockTransfer> allTransfers = new ArrayList<>();
    private final List<Warehouse> warehouseOptions = new ArrayList<>();
    private final List<ProductOption> productOptions = new ArrayList<>();
    private final List<WarehouseStock> warehouseStocks = new ArrayList<>();
    private final DecimalFormat quantityFormat = new DecimalFormat("#.##");

    public StockTransfersFragment() {
        super(R.layout.fragment_stock_transfers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        sessionManager = new SessionManager(requireContext());
        cacheRepository = new InventoryOperationCacheRepository(requireContext());

        initViews(view);
        setupRecyclerView();
        setupListeners();

        InsetsHelper.applyRecyclerBottomInsets(view, rvStockTransfers, "STOCK_TRANSFERS");
        applyFabBottomInset(fabAddStockTransfer, 56);

        loadDropdownOptions();
        loadStockTransfers();
    }

    private void initViews(View view) {
        rvStockTransfers = view.findViewById(R.id.rvStockTransfers);
        etSearchStockTransfer = view.findViewById(R.id.etSearchStockTransfer);
        progress = view.findViewById(R.id.progress);
        emptyState = view.findViewById(R.id.emptyState);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvEmptySub = view.findViewById(R.id.tvEmptySub);
        btnBack = view.findViewById(R.id.btnBack);
        ivRefreshStockTransfer = view.findViewById(R.id.ivRefreshStockTransfer);
        fabAddStockTransfer = view.findViewById(R.id.fabAddStockTransfer);
    }

    private void setupRecyclerView() {
        adapter = new StockTransferAdapter(new StockTransferAdapter.OnStockTransferActionListener() {
            @Override
            public void onEdit(StockTransfer transfer) {
                showStockTransferDialog(transfer);
            }

            @Override
            public void onDelete(StockTransfer transfer) {
                confirmDelete(transfer);
            }

            @Override
            public void onComplete(StockTransfer transfer) {
                confirmComplete(transfer);
            }

            @Override
            public void onCancel(StockTransfer transfer) {
                confirmCancel(transfer);
            }
        });

        rvStockTransfers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvStockTransfers.setHasFixedSize(false);
        rvStockTransfers.setClipToPadding(false);
        rvStockTransfers.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed()
        );

        ivRefreshStockTransfer.setOnClickListener(v -> {
            loadDropdownOptions();
            loadStockTransfers();
        });

        fabAddStockTransfer.setOnClickListener(v -> showStockTransferDialog(null));

        fabAddStockTransfer.post(() -> {
            if (fabAddStockTransfer == null) return;
            fabAddStockTransfer.bringToFront();
            fabAddStockTransfer.setElevation(100f);
            fabAddStockTransfer.setTranslationZ(100f);
        });

        etSearchStockTransfer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterStockTransfers(s.toString());
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

    private void loadDropdownOptions() {
        if (!isAdded()) return;
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
                Log.d("PRODUCTS", "Transfer products loaded=" + productOptions.size());
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                showApiError(getString(R.string.msg_load_products_failed, message));
            }
        });

        WarehouseStockApi.getWarehouseStocks(requireContext(), getSessionManager(), new WarehouseStockApi.WarehouseStockListCallback() {
            @Override
            public void onSuccess(List<WarehouseStock> stocks) {
                if (!isAdded()) return;

                warehouseStocks.clear();
                if (stocks != null) {
                    warehouseStocks.addAll(stocks);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Log.d("STOCK", "Load warehouse stocks failed: " + message);
            }
        });
    }

    private void loadStockTransfers() {
        showLoading(true);

        cacheRepository.loadStockTransfersRoomFirst(new InventoryOperationCacheRepository.RoomFirstCallback<StockTransfer>() {
            @Override
            public void onLocal(@NonNull List<StockTransfer> transfers) {
                if (!isAdded() || transfers.isEmpty()) return;

                allTransfers.clear();
                allTransfers.addAll(transfers);
                filterStockTransfers(etSearchStockTransfer.getText() != null ? etSearchStockTransfer.getText().toString() : "");
                showLoading(false);
            }

            @Override
            public void onRemote(@NonNull List<StockTransfer> transfers) {
                if (!isAdded()) return;

                showLoading(false);

                allTransfers.clear();
                allTransfers.addAll(transfers);

                filterStockTransfers(etSearchStockTransfer.getText() != null ? etSearchStockTransfer.getText().toString() : "");
            }

            @Override
            public void onNoInternet(boolean hasLocalData) {
                if (!isAdded()) return;

                showLoading(false);
                if (!hasLocalData && allTransfers.isEmpty()) {
                    updateLocalEmptyState();
                    Toast.makeText(requireContext(), InventoryOperationCacheRepository.NO_LOCAL_DATA_MESSAGE, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(@NonNull String message, boolean hasLocalData) {
                if (!isAdded()) return;

                showLoading(false);
                if (!hasLocalData && allTransfers.isEmpty()) {
                    updateLocalEmptyState();
                    showApiError(message);
                }
            }
        });
    }

    private void filterStockTransfers(String keyword) {
        String query = keyword == null ? "" : keyword.toLowerCase(Locale.US).trim();

        if (query.isEmpty()) {
            adapter.setData(allTransfers);
            updateEmptyState(allTransfers.isEmpty());
            return;
        }

        List<StockTransfer> filtered = new ArrayList<>();

        for (StockTransfer transfer : allTransfers) {
            String reference = safeLower(transfer.getReferenceNo());
            String fromWarehouse = safeLower(transfer.getFromWarehouseName());
            String fromCode = safeLower(transfer.getFromWarehouseCode());
            String toWarehouse = safeLower(transfer.getToWarehouseName());
            String toCode = safeLower(transfer.getToWarehouseCode());
            String status = safeLower(transfer.getStatus());
            String note = safeLower(transfer.getNote());
            String createdAt = safeLower(transfer.getCreatedAt());

            if (reference.contains(query)
                    || fromWarehouse.contains(query)
                    || fromCode.contains(query)
                    || toWarehouse.contains(query)
                    || toCode.contains(query)
                    || status.contains(query)
                    || note.contains(query)
                    || createdAt.contains(query)) {
                filtered.add(transfer);
            }
        }

        adapter.setData(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void showStockTransferDialog(@Nullable StockTransfer transfer) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), InventoryOperationCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        if (stockTransferDialogShowing) {
            return;
        }

        boolean isEdit = transfer != null;

        if (warehouseOptions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_warehouse_data_loading), Toast.LENGTH_SHORT).show();
            loadDropdownOptions();
            return;
        }

        if (productOptions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_product_data_loading), Toast.LENGTH_SHORT).show();
            loadDropdownOptions();
            return;
        }

        stockTransferDialogShowing = true;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_stock_transfer_form, null, false);

        AutoCompleteTextView actFromWarehouse = dialogView.findViewById(R.id.actFromWarehouse);
        AutoCompleteTextView actToWarehouse = dialogView.findViewById(R.id.actToWarehouse);
        AutoCompleteTextView actProduct = dialogView.findViewById(R.id.actProduct);
        AutoCompleteTextView actUnit = dialogView.findViewById(R.id.actUnit);
        EditText etBarcode = dialogView.findViewById(R.id.etBarcode);
        EditText etQuantity = dialogView.findViewById(R.id.etQuantity);
        EditText etNote = dialogView.findViewById(R.id.etNote);
        Button btnScanBarcode = dialogView.findViewById(R.id.btnScanBarcode);
        TextView tvQuantityPreview = dialogView.findViewById(R.id.tvQuantityPreview);
        TextView tvAvailableStock = dialogView.findViewById(R.id.tvAvailableStock);

        preparePickerField(actFromWarehouse);
        preparePickerField(actToWarehouse);
        preparePickerField(actProduct);
        preparePickerField(actUnit);

        final int[] selectedFromWarehouseId = {isEdit ? transfer.getFromWarehouse() : 0};
        final int[] selectedToWarehouseId = {isEdit ? transfer.getToWarehouse() : 0};
        final int[] selectedProductId = {0};
        final int[] selectedProductUnitId = {0};
        final ProductOption[] selectedProduct = {null};
        final ProductUnit[] selectedUnit = {null};
        final List<ProductUnit> unitOptions = new ArrayList<>();

        ArrayAdapter<ProductUnit> unitAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );
        actUnit.setAdapter(unitAdapter);

        actFromWarehouse.setOnClickListener(v -> showWarehousePicker(
                getString(R.string.dialog_select_from_warehouse_title),
                actFromWarehouse,
                selected -> {
                    selectedFromWarehouseId[0] = selected.getId();
                    actFromWarehouse.setError(null);
                    actFromWarehouse.setText(selected.getDisplayName(), false);
                    updateAvailableStockPreview(
                            tvAvailableStock,
                            selectedFromWarehouseId[0],
                            selectedProductId[0],
                            selectedUnit[0]
                    );
                }
        ));

        actToWarehouse.setOnClickListener(v -> showWarehousePicker(
                getString(R.string.dialog_select_to_warehouse_title),
                actToWarehouse,
                selected -> {
                    selectedToWarehouseId[0] = selected.getId();
                    actToWarehouse.setError(null);
                    actToWarehouse.setText(selected.getDisplayName(), false);
                }
        ));

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
                    updateAvailableStockPreview(
                            tvAvailableStock,
                            selectedFromWarehouseId[0],
                            selectedProductId[0],
                            selectedUnit[0]
                    );
                }
        ));

        actUnit.setOnClickListener(v -> actUnit.showDropDown());
        actUnit.setOnItemClickListener((parent, view, position, id) -> {
            ProductUnit selected = (ProductUnit) parent.getItemAtPosition(position);
            selectedProductUnitId[0] = selected.getId();
            selectedUnit[0] = selected;
            actUnit.setText(selected.getDisplayName(), false);
            actUnit.setError(null);
            updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);
            updateAvailableStockPreview(
                    tvAvailableStock,
                    selectedFromWarehouseId[0],
                    selectedProductId[0],
                    selectedUnit[0]
            );
        });

        btnScanBarcode.setOnClickListener(v -> {
            String manualCode = etBarcode.getText().toString().trim();
            if (!manualCode.isEmpty()) {
                scanProductIntoTransferForm(
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
                        tvQuantityPreview,
                        tvAvailableStock,
                        selectedFromWarehouseId
                );
                return;
            }

            openBarcodeScanner(code -> {
                etBarcode.setText(code);
                scanProductIntoTransferForm(
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
                        tvQuantityPreview,
                        tvAvailableStock,
                        selectedFromWarehouseId
                );
            });
        });

        etBarcode.setOnEditorActionListener((v, actionId, event) -> {
            String code = etBarcode.getText().toString().trim();
            if (!code.isEmpty()) {
                scanProductIntoTransferForm(
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
                        tvQuantityPreview,
                        tvAvailableStock,
                        selectedFromWarehouseId
                );
            }
            return false;
        });

        etQuantity.addTextChangedListener(new TextWatcher() {
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
        });

        if (isEdit) {
            actFromWarehouse.setText(
                    getString(
                            R.string.warehouse_display_code_format,
                            safeText(transfer.getFromWarehouseName()),
                            safeText(transfer.getFromWarehouseCode())
                    ),
                    false
            );

            actToWarehouse.setText(
                    getString(
                            R.string.warehouse_display_code_format,
                            safeText(transfer.getToWarehouseName()),
                            safeText(transfer.getToWarehouseCode())
                    ),
                    false
            );

            etNote.setText(safeText(transfer.getNote()));

            if (transfer.getItems() != null && !transfer.getItems().isEmpty()) {
                StockTransferItem item = transfer.getItems().get(0);

                selectedProductId[0] = item.getProduct();
                selectedProductUnitId[0] = item.getProductUnit();
                selectedProduct[0] = findProductById(item.getProduct());

                actProduct.setText(
                        getString(
                                R.string.product_sku_display_format,
                                safeText(item.getProductName()),
                                safeText(item.getProductSku())
                        ),
                        false
                );

                etQuantity.setText(String.valueOf(item.getQuantityInput()));
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
            }
        }

        updateQuantityPreview(etQuantity, tvQuantityPreview, selectedUnit[0]);
        updateAvailableStockPreview(
                tvAvailableStock,
                selectedFromWarehouseId[0],
                selectedProductId[0],
                selectedUnit[0]
        );

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? getString(R.string.dialog_edit_stock_transfer_title) : getString(R.string.dialog_add_stock_transfer_title))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(isEdit ? getString(R.string.action_update) : getString(R.string.action_create), null)
                .create();

        dialog.setOnDismissListener(d -> stockTransferDialogShowing = false);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_green_dark));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String quantityText = etQuantity.getText().toString().trim();
                String noteText = etNote.getText().toString().trim();

                if (warehouseOptions.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.msg_warehouse_list_refreshed_select_again), Toast.LENGTH_LONG).show();
                    loadDropdownOptions();
                    return;
                }

                if (selectedFromWarehouseId[0] <= 0) {
                    actFromWarehouse.setError(getString(R.string.msg_from_warehouse_required));
                    actFromWarehouse.performClick();
                    return;
                }

                if (selectedToWarehouseId[0] <= 0) {
                    actToWarehouse.setError(getString(R.string.msg_to_warehouse_required));
                    actToWarehouse.performClick();
                    return;
                }

                Warehouse fromWarehouse = findWarehouseById(selectedFromWarehouseId[0]);
                if (fromWarehouse == null || !fromWarehouse.isActive()) {
                    Toast.makeText(requireContext(), getString(R.string.msg_invalid_source_warehouse), Toast.LENGTH_LONG).show();
                    selectedFromWarehouseId[0] = 0;
                    actFromWarehouse.setText("", false);
                    loadDropdownOptions();
                    return;
                }

                Warehouse toWarehouse = findWarehouseById(selectedToWarehouseId[0]);
                if (toWarehouse == null || !toWarehouse.isActive()) {
                    Toast.makeText(requireContext(), getString(R.string.msg_invalid_destination_warehouse), Toast.LENGTH_LONG).show();
                    selectedToWarehouseId[0] = 0;
                    actToWarehouse.setText("", false);
                    loadDropdownOptions();
                    return;
                }

                if (selectedFromWarehouseId[0] == selectedToWarehouseId[0]) {
                    Toast.makeText(requireContext(), getString(R.string.msg_warehouses_cannot_be_same), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (selectedProductId[0] <= 0) {
                    actProduct.setError(getString(R.string.msg_product_required));
                    actProduct.performClick();
                    return;
                }

                if (TextUtils.isEmpty(quantityText)) {
                    etQuantity.setError(getString(R.string.msg_quantity_required));
                    etQuantity.requestFocus();
                    return;
                }

                double quantity;

                try {
                    quantity = Double.parseDouble(quantityText);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), getString(R.string.msg_invalid_quantity_format), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (quantity <= 0) {
                    etQuantity.setError(getString(R.string.msg_quantity_greater_than_zero));
                    etQuantity.requestFocus();
                    return;
                }

                int productUnitIdForPayload = resolveProductUnitIdForPayload(selectedProductId[0], selectedUnit[0]);

                if (exceedsAvailableStock(selectedFromWarehouseId[0], selectedProductId[0], selectedUnit[0], quantity)) {
                    etQuantity.setError(getString(R.string.msg_quantity_exceeds_available_stock));
                    etQuantity.requestFocus();
                    return;
                }

                ArrayList<StockTransferItem> items = new ArrayList<>();
                items.add(new StockTransferItem(selectedProductId[0], productUnitIdForPayload, quantity));

                StockTransfer payload = new StockTransfer(
                        selectedFromWarehouseId[0],
                        selectedToWarehouseId[0],
                        noteText,
                        items
                );

                if (isEdit) {
                    updateStockTransfer(transfer.getId(), payload, dialog);
                } else {
                    if (BuildConfig.DEBUG) {
                        String productName = selectedProduct[0] != null ? selectedProduct[0].getName() : "-";
                        String productSku = selectedProduct[0] != null ? selectedProduct[0].getSku() : "-";
                        int selectedUnitId = selectedUnit[0] != null ? selectedUnit[0].getId() : 0;
                        String unitName = selectedUnit[0] != null ? selectedUnit[0].getUnitName() : "-";
                        boolean isBaseUnit = selectedUnit[0] != null && selectedUnit[0].isBaseUnit();
                        double conversionFactor = selectedUnit[0] != null ? selectedUnit[0].getConversionQty() : 1.0;
                        Log.d("STOCK_TRANSFER_POST", "from_warehouse=" + fromWarehouse.getId()
                                + " (" + fromWarehouse.getName() + " / " + fromWarehouse.getCode() + ")"
                                + ", to_warehouse=" + toWarehouse.getId()
                                + " (" + toWarehouse.getName() + " / " + toWarehouse.getCode() + ")"
                                + ", selectedProduct.id=" + selectedProductId[0]
                                + ", selectedProduct.name=" + productName
                                + ", selectedProduct.sku=" + productSku
                                + ", selectedProductUnit.id=" + selectedUnitId
                                + ", selectedProductUnit.name=" + unitName
                                + ", selectedProductUnit.isBase=" + isBaseUnit
                                + ", selectedProductUnit.conversionFactor=" + conversionFactor
                                + ", payloadProductUnit.id=" + productUnitIdForPayload
                                + ", quantity=" + quantity);
                        try {
                            Log.d("STOCK_TRANSFER_POST", "payload=" + payload.toJson());
                        } catch (Exception e) {
                            Log.d("STOCK_TRANSFER_POST", "payload build failed: " + e.getMessage());
                        }
                    }
                    createStockTransfer(payload, dialog);
                }
            });
        });

        dialog.show();
    }

    private void scanProductIntoTransferForm(
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
            TextView tvQuantityPreview,
            TextView tvAvailableStock,
            int[] selectedFromWarehouseId
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
                updateAvailableStockPreview(
                        tvAvailableStock,
                        selectedFromWarehouseId[0],
                        selectedProductId[0],
                        selectedUnit[0]
                );
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
    private Warehouse findWarehouseById(int id) {
        if (id <= 0) return null;
        for (Warehouse warehouse : warehouseOptions) {
            if (warehouse.getId() == id) return warehouse;
        }
        return null;
    }

    private int resolveProductUnitIdForPayload(
            int productId,
            @Nullable ProductUnit selectedUnit
    ) {
        if (selectedUnit == null) return 0;

        if (selectedUnit.hasValidProductUnitIdForProduct(productId)) {
            return selectedUnit.getId();
        }

        return 0;
    }

    @Nullable
    private ProductOption findProductById(int id) {
        if (id <= 0) return null;
        for (ProductOption product : productOptions) {
            if (product.getId() == id) return product;
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

        tvQuantityPreview.setText(getString(
                R.string.quantity_preview_conversion_format,
                quantityFormat.format(quantity),
                unitName,
                quantityFormat.format(baseQty)
        ));
    }

    private void updateAvailableStockPreview(
            TextView tvAvailableStock,
            int warehouseId,
            int productId,
            @Nullable ProductUnit selectedUnit
    ) {
        if (tvAvailableStock == null) return;

        if (warehouseId <= 0 || productId <= 0) {
            tvAvailableStock.setText("");
            return;
        }

        WarehouseStock stock = findWarehouseStock(warehouseId, productId);
        if (stock == null) {
            tvAvailableStock.setText(getString(R.string.available_stock_empty));
            return;
        }

        String available = formatAvailableBaseStock(stock);
        if (selectedUnit != null
                && selectedUnit.getId() > 0
                && !selectedUnit.isBaseUnit()
                && selectedUnit.getConversionQty() > 1.0) {
            String unitName = selectedUnit.getUnitName().trim().isEmpty()
                    ? getString(R.string.label_unit_fallback)
                    : selectedUnit.getUnitName().trim().toUpperCase(Locale.US);
            double converted = stock.getQuantityBase() / selectedUnit.getConversionQty();
            tvAvailableStock.setText(getString(
                    R.string.available_stock_conversion_format,
                    available,
                    quantityFormat.format(converted),
                    unitName
            ));
            return;
        }

        tvAvailableStock.setText(getString(R.string.available_stock_base_format, available));
    }

    @Nullable
    private WarehouseStock findWarehouseStock(int warehouseId, int productId) {
        for (WarehouseStock stock : warehouseStocks) {
            if (stock.getWarehouse() == warehouseId && stock.getProduct() == productId) {
                return stock;
            }
        }
        return null;
    }

    private String formatAvailableBaseStock(WarehouseStock stock) {
        String display = stock.getDisplayStock();
        if (display != null && !display.trim().isEmpty()) {
            return display.trim();
        }

        String baseUnit = stock.getBaseUnitName();
        if (baseUnit != null && !baseUnit.trim().isEmpty()) {
            return quantityFormat.format(stock.getQuantityBase()) + " " + baseUnit.trim().toUpperCase(Locale.US);
        }

        return quantityFormat.format(stock.getQuantity());
    }

    private boolean exceedsAvailableStock(
            int warehouseId,
            int productId,
            @Nullable ProductUnit selectedUnit,
            double quantity
    ) {
        WarehouseStock stock = findWarehouseStock(warehouseId, productId);
        if (stock == null) return false;

        double requestedBase = quantity;
        if (selectedUnit != null && selectedUnit.getId() > 0) {
            requestedBase = quantity * selectedUnit.getConversionQty();
        }

        return requestedBase > stock.getQuantityBase() + 0.000001d;
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
        dlg.show(getParentFragmentManager(), "stock_transfer_barcode_scanner");
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

    private interface WarehousePickerCallback {
        void onSelected(Warehouse warehouse);
    }

    private interface ProductPickerCallback {
        void onSelected(ProductOption product);
    }

    private void showWarehousePicker(
            String title,
            AutoCompleteTextView target,
            WarehousePickerCallback callback
    ) {
        View pickerView = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.select_dialog_item, null, false);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);

        EditText search = new EditText(requireContext());
        search.setHint(getString(R.string.hint_search_warehouse));
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

        ArrayAdapter<Warehouse> pickerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                new ArrayList<>(warehouseOptions)
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
            Warehouse selected = pickerAdapter.getItem(position);
            if (selected == null) return;

            callback.onSelected(selected);
            target.setText(selected.getDisplayName(), false);
            pickerDialog.dismiss();
        });

        pickerDialog.setOnShowListener(d -> search.requestFocus());
        pickerDialog.show();
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

    private void createStockTransfer(StockTransfer payload, AlertDialog dialog) {
        if (!ensureOnlineForAction()) return;
        showLoading(true);

        StockTransferApi.createStockTransfer(requireContext(), getSessionManager(), payload, new StockTransferApi.StockTransferCallback() {
            @Override
            public void onSuccess(StockTransfer transfer) {
                if (!isAdded()) return;

                showLoading(false);
                dialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.msg_stock_transfer_created), Toast.LENGTH_SHORT).show();
                loadStockTransfers();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void updateStockTransfer(int id, StockTransfer payload, AlertDialog dialog) {
        if (!ensureOnlineForAction()) return;
        showLoading(true);

        StockTransferApi.updateStockTransfer(requireContext(), getSessionManager(), id, payload, new StockTransferApi.StockTransferCallback() {
            @Override
            public void onSuccess(StockTransfer transfer) {
                if (!isAdded()) return;

                showLoading(false);
                dialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.msg_stock_transfer_updated), Toast.LENGTH_SHORT).show();
                loadStockTransfers();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void confirmComplete(StockTransfer transfer) {
        if (!ensureOnlineForAction()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_complete_stock_transfer_title))
                .setMessage(getString(R.string.dialog_complete_stock_transfer_message, safeText(transfer.getReferenceNo())))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_complete), (dialog, which) -> completeStockTransfer(transfer.getId()))
                .show();
    }

    private void completeStockTransfer(int id) {
        if (!ensureOnlineForAction()) return;
        showLoading(true);

        StockTransferApi.completeStockTransfer(requireContext(), getSessionManager(), id, new StockTransferApi.StockTransferCallback() {
            @Override
            public void onSuccess(StockTransfer transfer) {
                if (!isAdded()) return;

                showLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_stock_transfer_completed), Toast.LENGTH_SHORT).show();
                loadStockTransfers();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void confirmCancel(StockTransfer transfer) {
        if (!ensureOnlineForAction()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_cancel_stock_transfer_title))
                .setMessage(getString(R.string.dialog_cancel_stock_transfer_message, safeText(transfer.getReferenceNo())))
                .setNegativeButton(getString(R.string.action_no), null)
                .setPositiveButton(getString(R.string.action_yes_cancel), (dialog, which) -> cancelStockTransfer(transfer.getId()))
                .show();
    }

    private void cancelStockTransfer(int id) {
        if (!ensureOnlineForAction()) return;
        showLoading(true);

        StockTransferApi.cancelStockTransfer(requireContext(), getSessionManager(), id, new StockTransferApi.StockTransferCallback() {
            @Override
            public void onSuccess(StockTransfer transfer) {
                if (!isAdded()) return;

                showLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_stock_transfer_cancelled), Toast.LENGTH_SHORT).show();
                loadStockTransfers();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;

                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void confirmDelete(StockTransfer transfer) {
        if (!ensureOnlineForAction()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_delete_stock_transfer_title))
                .setMessage(getString(R.string.dialog_delete_stock_transfer_message, safeText(transfer.getReferenceNo())))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> deleteStockTransfer(transfer.getId()))
                .show();
    }

    private void deleteStockTransfer(int id) {
        if (!ensureOnlineForAction()) return;
        showLoading(true);

        StockTransferApi.deleteStockTransfer(requireContext(), getSessionManager(), id, new StockTransferApi.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                showLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_stock_transfer_deleted), Toast.LENGTH_SHORT).show();
                loadStockTransfers();
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

        if (rvStockTransfers != null) {
            rvStockTransfers.setEnabled(!loading);
        }

        if (ivRefreshStockTransfer != null) {
            ivRefreshStockTransfer.setEnabled(!loading);
            ivRefreshStockTransfer.setAlpha(loading ? 0.5f : 1f);
        }

        if (fabAddStockTransfer != null) {
            fabAddStockTransfer.setEnabled(!loading);
            fabAddStockTransfer.setAlpha(loading ? 0.65f : 1f);
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyState != null) {
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }

        if (rvStockTransfers != null) {
            rvStockTransfers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void updateLocalEmptyState() {
        if (tvEmpty != null) {
            tvEmpty.setText(InventoryOperationCacheRepository.NO_LOCAL_DATA_MESSAGE);
        }
        if (tvEmptySub != null) {
            tvEmptySub.setText("");
        }
        updateEmptyState(true);
    }

    private boolean ensureOnlineForAction() {
        if (!isAdded()) return false;
        if (NetworkUtils.isNetworkAvailable(requireContext())) return true;
        Toast.makeText(requireContext(), InventoryOperationCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
        return false;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private String safeText(String value) {
        if (value == null) return "-";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        stockTransferDialogShowing = false;
        rvStockTransfers = null;
        etSearchStockTransfer = null;
        progress = null;
        emptyState = null;
        tvEmpty = null;
        tvEmptySub = null;
        btnBack = null;
        ivRefreshStockTransfer = null;
        fabAddStockTransfer = null;
        cacheRepository = null;
    }
}
