package com.valdker.pos.ui.customers;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.models.Customer;
import com.valdker.pos.repositories.CustomerRepository;
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

        TextView tvTitle = v.findViewById(R.id.tvTitleCustomerForm);
        EditText etName = v.findViewById(R.id.etCustomerName);
        EditText etCell = v.findViewById(R.id.etCustomerCell);
        EditText etEmail = v.findViewById(R.id.etCustomerEmail);
        EditText etAddress = v.findViewById(R.id.etCustomerAddress);
        ProgressBar progress = v.findViewById(R.id.progressCustomerForm);

        Bundle args = getArguments();
        final String mode = args != null ? args.getString(ARG_MODE, MODE_ADD) : MODE_ADD;
        final boolean isEdit = MODE_EDIT.equals(mode);

        tvTitle.setText(
                isEdit
                        ? getString(R.string.title_edit_customer)
                        : getString(R.string.title_add_customer)
        );

        if (isEdit && args != null) {
            etName.setText(valueOrEmpty(args.getString(ARG_NAME)));
            etCell.setText(valueOrEmpty(args.getString(ARG_CELL)));
            etEmail.setText(valueOrEmpty(args.getString(ARG_EMAIL)));
            etAddress.setText(valueOrEmpty(args.getString(ARG_ADDRESS)));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dlg -> {
            final View positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            final View negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            View buttonBar = (View) positiveButton.getParent();
            buttonBar.setBackgroundColor(0xFFF9FAFB);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String name = getTrimmed(etName);
                String cell = getTrimmed(etCell);
                String email = emptyToNull(getTrimmed(etEmail));
                String address = emptyToNull(getTrimmed(etAddress));

                if (TextUtils.isEmpty(name)) {
                    etName.setError("Name required");
                    etName.requestFocus();
                    return;
                }

                if (TextUtils.isEmpty(cell)) {
                    etCell.setError("Cell required");
                    etCell.requestFocus();
                    return;
                }

                SessionManager session = new SessionManager(requireContext());
                String token = session.getToken();
                if (TextUtils.isEmpty(token)) {
                    toast("Session expired. Please login again.");
                    return;
                }

                etName.setError(null);
                etCell.setError(null);

                setSavingState(progress, positiveButton, true);

                CustomerRepository repo = new CustomerRepository(requireContext());

                CustomerRepository.ItemCallback callback = new CustomerRepository.ItemCallback() {
                    @Override
                    public void onSuccess(@NonNull Customer saved) {
                        if (!isAdded()) return;
                        setSavingState(progress, positiveButton, false);
                        if (cb != null) cb.onDone(saved);
                        dismissAllowingStateLoss();
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String message) {
                        if (!isAdded()) return;
                        setSavingState(progress, positiveButton, false);
                        toast(buildErrorMessage(isEdit, statusCode, message));
                    }
                };

                if (isEdit && args != null) {
                    int id = args.getInt(ARG_ID, 0);
                    if (id <= 0) {
                        setSavingState(progress, positiveButton, false);
                        toast("Invalid customer ID");
                        return;
                    }

                    repo.updateCustomer(token, id, name, cell, email, address, callback);
                } else {
                    repo.createCustomer(token, name, cell, email, address, callback);
                }
            });
        });

        return dialog;
    }

    private void setSavingState(@NonNull ProgressBar progress, @Nullable View positiveButton, boolean saving) {
        progress.setVisibility(saving ? View.VISIBLE : View.GONE);
        if (positiveButton != null) {
            positiveButton.setEnabled(!saving);
            positiveButton.setAlpha(saving ? 0.6f : 1f);
        }
    }

    @NonNull
    private String getTrimmed(@Nullable EditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        return TextUtils.isEmpty(value) ? null : value;
    }

    @NonNull
    private String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NonNull
    private String buildErrorMessage(boolean isEdit, int statusCode, @Nullable String message) {
        String action = isEdit ? "Update" : "Create";

        if (!TextUtils.isEmpty(message)) {
            return action + " failed: " + message;
        }

        if (statusCode > 0) {
            return action + " failed (" + statusCode + ")";
        }

        return action + " failed";
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}