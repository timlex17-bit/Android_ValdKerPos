package com.valdker.pos.ui.ownerchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pemetaan kegagalan asisten ke keadaan yang dilihat pengguna.
 *
 * <p>Tabel galat kontraknya ada di {@code docs/api/AI_ASSISTANT_API.md}.
 */
public class AssistantErrorTest {

    @Test
    public void unauthenticatedIsRecognisedFromCodeAndFromStatus() {
        assertEquals(AssistantError.UNAUTHENTICATED,
                AssistantError.classify(401, "not_authenticated"));
        assertEquals(AssistantError.UNAUTHENTICATED,
                AssistantError.classify(401, null));
    }

    @Test
    public void permissionDeniedIsRecognisedFromCodeAndFromStatus() {
        assertEquals(AssistantError.FORBIDDEN,
                AssistantError.classify(403, "permission_denied"));
        assertEquals(AssistantError.FORBIDDEN,
                AssistantError.classify(403, null));
    }

    /**
     * 429 punya dua arti yang berbeda dan hanya kodenya yang membedakannya.
     * Keduanya berakhir pada satu nasihat yang sama bagi pengguna - tunggu
     * sebentar - tetapi keduanya harus dikenali, bukan jatuh ke "galat tidak
     * dikenal".
     */
    @Test
    public void bothKindsOfRateLimitAreRecognised() {
        assertEquals(AssistantError.RATE_LIMITED,
                AssistantError.classify(429, "rate_limited"));
        assertEquals(AssistantError.RATE_LIMITED,
                AssistantError.classify(429, "ai_rate_limited"));
        assertEquals(AssistantError.RATE_LIMITED,
                AssistantError.classify(429, null));
    }

    /**
     * Penyedia AI yang tidak tersedia datang sebagai 502 maupun 503, dengan
     * satu kode yang sama. Keduanya adalah masalah di sisi server bagi
     * pengguna.
     */
    @Test
    public void providerUnavailableIsAServerProblem() {
        assertEquals(AssistantError.SERVER,
                AssistantError.classify(502, "ai_provider_unavailable"));
        assertEquals(AssistantError.SERVER,
                AssistantError.classify(503, "ai_provider_unavailable"));
        assertEquals(AssistantError.SERVER,
                AssistantError.classify(500, "internal_error"));
        assertEquals(AssistantError.SERVER, AssistantError.classify(500, null));
        assertEquals(AssistantError.SERVER, AssistantError.classify(504, null));
    }

    @Test
    public void validationErrorIsRecognised() {
        assertEquals(AssistantError.INVALID_REQUEST,
                AssistantError.classify(400, "validation_error"));
        assertEquals(AssistantError.INVALID_REQUEST, AssistantError.classify(400, null));
    }

    @Test
    public void unknownConversationIsItsOwnState() {
        assertEquals(AssistantError.CONVERSATION_GONE,
                AssistantError.classify(404, "not_found"));
        assertEquals(AssistantError.CONVERSATION_GONE, AssistantError.classify(404, null));
    }

    /**
     * Hanya percakapan hilang yang boleh menghapus id tersimpan. Menghapusnya
     * pada 401 atau 429 berarti membuang riwayat percakapan hanya karena
     * jaringan sedang sibuk.
     */
    @Test
    public void onlyAGoneConversationClearsTheStoredId() {
        assertTrue(AssistantError.CONVERSATION_GONE.shouldForgetConversation());
        for (AssistantError kind : AssistantError.values()) {
            if (kind == AssistantError.CONVERSATION_GONE) continue;
            assertFalse(kind.name(), kind.shouldForgetConversation());
        }
    }

    /**
     * Kode lebih spesifik daripada status: sebuah proxy yang menulis ulang
     * status tidak boleh membuat galat izin terbaca sebagai galat server.
     */
    @Test
    public void codeWinsOverAContradictoryStatus() {
        assertEquals(AssistantError.FORBIDDEN,
                AssistantError.classify(500, "permission_denied"));
        assertEquals(AssistantError.RATE_LIMITED,
                AssistantError.classify(200, "ai_rate_limited"));
    }

    @Test
    public void unrecognisedFourXxDoesNotPretendToBeSomethingElse() {
        assertEquals(AssistantError.UNKNOWN, AssistantError.classify(418, null));
        assertEquals(AssistantError.UNKNOWN, AssistantError.classify(409, "something_new"));
    }

    @Test
    public void blankCodeIsIgnoredInFavourOfTheStatus() {
        assertEquals(AssistantError.FORBIDDEN, AssistantError.classify(403, "   "));
        assertEquals(AssistantError.SERVER, AssistantError.classify(503, ""));
    }
}
