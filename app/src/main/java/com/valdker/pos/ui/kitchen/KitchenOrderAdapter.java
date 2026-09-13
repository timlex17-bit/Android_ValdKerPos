package com.valdker.pos.ui.kitchen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.valdker.pos.R;
import com.valdker.pos.restaurant.kitchen.Iso8601;
import com.valdker.pos.restaurant.kitchen.KitchenBoard;
import com.valdker.pos.restaurant.kitchen.KitchenStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Kartu order di papan dapur, masing-masing berisi item-itemnya.
 *
 * <p>Item digambar ke dalam {@code itemContainer} milik kartu, bukan lewat
 * RecyclerView bersarang. Satu order jarang punya lebih dari sepuluh item, dan
 * daftar bersarang yang bisa menggulir di dalam daftar yang menggulir membuat
 * gulir utama tersendat tanpa memberi apa pun sebagai gantinya.
 */
public class KitchenOrderAdapter extends RecyclerView.Adapter<KitchenOrderAdapter.VH> {

    public interface Listener {
        /** Aksi maju atau batal pada satu item. */
        void onStatusAction(@NonNull KitchenBoard.Item item, @NonNull String newStatus);
    }

    private final Listener listener;
    private final List<KitchenBoard.Order> orders = new ArrayList<>();

    /**
     * Jam yang dipakai untuk menghitung semua durasi dalam satu penggambaran.
     *
     * <p>Diambil sekali per submit, bukan per item: kalau tiap baris memanggil
     * {@code System.currentTimeMillis()} sendiri, dua item dengan timestamp
     * sama bisa menampilkan menit berbeda hanya karena digambar terpisah
     * beberapa milidetik di sekitar pergantian menit.
     */
    private long renderNowMillis = System.currentTimeMillis();

    /** Item yang sedang menunggu jawaban server; tombolnya dinonaktifkan. */
    private long pendingItemId = -1L;

