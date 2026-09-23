package com.valdker.pos.ui.ownerchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Menjaga badan permintaan tetap sama dengan yang ditegakkan
 * {@code ai_assistant/serializers.py}.
 *
 * <p>Ketidakcocokan di sini tidak terlihat saat kompilasi dan tidak terlihat
 * saat aplikasi dijalankan sampai server menjawab 400 - dan pesan 400 itu
 * ditampilkan sebagai "pertanyaan tidak dapat diproses", yang menyembunyikan
 * penyebab sebenarnya.
 */
public class AssistantChatRequestTest {

    @Test
    public void bodyCarriesExactlyTheContractFieldsForANewConversation() throws Exception {
        JSONObject body = AssistantChatRequest.body(
                "Berapa total penjualan toko saya hari ini?", null, "id");

        assertEquals("Berapa total penjualan toko saya hari ini?", body.getString("message"));
        assertEquals("id", body.getString("language"));

        // Percakapan baru berarti kuncinya tidak ada sama sekali - bukan 0,
        // bukan string kosong, keduanya akan ditolak IntegerField.
        assertFalse(body.has("conversation_id"));
        assertEquals(2, body.length());
    }

    @Test
    public void bodyCarriesConversationIdAsAnInteger() throws Exception {
        JSONObject body = AssistantChatRequest.body("lanjut", 12, "en");

        assertTrue(body.get("conversation_id") instanceof Integer);
        assertEquals(12, body.getInt("conversation_id"));
        assertEquals(3, body.length());
    }

    @Test
    public void nonPositiveConversationIdIsTreatedAsNoConversation() throws Exception {
        assertFalse(AssistantChatRequest.body("x", 0, "en").has("conversation_id"));
        assertFalse(AssistantChatRequest.body("x", -3, "en").has("conversation_id"));
    }

    @Test
    public void bodyNeverCarriesCredentialsOrShopCode() throws Exception {
        JSONObject body = AssistantChatRequest.body("x", 1, "en");

        // Autentikasi memakai header; toko diselesaikan server dari pengguna.
        // Sebuah token di dalam badan permintaan akan ikut tersimpan di log
        // akses server mana pun yang mencatat badan permintaan.
        assertFalse(body.has("token"));
        assertFalse(body.has("password"));
        assertFalse(body.has("authorization"));
        assertFalse(body.has("shop_code"));
        assertFalse(body.has("device_id"));
    }

    // ------------------------------------------------------------- header

    @Test
    public void authHeaderAddsTheSchemeWhenMissing() {
        assertEquals("Token abc123", AssistantChatRequest.authHeader("abc123"));
    }

    @Test
    public void authHeaderDoesNotDoubleTheScheme() {
        assertEquals("Token abc123", AssistantChatRequest.authHeader("Token abc123"));
        assertEquals("Token abc123", AssistantChatRequest.authHeader("  Token abc123  "));
    }

    // ------------------------------------------------------------ language

    @Test
    public void indonesianMapsToId() {
        assertEquals("id", AssistantChatRequest.language("id"));
        assertEquals("id", AssistantChatRequest.language("id-ID"));
        assertEquals("id", AssistantChatRequest.language("id_ID"));
    }

    /**
     * {@code Locale.getLanguage()} pada Java masih mengembalikan kode lama
     * {@code "in"} untuk bahasa Indonesia, dan folder terjemahannya pun
     * bernama {@code values-in}. Melewatkannya berarti pemilik toko berbahasa
     * Indonesia menerima jawaban berbahasa Inggris.
     */
    @Test
    public void legacyIndonesianCodeAlsoMapsToId() {
        assertEquals("id", AssistantChatRequest.language("in"));
        assertEquals("id", AssistantChatRequest.language("in-ID"));
    }

    /**
     * Kontraknya hanya menerima {@code en} dan {@code id}; nilai lain dijawab
     * 400. Tetun adalah bahasa ketiga aplikasi ini, jadi ia harus turun ke
     * bahasa Inggris alih-alih membuat setiap pertanyaan gagal.
     */
    @Test
    public void unsupportedLanguagesFallBackToEnglishInsteadOfFailing() {
        assertEquals("en", AssistantChatRequest.language("tet"));
        assertEquals("en", AssistantChatRequest.language("tt"));
        assertEquals("en", AssistantChatRequest.language("pt"));
        assertEquals("en", AssistantChatRequest.language(""));
        assertEquals("en", AssistantChatRequest.language(null));
    }

    @Test
    public void englishStaysEnglish() {
        assertEquals("en", AssistantChatRequest.language("en"));
        assertEquals("en", AssistantChatRequest.language("en-US"));
        assertEquals("en", AssistantChatRequest.language("EN"));
    }

    @Test
    public void contractMessageLimitIsRecorded() {
        assertEquals(2000, AssistantChatRequest.MAX_MESSAGE_LENGTH);
    }
}
