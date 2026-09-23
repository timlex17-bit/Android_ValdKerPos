package com.valdker.pos.ui.ownerchat;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.ui.common.SystemBars;

import java.util.ArrayList;
import java.util.List;

public class OwnerChatActivity extends AppCompatActivity {

    private RecyclerView rv;
    private EditText etMessage;
    private ImageButton btnSend;
    private MaterialCardView sendContainer;
    private TextView tvStatus;
    private View dotStatus;
    private View suggestionScroll;

    private OwnerChatAdapter adapter;
    private final List<OwnerChatMessage> data = new ArrayList<>();

    private OwnerChatRepository repo;
    private SessionManager session;
    /**
     * Id percakapan dari server, bilangan bulat sesuai kontrak asisten.
     *
     * <p>Hanya hidup selama layar ini terbuka - tidak disimpan ke disk, dan
     * tidak ada basis data baru untuknya. Endpoint lama tidak pernah
     * mengembalikan field ini, jadi sebelumnya nilainya selalu null dan setiap
     * pesan memulai percakapan dari nol.
     */
    @Nullable
    private Integer conversationId;

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

        bindViews();
        setupSystemBars();
        setupList();
        setupInput();
        setupSuggestions();

        addBotMessage(getString(R.string.owner_chat_welcome));
    }

    // ------------------------------------------------------------ penyiapan

    private void bindViews() {
        rv = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        sendContainer = findViewById(R.id.sendContainer);
        tvStatus = findViewById(R.id.tvStatus);
        dotStatus = findViewById(R.id.dotStatus);
        suggestionScroll = findViewById(R.id.suggestionScroll);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearConversation());
    }

    private void setupSystemBars() {
        SystemBars.apply(this);
        SystemBars.padTopBar(findViewById(R.id.topBar));
        // Bilah input ikut naik bersama papan ketik, bukan tertinggal di
        // baliknya pada perangkat yang jendelanya tidak diubah ukurannya.
        SystemBars.padBottomWithIme(findViewById(R.id.bottomBar));
    }

    private void setupList() {
        adapter = new OwnerChatAdapter(data);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        // Saat papan ketik muncul, baris terakhir harus tetap terlihat.
        rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> {
            if (b < ob) scrollToEnd();
        });
    }

    private void setupInput() {
        btnSend.setOnClickListener(v -> sendMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Tombol kirim dulu selalu terlihat aktif, bahkan saat kolomnya kosong;
        // menekannya hanya memunculkan toast "ketik pertanyaan dulu". Sekarang
        // keadaannya terlihat sebelum ditekan.
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSendEnabled();
            }
        });
        updateSendEnabled();
    }

    private void setupSuggestions() {
        bindSuggestion(findViewById(R.id.chipSuggestSales));
        bindSuggestion(findViewById(R.id.chipSuggestStock));
        bindSuggestion(findViewById(R.id.chipSuggestExpense));
    }

    private void bindSuggestion(Chip chip) {
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            etMessage.setText(chip.getText());
            etMessage.setSelection(etMessage.getText().length());
            sendMessage();
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

    // ---------------------------------------------------------------- kirim

    private void sendMessage() {
        String msg = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) {
            Toast.makeText(this, getString(R.string.owner_chat_empty_message), Toast.LENGTH_SHORT).show();
            return;
        }
        if (adapter.isThinking()) return;

        etMessage.setText("");

        addUserMessage(msg);
        setLoading(true);

        repo.sendChat(msg, conversationId, new OwnerChatRepository.ChatCallback() {
            @Override
            public void onSuccess(OwnerChatResponse res) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (res == null) {
                        addBotMessage(getString(R.string.owner_chat_response_unreadable));
                        return;
                    }

                    if (res.conversationId != null) {
                        conversationId = res.conversationId;
                    }

                    if (res.hasReplyText()) {
                        StructuredDataView businessData = StructuredDataMapper.map(res.structuredDataRaw);
                        addBotMessage(res.replyText, res.links, businessData);
                    } else {
                        addBotMessage(getString(R.string.owner_chat_response_unreadable));
                    }
                });
            }

            @Override
            public void onError(@NonNull AssistantError kind, @NonNull String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    // Sebuah id percakapan yang sudah tidak dikenal server akan
                    // membuat setiap pesan berikutnya gagal dengan galat yang
                    // sama; melupakannya membuat kiriman selanjutnya memulai
                    // percakapan baru dengan sendirinya.
                    if (kind.shouldForgetConversation()) {
                        conversationId = null;
                    }
                    addBotMessage(TextUtils.isEmpty(error)
                            ? getString(R.string.owner_chat_unable_contact)
                            : error);
                });
            }
        });
    }

    private void clearConversation() {
        int removed = data.size();
        if (removed == 0) return;
        data.clear();
        adapter.notifyItemRangeRemoved(0, removed);
        conversationId = null;
        addBotMessage(getString(R.string.owner_chat_welcome));
        Toast.makeText(this, getString(R.string.owner_chat_cleared), Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------- tampilan

    private void setLoading(boolean loading) {
        adapter.setThinking(loading);
        updateSendEnabled();
        tvStatus.setText(loading
                ? R.string.owner_chat_status_thinking
                : R.string.owner_chat_status_ready);
        dotStatus.setBackgroundTintList(ColorStateList.valueOf(
                loading ? Color.parseColor("#FFD479") : Color.parseColor("#7BF1A8")));
        if (loading) scrollToEnd();
    }

    private void updateSendEnabled() {
        boolean canSend = !adapter.isThinking()
                && !TextUtils.isEmpty(etMessage.getText().toString().trim());
        btnSend.setEnabled(canSend);
        sendContainer.setAlpha(canSend ? 1f : 0.45f);
    }

    /**
     * Saran pertanyaan hanya berguna selama percakapan masih kosong; begitu
     * ada pesan sungguhan, ruangnya dikembalikan kepada percakapan.
     */
    private void updateSuggestionVisibility() {
        boolean onlyWelcome = data.size() <= 1;
        suggestionScroll.setVisibility(onlyWelcome ? View.VISIBLE : View.GONE);
    }

    private void addUserMessage(String text) {
        data.add(OwnerChatMessage.user(text));
        adapter.notifyItemInserted(data.size() - 1);
        updateSuggestionVisibility();
        scrollToEnd();
    }

    private void addBotMessage(String text) {
        addBotMessage(text, null);
    }

    private void addBotMessage(String text, @Nullable List<OwnerChatResponse.Link> links) {
        addBotMessage(text, links, null);
    }

    private void addBotMessage(String text,
                                @Nullable List<OwnerChatResponse.Link> links,
                                @Nullable StructuredDataView businessData) {
        data.add(OwnerChatMessage.bot(text, links, businessData));
        adapter.notifyItemInserted(data.size() - 1);
        updateSuggestionVisibility();
        scrollToEnd();
    }

    private void scrollToEnd() {
        rv.post(() -> {
            int last = adapter.getItemCount() - 1;
            if (last >= 0) rv.scrollToPosition(last);
        });
    }
}
