package com.valdker.pos.restaurant;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid pemilih meja untuk order dine-in.
 *
 * <p>Meja yang {@code occupied} TETAP bisa dipilih. Satu meja boleh punya
 * beberapa order terbuka - bill terpisah, rombongan ronde kedua - jadi status
 * dari server adalah keterangan bagi kasir, bukan larangan. Menonaktifkan meja
 * terisi akan membuat kasus yang paling sering terjadi di restoran justru tidak
 * bisa dikerjakan.
 *
 * <p>Meja {@code is_active=false} sudah dibuang di
 * {@link RestaurantRepository#fetchTables}.
 */
public class TablePickerDialogFragment extends DialogFragment {

    /** Dipanggil dengan {@code null} kalau kasir memilih mengosongkan meja. */
    public interface Listener {
        void onTablePicked(@Nullable Long tableId, @Nullable String tableName);
    }

    private static final String ARG_SELECTED_ID = "selected_table_id";

    private static final int COLOR_OCCUPIED_BG = 0xFFFEF3C7;
    private static final int COLOR_OCCUPIED_TEXT = 0xFF92400E;
    private static final int COLOR_EMPTY_BG = 0xFFF1F5F9;
    private static final int COLOR_EMPTY_TEXT = 0xFF475569;

    private final List<RestaurantTable> tables = new ArrayList<>();
    private Adapter adapter;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView message;

    @NonNull
    public static TablePickerDialogFragment newInstance(@Nullable Long selectedTableId) {
        TablePickerDialogFragment f = new TablePickerDialogFragment();
        Bundle b = new Bundle();
        if (selectedTableId != null) b.putLong(ARG_SELECTED_ID, selectedTableId);
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_restaurant_picker, null);

        rv = v.findViewById(R.id.rvPicker);
        progress = v.findViewById(R.id.pbPicker);
        message = v.findViewById(R.id.tvPickerMessage);

        Long selected = null;
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_SELECTED_ID)) {
            selected = args.getLong(ARG_SELECTED_ID);
        }

        adapter = new Adapter(selected);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rv.setAdapter(adapter);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.table_picker_title)
                .setView(v)
                .setNeutralButton(R.string.table_picker_clear, (d, w) -> deliver(null, null))
                .setNegativeButton(R.string.action_cancel, (d, w) -> dismiss())
                .create();

        load();
        return dialog;
    }

    private void load() {
        SessionManager session = new SessionManager(requireContext());
        new RestaurantRepository(requireContext())
                .fetchTables(session.getToken(), new RestaurantRepository.TablesCallback() {
                    @Override
                    public void onSuccess(@NonNull List<RestaurantTable> loaded) {
                        if (!isAdded()) return;
                        tables.clear();
                        tables.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        showProgress(false);
                        if (tables.isEmpty()) {
                            showMessage(getString(R.string.table_picker_empty));
                        }
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String msg) {
                        if (!isAdded()) return;
                        showProgress(false);
                        showMessage(getString(R.string.table_picker_error, statusCode));
                    }
                });
    }

    private void showProgress(boolean on) {
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void showMessage(@NonNull String text) {
        if (message == null) return;
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void deliver(@Nullable Long id, @Nullable String name) {
        Listener listener = resolveListener();
        if (listener != null) listener.onTablePicked(id, name);
        dismiss();
    }

    @Nullable
    private Listener resolveListener() {
        Fragment parent = getParentFragment();
        if (parent instanceof Listener) return (Listener) parent;
        if (getActivity() instanceof Listener) return (Listener) getActivity();
        return null;
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        @Nullable private final Long selectedId;

        Adapter(@Nullable Long selectedId) {
            this.selectedId = selectedId;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_table_pick, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            RestaurantTable t = tables.get(position);

            h.name.setText(t.name);

            String meta = getString(R.string.table_picker_seats, t.capacity);
            if (!t.area.isEmpty()) meta = t.area + " · " + meta;
            h.meta.setText(meta);

            boolean occupied = t.isOccupied();
            h.status.setText(occupied
                    ? R.string.table_status_occupied
                    : R.string.table_status_empty);
            h.status.setTextColor(occupied ? COLOR_OCCUPIED_TEXT : COLOR_EMPTY_TEXT);
            tintBadge(h.status, occupied ? COLOR_OCCUPIED_BG : COLOR_EMPTY_BG);

            boolean isSelected = selectedId != null && selectedId == t.id;
            h.card.setStrokeWidth(dp(isSelected ? 2 : 1));
            h.card.setStrokeColor(isSelected
                    ? androidx.core.content.ContextCompat.getColor(
                            h.card.getContext(), R.color.brand_primary)
                    : 0xFFDFE4EC);

            // Meja terisi tetap dapat dipilih - lihat catatan kelas.
            h.card.setOnClickListener(v -> deliver(t.id, t.name));
        }

        @Override
        public int getItemCount() {
            return tables.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final com.google.android.material.card.MaterialCardView card;
            final TextView name;
            final TextView meta;
            final TextView status;

            VH(@NonNull View v) {
                super(v);
                card = (com.google.android.material.card.MaterialCardView) v;
                name = v.findViewById(R.id.tvTableName);
                meta = v.findViewById(R.id.tvTableMeta);
                status = v.findViewById(R.id.tvTableStatus);
            }
        }
    }

    private static void tintBadge(@NonNull TextView badge, int color) {
        Drawable bg = badge.getBackground();
        if (bg == null) return;
        Drawable wrapped = DrawableCompat.wrap(bg.mutate());
        DrawableCompat.setTint(wrapped, color);
        badge.setBackground(wrapped);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
