package com.valdker.pos.ui.inventorycount;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.InventoryCountItem;

import java.util.List;

public class InventoryCountItemAdapter extends RecyclerView.Adapter<InventoryCountItemAdapter.VH> {

    private final List<InventoryCountItem> list;

    public InventoryCountItemAdapter(List<InventoryCountItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_count_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        InventoryCountItem it = list.get(position);

        h.tvProduct.setText(h.itemView.getContext().getString(R.string.label_product_number, String.valueOf(it.product)));
        h.tvSystem.setText(h.itemView.getContext().getString(R.string.label_system_value, String.valueOf(it.system_stock)));
        h.tvCounted.setText(h.itemView.getContext().getString(R.string.label_counted_value, String.valueOf(it.counted_stock)));
        h.tvDiff.setText(h.itemView.getContext().getString(R.string.label_diff_value, String.valueOf(it.difference)));
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvProduct, tvSystem, tvCounted, tvDiff;
        VH(@NonNull View v) {
            super(v);
            tvProduct = v.findViewById(R.id.tvProduct);
            tvSystem = v.findViewById(R.id.tvSystem);
            tvCounted = v.findViewById(R.id.tvCounted);
            tvDiff = v.findViewById(R.id.tvDiff);
        }
    }
}