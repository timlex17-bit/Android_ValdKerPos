package com.valdker.pos.models;

import org.json.JSONException;
import org.json.JSONObject;

public class Warehouse {

    private int id;
    private int shopId;
    private String shopName;
    private String shopCode;
    private String name;
    private String code;
    private String location;
    private boolean isActive;
    private boolean isDefault;
    private String createdAt;
    private String updatedAt;

    public Warehouse() {
    }

    public Warehouse(String name, String code, String location, boolean isActive, boolean isDefault) {
        this.name = name;
        this.code = code;
        this.location = location;
        this.isActive = isActive;
        this.isDefault = isDefault;
    }

    public static Warehouse fromJson(JSONObject obj) {
        Warehouse warehouse = new Warehouse();

        if (obj == null) return warehouse;

        warehouse.id = obj.optInt("id");
        warehouse.shopId = obj.optInt("shop_id");
        warehouse.shopName = obj.optString("shop_name", "");
        warehouse.shopCode = obj.optString("shop_code", "");
        warehouse.name = obj.optString("name", "");
        warehouse.code = obj.optString("code", "");
        warehouse.location = obj.optString("location", "");
        warehouse.isActive = obj.optBoolean("is_active", true);
        warehouse.isDefault = obj.optBoolean("is_default", false);
        warehouse.createdAt = obj.optString("created_at", "");
        warehouse.updatedAt = obj.optString("updated_at", "");

        return warehouse;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("code", code);
        obj.put("location", location);
        obj.put("is_active", isActive);
        obj.put("is_default", isDefault);
        return obj;
    }

    public int getId() {
        return id;
    }

    public int getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public String getShopCode() {
        return shopCode;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        String display = name == null || name.trim().isEmpty() ? "Warehouse #" + id : name.trim();

        if (code != null && !code.trim().isEmpty()) {
            display += " - " + code.trim();
        }

        return display;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getCode() {
        return code;
    }

    public String getLocation() {
        return location;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setShopId(int shopId) {
        this.shopId = shopId;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public void setShopCode(String shopCode) {
        this.shopCode = shopCode;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}