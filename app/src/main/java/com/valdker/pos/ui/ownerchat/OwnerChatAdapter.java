package com.valdker.pos.ui.ownerchat;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OwnerChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /**
     * Tipe khusus untuk baris "sedang mengetik". Ia tidak punya entri di
     * {@code data}; jumlah barisnya ditambah satu selama {@link #thinking}
     * bernilai true, jadi tidak perlu menyisipkan lalu menghapus pesan palsu
     * dari daftar pesan sungguhan.
     */
    private static final int TYPE_TYPING = 3;

    private final List<OwnerChatMessage> data;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private boolean thinking = false;

    public OwnerChatAdapter(List<OwnerChatMessage> data) {
        this.data = data;
    }

    /** Menyalakan/mematikan baris "sedang mengetik" di ujung daftar. */
    public void setThinking(boolean value) {
        if (thinking == value) return;
        thinking = value;
        if (value) {
            notifyItemInserted(data.size());
        } else {
            notifyItemRemoved(data.size());
        }
    }

    public boolean isThinking() {
        return thinking;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= data.size()) return TYPE_TYPING;
        return data.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TYPING) {
            return new TypingVH(inf.inflate(R.layout.item_owner_chat_typing, parent, false));
        }
        if (viewType == OwnerChatMessage.TYPE_USER) {
            return new UserVH(inf.inflate(R.layout.item_owner_chat_user, parent, false));
        }
        return new BotVH(inf.inflate(R.layout.item_owner_chat_bot, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TypingVH) return;

        OwnerChatMessage msg = data.get(position);
        String time = timeFormat.format(new Date(msg.timeMs));

        if (holder instanceof UserVH) {
            ((UserVH) holder).bind(msg, time);
        } else if (holder instanceof BotVH) {
            ((BotVH) holder).bind(msg, time);
        }
    }

    @Override
    public int getItemCount() {
        return data.size() + (thinking ? 1 : 0);
    }

    static class TypingVH extends RecyclerView.ViewHolder {
        TypingVH(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView tv;
        final TextView time;

        UserVH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvText);
            time = itemView.findViewById(R.id.tvTime);
        }

        void bind(@NonNull OwnerChatMessage msg, @NonNull String timeText) {
            tv.setText(msg.text);
            time.setText(timeText);
        }
    }

    static class BotVH extends RecyclerView.ViewHolder {
        final TextView tv;
        final TextView time;
        final LinearLayout linksContainer;

        BotVH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvText);
            time = itemView.findViewById(R.id.tvTime);
            linksContainer = itemView.findViewById(R.id.linksContainer);
        }

        void bind(@NonNull OwnerChatMessage msg, @NonNull String timeText) {
            tv.setText(msg.text);
            time.setText(timeText);
            if (linksContainer == null) return;

            linksContainer.removeAllViews();
            linksContainer.setVisibility(msg.links.isEmpty() ? View.GONE : View.VISIBLE);

            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            for (OwnerChatResponse.Link link : msg.links) {
                if (link == null || TextUtils.isEmpty(link.title) || TextUtils.isEmpty(link.url)) {
                    continue;
                }
                // Pranala dibuat dari layout supaya ukuran sentuh, warna, dan
                // sudutnya sama dengan komponen lain. Versi sebelumnya memakai
                // new TextView() dengan padding dalam piksel mentah - 12px,
                // bukan 12dp - sehingga pada layar rapat area sentuhnya jauh
                // di bawah 48dp.
                View row = inflater.inflate(R.layout.item_owner_chat_link, linksContainer, false);
                TextView label = row.findViewById(R.id.tvLinkTitle);
                label.setText(link.title);
                row.setOnClickListener(v -> openSafeUrl(link.url));
                linksContainer.addView(row);
            }
        }

        private void openSafeUrl(@NonNull String rawUrl) {
            Uri uri;
            try {
                uri = Uri.parse(rawUrl.trim());
            } catch (Exception e) {
                showUnsupportedLink();
                return;
            }

            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                showUnsupportedLink();
                return;
            }

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                itemView.getContext().startActivity(intent);
            } catch (Exception e) {
                showUnsupportedLink();
            }
        }

        private void showUnsupportedLink() {
            Toast.makeText(
                    itemView.getContext(),
                    itemView.getContext().getString(R.string.owner_chat_link_cannot_open),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
