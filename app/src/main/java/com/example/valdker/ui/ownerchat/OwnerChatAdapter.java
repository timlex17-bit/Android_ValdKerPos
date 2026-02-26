package com.example.valdker.ui.ownerchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;

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
            ((BotVH) holder).tv.setText(msg.text);
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
        BotVH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvText);
        }
    }
}