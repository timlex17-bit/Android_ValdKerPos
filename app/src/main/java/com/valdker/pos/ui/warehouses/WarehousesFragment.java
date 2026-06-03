package com.valdker.pos.ui.warehouses;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.Warehouse;
import com.valdker.pos.network.WarehouseApi;
import com.valdker.pos.repositories.AdminMasterCacheRepository;
import com.valdker.pos.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class WarehousesFragment extends BaseFragment {

    private RecyclerView rvWarehouses;
    private EditText etSearchWarehouse;
    private ProgressBar progress;
    private LinearLayout emptyState;
    private ImageView btnBack, ivRefreshWarehouse;
    private FloatingActionButton fabAddWarehouse;
    private SessionManager sessionManager;
    private AdminMasterCacheRepository cacheRepo;
    private WarehouseAdapter adapter;
    private boolean warehouseDialogShowing = false;
    private boolean isLoading = false;

    private final List<Warehouse> allWarehouses = new ArrayList<>();

    public WarehousesFragment() {
        super(R.layout.fragment_warehouses);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        sessionManager = new SessionManager(requireContext());
        cacheRepo = new AdminMasterCacheRepository(requireContext());

        initViews(view);

        InsetsHelper.applyRecyclerBottomInsets(view, rvWarehouses, "WAREHOUSES");
        applyFabBottomInset(fabAddWarehouse, 56);

        setupRecyclerView();
        setupListeners();
        loadWarehouses();
    }

    private void initViews(View view) {
        rvWarehouses = view.findViewById(R.id.rvWarehouses);
        etSearchWarehouse = view.findViewById(R.id.etSearchWarehouse);
        progress = view.findViewById(R.id.progress);
        emptyState = view.findViewById(R.id.emptyState);
        btnBack = view.findViewById(R.id.btnBack);
        ivRefreshWarehouse = view.findViewById(R.id.ivRefreshWarehouse);
        fabAddWarehouse = view.findViewById(R.id.fabAddWarehouse);
    }

    private void setupRecyclerView() {
        adapter = new WarehouseAdapter(new WarehouseAdapter.OnWarehouseActionListener() {
            @Override
            public void onEdit(Warehouse warehouse) {
                showWarehouseDialog(warehouse);
            }

            @Override
            public void onDelete(Warehouse warehouse) {
                confirmDelete(warehouse);
            }
        });

        rvWarehouses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvWarehouses.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed()
        );

        ivRefreshWarehouse.setOnClickListener(v -> loadWarehouses());

        fabAddWarehouse.setOnClickListener(v -> showWarehouseDialog(null));
        fabAddWarehouse.post(() -> {
            if (fabAddWarehouse == null) return;
            fabAddWarehouse.bringToFront();
            fabAddWarehouse.setElevation(100f);
            fabAddWarehouse.setTranslationZ(100f);
        });

        etSearchWarehouse.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterWarehouses(s.toString());
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

    private void loadWarehouses() {
        if (!isAdded() || isLoading) return;
        isLoading = true;
        showLoading(true);

        cacheRepo.loadWarehouses(localWarehouses -> {
            if (!isAdded()) return;
            boolean hasLocal = !localWarehouses.isEmpty();
            if (hasLocal) {
                allWarehouses.clear();
                allWarehouses.addAll(localWarehouses);
                filterWarehouses(etSearchWarehouse == null ? "" : etSearchWarehouse.getText().toString());
            }

            if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                isLoading = false;
                showLoading(false);
                if (!hasLocal) {
                    showLocalEmpty();
                }
                return;
            }

            fetchWarehousesFromApi(hasLocal);
        });
    }

    private void fetchWarehousesFromApi(boolean hadLocalData) {
        WarehouseApi.getWarehouses(requireContext(), getSessionManager(), new WarehouseApi.WarehouseListCallback() {
            @Override
            public void onSuccess(List<Warehouse> warehouses) {
                isLoading = false;
                showLoading(false);

                cacheRepo.saveWarehouses(warehouses == null ? new ArrayList<>() : warehouses, cachedWarehouses -> {
                    if (!isAdded()) return;
                    allWarehouses.clear();
                    allWarehouses.addAll(cachedWarehouses);
                    filterWarehouses(etSearchWarehouse == null ? "" : etSearchWarehouse.getText().toString());
                });
            }

            @Override
            public void onError(String message) {
                isLoading = false;
                showLoading(false);
                showApiError(message);
                if (!hadLocalData && allWarehouses.isEmpty()) {
                    showLocalEmpty();
                } else {
                    updateEmptyState(allWarehouses.isEmpty());
                }
            }
        });
    }

    private void filterWarehouses(String keyword) {
        String query = keyword == null ? "" : keyword.toLowerCase().trim();

        if (query.isEmpty()) {
            adapter.setData(allWarehouses);
            updateEmptyState(allWarehouses.isEmpty());
            return;
        }

        List<Warehouse> filtered = new ArrayList<>();

        for (Warehouse warehouse : allWarehouses) {
            String name = warehouse.getName() == null ? "" : warehouse.getName().toLowerCase();
            String code = warehouse.getCode() == null ? "" : warehouse.getCode().toLowerCase();
            String location = warehouse.getLocation() == null ? "" : warehouse.getLocation().toLowerCase();

            if (name.contains(query) || code.contains(query) || location.contains(query)) {
                filtered.add(warehouse);
            }
        }

        adapter.setData(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void showWarehouseDialog(@Nullable Warehouse warehouse) {
        if (warehouseDialogShowing) {
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        warehouseDialogShowing = true;

        boolean isEdit = warehouse != null;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_warehouse_form, null, false);

        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etCode = dialogView.findViewById(R.id.etCode);
        EditText etLocation = dialogView.findViewById(R.id.etLocation);
        CheckBox cbActive = dialogView.findViewById(R.id.cbActive);
        CheckBox cbDefault = dialogView.findViewById(R.id.cbDefault);

        if (isEdit) {
            etName.setText(warehouse.getName());
            etCode.setText(warehouse.getCode());
            etLocation.setText(warehouse.getLocation());
            cbActive.setChecked(warehouse.isActive());
            cbDefault.setChecked(warehouse.isDefault());
        } else {
            cbActive.setChecked(true);
            cbDefault.setChecked(false);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? getString(R.string.dialog_edit_warehouse_title) : getString(R.string.dialog_add_warehouse_title))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(isEdit ? getString(R.string.action_update) : getString(R.string.action_create), null)
                .create();

        dialog.setOnDismissListener(d -> warehouseDialogShowing = false);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String code = etCode.getText().toString().trim();
                String location = etLocation.getText().toString().trim();

                if (name.isEmpty()) {
                    etName.setError(getString(R.string.msg_warehouse_name_required));
                    etName.requestFocus();
                    return;
                }

                if (code.isEmpty()) {
                    etCode.setError(getString(R.string.msg_warehouse_code_required));
                    etCode.requestFocus();
                    return;
                }

                Warehouse payload = new Warehouse(
                        name,
                        code,
                        location,
                        cbActive.isChecked(),
                        cbDefault.isChecked()
                );

                if (isEdit) {
                    updateWarehouse(warehouse.getId(), payload, dialog);
                } else {
                    createWarehouse(payload, dialog);
                }
            });
        });

        dialog.show();
    }

    private void createWarehouse(Warehouse payload, AlertDialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        WarehouseApi.createWarehouse(requireContext(), getSessionManager(), payload, new WarehouseApi.WarehouseCallback() {
            @Override
            public void onSuccess(Warehouse warehouse) {
                showLoading(false);
                dialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.msg_warehouse_created), Toast.LENGTH_SHORT).show();
                loadWarehouses();
            }

            @Override
            public void onError(String message) {
                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void updateWarehouse(int id, Warehouse payload, AlertDialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        WarehouseApi.updateWarehouse(requireContext(), getSessionManager(), id, payload, new WarehouseApi.WarehouseCallback() {
            @Override
            public void onSuccess(Warehouse warehouse) {
                showLoading(false);
                dialog.dismiss();
                Toast.makeText(requireContext(), getString(R.string.msg_warehouse_updated), Toast.LENGTH_SHORT).show();
                loadWarehouses();
            }

            @Override
            public void onError(String message) {
                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void confirmDelete(Warehouse warehouse) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_delete_warehouse_title))
                .setMessage(getString(R.string.dialog_delete_warehouse_message, warehouse.getName()))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> deleteWarehouse(warehouse.getId()))
                .show();
    }

    private void deleteWarehouse(int id) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), AdminMasterCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        WarehouseApi.deleteWarehouse(requireContext(), getSessionManager(), id, new WarehouseApi.DeleteCallback() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(requireContext(), getString(R.string.msg_warehouse_deleted), Toast.LENGTH_SHORT).show();
                loadWarehouses();
            }

            @Override
            public void onError(String message) {
                showLoading(false);
                showApiError(message);
            }
        });
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState(boolean isEmpty) {
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvWarehouses.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLocalEmpty() {
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
        }
        if (rvWarehouses != null) {
            rvWarehouses.setVisibility(View.GONE);
        }
        Toast.makeText(requireContext(), AdminMasterCacheRepository.NO_LOCAL_DATA_MESSAGE, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        warehouseDialogShowing = false;
        isLoading = false;
    }
}
