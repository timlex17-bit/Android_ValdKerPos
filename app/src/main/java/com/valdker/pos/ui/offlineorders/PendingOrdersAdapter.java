package com.valdker.pos.ui.offlineorders;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.valdker.pos.R;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.repositories.OfflineOrderRepository;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Daftar pesanan offline.
 *
 * <p>Baris-barisnya sekarang di-inflate dari {@code item_pending_order.xml}.
 * Sebelumnya setiap ViewHolder dirakit dengan {@code new TextView(...)} dan
 * warna hex yang ditulis ulang per view, sehingga tampilannya tidak bisa
 * dipratinjau, tidak bisa disesuaikan untuk layar lebar, dan teksnya tidak
 * bisa diterjemahkan.
 */
public class PendingOrdersAdapter extends RecyclerView.Adapter<PendingOrdersAdapter.VH> {

    public interface Listener {
        void onRetry(@NonNull PendingOrderEntity order);

        void onLoadItems(@NonNull String localOrderId);

        void onReprint(@NonNull PendingOrderEntity order);

        boolean isShopMismatch(@NonNull PendingOrderEntity order);
    }

    static class Row {
        final PendingOrderEntity order;
        List<PendingOrderItemEntity> items;
        boolean expanded;

        Row(@NonNull PendingOrderEntity order) {
            this.order = order;
        }
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());

    public PendingOrdersAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitOrders(@NonNull List<PendingOrderEntity> orders) {
        rows.clear();
        for (PendingOrderEntity order : orders) {
            if (order != null) rows.add(new Row(order));
        }
        notifyDataSetChanged();
    }

