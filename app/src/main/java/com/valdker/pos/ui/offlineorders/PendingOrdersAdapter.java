package com.valdker.pos.ui.offlineorders;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.valdker.pos.local.PendingOrderEntity;
import com.valdker.pos.local.PendingOrderItemEntity;
import com.valdker.pos.repositories.OfflineOrderRepository;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US);

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
        Context context = parent.getContext();

        MaterialCardView card = new MaterialCardView(context);
        RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = dp(context, 8);
        cardLp.setMargins(0, margin, 0, margin);
        card.setLayoutParams(cardLp);
        card.setRadius(dp(context, 10));
        card.setCardElevation(dp(context, 1));
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.parseColor("#E5E7EB"));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 12));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        box.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text(context, 15, "#111827", true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = text(context, 11, "#FFFFFF", true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5));
        top.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView meta = text(context, 12, "#6B7280", false);
        meta.setPadding(0, dp(context, 7), 0, 0);
        box.addView(meta);

        TextView total = text(context, 17, "#111827", true);
        total.setPadding(0, dp(context, 8), 0, 0);
        box.addView(total);

        TextView shortError = text(context, 12, "#B91C1C", false);
        shortError.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        box.addView(shortError);

        LinearLayout detail = new LinearLayout(context);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(0, dp(context, 12), 0, 0);
        box.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView detailText = text(context, 12, "#374151", false);
        detail.addView(detailText);

        TextView itemsText = text(context, 12, "#374151", false);
        itemsText.setPadding(0, dp(context, 10), 0, 0);
        detail.addView(itemsText);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsLp.topMargin = dp(context, 12);
        detail.addView(actions, actionsLp);

        MaterialButton retry = new MaterialButton(context);
        retry.setText("Retry");
        retry.setAllCaps(false);
        retry.setCornerRadius(dp(context, 8));
        retry.setMinHeight(dp(context, 38));
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        retryLp.rightMargin = dp(context, 8);
        actions.addView(retry, retryLp);

        MaterialButton reprint = new MaterialButton(context);
        reprint.setText("Reprint Receipt");
        reprint.setAllCaps(false);
        reprint.setCornerRadius(dp(context, 8));
        reprint.setMinHeight(dp(context, 38));
        LinearLayout.LayoutParams reprintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actions.addView(reprint, reprintLp);

        return new VH(card, title, status, meta, total, shortError, detail, detailText, itemsText, retry, reprint);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Row row = rows.get(position);
        PendingOrderEntity order = row.order;

        String clientOrderId = safe(order.clientOrderId);
        String title = !clientOrderId.isEmpty() ? clientOrderId : safe(order.localOrderId);
        holder.title.setText(title);
        holder.status.setText(statusLabel(order.syncStatus));
        holder.status.setTextColor(Color.WHITE);
        holder.status.setBackground(badgeBg(holder.itemView.getContext(), statusColor(order.syncStatus)));
        String meta = dateFormat.format(new Date(Math.max(0L, order.createdAt)));
        if (order.syncAttemptCount > 0) {
            meta += "  |  Attempts: " + order.syncAttemptCount;
        }
        holder.meta.setText(meta);
        holder.total.setText(String.format(Locale.US, "$%.2f", order.total));

        String error = safe(order.lastSyncError);
        boolean shopMismatch = listener.isShopMismatch(order);
        boolean showError = !error.isEmpty()
                && (OfflineOrderRepository.STATUS_FAILED.equals(order.syncStatus)
                || OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(order.syncStatus));
        if (!showError) {
            holder.shortError.setVisibility(View.GONE);
        } else {
            holder.shortError.setVisibility(View.VISIBLE);
            holder.shortError.setText(shorten(error, 120));
            holder.shortError.setBackground(softBg(holder.itemView.getContext(), "#FEF2F2"));
        }

        holder.detail.setVisibility(row.expanded ? View.VISIBLE : View.GONE);
        holder.detailText.setText(buildDetailText(order));
        holder.itemsText.setText(buildItemsText(row.items));

        boolean retryVisible = !OfflineOrderRepository.STATUS_SYNCED.equals(order.syncStatus)
                && !OfflineOrderRepository.STATUS_SYNCING.equals(order.syncStatus)
                && !shopMismatch;
        holder.retry.setVisibility(retryVisible ? View.VISIBLE : View.GONE);
        holder.retry.setOnClickListener(v -> listener.onRetry(order));
        holder.reprint.setVisibility(View.VISIBLE);
        holder.reprint.setOnClickListener(v -> listener.onReprint(order));

        holder.itemView.setOnClickListener(v -> {
            row.expanded = !row.expanded;
            notifyItemChanged(holder.getBindingAdapterPosition());
            if (row.expanded && row.items == null) {
                listener.onLoadItems(order.localOrderId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String buildDetailText(@NonNull PendingOrderEntity order) {
        StringBuilder out = new StringBuilder();
        out.append("Client order ID: ").append(safe(order.clientOrderId)).append('\n');
        out.append("Created: ").append(extractOfflineCreatedAt(order)).append('\n');
        if (!safe(order.lastSyncError).isEmpty()) {
            out.append("Error: ").append(safe(order.lastSyncError));
        }
        return out.toString();
    }

    private String buildItemsText(List<PendingOrderItemEntity> items) {
        if (items == null) return "Items: loading...";
        if (items.isEmpty()) return "Items: no item rows found.";

        StringBuilder out = new StringBuilder("Items");
        for (PendingOrderItemEntity item : items) {
            out.append('\n')
                    .append("- ")
                    .append(safe(item.name).isEmpty() ? ("Product #" + item.productId) : item.name)
                    .append('\n')
                    .append("  ")
                    .append(item.quantity)
                    .append(" x ")
                    .append(String.format(Locale.US, "$%.2f", item.price))
                    .append(" = ")
                    .append(String.format(Locale.US, "$%.2f", item.total));
            if (!safe(item.itemType).isEmpty()) {
                out.append("  ").append(safe(item.itemType));
            }
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

    private static String statusLabel(@NonNull String status) {
        if (OfflineOrderRepository.STATUS_PENDING_SYNC.equals(status)) return "Pending Sync";
        if (OfflineOrderRepository.STATUS_FAILED.equals(status)) return "Failed";
        if (OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(status)) return "Needs Review";
        if (OfflineOrderRepository.STATUS_SYNCED.equals(status)) return "Synced";
        if (OfflineOrderRepository.STATUS_SYNCING.equals(status)) return "Syncing";
        return status;
    }

    private static int statusColor(@NonNull String status) {
        if (OfflineOrderRepository.STATUS_FAILED.equals(status)
                || OfflineOrderRepository.STATUS_NEEDS_REVIEW.equals(status)) {
            return Color.parseColor("#B91C1C");
        }
        if (OfflineOrderRepository.STATUS_SYNCED.equals(status)) {
            return Color.parseColor("#047857");
        }
        if (OfflineOrderRepository.STATUS_SYNCING.equals(status)) {
            return Color.parseColor("#2563EB");
        }
        return Color.parseColor("#92400E");
    }

    private static TextView text(@NonNull Context context, int sp, @NonNull String color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setIncludeFontPadding(true);
        return tv;
    }

    @NonNull
    private static GradientDrawable badgeBg(@NonNull Context context, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(context, 999));
        return bg;
    }

    @NonNull
    private static GradientDrawable softBg(@NonNull Context context, @NonNull String color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(context, 8));
        return bg;
    }

    private static String shorten(@NonNull String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).trim() + "...";
    }

    private static String safe(String value) {
        return value == null || TextUtils.isEmpty(value.trim()) ? "" : value.trim();
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView status;
        final TextView meta;
        final TextView total;
        final TextView shortError;
        final LinearLayout detail;
        final TextView detailText;
        final TextView itemsText;
        final MaterialButton retry;
        final MaterialButton reprint;

        VH(@NonNull View itemView,
           @NonNull TextView title,
           @NonNull TextView status,
           @NonNull TextView meta,
           @NonNull TextView total,
           @NonNull TextView shortError,
           @NonNull LinearLayout detail,
           @NonNull TextView detailText,
           @NonNull TextView itemsText,
           @NonNull MaterialButton retry,
           @NonNull MaterialButton reprint) {
            super(itemView);
            this.title = title;
            this.status = status;
            this.meta = meta;
            this.total = total;
            this.shortError = shortError;
            this.detail = detail;
            this.detailText = detailText;
            this.itemsText = itemsText;
            this.retry = retry;
            this.reprint = reprint;
        }
    }
}
