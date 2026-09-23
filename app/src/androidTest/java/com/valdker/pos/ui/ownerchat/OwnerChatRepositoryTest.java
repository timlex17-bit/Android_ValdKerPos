package com.valdker.pos.ui.ownerchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Jalur jaringan Owner Chat, diuji terhadap server tiruan di dalam perangkat.
 *
 * <p>Uji unit JVM sudah menutup perakitan alamat, bentuk badan permintaan, dan
 * pembacaan respons secara terpisah. Yang hanya bisa diperiksa di sini adalah
 * sambungannya: apakah permintaan benar-benar mendarat di
 * {@code /api/ai/assistant/chat/}, apakah header {@code Authorization} ikut
 * terkirim, dan apakah tiap status HTTP berakhir pada teks yang benar di layar.
 * Sebuah kesalahan di lapisan itu lolos dari setiap uji unit.
 *
 * <p>Tidak ada panggilan OpenAI: seluruh respons di bawah adalah teks tetap
 * yang ditulis di berkas ini, disalin dari {@code docs/api/AI_ASSISTANT_API.md}.
 */
@RunWith(AndroidJUnit4.class)
public class OwnerChatRepositoryTest {

    private static final String PREF_NAME = "valdker_session";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SHOP_CODE = "shop_code";

    private static final long TIMEOUT_SECONDS = 15;

    private MockWebServer server;
    private Context context;
    private SessionManager session;

    /**
     * Sesi sungguhan milik pengguna dipulihkan setelah tiap uji. Berkas
     * preferensi ini sama dengan yang dipakai aplikasi, jadi menuliskan token
     * palsu tanpa mengembalikannya akan mengeluarkan pemilik toko dari
     * aplikasinya sendiri.
     */
    private String savedBaseUrl;
    private String savedToken;
    private String savedShopCode;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        savedBaseUrl = prefs.getString(KEY_BASE_URL, null);
        savedToken = prefs.getString(KEY_TOKEN, null);
        savedShopCode = prefs.getString(KEY_SHOP_CODE, null);

        server = new MockWebServer();
        server.start();

        prefs.edit()
                .putString(KEY_BASE_URL, server.url("/api/").toString())
                .putString(KEY_TOKEN, "test-token-value")
                .putString(KEY_SHOP_CODE, "TESTSHOP")
                .commit();

