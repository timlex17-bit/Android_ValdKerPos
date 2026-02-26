package com.example.valdker.offline.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.valdker.SessionManager;
import com.example.valdker.offline.db.ValdkerDatabase;
import com.example.valdker.offline.db.entities.OrderEntity;
import com.example.valdker.offline.db.entities.OrderItemEntity;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class OrderSyncWorker extends Worker {

    private static final String TAG = "OrderSyncWorker";

    public OrderSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ValdkerDatabase db = ValdkerDatabase.get(getApplicationContext());

            List<OrderEntity> pending = db.orderDao().getPendingSync(20);
            if (pending == null || pending.isEmpty()) return Result.success();

            SessionManager sm = new SessionManager(getApplicationContext());
            String token = sm.getToken();
            if (token == null || token.trim().isEmpty()) {
                Log.e(TAG, "No token -> retry later");
                return Result.retry();
            }

            for (OrderEntity o : pending) {
                List<OrderItemEntity> items = db.orderItemDao().listByOrderId(o.id);

                JSONObject payload = new JSONObject();
                payload.put("token", token.trim());
                payload.put("subtotal", o.subtotal);
                payload.put("order_type", o.order_type);
                payload.put("default_order_type", o.order_type);

                payload.put("table_number", o.table_number == null ? "" : o.table_number);
                payload.put("delivery_address", o.delivery_address == null ? "" : o.delivery_address);
                payload.put("delivery_fee", o.delivery_fee);

                // items
                JSONArray arr = new JSONArray();
                for (OrderItemEntity it : items) {
                    JSONObject row = new JSONObject();
                    row.put("product", it.product_id);
                    row.put("quantity", it.quantity);
                    row.put("order_type", it.order_type == null ? "" : it.order_type.toUpperCase(Locale.US));
                    arr.put(row);
                }
                payload.put("items", arr);
                payload.put("cart", arr);

                // TODO: ganti ini sesuai networking yang kamu pakai (Volley/OkHttp/Retrofit)
                // Di sini konsepnya:
                // String serverId = postToServer(payload);
                // if success:
                // db.orderDao().markSynced(o.id, serverId, nowIso);

                boolean ok = FakeNetwork.post(payload); // placeholder
                if (ok) {
                    String serverId = String.valueOf(o.id); // ganti dengan id dari server
                    db.orderDao().markSynced(o.id, serverId, Iso.now());
                } else {
                    return Result.retry();
                }
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Sync error", e);
            return Result.retry();
        }
    }

    // ===== helpers kecil =====
    private static class Iso {
        static String now() {
            return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .format(new java.util.Date());
        }
    }

    // placeholder biar compile dulu
    private static class FakeNetwork {
        static boolean post(JSONObject payload) {
            Log.d(TAG, "POST payload: " + payload);
            return true;
        }
    }
}