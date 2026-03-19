package com.example.valdker.ui.suppliers;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Supplier;
import com.example.valdker.repositories.SupplierRepository;
import com.example.valdker.ui.customers.ConfirmDeleteDialog;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class SuppliersFragment extends Fragment {

    private static final String TAG = "SUPPLIERS";
    private static final String TAG_ADD_SUPPLIER = "add_supplier";
    private static final long FAB_CLICK_DELAY_MS = 700L;

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;

    private final List<Supplier> items = new ArrayList<>();
    private SupplierAdapter adapter;

    private SessionManager session;
    private SupplierRepository repo;

    private long lastFabClickTime = 0L;

    public SuppliersFragment() {
        super(R.layout.fragment_suppliers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());
        repo = new SupplierRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefreshSuppliers);
        rv = view.findViewById(R.id.rvSuppliers);
        progress = view.findViewById(R.id.progressSuppliers);
        tvEmpty = view.findViewById(R.id.tvEmptySuppliers);
        fabAdd = view.findViewById(R.id.fabAddSupplier);

        if (swipe == null) Log.w(TAG, "swipeRefreshSuppliers not found.");
        if (rv == null) Log.w(TAG, "rvSuppliers not found.");
        if (progress == null) Log.w(TAG, "progressSuppliers not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmptySuppliers not found.");
        if (fabAdd == null) Log.w(TAG, "fabAddSupplier not found.");

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        }

        adapter = new SupplierAdapter(items, new SupplierAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Supplier s) {
                SupplierFormDialog.newEdit(s, (saved) -> load(false))
                        .show(getParentFragmentManager(), "edit_supplier");
            }

            @Override
            public void onDelete(@NonNull Supplier s) {
                ConfirmDeleteDialog.show(
                        requireContext(),
                        "Delete supplier?",
                        s.name + " • " + s.cell,
                        () -> doDelete(s)
                );
            }
        });

        if (rv != null) rv.setAdapter(adapter);

        if (swipe != null) swipe.setOnRefreshListener(() -> load(true));

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddSupplierDialogSafely());
        }

        load(false);
    }

    private void openAddSupplierDialogSafely() {
        if (!isAdded()) return;

        long now = System.currentTimeMillis();
        if (now - lastFabClickTime < FAB_CLICK_DELAY_MS) {
            return;
        }
        lastFabClickTime = now;

        if (getParentFragmentManager().findFragmentByTag(TAG_ADD_SUPPLIER) != null) {
            return;
        }

        if (fabAdd != null) {
            fabAdd.setEnabled(false);
            fabAdd.postDelayed(() -> {
                if (fabAdd != null) fabAdd.setEnabled(true);
            }, FAB_CLICK_DELAY_MS);
        }

        SupplierFormDialog dialog = SupplierFormDialog.newAdd(saved -> load(false));
        dialog.show(getParentFragmentManager(), TAG_ADD_SUPPLIER);
    }

    private void load(boolean fromSwipe) {
        if (!isAdded()) return;

        if (session == null || repo == null) {
            Log.e(TAG, "Session/Repo not initialized.");
            return;
        }

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            showEmpty("Token empty");
            stopRefreshing();
            return;
        }

        if (!fromSwipe && swipe != null && !swipe.isRefreshing()) showLoading();

        repo.fetchSuppliers(token, new SupplierRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Supplier> list) {
                if (!isAdded()) return;

                Log.i(TAG, "fetchSuppliers SUCCESS count=" + list.size());

                items.clear();
                items.addAll(list);
                if (adapter != null) adapter.notifyDataSetChanged();

                if (items.isEmpty()) showEmpty("No suppliers");
                else showList();

                stopRefreshing();
            }

            @Override
            public void onError(int code, @NonNull String message) {
                if (!isAdded()) return;

                Log.e(TAG, "fetchSuppliers ERROR " + code + " / " + message);
                toast("Fetch failed: " + code);

                if (!items.isEmpty()) showList();
                else showEmpty("Error " + code);

                stopRefreshing();
            }
        });
    }

    private void doDelete(@NonNull Supplier s) {
        if (!isAdded()) return;

        String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            toast("Token empty");
            return;
        }

        showLoading();
        repo.deleteSupplier(token, s.id, new SupplierRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                toast("Deleted");

                int pos = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id == s.id) {
                        pos = i;
                        break;
                    }
                }

                if (pos >= 0) {
                    items.remove(pos);
                    if (adapter != null) adapter.notifyItemRemoved(pos);
                } else {
                    if (adapter != null) adapter.notifyDataSetChanged();
                }

                if (items.isEmpty()) showEmpty("No suppliers");
                else showList();
            }

            @Override
            public void onError(int code, @NonNull String message) {
                if (!isAdded()) return;
                toast("Delete failed: " + code);
                if (!items.isEmpty()) showList();
                else showEmpty("Error " + code);
            }
        });
    }

    private void stopRefreshing() {
        if (swipe != null) swipe.setRefreshing(false);
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
}