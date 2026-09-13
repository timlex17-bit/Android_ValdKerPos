package com.valdker.pos.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.valdker.pos.network.BaseUrlRules.Verdict;

import org.junit.Test;

/**
 * Aturan alamat backend diuji di sini karena inilah satu-satunya tempat
 * "rentang privat" benar-benar ditegakkan - network_security_config.xml tidak
 * bisa menyatakan rentang sama sekali.
 */
public class BaseUrlRulesTest {

    // ------------------------------------------------------------ hostOf

    @Test
    public void hostOf_stripsSchemePortAndPath() {
        assertEquals("192.168.1.10", BaseUrlRules.hostOf("http://192.168.1.10:8000/api/"));
        assertEquals("api.valdker.web.id", BaseUrlRules.hostOf("https://api.valdker.web.id/api/"));
        assertEquals("10.0.2.2", BaseUrlRules.hostOf("http://10.0.2.2:8000"));
    }

    @Test
    public void hostOf_stripsCredentialsAndHandlesIpv6() {
        assertEquals("10.1.2.3", BaseUrlRules.hostOf("http://user:pass@10.1.2.3:8000/api/"));
        assertEquals("::1", BaseUrlRules.hostOf("http://[::1]:8000/api/"));
    }

    @Test
    public void hostOf_emptyWhenThereIsNoHost() {
        assertEquals("", BaseUrlRules.hostOf("http:///api/"));
        assertEquals("", BaseUrlRules.hostOf(""));
        assertEquals("", BaseUrlRules.hostOf(null));
    }

    // ------------------------------------------------------- isPrivateHost

    @Test
    public void privateRangesAreAccepted() {
        assertTrue(BaseUrlRules.isPrivateHost("10.0.2.2"));       // alias emulator
        assertTrue(BaseUrlRules.isPrivateHost("10.255.255.255"));
        assertTrue(BaseUrlRules.isPrivateHost("192.168.1.10"));
        assertTrue(BaseUrlRules.isPrivateHost("192.168.0.1"));
        assertTrue(BaseUrlRules.isPrivateHost("172.16.0.1"));
        assertTrue(BaseUrlRules.isPrivateHost("172.31.255.254"));
        assertTrue(BaseUrlRules.isPrivateHost("127.0.0.1"));
        assertTrue(BaseUrlRules.isPrivateHost("169.254.10.1"));
        assertTrue(BaseUrlRules.isPrivateHost("localhost"));
        assertTrue(BaseUrlRules.isPrivateHost("kasir.local"));
        assertTrue(BaseUrlRules.isPrivateHost("::1"));
    }

    @Test
    public void publicAndNearMissAddressesAreRejected() {
        assertFalse(BaseUrlRules.isPrivateHost("8.8.8.8"));
        assertFalse(BaseUrlRules.isPrivateHost("api.valdker.web.id"));
        // Tepat di luar 172.16/12 di kedua sisi.
        assertFalse(BaseUrlRules.isPrivateHost("172.15.0.1"));
        assertFalse(BaseUrlRules.isPrivateHost("172.32.0.1"));
        // 192.169 bukan 192.168.
        assertFalse(BaseUrlRules.isPrivateHost("192.169.1.1"));
        // Bentuk yang menyerupai IP tapi tidak sah.
        assertFalse(BaseUrlRules.isPrivateHost("10.0.0"));
        assertFalse(BaseUrlRules.isPrivateHost("10.0.0.256"));
        assertFalse(BaseUrlRules.isPrivateHost("10.0.0.01a"));
        // Nama yang cuma diawali "localhost" bukan loopback.
        assertFalse(BaseUrlRules.isPrivateHost("localhost.attacker.com"));
    }

    // -------------------------------------------------------------- check

    @Test
    public void debugBuildAcceptsLanOverHttp() {
        assertEquals(Verdict.OK, BaseUrlRules.check("http://192.168.1.10:8000/api/", true));
        assertEquals(Verdict.OK, BaseUrlRules.check("http://10.0.2.2:8000/api/", true));
    }

    @Test
    public void debugBuildStillRefusesCleartextToPublicHost() {
        assertEquals(Verdict.CLEARTEXT_PUBLIC_HOST,
                BaseUrlRules.check("http://api.valdker.web.id/api/", true));
        assertEquals(Verdict.CLEARTEXT_PUBLIC_HOST,
                BaseUrlRules.check("http://8.8.8.8/api/", true));
    }

    @Test
    public void releaseBuildRefusesAllCleartext() {
        // Bahkan ke LAN: build rilis menyetel usesCleartextTraffic="false",
        // jadi menyimpannya hanya akan berakhir sebagai kegagalan jaringan
        // yang membingungkan.
        assertEquals(Verdict.CLEARTEXT_NOT_ALLOWED_IN_RELEASE,
                BaseUrlRules.check("http://192.168.1.10:8000/api/", false));
    }

    @Test
    public void httpsIsAlwaysFine() {
        assertEquals(Verdict.OK, BaseUrlRules.check("https://api.valdker.web.id/api/", true));
        assertEquals(Verdict.OK, BaseUrlRules.check("https://api.valdker.web.id/api/", false));
    }

    @Test
    public void obviousMistakesAreNamedSpecifically() {
        assertEquals(Verdict.EMPTY, BaseUrlRules.check("   ", true));
        assertEquals(Verdict.BAD_SCHEME, BaseUrlRules.check("ftp://192.168.1.10/", true));
        assertEquals(Verdict.BAD_SCHEME, BaseUrlRules.check("192.168.1.10:8000", true));
        assertEquals(Verdict.BAD_HOST, BaseUrlRules.check("http:///api/", true));
    }

    // ---------------------------------------------------------- normalize

    @Test
    public void normalizeAddsSchemeAndTrailingSlash() {
        assertEquals("http://192.168.1.10:8000/", BaseUrlRules.normalize("192.168.1.10:8000"));
        assertEquals("http://192.168.1.10:8000/api/", BaseUrlRules.normalize("http://192.168.1.10:8000/api"));
        assertEquals("https://api.valdker.web.id/api/",
                BaseUrlRules.normalize("  https://api.valdker.web.id/api/  "));
    }

    @Test
    public void normalizeCollapsesTrailingSlashes() {
        assertEquals("http://10.0.2.2:8000/api/", BaseUrlRules.normalize("http://10.0.2.2:8000/api///"));
    }
}
