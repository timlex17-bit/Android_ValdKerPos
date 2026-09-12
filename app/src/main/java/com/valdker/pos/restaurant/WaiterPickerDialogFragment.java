package com.valdker.pos.restaurant;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Pemilih pelayan.
 *
 * <p>Daftarnya dipakai apa adanya. Tidak ada role "waiter" di backend dan
 * menyaring berdasarkan {@code role} di sini akan membuang staff yang sah -
 * endpoint-nya sudah mengembalikan hanya staff aktif di toko ini. Siapa yang
 * boleh jadi pelayan diatur lewat kunci modul {@code "waiters"}, bukan role.
 */
public class WaiterPickerDialogFragment extends DialogFragment {

    /** Dipanggil dengan {@code null} kalau kasir memilih mengosongkan pelayan. */
    public interface Listener {
        void onWaiterPicked(@Nullable Long waiterId, @Nullable String waiterName);
    }

    private static final String ARG_SELECTED_ID = "selected_waiter_id";

    private final List<Waiter> waiters = new ArrayList<>();
    private Adapter adapter;
    private RecyclerView rv;
    private ProgressBar progress;
    private TextView message;

    @NonNull
    public static WaiterPickerDialogFragment newInstance(@Nullable Long selectedWaiterId) {
        WaiterPickerDialogFragment f = new WaiterPickerDialogFragment();
        Bundle b = new Bundle();
        if (selectedWaiterId != null) b.putLong(ARG_SELECTED_ID, selectedWaiterId);
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
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.waiter_picker_title)
                .setView(v)
                .setNeutralButton(R.string.waiter_picker_clear, (d, w) -> deliver(null, null))
                .setNegativeButton(R.string.action_cancel, (d, w) -> dismiss())
                .create();

        load();
        return dialog;
    }

    private void load() {
        SessionManager session = new SessionManager(requireContext());
        new RestaurantRepository(requireContext())
                .fetchWaiters(session.getToken(), new RestaurantRepository.WaitersCallback() {
                    @Override
                    public void onSuccess(@NonNull List<Waiter> loaded) {
                        if (!isAdded()) return;
                        waiters.clear();
                        waiters.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        showProgress(false);
                        if (waiters.isEmpty()) {
                            showMessage(getString(R.string.waiter_picker_empty));
                        }
                    }

                    @Override
                    public void onError(int statusCode, @NonNull String msg) {
                        if (!isAdded()) return;
                        showProgress(false);
                        showMessage(getString(R.string.waiter_picker_error, statusCode));
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
        if (listener != null) listener.onWaiterPicked(id, name);
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
                    .inflate(R.layout.item_waiter_pick, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Waiter w = waiters.get(position);

            h.name.setText(w.displayName());

            // Beban kerja sekarang: inilah alasan kasir memilih pelayan tertentu.
            StringBuilder meta = new StringBuilder();
            if (!w.role.isEmpty()) meta.append(w.role);
            if (meta.length() > 0) meta.append(" · ");
            meta.append(getResources().getQuantityString(
                    R.plurals.waiter_active_orders, w.activeOrdersCount, w.activeOrdersCount));
            if (!w.activeTables.isEmpty()) {
                meta.append(" · ")
                        .append(getString(R.string.waiter_active_tables,
                                android.text.TextUtils.join(", ", w.activeTables)));
            }
            h.meta.setText(meta.toString());

            boolean isSelected = selectedId != null && selectedId == w.id;
            h.card.setStrokeWidth(dp(isSelected ? 2 : 1));
            h.card.setStrokeColor(isSelected
                    ? ContextCompat.getColor(h.card.getContext(), R.color.brand_primary)
                    : 0xFFDFE4EC);

            h.card.setOnClickListener(v -> deliver(w.id, w.displayName()));
        }

        @Override
        public int getItemCount() {
            return waiters.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView name;
            final TextView meta;

            VH(@NonNull View v) {
                super(v);
                card = (MaterialCardView) v;
                name = v.findViewById(R.id.tvWaiterName);
                meta = v.findViewById(R.id.tvWaiterMeta);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