        session = new SessionManager(context);
    }

    @After
    public void tearDown() throws Exception {
        SharedPreferences.Editor editor =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        restore(editor, KEY_BASE_URL, savedBaseUrl);
        restore(editor, KEY_TOKEN, savedToken);
        restore(editor, KEY_SHOP_CODE, savedShopCode);
        editor.commit();

        server.shutdown();
    }

    private static void restore(SharedPreferences.Editor editor, String key, String value) {
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
    }

    // ------------------------------------------------------------ permintaan

    @Test
    public void requestGoesToTheAiAssistantEndpointWithTheAuthHeader() throws Exception {
        server.enqueue(success("halo", 5));

        Result result = send("Berapa total penjualan toko saya hari ini?", null);
        RecordedRequest request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals("POST", request.getMethod());
        assertEquals("/api/ai/assistant/chat/", request.getPath());
        assertEquals("Token test-token-value", request.getHeader("Authorization"));
        assertEquals("TESTSHOP", request.getHeader("X-Shop-Code"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertTrue(result.succeeded);
    }

    @Test
    public void requestBodyMatchesTheContract() throws Exception {
        server.enqueue(success("halo", 5));

        send("penjualan hari ini", null);
        RecordedRequest request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JSONObject body = new JSONObject(request.getBody().readUtf8());

        assertEquals("penjualan hari ini", body.getString("message"));
        assertTrue(body.has("language"));
        assertFalse("percakapan baru tidak mengirim conversation_id",
                body.has("conversation_id"));

        // Kredensial tidak pernah masuk ke badan permintaan.
        assertFalse(body.has("token"));
        assertFalse(body.has("password"));
    }

    @Test
    public void conversationIdIsSentBackAsANumberOnTheNextTurn() throws Exception {
        server.enqueue(success("lanjut", 42));

        send("pertanyaan kedua", 42);
        RecordedRequest request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JSONObject body = new JSONObject(request.getBody().readUtf8());

        assertEquals(42, body.getInt("conversation_id"));
        assertTrue(body.get("conversation_id") instanceof Integer);
    }

    // --------------------------------------------------------------- sukses

    @Test
    public void successfulReplyIsDeliveredWithItsConversationId() throws Exception {
        server.enqueue(success("Penjualan hari ini $245.00 dari 3 transaksi.", 12));

        Result result = send("penjualan hari ini", null);

        assertTrue(result.succeeded);
        assertEquals("Penjualan hari ini $245.00 dari 3 transaksi.", result.response.replyText);
        assertEquals(Integer.valueOf(12), result.response.conversationId);
        assertEquals("tool", result.response.source);
        assertTrue(result.response.structuredData != null);
    }

    @Test
    public void replyWithoutStructuredDataIsStillASuccess() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"conversation_id\": 3,"
                + "\"message\": \"I can only answer questions about sales, stock, and expenses.\","
                + "\"structured_data\": null,"
                + "\"source\": \"llm\","
                + "\"generated_at\": \"2026-09-16T13:45:00+00:00\","
                + "\"error\": null}"));

        Result result = send("cuaca hari ini?", null);

        assertTrue(result.succeeded);
        assertNull(result.response.structuredData);
        assertEquals("llm", result.response.source);
    }

    // ---------------------------------------------------------------- galat

    @Test
    public void unauthorizedBecomesTheSessionExpiredMessage() throws Exception {
        server.enqueue(errorEnvelope(401, "not_authenticated", "Authentication is required."));

        Result result = send("x", null);

        assertFalse(result.succeeded);
        assertEquals(AssistantError.UNAUTHENTICATED, result.kind);
        assertEquals(context.getString(R.string.owner_chat_session_expired), result.message);
    }

    @Test
    public void forbiddenBecomesThePermissionMessage() throws Exception {
        server.enqueue(errorEnvelope(403, "permission_denied",
                "Owner, admin, manager, or platform admin only."));

        Result result = send("x", null);

        assertEquals(AssistantError.FORBIDDEN, result.kind);
        assertEquals(context.getString(R.string.owner_chat_no_permission), result.message);
    }

    @Test
    public void tooManyRequestsBecomesTheRateLimitMessage() throws Exception {
        server.enqueue(errorEnvelope(429, "rate_limited",
                "Too many requests. Please wait before trying again."));

        Result result = send("x", null);

        assertEquals(AssistantError.RATE_LIMITED, result.kind);
        assertEquals(context.getString(R.string.owner_chat_rate_limited), result.message);
    }

    @Test
    public void providerUnavailableBecomesTheServerMessage() throws Exception {
        server.enqueue(errorEnvelope(503, "ai_provider_unavailable",
                "AI service is temporarily unavailable."));

        Result result = send("x", null);

        assertEquals(AssistantError.SERVER, result.kind);
        assertEquals(context.getString(R.string.owner_chat_server_error), result.message);
    }

    @Test
    public void unknownConversationAsksForAFreshStart() throws Exception {
        server.enqueue(errorEnvelope(404, "not_found", "Not found."));

        Result result = send("x", 99);

        assertEquals(AssistantError.CONVERSATION_GONE, result.kind);
        assertTrue(result.kind.shouldForgetConversation());
        assertEquals(context.getString(R.string.owner_chat_conversation_expired), result.message);
    }

    /**
     * Yang paling berbahaya dari semuanya: sebuah amplop galat membawa kunci
     * {@code message} yang berisi kalimat berbahasa Inggris. Kalau ia lolos
     * sebagai jawaban, pemilik toko membaca teks internal server sebagai
     * kalimat asistennya.
     */
    @Test
    public void errorEnvelopeIsNeverShownAsAnAnswer() throws Exception {
        server.enqueue(errorEnvelope(403, "permission_denied",
                "Owner, admin, manager, or platform admin only."));

        Result result = send("x", null);

        assertFalse(result.succeeded);
        assertFalse(result.message.contains("platform admin"));
    }

    /** Termasuk kalau statusnya 200 - sesuatu yang tidak seharusnya terjadi. */
    @Test
    public void errorEnvelopeOnATwoHundredIsAlsoTreatedAsAnError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"conversation_id\": null,"
                + "\"message\": \"Something went wrong. Please try again.\","
                + "\"structured_data\": null,"
                + "\"source\": \"error\","
                + "\"error\": {\"code\": \"internal_error\", \"detail\": \"boom\"}}"));

        Result result = send("x", null);

        assertFalse(result.succeeded);
        assertEquals(AssistantError.SERVER, result.kind);
    }

    /**
     * Sebaliknya, 200 dengan {@code source: "error"} tetapi tanpa amplop galat
     * adalah jawaban yang sah menurut kontraknya - asisten yang gagal membantu,
     * bukan permintaan yang ditolak.
     */
    @Test
    public void gracefulToolFailureIsStillDisplayedAsAnAnswer() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"conversation_id\": 7,"
                + "\"message\": \"I couldn't calculate that right now.\","
                + "\"structured_data\": null,"
                + "\"source\": \"error\","
                + "\"error\": null}"));

        Result result = send("x", null);

        assertTrue(result.succeeded);
        assertEquals("I couldn't calculate that right now.", result.response.replyText);
    }

    @Test
    public void malformedJsonIsReportedAsUnreadableNotCrashed() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("<html><body>502 Bad Gateway</body></html>"));

        Result result = send("x", null);

        assertFalse(result.succeeded);
        assertEquals(AssistantError.UNREADABLE, result.kind);
        assertEquals(context.getString(R.string.owner_chat_response_unreadable), result.message);
    }

    @Test
    public void successfulResponseWithoutAMessageIsNotShownAsAnEmptyBubble() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"conversation_id\": 1, \"message\": \"\", \"error\": null}"));

        Result result = send("x", null);

        assertFalse(result.succeeded);
        assertEquals(AssistantError.UNREADABLE, result.kind);
    }

    // -------------------------------------------------------------- pembantu

    private static MockResponse success(String message, int conversationId) {
        return new MockResponse().setResponseCode(200).setBody("{"
                + "\"conversation_id\": " + conversationId + ","
                + "\"message\": \"" + message + "\","
                + "\"structured_data\": {\"orders\": 3, \"net_sales\": \"245.00\"},"
                + "\"source\": \"tool\","
                + "\"generated_at\": \"2026-09-16T13:45:00+00:00\","
                + "\"error\": null}");
    }

    private static MockResponse errorEnvelope(int status, String code, String detail) {
        return new MockResponse().setResponseCode(status).setBody("{"
                + "\"conversation_id\": null,"
                + "\"message\": \"" + detail + "\","
                + "\"structured_data\": null,"
                + "\"source\": \"error\","
                + "\"generated_at\": \"2026-09-16T13:45:00+00:00\","
                + "\"error\": {\"code\": \"" + code + "\", \"detail\": \"" + detail + "\"}}");
    }

    private Result send(String message, Integer conversationId) throws Exception {
        OwnerChatRepository repo = new OwnerChatRepository(context, session);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result> holder = new AtomicReference<>();

        repo.sendChat(message, conversationId, new OwnerChatRepository.ChatCallback() {
            @Override
            public void onSuccess(OwnerChatResponse res) {
                holder.set(Result.ok(res));
                latch.countDown();
            }

            @Override
            public void onError(@NonNull AssistantError kind, @NonNull String text) {
                holder.set(Result.failed(kind, text));
                latch.countDown();
            }
        });

        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Tidak ada jawaban dalam " + TIMEOUT_SECONDS + " detik.");
        }
        return holder.get();
    }

    private static final class Result {
        boolean succeeded;
        OwnerChatResponse response;
        AssistantError kind;
        String message = "";

        static Result ok(OwnerChatResponse res) {
            Result r = new Result();
            r.succeeded = true;
            r.response = res;
            return r;
        }

        static Result failed(AssistantError kind, String message) {
            Result r = new Result();
            r.succeeded = false;
            r.kind = kind;
            r.message = message;
            return r;
        }
    }
}