    public KitchenOrderAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull KitchenBoard board, long nowMillis) {
        renderNowMillis = nowMillis;
        orders.clear();
        orders.addAll(board.orders);
        notifyDataSetChanged();
    }

    /** Menandai satu item sedang dikirim, atau -1 untuk membersihkan. */
    public void setPendingItem(long itemId) {
        if (pendingItemId == itemId) return;
        pendingItemId = itemId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_kitchen_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        KitchenBoard.Order order = orders.get(position);
        Context context = holder.itemView.getContext();

        String table = order.table != null && !order.table.trim().isEmpty()
                ? order.table.trim()
                : context.getString(R.string.kitchen_table_unset);
        holder.table.setText(table);

        StringBuilder meta = new StringBuilder(order.invoiceNumber);
        if (order.waiter != null && !order.waiter.trim().isEmpty()) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(context.getString(R.string.kitchen_waiter_prefix, order.waiter.trim()));
        }
        holder.meta.setText(meta.toString());
        holder.itemCount.setText(String.valueOf(order.items.size()));

        bindItems(holder.itemContainer, order.items);
    }

    private void bindItems(@NonNull LinearLayout container,
                           @NonNull List<KitchenBoard.Item> items) {
        Context context = container.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // View lama dipakai ulang: satu order yang hanya berubah statusnya
        // tidak perlu membuang dan membangun kembali seluruh barisnya.
        while (container.getChildCount() > items.size()) {
            container.removeViewAt(container.getChildCount() - 1);
        }
        while (container.getChildCount() < items.size()) {
            container.addView(inflater.inflate(R.layout.item_kitchen_item, container, false));
        }

        for (int i = 0; i < items.size(); i++) {
            bindItem(container.getChildAt(i), items.get(i), i == items.size() - 1);
        }
    }

    private void bindItem(@NonNull View row,
                          @NonNull KitchenBoard.Item item,
                          boolean last) {
        Context context = row.getContext();

        TextView qty = row.findViewById(R.id.tvItemQty);
        TextView name = row.findViewById(R.id.tvItemName);
        TextView status = row.findViewById(R.id.tvItemStatus);
        TextView since = row.findViewById(R.id.tvItemSince);
        MaterialButton forward = row.findViewById(R.id.btnItemForward);
        MaterialButton cancel = row.findViewById(R.id.btnItemCancel);
        View actions = row.findViewById(R.id.itemActions);
        View divider = row.findViewById(R.id.itemDivider);

        qty.setText(context.getString(R.string.kitchen_qty, item.quantity));
        name.setText(item.productName);

        status.setText(context.getString(statusLabel(item.kitchenStatus)));
        status.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, statusColor(item.kitchenStatus))));

        // Durasi datang dari kitchen_status_updated_at; kalau tidak terbaca,
        // labelnya disembunyikan alih-alih menampilkan angka karangan.
        String ago = Iso8601.shortAgo(item.kitchenStatusUpdatedAt, renderNowMillis);
        since.setVisibility(ago == null ? View.GONE : View.VISIBLE);
        if (ago != null) since.setText(ago);

        List<String> next = KitchenStatus.allowedNext(item.kitchenStatus);
        boolean busy = pendingItemId == item.id;

        if (next.isEmpty()) {
            actions.setVisibility(View.GONE);
        } else {
            actions.setVisibility(View.VISIBLE);

            String forwardStatus = next.get(0);
            boolean forwardIsCancel = KitchenStatus.CANCELLED.equals(forwardStatus);

            forward.setVisibility(forwardIsCancel ? View.GONE : View.VISIBLE);
            if (!forwardIsCancel) {
                forward.setText(actionLabel(forwardStatus));
                forward.setEnabled(!busy);
                forward.setOnClickListener(v -> listener.onStatusAction(item, forwardStatus));
            }

            boolean canCancel = next.contains(KitchenStatus.CANCELLED);
            cancel.setVisibility(canCancel ? View.VISIBLE : View.GONE);
            cancel.setEnabled(!busy);
            cancel.setOnClickListener(v ->
                    listener.onStatusAction(item, KitchenStatus.CANCELLED));
        }

        divider.setVisibility(last ? View.GONE : View.VISIBLE);
        row.setAlpha(busy ? 0.55f : 1f);
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    @StringRes
    static int statusLabel(@Nullable String status) {
        String s = KitchenStatus.normalize(status);
        if (KitchenStatus.PREPARING.equals(s)) return R.string.kitchen_status_preparing;
        if (KitchenStatus.READY.equals(s)) return R.string.kitchen_status_ready;
        if (KitchenStatus.SERVED.equals(s)) return R.string.kitchen_status_served;
        if (KitchenStatus.CANCELLED.equals(s)) return R.string.kitchen_status_cancelled;
        return R.string.kitchen_status_pending;
    }

    @StringRes
    static int actionLabel(@Nullable String targetStatus) {
        String s = KitchenStatus.normalize(targetStatus);
        if (KitchenStatus.PREPARING.equals(s)) return R.string.kitchen_action_preparing;
        if (KitchenStatus.READY.equals(s)) return R.string.kitchen_action_ready;
        if (KitchenStatus.SERVED.equals(s)) return R.string.kitchen_action_served;
        return R.string.kitchen_action_cancelled;
    }

    @ColorRes
    static int statusColor(@Nullable String status) {
        String s = KitchenStatus.normalize(status);
        if (KitchenStatus.PREPARING.equals(s)) return R.color.state_info;
        if (KitchenStatus.READY.equals(s)) return R.color.state_success;
        if (KitchenStatus.CANCELLED.equals(s)) return R.color.state_danger_strong;
        if (KitchenStatus.SERVED.equals(s)) return R.color.text_secondary;
        return R.color.state_warning;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView table;
        final TextView meta;
        final TextView itemCount;
        final LinearLayout itemContainer;

        VH(@NonNull View itemView) {
            super(itemView);
            table = itemView.findViewById(R.id.tvKitchenTable);
            meta = itemView.findViewById(R.id.tvKitchenOrderMeta);
            itemCount = itemView.findViewById(R.id.tvKitchenItemCount);
            itemContainer = itemView.findViewById(R.id.itemContainer);
        }
    }
}
