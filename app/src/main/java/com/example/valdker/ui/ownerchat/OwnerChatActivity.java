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

        // mensajen (opsional)
        addBotMessage(
                "Halo Owner 👋\n"
                        + "Hau prontu atu ajuda ita hodi analiza dadus loja no esplika oinsá uza feature POS.\n\n"
                        + "Ita bele husu hanesan:\n"
                        + "• reseita ohin\n"
                        + "• vendas fulan ida ne'e\n"
                        + "• despeza fulan ida ne'e\n"
                        + "• lukru ohin\n"
                        + "• hira tranzasaun ohin\n"
                        + "• produtu ne'ebé fa'an barak liu fulan ida ne'e\n"
                        + "• stok ki'ik hela\n"
                        + "• produtu hotu ona\n"
                        + "• stok mie goreng\n"
                        + "• movimentu stok ohin\n"
                        + "• rekomendasaun promo fulan ida ne'e\n"
                        + "• tanba sa lukru tun fulan ida ne'e\n"
                        + "• oinsá aumenta produtu\n"
                        + "• oinsá halo retur sasán\n"
                        + "• oinsá halo stok opname\n"
                        + "• oinsá hatama sosa\n"
                        + "• oinsá imprime resibu"
        );

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