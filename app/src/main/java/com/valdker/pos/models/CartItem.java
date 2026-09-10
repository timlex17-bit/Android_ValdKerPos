package com.valdker.pos.models;

import androidx.annotation.NonNull;

import com.valdker.pos.money.Money;

public class CartItem {

    public static final String ORDER_TYPE_DINE_IN = "DINE_IN";
    public static final String ORDER_TYPE_TAKE_OUT = "TAKE_OUT";
    public static final String ORDER_TYPE_DELIVERY = "DELIVERY";

    public static final String ITEM_TYPE_PRODUCT = "PRODUCT";
    public static final String ITEM_TYPE_SERVICE = "SERVICE";
    public static final String ITEM_TYPE_PART = "PART";

    public int productId;
    public int servicePackageId;
    public int shopId;

    public String cartKey = "";
    public String name;

    /**
     * Harga satuan sebagai double. Dipertahankan karena 40 tempat masih
     * membacanya langsung; {@link #priceDecimal} adalah nilai yang otoritatif
     * dan keduanya selalu disinkronkan lewat {@link #setPrice}.
     */
    public double price;

    /**
     * Harga satuan sebagai teks desimal eksak ("1.75"), bentuk yang sama
     * dengan yang dikirim backend. Ini sumber kebenaran untuk semua
     * perhitungan; {@link #price} hanya turunannya.
     */
    public String priceDecimal = "";
    public String imageUrl;
    public int qty;

    public String orderType = "";
    public String itemType = ITEM_TYPE_PRODUCT;

    public CartItem() {
    }

    public CartItem(int productId,
                    int shopId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty) {
        this(productId, shopId, name, price, imageUrl, qty, "", ITEM_TYPE_PRODUCT);
    }

    public CartItem(int productId,
                    int shopId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty,
                    @NonNull String orderType) {
        this(productId, shopId, name, price, imageUrl, qty, orderType, ITEM_TYPE_PRODUCT);
    }

    public CartItem(int productId,
                    int shopId,
                    @NonNull String name,
                    double price,
                    String imageUrl,
                    int qty,
                    @NonNull String orderType,
                    @NonNull String itemType) {

        this.productId = productId;
        this.shopId = shopId;
        this.name = name;
        setPrice(Money.ofDouble(price));
        this.imageUrl = imageUrl;
        this.qty = qty;
        this.orderType = orderType;
        this.itemType = normalizeItemType(itemType);
        this.cartKey = buildCartKey(this.productId, this.itemType);
    }

    /** Menyetel harga satuan dan menjaga kedua representasi tetap sinkron. */
    public void setPrice(@NonNull Money value) {
        this.priceDecimal = value.toPlainString();
        this.price = value.toDouble();
    }

    /** Harga satuan yang otoritatif. */
    @NonNull
    public Money price() {
        // priceDecimal kosong hanya untuk item yang dibaca dari keranjang
        // tersimpan versi lama, sebelum field ini ada.
        return priceDecimal == null || priceDecimal.trim().isEmpty()
                ? Money.ofDouble(price)
                : Money.of(priceDecimal);
    }

    /** Total baris yang eksak: qty x harga satuan. */
    @NonNull
    public Money lineTotal() {
        return price().times(qty);
    }

    /**
     * @deprecated pakai {@link #lineTotal()}. Dipertahankan untuk pemanggil
     * yang belum dimigrasi; nilainya sudah dihitung eksak lalu baru dikonversi.
     */
    @Deprecated
    public double getLineTotal() {
        return lineTotal().toDouble();
    }

    @NonNull
    public static String normalizeItemType(String raw) {
        if (raw == null) return ITEM_TYPE_PRODUCT;

        String value = raw.trim().toUpperCase();

        switch (value) {
            case "SERVICE":
            case "SERVICE_PACKAGE":
            case "SERVICEPACKAGE":
            case "PACKAGE":
                return ITEM_TYPE_SERVICE;
            case "SPAREPART":
            case "PART":
                return ITEM_TYPE_PART;
            case "PRODUCT":
            default:
                return ITEM_TYPE_PRODUCT;
        }
    }

    @NonNull
    public static String buildCartKey(int productId, String itemType) {
        return normalizeItemType(itemType) + ":" + productId;
    }

    @NonNull
    public static String buildCartKey(int productId, String itemType, int servicePackageId) {
        String normalizedType = normalizeItemType(itemType);
        if (servicePackageId > 0) {
            return normalizedType + ":PACKAGE:" + servicePackageId;
        }
        return buildCartKey(productId, normalizedType);
    }

    public void refreshCartKey() {
        this.itemType = normalizeItemType(this.itemType);
        this.cartKey = buildCartKey(this.productId, this.itemType, this.servicePackageId);
    }
}
