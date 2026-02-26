package com.example.valdker.models;

public class Product {
    public String id;
    public String name;

    // ✅ NEW: SKU
    public String sku;

    // ✅ keep existing field (used by POS add-to-cart)
    public double price;

    public String imageUrl;
    public int stock;

    public String image_url;

    // Category (you already have)
    public String categoryId;
    public String categoryName;

    // ✅ you use "code" on backend, but on android you call it barcode
    public String barcode;

    public String buyPrice;
    public String sellPrice;
    public String weight;

    public String unitId;
    public String unitName;

    public String supplierId;
    public String supplierName;

    public Product() {}

    public Product(String id, String name, String sku, double price, String imageUrl,
                   int stock, String categoryId, String categoryName, String barcode) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.imageUrl = imageUrl;
        this.stock = stock;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.barcode = barcode;
    }
}
