package com.example.valdker.ui.stockadjustments;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.valdker.R;
import com.example.valdker.models.StockAdjustment;
import com.example.valdker.repositories.StockAdjustmentRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StockAdjustmentFormDialog extends DialogFragment {

    public interface DoneCallback {
        void onDone(); // refresh list
    }

    private Spinner spProduct, spReason;
    private EditText etOld, etNew, etNote;

    // data injection
    private JSONArray productsJson;          // [{"id":..,"name":..,"stock":..}, ...]
    private Integer editId = null;           // null = create
    private JSONObject editObj = null;       // data existing

    private DoneCallback callback;

    public static StockAdjustmentFormDialog create(JSONArray productsJson, DoneCallback cb) {
        StockAdjustmentFormDialog d = new StockAdjustmentFormDialog();
        d.productsJson = productsJson;
        d.callback = cb;
        return d;
    }

    public static StockAdjustmentFormDialog edit(JSONArray productsJson, JSONObject editObj, DoneCallback cb) {
        StockAdjustmentFormDialog d = new StockAdjustmentFormDialog();
        d.productsJson = productsJson;
        d.editObj = editObj;
        d.callback = cb;
        try { d.editId = editObj.getInt("id"); } catch (Exception ignored) {}
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_stock_adjustment, null);

        spProduct = v.findViewById(R.id.spProduct);
        spReason  = v.findViewById(R.id.spReason);
        etOld     = v.findViewById(R.id.etOldStock);
        etNew     = v.findViewById(R.id.etNewStock);
        etNote    = v.findViewById(R.id.etNote);

        etOld.setFocusable(false);
        etOld.setFocusableInTouchMode(false);
        etOld.setClickable(false);
        etOld.setCursorVisible(false);
        etOld.setLongClickable(false);

        setupReasonSpinner();
        setupProductSpinner();

        if (editObj != null) bindEditData();

        String title = (editId == null) ? "Stock Adjustment" : "Edit Stock Adjustment";

        android.widget.TextView tvTitle = v.findViewById(R.id.tvTitleStockAdjustment);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setNegativeButton("Cancel", (d, which) -> dismiss())
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            View positiveBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            View negativeBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);

            if (positiveBtn instanceof android.widget.TextView) {
                ((android.widget.TextView) positiveBtn).setTextColor(0xFF22C55E);
            }
            if (negativeBtn instanceof android.widget.TextView) {
                ((android.widget.TextView) negativeBtn).setTextColor(0xFF22C55E);
            }
            if (positiveBtn != null && positiveBtn.getParent() instanceof View) {
                ((View) positiveBtn.getParent()).setBackgroundColor(0xFFF9FAFB);
            }
        });

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dlg = getDialog();
        if (dlg == null) return;

        dlg.findViewById(android.R.id.button1).setOnClickListener(btn -> onSave());
    }

    private void setupReasonSpinner() {
        String[] reasons = new String[] {
                "CORRECTION",
                "LOST",
                "DAMAGED",
                "EXPIRED",
                "OTHER"
        };
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, reasons);
        spReason.setAdapter(ad);
    }

    private void setupProductSpinner() {
        List<String> names = new ArrayList<>();
        if (productsJson != null) {
            for (int i = 0; i < productsJson.length(); i++) {
                try {
                    JSONObject p = productsJson.getJSONObject(i);
                    names.add(p.optString("name", "Product #" + p.optInt("id")));
                } catch (Exception ignored) {}
            }
        }

        ArrayAdapter<String> ad = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
        );
        spProduct.setAdapter(ad);

        spProduct.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (productsJson != null && position >= 0 && position < productsJson.length()) {
                        JSONObject p = productsJson.getJSONObject(position);
                        int stock = p.optInt("stock", 0);
                        etOld.setText(String.valueOf(stock));
                    } else {
                        etOld.setText("");
                    }
                } catch (Exception e) {
                    etOld.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                etOld.setText("");
            }
        });
    }

    private void bindEditData() {
        try {
            int productId = editObj.optInt("product");
            int oldStock  = editObj.optInt("old_stock");
            int newStock  = editObj.optInt("new_stock");
            String reason = editObj.optString("reason", "CORRECTION");
            String note   = editObj.optString("note", "");

            // product spinner index by id
            int idx = 0;
            for (int i = 0; i < productsJson.length(); i++) {
                JSONObject p = productsJson.getJSONObject(i);
                if (p.optInt("id") == productId) { idx = i; break; }
            }
            spProduct.setSelection(idx);

            etOld.setText(String.valueOf(oldStock));
            etNew.setText(String.valueOf(newStock));
            etNote.setText(note);

            // reason spinner
            ArrayAdapter<String> ad = (ArrayAdapter<String>) spReason.getAdapter();
            int rPos = 0;
            for (int i = 0; i < ad.getCount(); i++) {
                if (ad.getItem(i).equalsIgnoreCase(reason)) { rPos = i; break; }
            }
            spReason.setSelection(rPos);

        } catch (Exception ignored) {}
    }

    private void onSave() {
        try {
            int pIndex = spProduct.getSelectedItemPosition();
            if (pIndex < 0 || productsJson == null || productsJson.length() == 0) {
                Toast.makeText(requireContext(), "Product must be selected", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject p = productsJson.getJSONObject(pIndex);
            int productId = p.getInt("id");

            String sOld = etOld.getText().toString().trim();
            String sNew = etNew.getText().toString().trim();
            String reason = String.valueOf(spReason.getSelectedItem());
            String note = etNote.getText().toString().trim();

            if (TextUtils.isEmpty(sNew)) {
                Toast.makeText(requireContext(), "New stock must be filled in", Toast.LENGTH_SHORT).show();
                return;
            }

            int oldStock = Integer.parseInt(sOld);
            int newStock = Integer.parseInt(sNew);

            JSONObject payload = new JSONObject();
            payload.put("product", productId);
            payload.put("old_stock", oldStock);
            payload.put("new_stock", newStock);
            payload.put("reason", reason);
            payload.put("note", note);

            if (editId == null) {
                StockAdjustmentRepository.create(
                        requireContext(),
                        payload,
                        new StockAdjustmentRepository.ObjectCallback() {
                            @Override
                            public void onSuccess(StockAdjustment obj) {
                                Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
                                if (callback != null) callback.onDone();
                                dismiss();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            } else {
                StockAdjustmentRepository.update(
                        requireContext(),
                        editId,
                        payload,
                        new StockAdjustmentRepository.ObjectCallback() {
                            @Override
                            public void onSuccess(StockAdjustment obj) {
                                Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show();
                                if (callback != null) callback.onDone();
                                dismiss();
                            }

                            @Override
                            public void onError(String msg) {
                                Toast.makeText(requireContext(), "Error: " + msg, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            }

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Invalid input", Toast.LENGTH_SHORT).show();
        }
    }
}