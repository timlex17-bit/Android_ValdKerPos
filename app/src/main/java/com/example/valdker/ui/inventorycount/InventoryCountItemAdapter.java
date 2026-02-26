package com.example.valdker.ui.inventorycount;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.InventoryCountItem;

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

        h.tvProduct.setText("Product #" + it.product);
        h.tvSystem.setText("System: " + it.system_stock);
        h.tvCounted.setText("Counted: " + it.counted_stock);
        h.tvDiff.setText("Diff: " + it.difference);
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