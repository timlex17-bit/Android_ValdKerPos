package com.valdker.pos.models;

import org.json.JSONException;
import org.json.JSONObject;

public class StockTransferItem {

    private int id;
    private int product;
    private int productUnit;
    private String productName;
    private String productCode;
    private String productSku;
    private double quantity;
    private double quantityInput;
    private double quantityBase;
    private String note;

    public StockTransferItem() {
    }

    public StockTransferItem(int product, double quantity) {
        this(product, 0, quantity);
    }

    public StockTransferItem(int product, int productUnit, double quantityInput) {
        this.product = product;
        this.productUnit = productUnit;
        this.quantity = quantityInput;
        this.quantityInput = quantityInput;
    }

    public static StockTransferItem fromJson(JSONObject obj) {
        StockTransferItem item = new StockTransferItem();
        if (obj == null) return item;

        item.id = obj.optInt("id");
        item.product = obj.optInt("product");
        item.productUnit = obj.optInt("product_unit", obj.optInt("product_unit_id", 0));
        item.productName = obj.optString("product_name", "");
        item.productCode = obj.optString("product_code", "");
        item.productSku = obj.optString("product_sku", "");
        item.quantity = obj.optDouble("quantity", 0);
        item.quantityInput = obj.optDouble("quantity_input", item.quantity);
        item.quantityBase = obj.optDouble("quantity_base", item.quantity);
        item.note = obj.optString("note", "");

        return item;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("product", product);
        obj.put("quantity_input", quantityInput > 0 ? quantityInput : quantity);
        if (productUnit > 0) {
            obj.put("product_unit", productUnit);
        }

        if (note != null && !note.trim().isEmpty()) {
            obj.put("note", note);
        }

        return obj;
    }

    public int getId() { return id; }
    public int getProduct() { return product; }
    public int getProductUnit() { return productUnit; }
    public String getProductName() { return productName; }
    public String getProductCode() { return productCode; }
    public String getProductSku() { return productSku; }
    public double getQuantity() { return quantity; }
    public double getQuantityInput() { return quantityInput > 0 ? quantityInput : quantity; }
    public double getQuantityBase() { return quantityBase; }
    public String getNote() { return note; }

    public void setProduct(int product) { this.product = product; }
    public void setProductUnit(int productUnit) { this.productUnit = productUnit; }
    public void setQuantity(double quantity) {
        this.quantity = quantity;
        this.quantityInput = quantity;
    }
    public void setQuantityInput(double quantityInput) { this.quantityInput = quantityInput; }
    public void setNote(String note) { this.note = note; }
}
