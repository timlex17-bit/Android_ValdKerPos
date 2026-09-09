package com.valdker.pos.ui.purchases;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.base.BaseFragment;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.repositories.PurchaseReturnCacheRepository;
import com.valdker.pos.utils.DateRangeFilterHelper;
import com.valdker.pos.utils.InsetsHelper;
import com.valdker.pos.utils.NetworkUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PurchasesFragment extends BaseFragment {

    private static final String TAG_REQ = "PURCHASES";
    private static final long CLICK_GUARD_MS = 700L;

    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;
    private EditText etSearchPurchase;
    private ImageView btnBack;
    private ImageView ivHeaderAction;
    private ImageView btnDateRange;
    private DateRangeFilterHelper dateRangeFilter;

    private PurchaseListAdapter adapter;
    private PurchaseReturnCacheRepository cacheRepository;

    private final List<PurchaseLite> allItems = new ArrayList<>();

    private boolean isLoading = false;
    private boolean isDialogOpening = false;
    private long lastFabClickAt = 0L;
    private String currentQuery = "";

    public PurchasesFragment() {
        super(R.layout.fragment_purchases);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        rv = view.findViewById(R.id.rvPurchases);
        progress = view.findViewById(R.id.progressPurchases);
        tvEmpty = view.findViewById(R.id.tvEmptyPurchases);
        fabAdd = view.findViewById(R.id.fabAddPurchase);
        etSearchPurchase = view.findViewById(R.id.etSearchPurchase);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);
        btnDateRange = view.findViewById(R.id.btnDateRange);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(false);
            rv.setClipToPadding(false);
        }

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG_REQ);
        applyFabBottomInset(fabAdd, 56);

        adapter = new PurchaseListAdapter(new ArrayList<>());
        if (rv != null) {
            rv.setAdapter(adapter);
        }

        cacheRepository = new PurchaseReturnCacheRepository(requireContext());

        if (fabAdd != null) {
            fabAdd.post(() -> {
                if (fabAdd == null) return;
                fabAdd.bringToFront();
                fabAdd.setElevation(100f);
                fabAdd.setTranslationZ(100f);
            });

            fabAdd.setOnClickListener(btn -> {
                if (!isAdded()) return;
                if (isRapidFabClick()) return;
                if (isDialogOpening) return;
                if (isStateSaved()) return;
                openAddDialog();
            });
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> {
                if (isLoading) return;
                loadPurchases();
            });
        }

        if (etSearchPurchase != null) {
            etSearchPurchase.addTextChangedListener(new TextWatcher() {
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
        dateRangeFilter = new DateRangeFilterHelper(this, btnDateRange, this::applyFilter);

        loadPurchases();
    }

    private boolean isRapidFabClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFabClickAt < CLICK_GUARD_MS) {
            return true;
        }
        lastFabClickAt = now;
        return false;
    }

    private void openAddDialog() {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), PurchaseReturnCacheRepository.INTERNET_REQUIRED_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        PurchaseAddDialog dlg = new PurchaseAddDialog();
        dlg.setListener(this::loadPurchases);

        dlg.addOnDismissListener(() -> {
            isDialogOpening = false;
            setFabEnabled(true);
        });

        dlg.show(getParentFragmentManager(), "PURCHASE_ADD");
    }

    private void loadPurchases() {
        if (!isAdded()) return;
        if (isLoading) return;
        if (cacheRepository == null) return;

        isLoading = true;
        showLoading(true);

        cacheRepository.loadPurchasesRoomFirst(new PurchaseReturnCacheRepository.RoomFirstCallback<PurchaseLite>() {
            @Override
            public void onLocal(@NonNull List<PurchaseLite> list) {
                if (!isAdded()) return;
                if (list.isEmpty()) return;

                showLoading(false);
                allItems.clear();
                allItems.addAll(list);
                applyFilter();
            }

            @Override
            public void onRemote(@NonNull List<PurchaseLite> list) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);

                allItems.clear();
                allItems.addAll(list);
                applyFilter();
            }

            @Override
            public void onNoInternet(boolean hasLocalData) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);
                if (!hasLocalData && allItems.isEmpty()) {
                    showLocalEmpty();
                    Toast.makeText(requireContext(), PurchaseReturnCacheRepository.NO_LOCAL_DATA_MESSAGE, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message, boolean hasLocalData) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);
                if (!hasLocalData && allItems.isEmpty()) {
                    showLocalEmpty();
                }
                showApiError(message);
            }
        });
    }

    private void showLocalEmpty() {
        if (tvEmpty != null) {
            tvEmpty.setText(PurchaseReturnCacheRepository.NO_LOCAL_DATA_MESSAGE);
            tvEmpty.setVisibility(View.VISIBLE);
        }
        if (rv != null) {
            rv.setVisibility(View.GONE);
        }
    }

    private void applyFilter() {
        List<PurchaseLite> filtered = new ArrayList<>();
        boolean hasQuery = currentQuery != null && !currentQuery.trim().isEmpty();
        String q = hasQuery ? currentQuery.toLowerCase(Locale.US) : "";

        for (PurchaseLite item : allItems) {
            if (item == null) continue;
            boolean matchesQuery = !hasQuery || matchesPurchase(item, q);
            boolean matchesDate = dateRangeFilter == null
                    || dateRangeFilter.matches(item.purchaseDate, extractFieldAsString(item, "createdAt"), extractFieldAsString(item, "created_at"));
            if (matchesQuery && matchesDate) {
                filtered.add(item);
            }
        }

        if (adapter != null) {
            adapter.setData(filtered);
        }

        if (tvEmpty != null) {
            tvEmpty.setText(filtered.isEmpty()
                    ? ((currentQuery == null || currentQuery.trim().isEmpty())
                    ? "No purchases yet."
                    : "No matching purchases.")
                    : "No purchases yet.");
            tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (rv != null) {
            rv.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private boolean matchesPurchase(@NonNull PurchaseLite item, @NonNull String q) {
        if (containsText(item, "invoiceNumber", q)) return true;
        if (containsText(item, "invoice_id", q)) return true;
        if (containsText(item, "supplierName", q)) return true;
        if (containsText(item, "supplier_name", q)) return true;
        if (containsText(item, "status", q)) return true;
        if (containsText(item, "date", q)) return true;
        if (containsText(item, "createdAt", q)) return true;
        if (containsText(item, "created_at", q)) return true;
        if (containsText(item, "note", q)) return true;
        if (containsText(item, "notes", q)) return true;
        if (containsText(item, "total", q)) return true;
        if (containsText(item, "grandTotal", q)) return true;
        return false;
    }

    private boolean containsText(@NonNull Object obj, @NonNull String fieldName, @NonNull String q) {
        String value = extractFieldAsString(obj, fieldName);
        return value != null && value.toLowerCase(Locale.US).contains(q);
    }

    @Nullable
    private String extractFieldAsString(@NonNull Object obj, @NonNull String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignored) {
        }

        try {
            String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method m = obj.getClass().getMethod(getter);
            Object v = m.invoke(obj);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignored) {
        }

        return null;
    }

    private void showLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }

        if (rv != null) {
            rv.setVisibility(loading ? View.GONE : View.VISIBLE);
        }

        if (tvEmpty != null && loading) {
            tvEmpty.setVisibility(View.GONE);
        }

        setFabEnabled(!loading && !isDialogOpening);

        if (ivHeaderAction != null) {
            ivHeaderAction.setEnabled(!loading);
            ivHeaderAction.setAlpha(loading ? 0.5f : 1f);
        }
    }

    private void setFabEnabled(boolean enabled) {
        if (fabAdd == null) return;
        fabAdd.setEnabled(enabled);
        fabAdd.setAlpha(enabled ? 1f : 0.65f);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isAdded()) {
            ApiClient.getInstance(requireContext()).cancelAll(TAG_REQ);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        isLoading = false;
        isDialogOpening = false;

        rv = null;
        progress = null;
        tvEmpty = null;
        fabAdd = null;
        etSearchPurchase = null;
        btnBack = null;
        ivHeaderAction = null;
        btnDateRange = null;
        dateRangeFilter = null;
        cacheRepository = null;
    }
}
