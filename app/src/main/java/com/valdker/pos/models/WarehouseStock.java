package com.valdker.pos.models;

import org.json.JSONException;
import org.json.JSONObject;

public class WarehouseStock {

    private int id;
    private int shopId;
    private String shopName;
    private String shopCode;

    private int warehouse;
    private String warehouseName;
    private String warehouseCode;

    private int product;
    private int productUnit;
    private String productName;
    private String productCode;
    private String productSku;
    private boolean productTrackStock;

    private double quantity;
    private double minStock;
    private double quantityBase;
    private String baseUnitName;
    private String displayStock;
    private String displayMinimumStock;
    private boolean isLowStock;

    private String createdAt;
    private String updatedAt;

    public WarehouseStock() {
    }

    public WarehouseStock(int warehouse, int product, double quantity, double minStock) {
        this(warehouse, product, 0, quantity, minStock);
    }

    public WarehouseStock(int warehouse, int product, int productUnit, double quantity, double minStock) {
        this.warehouse = warehouse;
        this.product = product;
        this.productUnit = productUnit;
        this.quantity = quantity;
        this.minStock = minStock;
    }

    public static WarehouseStock fromJson(JSONObject obj) {
        WarehouseStock stock = new WarehouseStock();
        if (obj == null) return stock;

        stock.id = obj.optInt("id");
        stock.shopId = obj.optInt("shop_id");
        stock.shopName = obj.optString("shop_name", "");
        stock.shopCode = obj.optString("shop_code", "");

        stock.warehouse = obj.optInt("warehouse");
        stock.warehouseName = obj.optString("warehouse_name", "");
        stock.warehouseCode = obj.optString("warehouse_code", "");

        stock.product = obj.optInt("product");
        stock.productUnit = obj.optInt("product_unit");
        stock.productName = obj.optString("product_name", "");
        stock.productCode = obj.optString("product_code", "");
        stock.productSku = obj.optString("product_sku", "");
        stock.productTrackStock = obj.optBoolean("product_track_stock", true);

        stock.quantity = obj.optDouble("quantity", 0);
        stock.minStock = obj.optDouble("minimum_stock", obj.optDouble("min_stock", 0));
        stock.quantityBase = obj.optDouble("quantity_base", stock.quantity);
        stock.baseUnitName = obj.optString("base_unit_name", obj.optString("unit_name", ""));
        stock.displayStock = obj.optString("display_stock", "");
        stock.displayMinimumStock = obj.optString("display_minimum_stock", obj.optString("display_min_stock", ""));
        stock.isLowStock = obj.optBoolean("is_low_stock", false);

        stock.createdAt = obj.optString("created_at", "");
        stock.updatedAt = obj.optString("updated_at", "");

        return stock;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("warehouse", warehouse);
        obj.put("product", product);
        if (productUnit > 0) {
            obj.put("product_unit", productUnit);
        }
        obj.put("quantity", quantity);
        obj.put("minimum_stock", minStock);
        return obj;
    }

    public int getId() { return id; }
    public int getShopId() { return shopId; }
    public String getShopName() { return shopName; }
    public String getShopCode() { return shopCode; }

    public int getWarehouse() { return warehouse; }
    public String getWarehouseName() { return warehouseName; }
    public String getWarehouseCode() { return warehouseCode; }

    public int getProduct() { return product; }
    public int getProductUnit() { return productUnit; }
    public String getProductName() { return productName; }
    public String getProductCode() { return productCode; }
    public String getProductSku() { return productSku; }
    public boolean isProductTrackStock() { return productTrackStock; }

    public double getQuantity() { return quantity; }
    public double getMinStock() { return minStock; }
    public double getQuantityBase() { return quantityBase; }
    public String getBaseUnitName() { return baseUnitName; }
    public String getDisplayStock() { return displayStock; }
    public String getDisplayMinimumStock() { return displayMinimumStock; }
    public boolean isLowStock() { return isLowStock; }

    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public void setWarehouse(int warehouse) { this.warehouse = warehouse; }
    public void setProduct(int product) { this.product = product; }
    public void setProductUnit(int productUnit) { this.productUnit = productUnit; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public void setMinStock(double minStock) { this.minStock = minStock; }
}
