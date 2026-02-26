package com.example.valdker.ui.ownerchat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.valdker.R;
import com.example.valdker.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class OwnerChatActivity extends AppCompatActivity {

    private RecyclerView rv;
    private ProgressBar progress;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnBack;

    private OwnerChatAdapter adapter;
    private final List<OwnerChatMessage> data = new ArrayList<>();

    private OwnerChatRepository repo;
    private SessionManager session;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_chat);

        session = new SessionManager(this);
        repo = new OwnerChatRepository(this, session);

        rv = findViewById(R.id.rvChat);
        progress = findViewById(R.id.progress);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);

        adapter = new OwnerChatAdapter(data);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        // pesan sambutan (opsional)
        addBotMessage("Halo Owner 👋\nCoba tanya:\n• income hari ini\n• expense bulan ini\n• top produk bulan ini\n• stok menipis");

        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) return;

        etMessage.setText("");

        addUserMessage(msg);
        setLoading(true);

        repo.sendChat(msg, new OwnerChatRepository.ChatCallback() {
            @Override
            public void onSuccess(OwnerChatResponse res) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (res != null && !TextUtils.isEmpty(res.replyText)) {
                        addBotMessage(res.replyText);
                    } else {
                        addBotMessage("Laiha resposta husi servidór.");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    addBotMessage("❌ Erru: " + error);
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!loading);
    }

    private void addUserMessage(String text) {
        data.add(OwnerChatMessage.user(text));
        adapter.notifyItemInserted(data.size() - 1);
        rv.scrollToPosition(data.size() - 1);
    }

    private void addBotMessage(String text) {
        data.add(OwnerChatMessage.bot(text));
        adapter.notifyItemInserted(data.size() - 1);
        rv.scrollToPosition(data.size() - 1);
    }
}