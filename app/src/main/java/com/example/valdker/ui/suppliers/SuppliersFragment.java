package com.example.valdker.ui.suppliers;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.models.Supplier;
import com.example.valdker.repositories.SupplierRepository;
import com.example.valdker.ui.customers.ConfirmDeleteDialog;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SuppliersFragment extends BaseFragment {

    private static final String TAG = "SUPPLIERS";
    private static final String TAG_ADD_SUPPLIER = "add_supplier";
    private static final String TAG_EDIT_SUPPLIER = "edit_supplier";
    private static final long CLICK_GUARD_MS = 700L;

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private TextView tvTitle;
    private FloatingActionButton fabAdd;
    private EditText etSearchSupplier;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private final List<Supplier> items = new ArrayList<>();
    private final List<Supplier> allItems = new ArrayList<>();
    private SupplierAdapter adapter;

    private SessionManager session;
    private SupplierRepository repo;

    private long lastFabClickTime = 0L;
    private long lastRowActionTime = 0L;

    private boolean isLoading = false;
    private boolean isDialogOpening = false;
    private boolean isDeleteRunning = false;

    private String currentQuery = "";

    public SuppliersFragment() {
        super(R.layout.fragment_suppliers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        session = new SessionManager(requireContext());
        repo = new SupplierRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefreshSuppliers);
        rv = view.findViewById(R.id.rvSuppliers);
        progress = view.findViewById(R.id.progressSuppliers);
        tvEmpty = view.findViewById(R.id.tvEmptySuppliers);
        fabAdd = view.findViewById(R.id.fabAddSupplier);
        tvTitle = view.findViewById(R.id.tvTitleSuppliers);
        etSearchSupplier = view.findViewById(R.id.etSearchSupplier);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);

        if (tvTitle != null) {
            tvTitle.setText("Suppliers");
        }

        if (swipe == null) Log.w(TAG, "swipeRefreshSuppliers not found.");
        if (rv == null) Log.w(TAG, "rvSuppliers not found.");
        if (progress == null) Log.w(TAG, "progressSuppliers not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmptySuppliers not found.");
        if (fabAdd == null) Log.w(TAG, "fabAddSupplier not found.");

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);
        applyFabBottomInset(fabAdd, 56);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(false);
            rv.setClipToPadding(false);
        }

        adapter = new SupplierAdapter(items, new SupplierAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Supplier s) {
                if (!canRunRowAction()) return;
                openEditSupplierDialogSafely(s);
            }

            @Override
            public void onDelete(@NonNull Supplier s) {
                if (!canRunRowAction()) return;
                confirmDeleteSafely(s);
            }
        });

        if (rv != null) {
            rv.setAdapter(adapter);
        }

        if (swipe != null) {
            swipe.setOnRefreshListener(() -> {
                if (isLoading) {
                    stopRefreshing();
                    return;
                }
                load(true);
            });
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddSupplierDialogSafely());

            fabAdd.post(() -> {
                if (fabAdd == null) return;
                fabAdd.bringToFront();
                fabAdd.setElevation(100f);
                fabAdd.setTranslationZ(100f);
            });
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (isLoading) {
                    stopRefreshing();
                    return;
                }
                load(false);
            });
        }

        if (etSearchSupplier != null) {
            etSearchSupplier.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s == null ? "" : s.toString().trim();
                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        load(false);
    }

    private boolean isRapidFabClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickTime < CLICK_GUARD_MS) {
            Log.d(TAG, "FAB click ignored: too fast");
            return true;
        }
        lastFabClickTime = now;
        return false;
    }

    private boolean canRunRowAction() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRowActionTime < CLICK_GUARD_MS) {
            Log.d(TAG, "Row action ignored: too fast");
            return false;
        }
        lastRowActionTime = now;
        return true;
    }

    private void openAddSupplierDialogSafely() {
        if (!isAdded()) return;
        if (isRapidFabClick()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        if (getParentFragmentManager().findFragmentByTag(TAG_ADD_SUPPLIER) != null) {
            Log.d(TAG, "Add supplier dialog already showing");
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        SupplierFormDialog dialog = SupplierFormDialog.newAdd(saved -> {
            isDialogOpening = false;
            setFabEnabled(true);
            load(false);
        });

        getParentFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull androidx.fragment.app.FragmentManager fm,
                                                        @NonNull androidx.fragment.app.Fragment f) {
                        if (f == dialog) {
                            isDialogOpening = false;
                            setFabEnabled(true);
                            fm.unregisterFragmentLifecycleCallbacks(this);
                        }
                    }
                }, false
        );

        dialog.show(getParentFragmentManager(), TAG_ADD_SUPPLIER);
    }

    private void openEditSupplierDialogSafely(@NonNull Supplier s) {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        if (getParentFragmentManager().findFragmentByTag(TAG_EDIT_SUPPLIER) != null) {
            Log.d(TAG, "Edit supplier dialog already showing");
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        SupplierFormDialog dialog = SupplierFormDialog.newEdit(s, saved -> {
            isDialogOpening = false;
            setFabEnabled(true);
            load(false);
        });

        getParentFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewDestroyed(@NonNull androidx.fragment.app.FragmentManager fm,
                                                        @NonNull androidx.fragment.app.Fragment f) {
                        if (f == dialog) {
                            isDialogOpening = false;
                            setFabEnabled(true);
                            fm.unregisterFragmentLifecycleCallbacks(this);
                        }
                    }
                }, false
        );

        dialog.show(getParentFragmentManager(), TAG_EDIT_SUPPLIER);
    }

    private void confirmDeleteSafely(@NonNull Supplier s) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        ConfirmDeleteDialog.show(
                requireContext(),
                "Delete supplier?",
                safeText(s.name) + " • " + safeText(s.cell),
                () -> {
                    if (isDeleteRunning) return;
                    doDelete(s);
                }
        );
    }

    private void load(boolean fromSwipe) {
        if (!isAdded()) return;

        if (isLoading) {
            stopRefreshing();
            return;
        }

        if (session == null || repo == null) {
            Log.e(TAG, "Session/Repo not initialized.");
            stopRefreshing();
            return;
        }

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            showEmpty("Token empty");
            stopRefreshing();
            return;
        }

        isLoading = true;

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) {
            showLoading();
        }

        repo.fetchSuppliers(token, new SupplierRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Supplier> list) {
                isLoading = false;
                if (!isAdded()) return;

                Log.i(TAG, "fetchSuppliers SUCCESS count=" + list.size());

                allItems.clear();
                allItems.addAll(list);

                applyFilter();
                stopRefreshing();
            }

            @Override
            public void onError(int code, @NonNull String message) {
                isLoading = false;
                if (!isAdded()) return;

                Log.e(TAG, "fetchSuppliers ERROR " + code + " / " + message);
                toast("Fetch failed: " + code);

                if (!items.isEmpty()) {
                    showList();
                } else {
                    showEmpty("Error " + code);
                }

                stopRefreshing();
            }
        });
    }

    private void applyFilter() {
        items.clear();

        if (TextUtils.isEmpty(currentQuery)) {
            items.addAll(allItems);
        } else {
            String q = currentQuery.toLowerCase(Locale.US);

            for (Supplier s : allItems) {
                String name = safeLower(s.name);
                String cell = safeLower(s.cell);
                String email = safeLower(s.email);
                String address = safeLower(s.address);

                if (name.contains(q)
                        || cell.contains(q)
                        || email.contains(q)
                        || address.contains(q)) {
                    items.add(s);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (items.isEmpty()) {
            showEmpty(TextUtils.isEmpty(currentQuery) ? "No suppliers" : "No matching suppliers");
        } else {
            showList();
        }
    }

    private void doDelete(@NonNull Supplier s) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            toast("Token empty");
            return;
        }

        isDeleteRunning = true;
        showLoading();

        repo.deleteSupplier(token, s.id, new SupplierRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                isDeleteRunning = false;
                if (!isAdded()) return;

                toast("Deleted");

                removeItemById(s.id, allItems);
                removeItemById(s.id, items);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (items.isEmpty()) {
                    showEmpty(TextUtils.isEmpty(currentQuery) ? "No suppliers" : "No matching suppliers");
                } else {
                    showList();
                }
            }

            @Override
            public void onError(int code, @NonNull String message) {
                isDeleteRunning = false;
                if (!isAdded()) return;

                toast("Delete failed: " + code);

                if (!items.isEmpty()) {
                    showList();
                } else {
                    showEmpty("Error " + code);
                }
            }
        });
    }

    private void removeItemById(int id, @NonNull List<Supplier> target) {
        for (int i = 0; i < target.size(); i++) {
            if (target.get(i).id == id) {
                target.remove(i);
                return;
            }
        }
    }

    private void stopRefreshing() {
        if (swipe != null) {
            swipe.setRefreshing(false);
        }
    }

    private void setFabEnabled(boolean enabled) {
        if (fabAdd == null) return;
        fabAdd.setEnabled(enabled);
        fabAdd.setAlpha(enabled ? 1f : 0.65f);
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void showLoading() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void showEmpty(@NonNull String msg) {
        if (progress != null) progress.setVisibility(View.GONE);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(msg);
        }
        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void showList() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.VISIBLE);
    }

    @NonNull
    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private String safeText(@Nullable String value) {
        if (value == null) return "-";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isLoading = false;
        isDialogOpening = false;
        isDeleteRunning = false;

        swipe = null;
        rv = null;
        progress = null;
        tvEmpty = null;
        tvTitle = null;
        fabAdd = null;
        etSearchSupplier = null;
        btnBack = null;
        ivHeaderAction = null;
    }
}