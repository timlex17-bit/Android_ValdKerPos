package com.valdker.pos.workshop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorkshopWorkspaceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onIncreaseQty(WorkshopCartItem item);
        void onDecreaseQty(WorkshopCartItem item);
        void onRemoveItem(WorkshopCartItem item);
    }

    private final List<WorkshopDisplayItem> items = new ArrayList<>();
    private final Listener listener;

    public WorkshopWorkspaceAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<WorkshopDisplayItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getViewType();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == WorkshopDisplayItem.VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_workshop_section_header, parent, false);
            return new HeaderViewHolder(view);
        }

        View view = inflater.inflate(R.layout.item_workshop_cart_line, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        WorkshopDisplayItem displayItem = items.get(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(displayItem);
        } else if (holder instanceof ItemViewHolder) {
            ((ItemViewHolder) holder).bind(displayItem.getCartItem(), listener);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtSectionHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSectionHeader = itemView.findViewById(R.id.txtSectionHeader);
        }

        public void bind(WorkshopDisplayItem item) {
            txtSectionHeader.setText(item.getHeaderTitle());
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtItemName;
        private final TextView txtItemTypeBadge;
        private final TextView txtItemMeta;
        private final TextView txtQty;
        private final TextView txtLineTotal;
        private final ImageButton btnMinus;
        private final ImageButton btnPlus;
        private final ImageButton btnRemove;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            txtItemName = itemView.findViewById(R.id.txtItemName);
            txtItemTypeBadge = itemView.findViewById(R.id.txtItemTypeBadge);
            txtItemMeta = itemView.findViewById(R.id.txtItemMeta);
            txtQty = itemView.findViewById(R.id.txtQty);
            txtLineTotal = itemView.findViewById(R.id.txtLineTotal);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }

        public void bind(final WorkshopCartItem item, final Listener listener) {
            txtItemName.setText(item.getName());
            txtQty.setText(String.valueOf(item.getQuantity()));
            txtLineTotal.setText(formatMoney(item.getLineTotal()));
            txtItemMeta.setText(String.format(
                    Locale.US,
                    "Qty %d × %s",
                    item.getQuantity(),
                    formatMoney(item.getUnitPrice())
            ));

            String badgeText;
            switch (item.getItemType()) {
                case WorkshopCartItem.TYPE_SERVICE:
                    badgeText = "SERVICE";
                    break;
                case WorkshopCartItem.TYPE_PART:
                    badgeText = "PART";
                    break;
                case WorkshopCartItem.TYPE_PRODUCT:
                default:
                    badgeText = "PRODUCT";
                    break;
            }

            txtItemTypeBadge.setText(badgeText);

            btnPlus.setOnClickListener(v -> listener.onIncreaseQty(item));
            btnMinus.setOnClickListener(v -> listener.onDecreaseQty(item));
            btnRemove.setOnClickListener(v -> listener.onRemoveItem(item));
        }

        private String formatMoney(double value) {
            return String.format(Locale.US, "$%.2f", value);
        }
    }
}