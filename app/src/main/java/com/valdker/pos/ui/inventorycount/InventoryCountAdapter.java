package com.valdker.pos.ui.inventorycount;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.InventoryCount;
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

    // ==============================
    // Helpers
    // ==============================

    private static String formatDate(String iso) {
        try {
            String clean = iso;

            if (clean.contains(".")) {
                clean = clean.substring(0, clean.indexOf(".")) + clean.substring(clean.indexOf("+"));
            }

            java.text.SimpleDateFormat input =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH);

            java.text.SimpleDateFormat output =
                    new java.text.SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.ENGLISH);

            java.util.Date date = input.parse(clean);
            return output.format(date);

        } catch (Exception e) {
            return iso; // fallback
        }
    }

    private static void applyDiffStyle(TextView tv, int diff) {
        if (tv == null) return;
        if (diff == 0) tv.setTextColor(0xFF2E7D32);
        else if (diff < 0) tv.setTextColor(0xFFC62828);
        else tv.setTextColor(0xFFEF6C00);
    }

    private static void applyStatusBadge(TextView badge, String status) {
        if (badge == null) return;

        String s = (status == null || status.trim().isEmpty())
                ? "DRAFT"
                : status.trim().toUpperCase(Locale.US);

        badge.setText(s);

        int bg;
        switch (s) {
            case "COMPLETED":
                bg = 0xFF2E7D32;
                break;
            case "APPROVED":
                bg = 0xFF1565C0;
                break;
            case "SUBMITTED":
                bg = 0xFFEF6C00;
                break;
            default:
                bg = 0xFF616161;
        }

        badge.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bg)
        );

        badge.setTextColor(0xFFFFFFFF);
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

        h.tvDate.setText(formatDate(it.counted_at));

        String byName = "-";

        if (it.counted_by != null) {
            if (it.counted_by.display_name != null && !it.counted_by.display_name.isEmpty()) {
                byName = it.counted_by.display_name;
            } else if (it.counted_by.username != null && !it.counted_by.username.isEmpty()) {
                byName = it.counted_by.username;
            }
        }

        h.tvBy.setText("By: " + byName);

        int itemsCount = (it.items != null) ? it.items.size() : 0;
        h.tvItems.setText(itemsCount + " item(s)");

        int totalDiff = 0;
        if (it.items != null) {
            for (int i = 0; i < it.items.size(); i++) {
                totalDiff += it.items.get(i).difference;
            }
        }
        h.tvDiff.setText("Diff: " + totalDiff);
        applyDiffStyle(h.tvDiff, totalDiff);

        applyStatusBadge(h.tvStatusBadge, it.status);

        // tap -> detail
        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(it);
        });

        // long press -> Edit/Delete menu
        h.itemView.setOnLongClickListener(v -> {

            String st = (it.status == null || it.status.trim().isEmpty())
                    ? "DRAFT"
                    : it.status.trim().toUpperCase(java.util.Locale.US);

            boolean locked =
                    "APPROVED".equals(st) ||
                            "COMPLETED".equals(st);

            if (locked) {
                new MaterialAlertDialogBuilder(v.getContext())
                        .setTitle(title)
                        .setMessage("This stock count is locked (" + st + ").\nEditing is not allowed.")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            }

            // Only show actions if not locked
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

        TextView tvTitle, tvDate, tvBy, tvItems, tvDiff, tvStatusBadge;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDate  = itemView.findViewById(R.id.tvDate);
            tvBy    = itemView.findViewById(R.id.tvBy);
            tvItems = itemView.findViewById(R.id.tvItems);
            tvDiff  = itemView.findViewById(R.id.tvDiff);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
        }
    }
}