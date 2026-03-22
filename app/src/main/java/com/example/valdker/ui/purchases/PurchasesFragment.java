package com.example.valdker.ui.purchases;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.example.valdker.R;
import com.example.valdker.base.BaseFragment;
import com.example.valdker.network.ApiClient;
import com.example.valdker.repositories.PurchaseRepository;
import com.example.valdker.utils.InsetsHelper;
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
    private TextView tvTitle;
    private FloatingActionButton fabAdd;
    private EditText etSearchPurchase;
    private ImageView btnBack;
    private ImageView ivHeaderAction;

    private PurchaseListAdapter adapter;
    private PurchaseRepository repo;

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
        tvTitle = view.findViewById(R.id.tvTitlePurchases);
        etSearchPurchase = view.findViewById(R.id.etSearchPurchase);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);

        if (tvTitle != null) {
            tvTitle.setText("Purchases");
        }

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

        repo = new PurchaseRepository(requireContext());

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
        if (repo == null) return;

        isLoading = true;
        showLoading(true);

        repo.fetchPurchases(new PurchaseRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<PurchaseLite> list) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);

                allItems.clear();
                allItems.addAll(list);
                applyFilter();
            }

            @Override
            public void onError(int code, @NonNull String message) {
                isLoading = false;
                if (!isAdded()) return;

                showLoading(false);

                if (tvEmpty != null) {
                    tvEmpty.setText("Failed to load purchases");
                    tvEmpty.setVisibility(View.VISIBLE);
                }

                if (rv != null) {
                    rv.setVisibility(View.GONE);
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter() {
        List<PurchaseLite> filtered = new ArrayList<>();

        if (currentQuery == null || currentQuery.trim().isEmpty()) {
            filtered.addAll(allItems);
        } else {
            String q = currentQuery.toLowerCase(Locale.US);

            for (PurchaseLite item : allItems) {
                if (matchesPurchase(item, q)) {
                    filtered.add(item);
                }
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
        tvTitle = null;
        fabAdd = null;
        etSearchPurchase = null;
        btnBack = null;
        ivHeaderAction = null;
    }
}