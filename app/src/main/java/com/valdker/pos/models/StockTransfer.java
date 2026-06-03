package com.valdker.pos.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class StockTransfer {

    public int id;
    public int shopId;
    public String shopName;
    public String shopCode;
    public String referenceNo;

    public int fromWarehouse;
    public String fromWarehouseName;
    public String fromWarehouseCode;

    public int toWarehouse;
    public String toWarehouseName;
    public String toWarehouseCode;

    public String note;
    public String status;

    public int createdBy;
    public String createdByName;
    public String completedByName;
    public String cancelledByName;

    public String completedAt;
    public String cancelledAt;
    public String createdAt;
    public String updatedAt;

    public ArrayList<StockTransferItem> items = new ArrayList<>();

    public StockTransfer() {
    }

    public StockTransfer(int fromWarehouse, int toWarehouse, String note, ArrayList<StockTransferItem> items) {
        this.fromWarehouse = fromWarehouse;
        this.toWarehouse = toWarehouse;
        this.note = note;
        this.items = items;
    }

    public static StockTransfer fromJson(JSONObject obj) {
        StockTransfer transfer = new StockTransfer();
        if (obj == null) return transfer;

        transfer.id = obj.optInt("id");
        transfer.shopId = obj.optInt("shop_id");
        transfer.shopName = obj.optString("shop_name", "");
        transfer.shopCode = obj.optString("shop_code", "");
        transfer.referenceNo = obj.optString("reference_no", "");

        transfer.fromWarehouse = obj.optInt("from_warehouse");
        transfer.fromWarehouseName = obj.optString("from_warehouse_name", "");
        transfer.fromWarehouseCode = obj.optString("from_warehouse_code", "");

        transfer.toWarehouse = obj.optInt("to_warehouse");
        transfer.toWarehouseName = obj.optString("to_warehouse_name", "");
        transfer.toWarehouseCode = obj.optString("to_warehouse_code", "");

        transfer.note = obj.optString("note", "");
        transfer.status = obj.optString("status", "");

        transfer.createdBy = obj.optInt("created_by");
        transfer.createdByName = obj.optString("created_by_name", "");
        transfer.completedByName = obj.optString("completed_by_name", "");
        transfer.cancelledByName = obj.optString("cancelled_by_name", "");

        transfer.completedAt = obj.optString("completed_at", "");
        transfer.cancelledAt = obj.optString("cancelled_at", "");
        transfer.createdAt = obj.optString("created_at", "");
        transfer.updatedAt = obj.optString("updated_at", "");

        JSONArray arr = obj.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject itemObj = arr.optJSONObject(i);
                if (itemObj != null) {
                    transfer.items.add(StockTransferItem.fromJson(itemObj));
                }
            }
        }

        return transfer;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();

        obj.put("from_warehouse", fromWarehouse);
        obj.put("to_warehouse", toWarehouse);
        obj.put("note", note == null ? "" : note);

        JSONArray arr = new JSONArray();

        if (items != null) {
            for (StockTransferItem item : items) {
                arr.put(item.toJson());
            }
        }

        obj.put("items", arr);

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

    public String getReferenceNo() {
        return referenceNo;
    }

    public int getFromWarehouse() {
        return fromWarehouse;
    }

    public String getFromWarehouseName() {
        return fromWarehouseName;
    }

    public String getFromWarehouseCode() {
        return fromWarehouseCode;
    }

    public int getToWarehouse() {
        return toWarehouse;
    }

    public String getToWarehouseName() {
        return toWarehouseName;
    }

    public String getToWarehouseCode() {
        return toWarehouseCode;
    }

    public String getNote() {
        return note;
    }

    public String getStatus() {
        return status;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public String getCompletedByName() {
        return completedByName;
    }

    public String getCancelledByName() {
        return cancelledByName;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public String getCancelledAt() {
        return cancelledAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public ArrayList<StockTransferItem> getItems() {
        return items;
    }

    public void setFromWarehouse(int fromWarehouse) {
        this.fromWarehouse = fromWarehouse;
    }

    public void setToWarehouse(int toWarehouse) {
        this.toWarehouse = toWarehouse;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setItems(ArrayList<StockTransferItem> items) {
        this.items = items;
    }
}