    public void setItems(@NonNull String localOrderId, @NonNull List<PendingOrderItemEntity> items) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (localOrderId.equals(row.order.localOrderId)) {
                row.items = new ArrayList<>(items);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_order, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Row row = rows.get(position);
        PendingOrderEntity order = row.order;
        Context context = holder.itemView.getContext();

        String clientOrderId = safe(order.clientOrderId);
        holder.orderId.setText(!clientOrderId.isEmpty() ? clientOrderId : safe(order.localOrderId));

        String meta = dateFormat.format(new Date(Math.max(0L, order.createdAt)));
        if (order.syncAttemptCount > 0) {
            meta += "  •  " + context.getString(
                    R.string.offline_orders_attempts, order.syncAttemptCount);
        }
        holder.orderMeta.setText(meta);

        holder.statusBadge.setText(context.getString(statusLabel(order.syncStatus)));
        holder.statusBadge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, statusColor(order.syncStatus))));

        holder.orderTotal.setText(order.totalMoney().format());

        int itemCount = row.items == null ? -1 : row.items.size();
        holder.itemCount.setVisibility(itemCount >= 0 ? View.VISIBLE : View.GONE);
        if (itemCount >= 0) {
            holder.itemCount.setText(
                    context.getString(R.string.offline_orders_items_count, itemCount));
        }

        String error = safe(order.lastSyncError);
        boolean showError = !error.isEmpty()
                && (OfflineOrderRepository.STATUS_FAILED.equals(order.syncStatus)
                || OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(order.syncStatus));
        holder.shortError.setVisibility(showError ? View.VISIBLE : View.GONE);
        if (showError) {
            holder.shortError.setText(error);
        }

        holder.detail.setVisibility(row.expanded ? View.VISIBLE : View.GONE);
        holder.expandIcon.setImageResource(
                row.expanded ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
        holder.expandIcon.setContentDescription(context.getString(row.expanded
                ? R.string.offline_orders_collapse
                : R.string.offline_orders_expand));

        holder.detailClientId.setText(context.getString(R.string.offline_orders_detail_client_id)
                + ": " + (clientOrderId.isEmpty() ? "-" : clientOrderId));
        holder.detailCreated.setText(context.getString(R.string.offline_orders_detail_created)
                + ": " + extractOfflineCreatedAt(order));
        holder.detailItems.setText(buildItemsText(context, row.items));

        boolean shopMismatch = listener.isShopMismatch(order);
        boolean retryVisible = !OfflineOrderRepository.STATUS_SYNCED.equals(order.syncStatus)
                && !OfflineOrderRepository.STATUS_SYNCING.equals(order.syncStatus)
                && !shopMismatch;
        holder.retry.setVisibility(retryVisible ? View.VISIBLE : View.GONE);
        holder.retry.setOnClickListener(v -> listener.onRetry(order));
        holder.reprint.setOnClickListener(v -> listener.onReprint(order));

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;
            row.expanded = !row.expanded;
            notifyItemChanged(adapterPosition);
            if (row.expanded && row.items == null) {
                listener.onLoadItems(order.localOrderId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    private String buildItemsText(@NonNull Context context, List<PendingOrderItemEntity> items) {
        if (items == null) return context.getString(R.string.offline_orders_items_loading);
        if (items.isEmpty()) return context.getString(R.string.offline_orders_items_empty);

        StringBuilder out = new StringBuilder(context.getString(R.string.offline_orders_items_title));
        for (PendingOrderItemEntity item : items) {
            String name = safe(item.name);
            out.append('\n')
                    .append("• ")
                    .append(name.isEmpty() ? ("#" + item.productId) : name)
                    .append('\n')
                    .append("   ")
                    .append(item.quantity)
                    .append(" × ")
                    .append(item.priceMoney().format())
                    .append("  =  ")
                    .append(item.totalMoney().format());
        }
        return out.toString();
    }

    private static String extractOfflineCreatedAt(@NonNull PendingOrderEntity order) {
        try {
            JSONObject payload = new JSONObject(order.rawPayloadJson);
            String offlineCreatedAt = safe(payload.optString("offline_created_at", ""));
            if (!offlineCreatedAt.isEmpty()) return offlineCreatedAt;
            String deviceTime = safe(payload.optString("device_time", ""));
            return deviceTime.isEmpty() ? "-" : deviceTime;
        } catch (Exception ignored) {
            return "-";
        }
    }

    @StringRes
    static int statusLabel(@NonNull String status) {
        if (OfflineOrderRepository.STATUS_PENDING_SYNC.equals(status)) {
            return R.string.offline_orders_status_pending;
        }
        if (OfflineOrderRepository.STATUS_FAILED.equals(status)) {
            return R.string.offline_orders_status_failed;
        }
        if (OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(status)) {
            return R.string.offline_orders_status_review;
        }
        if (OfflineOrderRepository.STATUS_SYNCED.equals(status)) {
            return R.string.offline_orders_status_synced;
        }
        return R.string.offline_orders_status_syncing;
    }

    @ColorRes
    static int statusColor(@NonNull String status) {
        if (OfflineOrderRepository.STATUS_FAILED.equals(status)) {
            return R.color.state_danger_strong;
        }
        if (OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(status)) {
            return R.color.state_review;
        }
        if (OfflineOrderRepository.STATUS_SYNCED.equals(status)) {
            return R.color.state_success;
        }
        if (OfflineOrderRepository.STATUS_SYNCING.equals(status)) {
            return R.color.state_info;
        }
        return R.color.state_warning;
    }

    private static String safe(String value) {
        return value == null || TextUtils.isEmpty(value.trim()) ? "" : value.trim();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView orderId;
        final TextView orderMeta;
        final TextView statusBadge;
        final TextView orderTotal;
        final TextView itemCount;
        final ImageView expandIcon;
        final TextView shortError;
        final LinearLayout detail;
        final TextView detailClientId;
        final TextView detailCreated;
        final TextView detailItems;
        final MaterialButton retry;
        final MaterialButton reprint;

        VH(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.tvOrderId);
            orderMeta = itemView.findViewById(R.id.tvOrderMeta);
            statusBadge = itemView.findViewById(R.id.tvStatusBadge);
            orderTotal = itemView.findViewById(R.id.tvOrderTotal);
            itemCount = itemView.findViewById(R.id.tvItemCount);
            expandIcon = itemView.findViewById(R.id.imgExpand);
            shortError = itemView.findViewById(R.id.tvShortError);
            detail = itemView.findViewById(R.id.layoutDetail);
            detailClientId = itemView.findViewById(R.id.tvDetailClientId);
            detailCreated = itemView.findViewById(R.id.tvDetailCreated);
            detailItems = itemView.findViewById(R.id.tvDetailItems);
            retry = itemView.findViewById(R.id.btnRetry);
            reprint = itemView.findViewById(R.id.btnReprint);
        }
    }
}
