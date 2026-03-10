package com.example.valdker.ui.suppliers;

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

import com.example.valdker.R;
import com.example.valdker.SessionManager;
import com.example.valdker.models.Supplier;
import com.example.valdker.repositories.SupplierRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SupplierFormDialog extends DialogFragment {

    public interface DoneCallback {
        void onDone(@NonNull Supplier saved);
    }

    private static final String ARG_MODE = "mode";
    private static final String ARG_ID = "id";
    private static final String ARG_NAME = "name";
    private static final String ARG_CONTACT = "contact_person";
    private static final String ARG_CELL = "cell";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_ADDRESS = "address";

    private static final String MODE_ADD = "add";
    private static final String MODE_EDIT = "edit";

    private DoneCallback cb;

    public static SupplierFormDialog newAdd(@NonNull DoneCallback cb) {
        SupplierFormDialog f = new SupplierFormDialog();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, MODE_ADD);
        f.setArguments(b);
        f.cb = cb;
        return f;
    }

    public static SupplierFormDialog newEdit(@NonNull Supplier s, @NonNull DoneCallback cb) {
        SupplierFormDialog f = new SupplierFormDialog();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, MODE_EDIT);
        b.putInt(ARG_ID, s.id);
        b.putString(ARG_NAME, s.name);
        b.putString(ARG_CONTACT, s.contactPerson);
        b.putString(ARG_CELL, s.cell);
        b.putString(ARG_EMAIL, s.email);
        b.putString(ARG_ADDRESS, s.address);
        f.setArguments(b);
        f.cb = cb;
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_supplier_form, null);

        TextView tvTitle = v.findViewById(R.id.tvTitleSupplierForm);
        EditText etName = v.findViewById(R.id.etSupplierName);
        EditText etContact = v.findViewById(R.id.etSupplierContact);
        EditText etCell = v.findViewById(R.id.etSupplierCell);
        EditText etEmail = v.findViewById(R.id.etSupplierEmail);
        EditText etAddress = v.findViewById(R.id.etSupplierAddress);
        ProgressBar progress = v.findViewById(R.id.progressSupplierForm);

        Bundle args = getArguments();
        final String mode = args != null ? args.getString(ARG_MODE, MODE_ADD) : MODE_ADD;
        final boolean isEdit = MODE_EDIT.equals(mode);

        tvTitle.setText(isEdit ? "Edit Supplier" : "Add Supplier");

        if (isEdit && args != null) {
            etName.setText(valueOrEmpty(args.getString(ARG_NAME)));
            etContact.setText(valueOrEmpty(args.getString(ARG_CONTACT)));
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

            if (positiveButton != null) {
                positiveButton.setAlpha(1f);
            }

            if (negativeButton != null) {
                negativeButton.setAlpha(1f);
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF22C55E);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF22C55E);

            View buttonBar = (View) dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent();
            buttonBar.setBackgroundColor(0xFFF9FAFB);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String name = getTrimmed(etName);
                String contact = getTrimmed(etContact);
                String cell = getTrimmed(etCell);
                String email = emptyToNull(getTrimmed(etEmail));
                String address = emptyToNull(getTrimmed(etAddress));

                if (TextUtils.isEmpty(name)) {
                    etName.setError("Name required");
                    etName.requestFocus();
                    return;
                }

                if (TextUtils.isEmpty(contact)) {
                    etContact.setError("Contact person required");
                    etContact.requestFocus();
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
                etContact.setError(null);
                etCell.setError(null);

                setSavingState(progress, positiveButton, true);

                SupplierRepository repo = new SupplierRepository(requireContext());

                SupplierRepository.ItemCallback callback = new SupplierRepository.ItemCallback() {
                    @Override
                    public void onSuccess(@NonNull Supplier saved) {
                        if (!isAdded()) return;
                        setSavingState(progress, positiveButton, false);
                        if (cb != null) cb.onDone(saved);
                        dismissAllowingStateLoss();
                    }

                    @Override
                    public void onError(int code, @NonNull String message) {
                        if (!isAdded()) return;
                        setSavingState(progress, positiveButton, false);
                        toast(buildErrorMessage(isEdit, code, message));
                    }
                };

                if (isEdit && args != null) {
                    int id = args.getInt(ARG_ID, 0);
                    if (id <= 0) {
                        setSavingState(progress, positiveButton, false);
                        toast("Invalid supplier ID");
                        return;
                    }

                    repo.updateSupplier(token, id, name, contact, cell, email, address, callback);
                } else {
                    repo.createSupplier(token, name, contact, cell, email, address, callback);
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