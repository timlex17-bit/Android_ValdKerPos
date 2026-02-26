package com.example.valdker.ui.inventorycount;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.models.InventoryCount;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class InventoryCountAdapter extends RecyclerView.Adapter<InventoryCountAdapter.VH> {

    public interface OnClick { void onClick(@NonNull InventoryCount item); }
    public interface OnEdit { void onEdit(@NonNull InventoryCount item); }
    public interface OnDelete { void onDelete(@NonNull InventoryCount item); }

    private final List<InventoryCount> data;
    private final OnClick onClick;
    private final OnEdit onEdit;
    private final OnDelete onDelete;

    public InventoryCountAdapter(@NonNull List<InventoryCount> data,
         @NonNull OnClick onClick,
         @NonNull OnEdit onEdit,
         @NonNull OnDelete onDelete) {
        this.data = data;
        this.onClick = onClick;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_count, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        InventoryCount it = data.get(position);

        String title = (it.title != null && !it.title.trim().isEmpty())
                ? it.title
                : ("Stock Count #" + it.id);
        h.tvTitle.setText(title);

        // tampilkan raw counted_at dulu (aman)
        String date = (it.counted_at != null && !it.counted_at.trim().isEmpty())
                ? it.counted_at
                : "-";
        h.tvDate.setText(date);

        h.tvBy.setText("By: #" + it.counted_by);

        int itemsCount = (it.items != null) ? it.items.size() : 0;
        h.tvItems.setText(itemsCount + " item(s)");

        int totalDiff = 0;
        if (it.items != null) {
            for (int i = 0; i < it.items.size(); i++) {
                totalDiff += it.items.get(i).difference;
            }
        }
        h.tvDiff.setText("Diff: " + totalDiff);

        // tap -> detail
        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(it);
        });

        // long press -> Edit/Delete menu
        h.itemView.setOnLongClickListener(v -> {
            String[] actions = new String[]{"Edit", "Delete"};
            new MaterialAlertDialogBuilder(v.getContext())
                    .setTitle(title)
                    .setItems(actions, (d, which) -> {
                        if (which == 0 && onEdit != null) onEdit.onEdit(it);
                        if (which == 1 && onDelete != null) onDelete.onDelete(it);
                    })
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data != null ? data.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvTitle, tvDate, tvBy, tvItems, tvDiff;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate  = itemView.findViewById(R.id.tvDate);
            tvBy    = itemView.findViewById(R.id.tvBy);
            tvItems = itemView.findViewById(R.id.tvItems);
            tvDiff  = itemView.findViewById(R.id.tvDiff);
        }
    }
}