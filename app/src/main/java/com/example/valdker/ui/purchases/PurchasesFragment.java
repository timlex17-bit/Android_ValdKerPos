package com.example.valdker.ui.purchases;

import android.os.Bundle;
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
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class PurchasesFragment extends Fragment {

    private static final String TAG_REQ = "PURCHASES";

    private View root;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;

    private PurchaseListAdapter adapter;
    private PurchaseRepository repo;

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

        InsetsHelper.applyFabMarginInsets(fabAdd, 16, "PURCHASES_UI");
        InsetsHelper.applyRecyclerBottomInsetsWithFab(root, rv, fabAdd, 12, "PURCHASES_UI");

        fabAdd.setOnClickListener(btn -> openAddDialog());

        loadPurchases();

        return v;
    }

    private void openAddDialog() {
        if (fabAdd != null) fabAdd.hide();

        PurchaseAddDialog dlg = new PurchaseAddDialog();
        dlg.setListener(() -> loadPurchases());

        dlg.addOnDismissListener(() -> {
            if (fabAdd != null) fabAdd.show();
        });

        dlg.show(getParentFragmentManager(), "PURCHASE_ADD");
    }

    private void loadPurchases() {
        showLoading(true);

        repo.fetchPurchases(new PurchaseRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<PurchaseLite> list) {
                if (!isAdded()) return;

                showLoading(false);
                adapter.setData(list);

                boolean empty = (list == null || list.isEmpty());
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(int code, @NonNull String message) {
                if (!isAdded()) return;

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

    @Override
    public void onStop() {
        super.onStop();
        // Cancel pending requests by tag (repo should tag requests with TAG_REQ if you want this to work strictly)
        ApiClient.getInstance(requireContext()).cancelAll(TAG_REQ);
    }
}