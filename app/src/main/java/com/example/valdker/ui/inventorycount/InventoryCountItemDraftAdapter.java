package com.example.valdker.ui.inventorycount;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InventoryCountItemDraftAdapter
        extends RecyclerView.Adapter<InventoryCountItemDraftAdapter.VH> {

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    private final JSONArray productsJson;
    private final List<ItemDraft> drafts;
    private final OnRemoveListener removeListener;

    public static class ItemDraft {
        public int productId = 0;
        public int countedStock = 0;

        public boolean isEmpty() {
            return productId <= 0 && countedStock <= 0;
        }

        public boolean isValid() {
            return productId > 0 && countedStock >= 0;
        }
    }

    public InventoryCountItemDraftAdapter(
            @NonNull JSONArray productsJson,
            @NonNull List<ItemDraft> drafts,
            OnRemoveListener removeListener
    ) {
        this.productsJson = productsJson;
        this.drafts = drafts;
        this.removeListener = removeListener;
        setHasStableIds(false);
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

        if (h.spProduct == null || h.etCounted == null || h.btnRemove == null) {
            Toast.makeText(
                    h.itemView.getContext(),
                    "Layout row_inventory_count_item_draft.xml missing spProduct/etCounted/btnRemove",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        List<String> names = buildProductNames();
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                h.itemView.getContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
        );
        h.spProduct.setAdapter(ad);

        int sel = findSelectionIndexByProductId(d.productId);
        h.spProduct.setSelection(sel, false);

        // pastikan productId langsung terisi saat bind,
        // walaupun user belum menyentuh spinner
        if (d.productId <= 0) {
            int resolvedId = getProductIdByIndex(sel);
            if (resolvedId > 0) {
                d.productId = resolvedId;
            }
        }

        h.spProduct.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                int selectedId = getProductIdByIndex(pos);
                if (selectedId > 0) {
                    d.productId = selectedId;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        if (h.watcher != null) {
            h.etCounted.removeTextChangedListener(h.watcher);
        }

        h.etCounted.setText(d.countedStock == 0 ? "" : String.valueOf(d.countedStock));

        h.watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String t = (s == null) ? "" : s.toString().trim();
                if (TextUtils.isEmpty(t)) {
                    d.countedStock = 0;
                    return;
                }

                try {
                    int parsed = Integer.parseInt(t);
                    d.countedStock = Math.max(parsed, 0);
                } catch (Exception e) {
                    d.countedStock = 0;
                }
            }
        };
        h.etCounted.addTextChangedListener(h.watcher);

        h.btnRemove.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && removeListener != null) {
                removeListener.onRemove(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return drafts.size();
    }

    public boolean hasInvalidRows() {
        for (ItemDraft d : drafts) {
            if (!d.isValid()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDuplicateProducts() {
        Set<Integer> seen = new HashSet<>();
        for (ItemDraft d : drafts) {
            if (d.productId <= 0) continue;
            if (!seen.add(d.productId)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAtLeastOneRow() {
        return !drafts.isEmpty();
    }

    public boolean hasAtLeastOneValidRow() {
        for (ItemDraft d : drafts) {
            if (d.isValid()) return true;
        }
        return false;
    }

    @NonNull
    public List<ItemDraft> getValidDraftsOnly() {
        List<ItemDraft> result = new ArrayList<>();
        for (ItemDraft d : drafts) {
            if (d.isValid()) {
                result.add(d);
            }
        }
        return result;
    }

    public String validateForSubmit() {
        if (drafts.isEmpty()) {
            return "Please add at least one item.";
        }

        for (int i = 0; i < drafts.size(); i++) {
            ItemDraft d = drafts.get(i);

            if (d.productId <= 0) {
                return "Please select product on row " + (i + 1) + ".";
            }

            if (d.countedStock < 0) {
                return "Counted stock cannot be negative on row " + (i + 1) + ".";
            }
        }

        if (hasDuplicateProducts()) {
            return "Duplicate product is not allowed.";
        }

        return null;
    }

    private List<String> buildProductNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < productsJson.length(); i++) {
            try {
                JSONObject p = productsJson.getJSONObject(i);
                String name = p.optString("name", "Product #" + p.optInt("id"));
                names.add(name);
            } catch (Exception ignored) {
                names.add("Product");
            }
        }
        return names;
    }

    private int findSelectionIndexByProductId(int productId) {
        if (productId > 0) {
            for (int i = 0; i < productsJson.length(); i++) {
                try {
                    if (productsJson.getJSONObject(i).optInt("id") == productId) {
                        return i;
                    }
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private int getProductIdByIndex(int index) {
        if (index < 0 || index >= productsJson.length()) {
            return 0;
        }
        try {
            JSONObject p = productsJson.getJSONObject(index);
            return p.optInt("id", 0);
        } catch (Exception ignored) {
            return 0;
        }
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