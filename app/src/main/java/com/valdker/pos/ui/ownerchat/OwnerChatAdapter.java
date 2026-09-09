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

import java.util.List;

public class OwnerChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<OwnerChatMessage> data;

    public OwnerChatAdapter(List<OwnerChatMessage> data) {
        this.data = data;
    }

    @Override
    public int getItemViewType(int position) {
        return data.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == OwnerChatMessage.TYPE_USER) {
            View v = inf.inflate(R.layout.item_owner_chat_user, parent, false);
            return new UserVH(v);
        } else {
            View v = inf.inflate(R.layout.item_owner_chat_bot, parent, false);
            return new BotVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        OwnerChatMessage msg = data.get(position);
        if (holder instanceof UserVH) {
            ((UserVH) holder).tv.setText(msg.text);
        } else if (holder instanceof BotVH) {
            ((BotVH) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class UserVH extends RecyclerView.ViewHolder {
        TextView tv;
        UserVH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvText);
        }
    }

    static class BotVH extends RecyclerView.ViewHolder {
        TextView tv;
        LinearLayout linksContainer;

        BotVH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvText);
            linksContainer = itemView.findViewById(R.id.linksContainer);
        }

        void bind(@NonNull OwnerChatMessage msg) {
            tv.setText(msg.text);
            if (linksContainer == null) return;

            linksContainer.removeAllViews();
            linksContainer.setVisibility(msg.links.isEmpty() ? View.GONE : View.VISIBLE);
            for (OwnerChatResponse.Link link : msg.links) {
                if (link == null || TextUtils.isEmpty(link.title) || TextUtils.isEmpty(link.url)) {
                    continue;
                }
                TextView row = new TextView(itemView.getContext());
                row.setText(link.title);
                row.setTextColor(0xFF2563EB);
                row.setTextSize(13f);
                row.setPadding(12, 8, 12, 8);
                row.setSingleLine(false);
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
