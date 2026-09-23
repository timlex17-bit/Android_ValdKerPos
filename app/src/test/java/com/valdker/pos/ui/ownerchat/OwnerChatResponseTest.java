package com.valdker.pos.ui.ownerchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Pembacaan respons asisten.
 *
 * <p>Bentuk JSON di bawah disalin dari {@code docs/api/AI_ASSISTANT_API.md},
 * yang pada gilirannya disusun dari {@code ai_assistant/tests/test_chat_api.py}
 * - jadi yang diuji di sini adalah bentuk sungguhan, bukan bentuk yang saya
 * bayangkan.
 */
public class OwnerChatResponseTest {

    private static OwnerChatResponse parse(String json) throws Exception {
        return OwnerChatResponse.fromJson(new JSONObject(json));
    }

    // ------------------------------------------------------------- sukses

    @Test
    public void toolBackedReplyIsReadCompletely() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"conversation_id\": 12,"
                + "\"message\": \"Here's what I found (todays sales).\","
                + "\"structured_data\": {\"orders\": 3, \"net_sales\": \"245.00\","
                + " \"currency\": \"USD\"},"
                + "\"source\": \"tool\","
                + "\"generated_at\": \"2026-09-16T13:45:00.123456+00:00\","
                + "\"error\": null}");

        assertEquals("Here's what I found (todays sales).", res.replyText);
        assertTrue(res.hasReplyText());
        assertEquals(Integer.valueOf(12), res.conversationId);
        assertEquals("tool", res.source);
        assertEquals("2026-09-16T13:45:00.123456+00:00", res.generatedAt);
        assertNotNull(res.structuredData);
        assertEquals(3, res.structuredData.getInt("orders"));
        assertEquals("245.00", res.structuredData.getString("net_sales"));
        assertFalse(res.hasError());

        // structuredDataRaw is the same object, held without narrowing to
        // JSONObject - StructuredDataMapper reads this one, not
        // structuredData (kept only for callers already using .getInt/
        // .getString directly, see the field's own docstring).
        assertNotNull(res.structuredDataRaw);
        assertTrue(res.structuredDataRaw instanceof JSONObject);
    }

    @Test
    public void plainReplyWithoutStructuredDataIsStillAnAnswer() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"conversation_id\": 7,"
                + "\"message\": \"I can only answer questions about sales, stock, and expenses.\","
                + "\"structured_data\": null,"
                + "\"source\": \"llm\","
                + "\"generated_at\": \"2026-09-16T13:45:00+00:00\","
                + "\"error\": null}");

        assertTrue(res.hasReplyText());
        assertNull(res.structuredData);
        assertNull(res.structuredDataRaw);
        assertEquals("llm", res.source);
        assertFalse(res.hasError());
    }

    /**
     * Ketika model mengusulkan argumen alat yang tidak sah, server menjawab 200
     * dengan penjelasan berbahasa manusia dan {@code source: "error"} tetapi
     * {@code error: null}. Itu jawaban yang sah - asisten yang gagal membantu,
     * bukan permintaan yang ditolak - dan harus tetap ditampilkan.
     */
    @Test
    public void sourceErrorWithoutAnErrorEnvelopeIsStillADisplayableAnswer() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"conversation_id\": 7,"
                + "\"message\": \"I couldn't calculate that right now. Please try again in a moment.\","
                + "\"structured_data\": null,"
                + "\"source\": \"error\","
                + "\"generated_at\": \"2026-09-16T13:45:00+00:00\","
                + "\"error\": null}");

        assertTrue(res.hasReplyText());
        assertFalse("source=error bukan amplop galat", res.hasError());
        assertNull(res.errorCode);
    }

    @Test
    public void conversationIdIsReadFromANumberAndFromANumericString() throws Exception {
        assertEquals(Integer.valueOf(12), parse("{\"conversation_id\": 12}").conversationId);
        assertEquals(Integer.valueOf(12), parse("{\"conversation_id\": \"12\"}").conversationId);
    }

    @Test
    public void missingOrNullConversationIdStaysNull() throws Exception {
        assertNull(parse("{\"message\":\"hi\"}").conversationId);
        assertNull(parse("{\"conversation_id\": null}").conversationId);
        assertNull(parse("{\"conversation_id\": 0}").conversationId);
        assertNull(parse("{\"conversation_id\": \"abc\"}").conversationId);
    }

    // -------------------------------------------------------- amplop galat

    @Test
    public void errorEnvelopeIsNeverMistakenForAnAnswer() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"conversation_id\": null,"
                + "\"message\": \"Owner, admin, manager, or platform admin only.\","
                + "\"structured_data\": null,"
                + "\"source\": \"error\","
                + "\"generated_at\": \"2026-09-16T13:45:00+00:00\","
                + "\"error\": {\"code\": \"permission_denied\","
                + " \"detail\": \"Owner, admin, manager, or platform admin only.\"}}");

        assertTrue(res.hasError());
        assertEquals("permission_denied", res.errorCode);
    }

    @Test
    public void validationErrorKeepsItsCodeEvenWhenDetailIsADict() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"conversation_id\": null,"
                + "\"message\": \"{'message': [ErrorDetail(string='This field may not be blank.')]}\","
                + "\"source\": \"error\","
                + "\"error\": {\"code\": \"validation_error\","
                + " \"detail\": {\"message\": [\"This field may not be blank.\"]}}}");

        assertTrue(res.hasError());
        assertEquals("validation_error", res.errorCode);
    }

    // -------------------------------------------- kompatibilitas ke belakang

    /**
     * Endpoint lama memakai {@code reply_text}. Tetap terbaca, supaya sebuah
     * build lama yang masih memanggilnya tidak mendadak menampilkan
     * "respons tidak dapat dipahami".
     */
    @Test
    public void legacyReplyTextIsStillReadWhenThereIsNoMessage() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"reply_text\": \"Vendas hari ini $0.00\","
                + "\"cards\": [{\"label\": \"Total Pedido\", \"value\": \"0\"}],"
                + "\"links\": [{\"title\": \"Laporan\", \"url\": \"https://x.test/r\"}]}");

        assertEquals("Vendas hari ini $0.00", res.replyText);
        assertEquals(1, res.cards.size());
        assertEquals("Total Pedido", res.cards.get(0).label);
        assertEquals(1, res.links.size());
        assertEquals("https://x.test/r", res.links.get(0).url);
        // No structured_data key at all in this legacy shape - must not be
        // confused with an explicit JSON null.
        assertNull(res.structuredDataRaw);
    }

    /**
     * Endpoint lama menyalin {@code reply_text} ke {@code message} juga. Kalau
     * urutan pembacaan mendahulukan {@code reply_text}, sebuah respons yang
     * membawa keduanya akan menampilkan teks lama alih-alih jawaban AI - itulah
     * alasan {@code message} diperiksa lebih dulu.
     */
    @Test
    public void staleReplyTextNeverOverridesTheAiMessage() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"message\": \"jawaban AI\","
                + "\"reply_text\": \"template lama\","
                + "\"reply\": \"template lama\"}");

        assertEquals("jawaban AI", res.replyText);
    }

    @Test
    public void answerFieldIsAcceptedAsALastResort() throws Exception {
        assertEquals("dari answer", parse("{\"answer\": \"dari answer\"}").replyText);
    }

    @Test
    public void legacyStringErrorEnvelopeIsAlsoRecognised() throws Exception {
        OwnerChatResponse res = parse("{\"ok\": false, \"error\": \"Gagal\","
                + " \"message\": \"Gagal\"}");
        assertTrue(res.hasError());
    }

    @Test
    public void legacyOkTrueIsNotAnError() throws Exception {
        assertFalse(parse("{\"ok\": true, \"message\": \"halo\"}").hasError());
    }

    // ------------------------------------------------------- bentuk rusak

    @Test
    public void emptyObjectYieldsNoReplyRatherThanCrashing() throws Exception {
        OwnerChatResponse res = parse("{}");
        assertFalse(res.hasReplyText());
        assertFalse(res.hasError());
        assertNull(res.conversationId);
        assertNull(res.structuredData);
        assertTrue(res.cards.isEmpty());
        assertTrue(res.links.isEmpty());
    }

    /**
     * {@code JSONObject.optString} mengembalikan teks {@code "null"} untuk
     * sebuah null JSON. Tanpa penanganan khusus, kata itu lolos setiap
     * pemeriksaan "tidak kosong" dan tampil di gelembung pesan sebagai jawaban.
     */
    @Test
    public void jsonNullMessageDoesNotBecomeTheWordNull() throws Exception {
        OwnerChatResponse res = parse("{\"message\": null, \"reply_text\": null}");
        assertEquals("", res.replyText);
        assertFalse(res.hasReplyText());
    }

    @Test
    public void malformedEntriesInsideArraysAreSkippedNotRendered() throws Exception {
        OwnerChatResponse res = parse("{"
                + "\"message\": \"ok\","
                + "\"cards\": [{\"label\": \"tanpa nilai\"}, \"bukan objek\", null,"
                + " {\"label\": \"A\", \"value\": \"1\"}],"
                + "\"links\": [{\"title\": \"tanpa url\"}, {\"url\": \"https://x.test\"},"
                + " {\"title\": \"T\", \"url\": \"https://y.test\"}]}");

        assertEquals(1, res.cards.size());
        assertEquals("A", res.cards.get(0).label);
        assertEquals(1, res.links.size());
        assertEquals("T", res.links.get(0).title);
    }

    @Test
    public void whitespaceOnlyMessageCountsAsNoAnswer() throws Exception {
        assertFalse(parse("{\"message\": \"   \"}").hasReplyText());
    }
}
