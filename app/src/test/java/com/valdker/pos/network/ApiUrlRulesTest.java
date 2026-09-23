package com.valdker.pos.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Mengunci perakitan alamat API untuk setiap bentuk base URL yang didukung.
 *
 * <p>Ini bukan uji akademis. Base URL toko datang dari tiga sumber yang
 * bentuknya berbeda-beda - bawaan build rilis berakhiran {@code /api/}, nilai
 * yang diketik pemilik di layar login sering tanpa {@code /api} sama sekali,
 * dan yang tersimpan dari sesi lama bisa keduanya. Satu penanganan yang salah
 * menghasilkan {@code /api/api/...}, yang dijawab 404 oleh server dan terlihat
 * di aplikasi sebagai "tidak dapat menghubungi" tanpa petunjuk apa pun.
 */
public class ApiUrlRulesTest {

    private static final String ASSISTANT = "api/ai/assistant/chat/";

    private String assistantUrlFor(String base) {
        return ApiUrlRules.join(ApiUrlRules.normalizeBase(base, "https://fallback.example/api/"), ASSISTANT);
    }

    // ------------------------------------------------------------ normalize

    @Test
    public void baseKeepsHttpsAndGainsTrailingSlash() {
        assertEquals("https://api.valdker.web.id/api/",
                ApiUrlRules.normalizeBase("https://api.valdker.web.id/api", null));
    }

    @Test
    public void baseWithoutSchemeBecomesHttp() {
        assertEquals("http://192.168.1.10:8000/",
                ApiUrlRules.normalizeBase("192.168.1.10:8000", null));
    }

    @Test
    public void surroundingWhitespaceIsIgnored() {
        assertEquals("https://api.valdker.web.id/api/",
                ApiUrlRules.normalizeBase("  https://api.valdker.web.id/api/  ", null));
    }

    @Test
    public void emptyBaseFallsBackToTheBuildDefault() {
        assertEquals("https://fallback.example/api/",
                ApiUrlRules.normalizeBase("", "https://fallback.example/api/"));
        assertEquals("https://fallback.example/api/",
                ApiUrlRules.normalizeBase(null, "https://fallback.example/api/"));
        assertEquals("https://fallback.example/api/",
                ApiUrlRules.normalizeBase("   ", "https://fallback.example/api/"));
    }

    /**
     * Kedua sumber kosong adalah keadaan yang tidak seharusnya terjadi -
     * {@code BuildConfig.BASE_URL} selalu terisi. Hasilnya bukan alamat yang
     * berguna, dan uji ini ada untuk merekam bahwa perilakunya memang sudah
     * begitu sejak dulu, bukan sesuatu yang berubah saat aturannya dipindahkan
     * keluar dari ApiConfig.
     */
    @Test
    public void bothSourcesEmptyKeepsTheOldOddResult() {
        // "http://" sudah berakhiran garis miring, jadi tidak ada yang
        // ditambahkan lagi - sama persis dengan ApiConfig sebelum aturannya
        // dipindahkan ke sini.
        assertEquals("http://", ApiUrlRules.normalizeBase("", ""));
        assertEquals("http://", ApiUrlRules.normalizeBase(null, null));
    }

    // ----------------------------------------------------------------- join

    @Test
    public void baseEndingInApiSlashDoesNotDuplicateApi() {
        assertEquals("https://api.valdker.web.id/api/ai/assistant/chat/",
                assistantUrlFor("https://api.valdker.web.id/api/"));
    }

    @Test
    public void baseEndingInApiWithoutSlashDoesNotDuplicateApi() {
        assertEquals("https://api.valdker.web.id/api/ai/assistant/chat/",
                assistantUrlFor("https://api.valdker.web.id/api"));
    }

    @Test
    public void baseWithoutApiGainsExactlyOneApiSegment() {
        assertEquals("http://192.168.1.10:8000/api/ai/assistant/chat/",
                assistantUrlFor("192.168.1.10:8000"));
        assertEquals("http://10.0.2.2:8000/api/ai/assistant/chat/",
                assistantUrlFor("http://10.0.2.2:8000/"));
    }

    @Test
    public void emptyBaseStillProducesTheFallbackHostsUrl() {
        assertEquals("https://fallback.example/api/ai/assistant/chat/", assistantUrlFor(""));
    }

    @Test
    public void leadingSlashOnThePathIsStripped() {
        assertEquals("https://api.valdker.web.id/api/ai/assistant/chat/",
                ApiUrlRules.join("https://api.valdker.web.id/api/", "/api/ai/assistant/chat/"));
    }

    /**
     * Path tanpa awalan {@code api/} tetap harus mendarat di tempat yang sama,
     * apa pun bentuk base-nya - inilah yang membuat konstanta endpoint boleh
     * ditulis dengan atau tanpa awalan itu.
     */
    @Test
    public void pathWithoutApiPrefixLandsOnTheSameUrl() {
        assertEquals("https://api.valdker.web.id/api/ai/assistant/chat/",
                ApiUrlRules.join("https://api.valdker.web.id/api/", "ai/assistant/chat/"));
        assertEquals("http://192.168.1.10:8000/api/ai/assistant/chat/",
                ApiUrlRules.join("http://192.168.1.10:8000/", "ai/assistant/chat/"));
    }

    @Test
    public void theOldOwnerChatPathStillResolvesUnchanged() {
        // Endpoint lama tidak dihapus di server; kalau suatu saat ada yang
        // memanggilnya lagi, perakitannya harus tetap seperti dulu.
        assertEquals("https://api.valdker.web.id/api/owner/chat/",
                ApiUrlRules.join("https://api.valdker.web.id/api/", "api/owner/chat/"));
    }

    @Test
    public void bareApiPathCollapsesInsteadOfDoubling() {
        assertEquals("https://api.valdker.web.id/api",
                ApiUrlRules.join("https://api.valdker.web.id/api/", "api/"));
    }
}
