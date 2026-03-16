package com.example.valdker.models;

public class Product {
    public String id;
    public String name;

    // ✅ multi-tenant
    public int shopId;

    // optional alias if some code references snake_case
    public int shop_id;

    public String sku;
    public double price;

    public String imageUrl;
    public int stock;

    public String image_url;

    public String categoryId;
    public String categoryName;

    public String barcode;

    public String buyPrice;
    public String sellPrice;
    public String weight;

    public String unitId;
    public String unitName;

    public String supplierId;
    public String supplierName;

    public Product() {}

    public Product(String id,
                   String name,
                   int shopId,
                   String sku,
                   double price,
                   String imageUrl,
                   int stock,
                   String categoryId,
                   String categoryName,
                   String barcode) {
        this.id = id;
        this.name = name;
        this.shopId = shopId;
        this.shop_id = shopId;
        this.sku = sku;
        this.price = price;
        this.imageUrl = imageUrl;
        this.stock = stock;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.barcode = barcode;
    }
}