package com.valdker.pos.ui.workshop;

import com.valdker.pos.money.Money;
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

    /**
     * @return true kalau isinya benar-benar berubah.
     *
     * <p>Daftar ini dimuat ulang setiap {@code onResume()}, jadi kembali dari
     * layar lain biasanya menghasilkan isi yang sama persis. Tanpa penjagaan
     * ini, {@code notifyDataSetChanged()} tetap dipanggil dan posisi gulir
     * terlempar kembali ke baris teratas.
     */
    public boolean submit(@NonNull List<ServicePackageResponse> next) {
        if (sameContent(next)) return false;
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
        return true;
    }

    private boolean sameContent(@NonNull List<ServicePackageResponse> next) {
        if (items.size() != next.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            ServicePackageResponse a = items.get(i);
            ServicePackageResponse b = next.get(i);
            if (a.id != b.id
                    || !a.name.equals(b.name)
                    || !a.description.equals(b.description)
                    || !a.price.equals(b.price)
                    || a.durationMinutes != b.durationMinutes
                    || a.isActive != b.isActive) {
                return false;
            }
        }
        return true;
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
        holder.price.setText(holder.itemView.getContext().getString(
                R.string.label_price_value, formatPrice(item.price)));
        holder.duration.setText(holder.itemView.getContext().getString(
                R.string.label_duration_minutes_value, item.durationMinutes));
        holder.description.setText(item.description.isEmpty()
                ? holder.itemView.getContext().getString(
                        R.string.label_description_value,
                        holder.itemView.getContext().getString(R.string.label_default_dash))
                : item.description);
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
        // Tanpa Money di sini, harga paket tergambar "0.00" tanpa $
        // sama sekali, sementara layar lain menampilkan "$0.00".
        return Money.of(price.trim()).format();
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
