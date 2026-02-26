package com.example.valdker.ui.customers;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Customer;
import com.example.valdker.repositories.CustomerRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class CustomerFormDialog extends DialogFragment {

    public interface DoneCallback {
        void onDone(@NonNull Customer saved);
    }

    private static final String ARG_MODE = "mode";
    private static final String ARG_ID = "id";
    private static final String ARG_NAME = "name";
    private static final String ARG_CELL = "cell";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_ADDRESS = "address";

    private static final String MODE_ADD = "add";
    private static final String MODE_EDIT = "edit";

    private DoneCallback cb;

    public static CustomerFormDialog newAdd(@NonNull DoneCallback cb) {
        CustomerFormDialog f = new CustomerFormDialog();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, MODE_ADD);
        f.setArguments(b);
        f.cb = cb;
        return f;
    }

    public static CustomerFormDialog newEdit(@NonNull Customer c, @NonNull DoneCallback cb) {
        CustomerFormDialog f = new CustomerFormDialog();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, MODE_EDIT);
        b.putInt(ARG_ID, c.id);
        b.putString(ARG_NAME, c.name);
        b.putString(ARG_CELL, c.cell);
        b.putString(ARG_EMAIL, c.email);
        b.putString(ARG_ADDRESS, c.address);
        f.setArguments(b);
        f.cb = cb;
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_customer_form, null);

        EditText etName = v.findViewById(R.id.etCustomerName);
        EditText etCell = v.findViewById(R.id.etCustomerCell);
        EditText etEmail = v.findViewById(R.id.etCustomerEmail);
        EditText etAddress = v.findViewById(R.id.etCustomerAddress);
        ProgressBar progress = v.findViewById(R.id.progressCustomerForm);

        Bundle args = getArguments();
        String mode = args != null ? args.getString(ARG_MODE, MODE_ADD) : MODE_ADD;

        if (MODE_EDIT.equals(mode) && args != null) {
            etName.setText(args.getString(ARG_NAME, ""));
            etCell.setText(args.getString(ARG_CELL, ""));
            etEmail.setText(args.getString(ARG_EMAIL, ""));
            etAddress.setText(args.getString(ARG_ADDRESS, ""));
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(MODE_EDIT.equals(mode) ? "Edit Customer" : "Add Customer")
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null);

        androidx.appcompat.app.AlertDialog dialog = b.create();
        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(btn -> {
                        String name = etName.getText().toString().trim();
                        String cell = etCell.getText().toString().trim();
                        String email = etEmail.getText().toString().trim();
                        String address = etAddress.getText().toString().trim();

                        if (name.isEmpty()) { toast("Name required"); return; }
                        if (cell.isEmpty()) { toast("Cell required"); return; }

                        // optional fields -> null if empty
                        if (email.isEmpty()) email = null;
                        if (address.isEmpty()) address = null;

                        SessionManager session = new SessionManager(requireContext());
                        String token = session.getToken();
                        if (token == null || token.trim().isEmpty()) {
                            toast("Token empty");
                            return;
                        }

                        CustomerRepository repo = new CustomerRepository(requireContext());

                        progress.setVisibility(View.VISIBLE);
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                        if (MODE_EDIT.equals(mode) && args != null) {
                            int id = args.getInt(ARG_ID, 0);
                            repo.updateCustomer(token, id, name, cell, email, address, new CustomerRepository.ItemCallback() {
                                @Override public void onSuccess(@NonNull Customer saved) {
                                    progress.setVisibility(View.GONE);
                                    if (cb != null) cb.onDone(saved);
                                    dismiss();
                                }
                                @Override public void onError(int statusCode, @NonNull String message) {
                                    progress.setVisibility(View.GONE);
                                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    toast("Update failed: " + statusCode);
                                }
                            });
                        } else {
                            repo.createCustomer(token, name, cell, email, address, new CustomerRepository.ItemCallback() {
                                @Override public void onSuccess(@NonNull Customer saved) {
                                    progress.setVisibility(View.GONE);
                                    if (cb != null) cb.onDone(saved);
                                    dismiss();
                                }
                                @Override public void onError(int statusCode, @NonNull String message) {
                                    progress.setVisibility(View.GONE);
                                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    toast("Create failed: " + statusCode);
                                }
                            });
                        }
                    });
        });

        return dialog;
    }

    private void toast(@NonNull String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
