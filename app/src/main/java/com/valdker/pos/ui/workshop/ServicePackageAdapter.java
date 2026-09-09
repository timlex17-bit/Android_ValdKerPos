package com.valdker.pos.ui.workshop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.ServicePackageResponse;

import java.util.ArrayList;
import java.util.List;

public class ServicePackageAdapter extends RecyclerView.Adapter<ServicePackageAdapter.VH> {
    public interface Listener {
        void onEdit(@NonNull ServicePackageResponse item);
        void onDelete(@NonNull ServicePackageResponse item);
    }

    private final List<ServicePackageResponse> items = new ArrayList<>();
    private final Listener listener;

    public ServicePackageAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<ServicePackageResponse> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service_package, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ServicePackageResponse item = items.get(position);
        holder.name.setText(item.name);
        holder.price.setText("Price: " + formatPrice(item.price));
        holder.duration.setText("Duration: " + item.durationMinutes + " min");
        holder.description.setText(item.description.isEmpty() ? "Description: -" : item.description);
        holder.status.setText(item.isActive ? "Active" : "Inactive");
        holder.status.setTextColor(item.isActive ? 0xFF047857 : 0xFFB91C1C);
        holder.edit.setOnClickListener(v -> listener.onEdit(item));
        holder.delete.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    private static String formatPrice(@NonNull String price) {
        return price.trim().isEmpty() ? "0.00" : price.trim();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView price;
        final TextView duration;
        final TextView description;
        final TextView status;
        final Button edit;
        final Button delete;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvPackageName);
            price = itemView.findViewById(R.id.tvPrice);
            duration = itemView.findViewById(R.id.tvDuration);
            description = itemView.findViewById(R.id.tvDescription);
            status = itemView.findViewById(R.id.tvStatus);
            edit = itemView.findViewById(R.id.btnEdit);
            delete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
