package com.valdker.pos.ui.ownerchat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;

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
    @Nullable
    private String conversationId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        if (!validateAccess()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_owner_chat);
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
        addBotMessage(getString(R.string.owner_chat_welcome));

        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private boolean validateAccess() {
        if (TextUtils.isEmpty(session.getToken())) {
            Toast.makeText(this, getString(R.string.owner_chat_session_expired), Toast.LENGTH_LONG).show();
            return false;
        }

        if (TextUtils.isEmpty(session.getShopCode())) {
            Toast.makeText(this, getString(R.string.owner_chat_shop_context_missing), Toast.LENGTH_LONG).show();
            return false;
        }

        if (!canAccessOwnerChat()) {
            Toast.makeText(this, getString(R.string.owner_chat_no_permission), Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private boolean canAccessOwnerChat() {
        return session.isOwner() || session.canAccessMenu("owner_chat");
    }

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) {
            Toast.makeText(this, getString(R.string.owner_chat_empty_message), Toast.LENGTH_SHORT).show();
            return;
        }

        etMessage.setText("");

        addUserMessage(msg);
        setLoading(true);

        repo.sendChat(msg, conversationId, new OwnerChatRepository.ChatCallback() {
            @Override
            public void onSuccess(OwnerChatResponse res) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (res != null && !TextUtils.isEmpty(res.conversationId)) {
                        conversationId = res.conversationId;
                    }

                    if (res != null && !TextUtils.isEmpty(res.replyText)) {
                        addBotMessage(res.replyText, res.links);
                    } else {
                        addBotMessage(getString(R.string.owner_chat_response_unreadable));
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    addBotMessage(TextUtils.isEmpty(error)
                            ? getString(R.string.owner_chat_unable_contact)
                            : error);
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
        addBotMessage(text, null);
    }

    private void addBotMessage(String text, @Nullable List<OwnerChatResponse.Link> links) {
        data.add(OwnerChatMessage.bot(text, links));
        adapter.notifyItemInserted(data.size() - 1);
        rv.scrollToPosition(data.size() - 1);
    }
}
