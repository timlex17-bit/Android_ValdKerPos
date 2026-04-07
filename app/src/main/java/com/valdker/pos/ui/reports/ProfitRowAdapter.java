package com.valdker.pos.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.models.DailyProfitRow;

import java.util.ArrayList;
import java.util.List;

public class ProfitRowAdapter extends RecyclerView.Adapter<ProfitRowAdapter.VH> {

    private final List<DailyProfitRow> items = new ArrayList<>();

    public void submit(@NonNull List<DailyProfitRow> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profit_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DailyProfitRow r = items.get(pos);
        h.tvDate.setText(r.date);
        h.tvS.setText("S: $" + r.sales);
        h.tvE.setText("E: $" + r.expense);
        h.tvP.setText("P: $" + r.profit);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvS, tvE, tvP;
        VH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvS = itemView.findViewById(R.id.tvS);
            tvE = itemView.findViewById(R.id.tvE);
            tvP = itemView.findViewById(R.id.tvP);
        }
    }
}
