package com.valdker.pos.workshop;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.valdker.pos.utils.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.valdker.pos.SessionManager;
import com.valdker.pos.models.Customer;
import com.valdker.pos.repositories.CustomerRepository;
import com.valdker.pos.repositories.MasterDataRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class CustomerPickerDialog extends DialogFragment {
    private static final String ARG_ALLOW_WALK_IN = "allow_walk_in";
    private static final String ARG_ALLOW_CLEAR = "allow_clear";

    public interface Listener {
        void onSelected(@NonNull Customer customer);
    }

    private Listener listener;

    private SessionManager sessionManager;
    private CustomerRepository customerRepository;
    private MasterDataRepository masterDataRepository;
    private boolean offlineNoticeShown = false;
    private boolean noLocalDataNoticeShown = false;

    private final List<Customer> customerList = new ArrayList<>();
    private final List<String> displayItems = new ArrayList<>();

    private ArrayAdapter<String> adapter;
    private AlertDialog dialog;

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public static CustomerPickerDialog newInstance() {
        return newInstance(true, false);
    }

    public static CustomerPickerDialog newInstance(boolean allowWalkIn, boolean allowClear) {
        CustomerPickerDialog dialog = new CustomerPickerDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_ALLOW_WALK_IN, allowWalkIn);
        args.putBoolean(ARG_ALLOW_CLEAR, allowClear);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        sessionManager = new SessionManager(requireContext());
        customerRepository = new CustomerRepository(requireContext());
        masterDataRepository = new MasterDataRepository(requireContext());

        ListView listView = new ListView(requireContext());
        listView.setDividerHeight(1);
        listView.setPadding(0, 16, 0, 0);

        displayItems.clear();
        displayItems.add("Loading customers...");

        adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                displayItems
        );

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= customerList.size()) {
                return;
            }

            Customer selected = customerList.get(position);

            if (listener != null) {
                listener.onSelected(selected);
            }

            dismissAllowingStateLoss();
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Customer")
                .setNegativeButton("Close", null)
                .setView(listView);

        Bundle args = getArguments();
        boolean allowWalkIn = args == null || args.getBoolean(ARG_ALLOW_WALK_IN, true);
        boolean allowClear = args != null && args.getBoolean(ARG_ALLOW_CLEAR, false);
        if (allowWalkIn) {
            builder.setNeutralButton("Walk-in", (d, which) -> {
                if (listener != null) {
                    listener.onSelected(new Customer(
                            0,
                            "Walk-in Customer",
                            "",
                            null,
                            null,
                            0L
                    ));
                }
            });
        } else if (allowClear) {
            builder.setNeutralButton("Clear", (d, which) -> {
                if (listener != null) {
                    listener.onSelected(new Customer(
                            0,
                            "",
                            "",
                            null,
                            null,
                            0L
                    ));
                }
            });
        }

        dialog = builder.create();

        loadCustomers();

        return dialog;
    }

    private void loadCustomers() {
        if (!isAdded()) return;

        String token = sessionManager.getToken();
        if (TextUtils.isEmpty(token)) {
            showErrorState("Token login tidak ditemukan");
            Toast.makeText(requireContext(), "Token login tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        masterDataRepository.loadCustomersRoomFirst(token, new MasterDataRepository.CustomersCallback() {
            @Override
            public void onLocalCustomers(@NonNull List<Customer> customers) {
                if (!isAdded()) return;
                renderCustomers(customers);
            }

            @Override
            public void onRemoteCustomers(@NonNull List<Customer> customers) {
                if (!isAdded()) return;
                offlineNoticeShown = false;
                noLocalDataNoticeShown = false;
                renderCustomers(customers);
            }

            @Override
            public void onNoInternet(@NonNull List<Customer> localCustomers) {
                if (!isAdded()) return;
                if (localCustomers.isEmpty() && customerList.isEmpty()) {
                    showErrorState(MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA);
                    if (!noLocalDataNoticeShown) {
                        noLocalDataNoticeShown = true;
                        Toast.makeText(requireContext(), MasterDataRepository.MESSAGE_NO_LOCAL_POS_DATA, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!offlineNoticeShown) {
                    offlineNoticeShown = true;
                    Toast.makeText(requireContext(), MasterDataRepository.MESSAGE_NO_INTERNET_SHOWING_LOCAL, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                if (!isAdded()) return;
                if (customerList.isEmpty()) {
                    showErrorState("Failed load customer");
                    Toast.makeText(
                            requireContext(),
                            "Gagal memuat customer: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });
    }

    private void renderCustomers(@NonNull List<Customer> customers) {
        customerList.clear();
        displayItems.clear();

        if (customers.isEmpty()) {
            displayItems.add("No customer found");
        } else {
            customerList.addAll(customers);

            for (Customer c : customers) {
                StringBuilder line = new StringBuilder();
                line.append(c.name);

                if (!TextUtils.isEmpty(c.cell)) {
                    line.append("\n").append(c.cell);
                } else if (!TextUtils.isEmpty(c.email)) {
                    line.append("\n").append(c.email);
                }
                displayItems.add(line.toString());
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showErrorState(@NonNull String message) {
        customerList.clear();
        displayItems.clear();
        displayItems.add(message);

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
