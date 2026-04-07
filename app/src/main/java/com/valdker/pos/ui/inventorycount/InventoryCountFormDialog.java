package com.valdker.pos.ui.inventorycount;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.InventoryCount;
import com.valdker.pos.models.InventoryCountItem;
import com.valdker.pos.repositories.InventoryCountRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InventoryCountFormDialog extends DialogFragment {

    private static final String ARG_PRODUCTS_JSON = "products_json";
    private static final String ARG_MODE = "mode"; // "add" | "edit"
    private static final String ARG_EDIT_ID = "edit_id";
    private static final String ARG_EDIT_TITLE = "edit_title";
    private static final String ARG_EDIT_NOTE = "edit_note";
    private static final String ARG_EDIT_ITEMS = "edit_items_json";

    public static InventoryCountFormDialog newAddInstance(@NonNull String productsJsonString) {
        InventoryCountFormDialog f = new InventoryCountFormDialog();
        Bundle b = new Bundle();
        b.putString(ARG_PRODUCTS_JSON, productsJsonString);
        b.putString(ARG_MODE, "add");
        f.setArguments(b);
        return f;
    }

    public static InventoryCountFormDialog newEditInstance(@NonNull String productsJsonString,
                                                           @NonNull InventoryCount ic) {
        InventoryCountFormDialog f = new InventoryCountFormDialog();
        Bundle b = new Bundle();
        b.putString(ARG_PRODUCTS_JSON, productsJsonString);
        b.putString(ARG_MODE, "edit");
        b.putInt(ARG_EDIT_ID, ic.id);
        b.putString(ARG_EDIT_TITLE, ic.title != null ? ic.title : "");
        b.putString(ARG_EDIT_NOTE, ic.note != null ? ic.note : "");

        JSONArray items = new JSONArray();
        try {
            if (ic.items != null) {
                for (InventoryCountItem it : ic.items) {
                    JSONObject o = new JSONObject();
                    o.put("product", it.product);
                    o.put("counted_stock", it.counted_stock);
                    items.put(o);
                }
            }
        } catch (Exception ignored) {
        }

        b.putString(ARG_EDIT_ITEMS, items.toString());
        f.setArguments(b);
        return f;
    }

    private Runnable onSavedListener;
    private DialogInterface.OnDismissListener onDismissListener;

    private EditText etTitle;
    private EditText etNote;
    private RecyclerView rvItems;
    private MaterialButton btnAddRow;
    private MaterialButton btnSave;
    private MaterialButton btnCancel;
    private ProgressBar progress;

    private JSONArray productsJson = new JSONArray();
    private final List<InventoryCountItemDraftAdapter.ItemDraft> drafts = new ArrayList<>();
    private InventoryCountItemDraftAdapter draftAdapter;

    private String mode = "add";
    private int editId = 0;
    private boolean isSubmitting = false;

    public void setOnSavedListener(@Nullable Runnable cb) {
        this.onSavedListener = cb;
    }

    public void setOnDismissListener(@Nullable DialogInterface.OnDismissListener listener) {
        this.onDismissListener = listener;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String rawProducts = "[]";
        if (getArguments() != null) {
            rawProducts = getArguments().getString(ARG_PRODUCTS_JSON, "[]");
            mode = getArguments().getString(ARG_MODE, "add");
            editId = getArguments().getInt(ARG_EDIT_ID, 0);
        }

        try {
            productsJson = new JSONArray(rawProducts);
        } catch (Exception e) {
            productsJson = new JSONArray();
        }

        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_inventory_count_form, null, false);

        android.widget.TextView tvTitle = v.findViewById(R.id.tvTitleInventoryCountForm);
        etTitle = v.findViewById(R.id.etTitle);
        etNote = v.findViewById(R.id.etNote);
        rvItems = v.findViewById(R.id.rvItems);
        btnAddRow = v.findViewById(R.id.btnAddRow);
        btnSave = v.findViewById(R.id.btnSave);
        btnCancel = v.findViewById(R.id.btnCancel);
        progress = v.findViewById(R.id.progress);

        tvTitle.setText("edit".equals(mode) ? "Edit Stock Count" : "New Stock Count");

        rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        draftAdapter = new InventoryCountItemDraftAdapter(productsJson, drafts, position -> {
            int pos = position;
            if (pos < 0 || pos >= drafts.size()) return;

            drafts.remove(pos);
            draftAdapter.notifyItemRemoved(pos);

            if (drafts.isEmpty()) {
                drafts.add(new InventoryCountItemDraftAdapter.ItemDraft());
                draftAdapter.notifyItemInserted(0);
            }
        });
        rvItems.setAdapter(draftAdapter);

        if ("edit".equals(mode)) {
            prefillEdit();
        } else {
            drafts.clear();
            drafts.add(new InventoryCountItemDraftAdapter.ItemDraft());
            draftAdapter.notifyItemInserted(0);
        }

        btnAddRow.setOnClickListener(x -> {
            if (isSubmitting) return;
            drafts.add(new InventoryCountItemDraftAdapter.ItemDraft());
            draftAdapter.notifyItemInserted(drafts.size() - 1);
            rvItems.smoothScrollToPosition(drafts.size() - 1);
        });

        btnCancel.setOnClickListener(x -> {
            if (isSubmitting) return;
            dismissAllowingStateLoss();
        });

        btnSave.setOnClickListener(x -> {
            if (isSubmitting) return;
            if ("edit".equals(mode)) {
                submitUpdate();
            } else {
                submitCreate();
            }
        });

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .create();

        dialog.setCanceledOnTouchOutside(!isSubmitting);
        return dialog;
    }

    private void prefillEdit() {
        if (getArguments() == null) return;

        etTitle.setText(getArguments().getString(ARG_EDIT_TITLE, ""));
        etNote.setText(getArguments().getString(ARG_EDIT_NOTE, ""));

        String rawItems = getArguments().getString(ARG_EDIT_ITEMS, "[]");
        JSONArray arr;
        try {
            arr = new JSONArray(rawItems);
        } catch (Exception e) {
            arr = new JSONArray();
        }

        drafts.clear();

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                InventoryCountItemDraftAdapter.ItemDraft d = new InventoryCountItemDraftAdapter.ItemDraft();
                d.productId = o.optInt("product", 0);
                d.countedStock = o.optInt("counted_stock", 0);
                drafts.add(d);
            } catch (Exception ignored) {
            }
        }

        if (drafts.isEmpty()) {
            drafts.add(new InventoryCountItemDraftAdapter.ItemDraft());
        }
        draftAdapter.notifyDataSetChanged();
    }

    private void setLoading(boolean loading) {
        isSubmitting = loading;

        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnSave != null) btnSave.setEnabled(!loading);
        if (btnAddRow != null) btnAddRow.setEnabled(!loading);
        if (btnCancel != null) btnCancel.setEnabled(!loading);

        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.setCanceledOnTouchOutside(!loading);
            dialog.setCancelable(!loading);
        }
        setCancelable(!loading);
    }

    private JSONObject buildPayloadOrNull() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Required");
            etTitle.requestFocus();
            return null;
        }

        JSONArray items = new JSONArray();

        for (int i = 0; i < drafts.size(); i++) {
            InventoryCountItemDraftAdapter.ItemDraft d = drafts.get(i);

            if (d.productId <= 0) {
                Toast.makeText(requireContext(),
                        "Select a product in row " + (i + 1),
                        Toast.LENGTH_SHORT).show();
                return null;
            }

            if (d.countedStock < 0) {
                Toast.makeText(requireContext(),
                        "Qty cannot be negative (line " + (i + 1) + ")",
                        Toast.LENGTH_SHORT).show();
                return null;
            }

            JSONObject it = new JSONObject();
            try {
                it.put("product", d.productId);
                it.put("counted_stock", d.countedStock);
                items.put(it);
            } catch (Exception ignored) {
            }
        }

        if (items.length() == 0) {
            Toast.makeText(requireContext(), "Add at least 1 item", Toast.LENGTH_SHORT).show();
            return null;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("title", title);
            payload.put("note", note);
            payload.put("items", items);
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Payload error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return null;
        }

        return payload;
    }

    private void submitCreate() {
        if (isSubmitting) return;

        JSONObject payload = buildPayloadOrNull();
        if (payload == null) return;

        setLoading(true);

        InventoryCountRepository.create(requireContext(), payload, new InventoryCountRepository.CreateCallback() {
            @Override
            public void onSuccess(@NonNull InventoryCount created) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
                if (onSavedListener != null) onSavedListener.run();
                dismissAllowingStateLoss();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submitUpdate() {
        if (isSubmitting) return;

        if (editId <= 0) {
            Toast.makeText(requireContext(), "Invalid item", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = buildPayloadOrNull();
        if (payload == null) return;

        setLoading(true);

        InventoryCountRepository.update(requireContext(), editId, payload, new InventoryCountRepository.UpdateCallback() {
            @Override
            public void onSuccess(@NonNull InventoryCount updated) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show();
                if (onSavedListener != null) onSavedListener.run();
                dismissAllowingStateLoss();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}