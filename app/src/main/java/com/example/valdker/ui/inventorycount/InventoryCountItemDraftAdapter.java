package com.example.valdker.ui.inventorycount;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InventoryCountItemDraftAdapter extends RecyclerView.Adapter<InventoryCountItemDraftAdapter.VH> {

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    private final JSONArray productsJson;
    private final List<ItemDraft> drafts;
    private final OnRemoveListener removeListener;

    public static class ItemDraft {
        public int productId = 0;
        public int countedStock = 0;
    }

    public InventoryCountItemDraftAdapter(JSONArray productsJson, List<ItemDraft> drafts, OnRemoveListener removeListener) {
        this.productsJson = productsJson;
        this.drafts = drafts;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_inventory_count_item_draft, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ItemDraft d = drafts.get(position);

        // Defensive: if layout is wrong, show message and stop binding to avoid crash
        if (h.spProduct == null || h.etCounted == null || h.btnRemove == null) {
            Toast.makeText(h.itemView.getContext(),
                    "Layout row_inventory_count_item_draft.xml missing spProduct/etCounted/btnRemove",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Build product names list (once per bind - ok for small list; for huge list optimize later)
        List<String> names = new ArrayList<>();
        for (int i = 0; i < productsJson.length(); i++) {
            try {
                JSONObject p = productsJson.getJSONObject(i);
                names.add(p.optString("name", "Product #" + p.optInt("id")));
            } catch (Exception ignored) {}
        }

        ArrayAdapter<String> ad = new ArrayAdapter<>(
                h.itemView.getContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
        );
        h.spProduct.setAdapter(ad);

        // Set selection by productId
        int sel = 0;
        if (d.productId != 0) {
            for (int i = 0; i < productsJson.length(); i++) {
                try {
                    if (productsJson.getJSONObject(i).optInt("id") == d.productId) {
                        sel = i;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        h.spProduct.setSelection(sel);

        h.spProduct.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                try {
                    JSONObject p = productsJson.getJSONObject(pos);
                    d.productId = p.optInt("id");
                } catch (Exception ignored) {}
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Counted stock text
        h.etCounted.setText(d.countedStock == 0 ? "" : String.valueOf(d.countedStock));

        // Avoid multiple watchers
        if (h.watcher != null) h.etCounted.removeTextChangedListener(h.watcher);

        h.watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String t = (s == null) ? "" : s.toString().trim();
                if (TextUtils.isEmpty(t)) {
                    d.countedStock = 0;
                } else {
                    try { d.countedStock = Integer.parseInt(t); }
                    catch (Exception e) { d.countedStock = 0; }
                }
            }
        };
        h.etCounted.addTextChangedListener(h.watcher);

        // Remove row
        h.btnRemove.setOnClickListener(v -> {
            if (removeListener != null) removeListener.onRemove(h.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return drafts.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        Spinner spProduct;
        EditText etCounted;
        ImageView btnRemove;
        TextWatcher watcher;

        VH(@NonNull View itemView) {
            super(itemView);
            spProduct = itemView.findViewById(R.id.spProduct);
            etCounted = itemView.findViewById(R.id.etCounted);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}