package com.example.valdker.ui.suppliers;

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

        EditText etName = v.findViewById(R.id.etSupplierName);
        EditText etContact = v.findViewById(R.id.etSupplierContact);
        EditText etCell = v.findViewById(R.id.etSupplierCell);
        EditText etEmail = v.findViewById(R.id.etSupplierEmail);
        EditText etAddress = v.findViewById(R.id.etSupplierAddress);
        ProgressBar progress = v.findViewById(R.id.progressSupplierForm);

        Bundle args = getArguments();
        String mode = args != null ? args.getString(ARG_MODE, MODE_ADD) : MODE_ADD;

        if (MODE_EDIT.equals(mode) && args != null) {
            etName.setText(args.getString(ARG_NAME, ""));
            etContact.setText(args.getString(ARG_CONTACT, ""));
            etCell.setText(args.getString(ARG_CELL, ""));
            etEmail.setText(args.getString(ARG_EMAIL, ""));
            etAddress.setText(args.getString(ARG_ADDRESS, ""));
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(MODE_EDIT.equals(mode) ? "Edit Supplier" : "Add Supplier")
                .setView(v)
                .setNegativeButton("Cancel", (d, w) -> dismiss())
                .setPositiveButton("Save", null);

        androidx.appcompat.app.AlertDialog dialog = b.create();
        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(btn -> {

                        String name = etName.getText().toString().trim();
                        String contact = etContact.getText().toString().trim();
                        String cell = etCell.getText().toString().trim();
                        String email = etEmail.getText().toString().trim();
                        String address = etAddress.getText().toString().trim();

                        if (name.isEmpty()) { toast("Name required"); return; }
                        if (contact.isEmpty()) { toast("Contact person required"); return; }
                        if (cell.isEmpty()) { toast("Cell required"); return; }

                        if (email.isEmpty()) email = null;
                        if (address.isEmpty()) address = null;

                        SessionManager session = new SessionManager(requireContext());
                        String token = session.getToken();
                        if (token == null || token.trim().isEmpty()) {
                            toast("Token empty");
                            return;
                        }

                        SupplierRepository repo = new SupplierRepository(requireContext());

                        progress.setVisibility(View.VISIBLE);
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                        if (MODE_EDIT.equals(mode) && args != null) {
                            int id = args.getInt(ARG_ID, 0);
                            repo.updateSupplier(token, id, name, contact, cell, email, address, new SupplierRepository.ItemCallback() {
                                @Override public void onSuccess(@NonNull Supplier saved) {
                                    progress.setVisibility(View.GONE);
                                    if (cb != null) cb.onDone(saved);
                                    dismiss();
                                }
                                @Override public void onError(int code, @NonNull String message) {
                                    progress.setVisibility(View.GONE);
                                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    toast("Update failed: " + code);
                                }
                            });
                        } else {
                            repo.createSupplier(token, name, contact, cell, email, address, new SupplierRepository.ItemCallback() {
                                @Override public void onSuccess(@NonNull Supplier saved) {
                                    progress.setVisibility(View.GONE);
                                    if (cb != null) cb.onDone(saved);
                                    dismiss();
                                }
                                @Override public void onError(int code, @NonNull String message) {
                                    progress.setVisibility(View.GONE);
                                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    toast("Create failed: " + code);
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
