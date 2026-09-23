package com.valdker.pos.ui.workshop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.MechanicResponse;

import java.util.ArrayList;
import java.util.List;

public class MechanicAdapter extends RecyclerView.Adapter<MechanicAdapter.VH> {
    public interface Listener {
        void onEdit(@NonNull MechanicResponse mechanic);
        void onDelete(@NonNull MechanicResponse mechanic);
    }

    private final List<MechanicResponse> items = new ArrayList<>();
    private final Listener listener;

    public MechanicAdapter(@NonNull Listener listener) {
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
    public boolean submit(@NonNull List<MechanicResponse> next) {
        if (sameContent(next)) return false;
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
        return true;
    }

    private boolean sameContent(@NonNull List<MechanicResponse> next) {
        if (items.size() != next.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            MechanicResponse a = items.get(i);
            MechanicResponse b = next.get(i);
            if (a.id != b.id
                    || !a.name.equals(b.name)
                    || !a.phone.equals(b.phone)
                    || !a.specialty.equals(b.specialty)
                    || a.isActive != b.isActive) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mechanic, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MechanicResponse item = items.get(position);
        holder.name.setText(item.name);
        holder.phone.setText(item.phone.isEmpty() ? "Phone: -" : "Phone: " + item.phone);
        holder.specialty.setText(item.specialty.isEmpty() ? "Specialty: -" : "Specialty: " + item.specialty);
        holder.status.setText(item.isActive ? "Active" : "Inactive");
        holder.status.setTextColor(item.isActive ? 0xFF047857 : 0xFFB91C1C);
        holder.edit.setOnClickListener(v -> listener.onEdit(item));
        holder.delete.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView phone;
        final TextView specialty;
        final TextView status;
        final Button edit;
        final Button delete;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvMechanicName);
            phone = itemView.findViewById(R.id.tvPhone);
            specialty = itemView.findViewById(R.id.tvSpecialty);
            status = itemView.findViewById(R.id.tvStatus);
            edit = itemView.findViewById(R.id.btnEdit);
            delete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
