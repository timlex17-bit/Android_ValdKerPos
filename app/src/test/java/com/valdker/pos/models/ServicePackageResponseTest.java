package com.valdker.pos.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * ServicePackage sekarang bisa dijual lewat POS memakai Product bayangan
 * (product_id) yang disinkronkan backend. Bug lama di sini akan berarti
 * paket ditawarkan untuk dijual padahal tidak punya product_id yang valid,
 * atau sebaliknya paket yang sah ditolak - jadi setiap kombinasi
 * ada/tidak ada, null, dan tidak valid diuji secara eksplisit.
 */
public class ServicePackageResponseTest {

    private static JSONObject basePackage() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", 7);
        obj.put("name", "Oil Change");
        obj.put("description", "Ganti oli mesin");
        obj.put("price", "45.00");
        obj.put("duration_minutes", 30);
        obj.put("is_active", true);
        return obj;
    }

    @Test
    public void productIdIsParsedWhenPresentAndPositive() throws JSONException {
        JSONObject obj = basePackage();
        obj.put("product_id", 108);

        ServicePackageResponse item = ServicePackageResponse.fromJson(obj);

        assertEquals(Integer.valueOf(108), item.productId);
    }

    @Test
    public void productIdIsNullWhenKeyIsMissingEntirely() throws JSONException {
        // Backend lama sebelum field ini ada - key tidak pernah dikirim.
        JSONObject obj = basePackage();

        ServicePackageResponse item = ServicePackageResponse.fromJson(obj);

        assertNull(item.productId);
    }

    @Test
    public void productIdIsNullWhenExplicitJsonNull() throws JSONException {
        // Backfill belum jalan untuk paket ini - backend baru tapi belum sync.
        JSONObject obj = basePackage();
        obj.put("product_id", JSONObject.NULL);

        ServicePackageResponse item = ServicePackageResponse.fromJson(obj);

        assertNull(item.productId);
    }

    @Test
    public void productIdIsNullWhenZeroOrNegative() throws JSONException {
        JSONObject zero = basePackage();
        zero.put("product_id", 0);
        assertNull(ServicePackageResponse.fromJson(zero).productId);

        JSONObject negative = basePackage();
        negative.put("product_id", -3);
        assertNull(ServicePackageResponse.fromJson(negative).productId);
    }

    @Test
    public void productIdIsNullWhenMalformedNonNumericString() throws JSONException {
        JSONObject obj = basePackage();
        obj.put("product_id", "not-a-number");

        ServicePackageResponse item = ServicePackageResponse.fromJson(obj);

        assertNull(item.productId);
    }

    @Test
    public void inactivePackageIsStillParsedButFlaggedInactive() throws JSONException {
        JSONObject obj = basePackage();
        obj.put("is_active", false);
        obj.put("product_id", 108);

        ServicePackageResponse item = ServicePackageResponse.fromJson(obj);

        assertTrue("product_id should still parse for an inactive package", item.productId != null);
        assertEquals(false, item.isActive);
    }

    @Test
    public void isActiveFallsBackToLegacyActiveKeyWhenIsActiveKeyMissing() throws JSONException {
        JSONObject obj = basePackage();
        obj.remove("is_active");
        obj.put("active", false);

        ServicePackageResponse item = ServicePackageResponse.fromJson(obj);

        assertEquals(false, item.isActive);
    }
}
