package com.example.valdker.ui.customers;

import android.os.Bundle;
import android.os.SystemClock;
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
import com.example.valdker.base.BaseFragment;
import com.example.valdker.models.Customer;
import com.example.valdker.repositories.CustomerRepository;
import com.example.valdker.utils.InsetsHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CustomersFragment extends BaseFragment {

    private static final String TAG = "CUSTOMERS";
    private static final String TAG_ADD_CUSTOMER = "add_customer";
    private static final String TAG_EDIT_CUSTOMER = "edit_customer";
    private static final long CLICK_GUARD_MS = 700L;

    private TextView tvTitle;
    private android.widget.EditText etSearchCustomer;
    private android.widget.ImageView btnBack;
    private android.widget.ImageView ivHeaderAction;

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
    private long lastRowActionTime = 0L;

    private boolean isLoading = false;
    private boolean isDialogOpening = false;
    private boolean isDeleteRunning = false;

    public CustomersFragment() {
        super(R.layout.fragment_customers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        applyTopInset(view.findViewById(R.id.topBar));

        session = new SessionManager(requireContext());
        repo = new CustomerRepository(requireContext());

        swipe = view.findViewById(R.id.swipeRefreshCustomers);
        rv = view.findViewById(R.id.rvCustomers);
        progress = view.findViewById(R.id.progressCustomers);
        tvEmpty = view.findViewById(R.id.tvEmptyCustomers);
        fabAdd = view.findViewById(R.id.fabAddCustomer);

        tvTitle = view.findViewById(R.id.tvTitleCustomers);
        etSearchCustomer = view.findViewById(R.id.etSearchCustomer);
        btnBack = view.findViewById(R.id.btnBack);
        ivHeaderAction = view.findViewById(R.id.ivHeaderAction);

        if (tvTitle != null) {
            tvTitle.setText("Customers");
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        if (ivHeaderAction != null) {
            ivHeaderAction.setOnClickListener(v -> load(false));
        }

        if (swipe == null) Log.w(TAG, "swipeRefreshCustomers not found.");
        if (rv == null) Log.w(TAG, "rvCustomers not found.");
        if (progress == null) Log.w(TAG, "progressCustomers not found.");
        if (tvEmpty == null) Log.w(TAG, "tvEmptyCustomers not found.");
        if (fabAdd == null) Log.w(TAG, "fabAddCustomer not found.");

        InsetsHelper.applyRecyclerBottomInsets(view, rv, TAG);
        applyFabBottomInset(fabAdd, 56);

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setHasFixedSize(false);
            rv.setClipToPadding(false);
        }

        adapter = new CustomerAdapter(items, new CustomerAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Customer c) {
                if (!canRunRowAction()) return;
                openEditCustomerDialogSafely(c);
            }

            @Override
            public void onDelete(@NonNull Customer c) {
                if (!canRunRowAction()) return;
                confirmDeleteSafely(c);
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
            fabAdd.setOnClickListener(v -> openAddCustomerDialogSafely());

            fabAdd.post(() -> {
                if (fabAdd == null) return;
                fabAdd.bringToFront();
                fabAdd.setElevation(100f);
                fabAdd.setTranslationZ(100f);
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

    private void openAddCustomerDialogSafely() {
        if (!isAdded()) return;
        if (isRapidFabClick()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        if (getParentFragmentManager().findFragmentByTag(TAG_ADD_CUSTOMER) != null) {
            Log.d(TAG, "Add customer dialog already showing");
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        CustomerFormDialog dialog = CustomerFormDialog.newAdd(saved -> {
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

        dialog.show(getParentFragmentManager(), TAG_ADD_CUSTOMER);
    }

    private void openEditCustomerDialogSafely(@NonNull Customer c) {
        if (!isAdded()) return;
        if (isDialogOpening) return;
        if (isStateSaved()) return;

        if (getParentFragmentManager().findFragmentByTag(TAG_EDIT_CUSTOMER) != null) {
            Log.d(TAG, "Edit customer dialog already showing");
            return;
        }

        isDialogOpening = true;
        setFabEnabled(false);

        CustomerFormDialog dialog = CustomerFormDialog.newEdit(c, saved -> {
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

        dialog.show(getParentFragmentManager(), TAG_EDIT_CUSTOMER);
    }

    private void confirmDeleteSafely(@NonNull Customer c) {
        if (!isAdded()) return;
        if (isDeleteRunning) return;

        ConfirmDeleteDialog.show(
                requireContext(),
                "Delete customer?",
                c.name + " (" + c.cell + ")",
                () -> {
                    if (isDeleteRunning) return;
                    doDelete(c);
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

        repo.fetchCustomers(token, new CustomerRepository.ListCallback() {
            @Override
            public void onSuccess(@NonNull List<Customer> customers) {
                if (!isAdded()) return;

                isLoading = false;

                Log.i(TAG, "fetchCustomers SUCCESS count=" + customers.size());

                items.clear();
                items.addAll(customers);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (items.isEmpty()) {
                    showEmpty("No customers");
                } else {
                    showList();
                }

                stopRefreshing();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                isLoading = false;

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
        if (isDeleteRunning) return;

        String token = session != null ? session.getToken() : null;
        if (token == null || token.trim().isEmpty()) {
            toast("Token empty");
            return;
        }

        isDeleteRunning = true;
        showLoading();

        repo.deleteCustomer(token, c.id, new CustomerRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                isDeleteRunning = false;
                toast("Deleted");

                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).id == c.id) {
                        items.remove(i);
                        if (adapter != null) {
                            adapter.notifyItemRemoved(i);
                        }
                        break;
                    }
                }

                if (items.isEmpty()) {
                    showEmpty("No customers");
                } else {
                    showList();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;

                isDeleteRunning = false;
                toast("Delete failed: " + statusCode);

                if (!items.isEmpty()) {
                    showList();
                } else {
                    showEmpty("No customers");
                }
            }
        });
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        swipe = null;
        rv = null;
        progress = null;
        tvEmpty = null;
        fabAdd = null;
    }
}