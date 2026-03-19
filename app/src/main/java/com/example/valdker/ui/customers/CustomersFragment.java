package com.example.valdker.ui.customers;

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
import com.example.valdker.models.Customer;
import com.example.valdker.repositories.CustomerRepository;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CustomersFragment extends Fragment {

    private static final String TAG = "CUSTOMERS";
    private static final String TAG_ADD_CUSTOMER = "add_customer";
    private static final long FAB_CLICK_DEBOUNCE_MS = 700L;

    private SwipeRefreshLayout swipe;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView tvEmpty;
    private FloatingActionButton fabAdd;

    private final List<Customer> items = new ArrayList<>();
    private CustomerAdapter adapter;

    private SessionManager session;
    private CustomerRepository repo;

    private long lastFabClickTime = 0L;

    public CustomersFragment() {
        super(R.layout.fragment_customers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());
        repo = new CustomerRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefreshCustomers);
        rv = view.findViewById(R.id.rvCustomers);
        progress = view.findViewById(R.id.progressCustomers);
        tvEmpty = view.findViewById(R.id.tvEmptyCustomers);
        fabAdd = view.findViewById(R.id.fabAddCustomer);

        if (swipe == null) Log.w(TAG, "swipeRefreshCustomers not found.");
        if (rv == null) Log.w(TAG, "rvCustomers not found.");
        if (progress == null) Log.w(TAG, "progressCustomers not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmptyCustomers not found.");
        if (fabAdd == null) Log.w(TAG, "fabAddCustomer not found.");

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);
        InsetsHelper.applyFabMarginInsets(fabAdd, 16, TAG);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        }

        adapter = new CustomerAdapter(items, new CustomerAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Customer c) {
                CustomerFormDialog.newEdit(c, (saved) -> load(false))
                        .show(getParentFragmentManager(), "edit_customer");
            }

            @Override
            public void onDelete(@NonNull Customer c) {
                ConfirmDeleteDialog.show(
                        requireContext(),
                        "Delete customer?",
                        c.name + " (" + c.cell + ")",
                        () -> doDelete(c)
                );
            }
        });

        if (rv != null) rv.setAdapter(adapter);

        if (swipe != null) swipe.setOnRefreshListener(() -> load(true));

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> openAddCustomerDialogSafely());
        }

        load(false);
    }

    private void openAddCustomerDialogSafely() {
        if (!isAdded()) return;

        long now = System.currentTimeMillis();
        if (now - lastFabClickTime < FAB_CLICK_DEBOUNCE_MS) {
            Log.d(TAG, "FAB click ignored: too fast");
            return;
        }
        lastFabClickTime = now;

        if (getParentFragmentManager().findFragmentByTag(TAG_ADD_CUSTOMER) != null) {
            Log.d(TAG, "Add customer dialog already showing");
            return;
        }

        if (fabAdd != null) {
            fabAdd.setEnabled(false);
            fabAdd.postDelayed(() -> {
                if (fabAdd != null) fabAdd.setEnabled(true);
            }, FAB_CLICK_DEBOUNCE_MS);
        }

        CustomerFormDialog dialog = CustomerFormDialog.newAdd((saved) -> load(false));
        dialog.show(getParentFragmentManager(), TAG_ADD_CUSTOMER);
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

        repo.fetchCustomers(token, new CustomerRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Customer> customers) {
                if (!isAdded()) return;

                Log.i(TAG, "fetchCustomers SUCCESS count=" + customers.size());

                items.clear();
                items.addAll(customers);

                if (adapter != null) adapter.notifyDataSetChanged();

                if (items.isEmpty()) showEmpty("No customers");
                else showList();

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                Log.e(TAG, "fetchCustomers ERROR " + statusCode + " / " + message);
                toast("Fetch failed: " + statusCode);

                if (!items.isEmpty()) {
                    showList();
                } else {
                    showEmpty("Error " + statusCode);
                }

                stopRefreshing();
            }
        });
    }

    private void doDelete(@NonNull Customer c) {
        if (!isAdded()) return;

        String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            toast("Token empty");
            return;
        }

        showLoading();
        repo.deleteCustomer(token, c.id, new CustomerRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                toast("Deleted");

                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id == c.id) {
                        items.remove(i);
                        adapter.notifyItemRemoved(i);
                        break;
                    }
                }

                if (items.isEmpty()) showEmpty("No customers");
                else showList();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                toast("Delete failed: " + statusCode);
                if (!items.isEmpty()) showList();
                else showEmpty("No customers");
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