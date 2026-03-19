package com.example.valdker.ui.purchases;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.network.ApiClient;
import com.example.valdker.repositories.PurchaseRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class PurchasesFragment extends Fragment {

    private static final String TAG_REQ = "PURCHASES";
    private static final long CLICK_GUARD_MS = 700L;

    private View root;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;

    private PurchaseListAdapter adapter;
    private PurchaseRepository repo;

    private boolean isLoading = false;
    private boolean isDialogOpening = false;
    private long lastFabClickAt = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_purchases, container, false);

        root = v.findViewById(R.id.rootPurchases);
        if (root == null) root = v;

        rv = v.findViewById(R.id.rvPurchases);
        progress = v.findViewById(R.id.progressPurchases);
        tvEmpty = v.findViewById(R.id.tvEmptyPurchases);
        fabAdd = v.findViewById(R.id.fabAddPurchase);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);
        rv.setClipToPadding(false);

        adapter = new PurchaseListAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        repo = new PurchaseRepository(requireContext());

        // Jangan pakai applyFabMarginInsets agar posisi FAB tetap persis di bawah sudut kanan
        // InsetsHelper.applyFabMarginInsets(fabAdd, 16, "PURCHASES_UI");

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
            openAddDialog();
        });

        loadPurchases();

        return v;
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

        isLoading = true;
        showLoading(true);

        repo.fetchPurchases(new PurchaseRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<PurchaseLite> list) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);
                adapter.setData(list);

                boolean empty = (list == null || list.isEmpty());
                tvEmpty.setText("No purchases yet.");
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(int code, @NonNull String message) {
                if (!isAdded()) return;

                isLoading = false;
                showLoading(false);
                tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (tvEmpty != null && loading) tvEmpty.setVisibility(View.GONE);
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
        root = null;
        rv = null;
        progress = null;
        tvEmpty = null;
        fabAdd = null;
    }
}