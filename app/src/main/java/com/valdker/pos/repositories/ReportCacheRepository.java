package com.valdker.pos.repositories;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.valdker.pos.SessionManager;
import com.valdker.pos.local.CachedReportEntity;
import com.valdker.pos.local.ValoraLocalDatabase;
import com.valdker.pos.network.ApiClient;
import com.valdker.pos.utils.NetworkUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportCacheRepository {

    public static final String NO_LOCAL_DATA_MESSAGE = "No local report data found. Please connect to internet and sync once.";
    public static final String NO_LOCAL_DASHBOARD_DATA_MESSAGE = "No local dashboard data found. Please connect to internet and sync once.";
    public static final String EXPORT_REQUIRES_INTERNET_MESSAGE = "Export requires internet connection.";
    public static final String TYPE_DASHBOARD_SUMMARY = "dashboard_summary";
    public static final long STALE_MS = 24L * 60L * 60L * 1000L;

    private static final long CACHE_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L;

    public interface RoomFirstCallback {
        void onLocal(@NonNull ReportRepository.ReportResponse response, @NonNull CacheInfo cacheInfo);
        void onRemote(@NonNull ReportRepository.ReportResponse response, @NonNull CacheInfo cacheInfo);
        void onNoInternet(boolean hasLocalData);
        void onError(int statusCode, @NonNull String message, boolean hasLocalData);
    }

    public static class CacheInfo {
        public final boolean cached;
        public final boolean stale;
        public final long lastSyncAt;

        CacheInfo(boolean cached, long lastSyncAt) {
            this.cached = cached;
            this.lastSyncAt = lastSyncAt;
            this.stale = cached && lastSyncAt > 0 && System.currentTimeMillis() - lastSyncAt > STALE_MS;
        }

        @NonNull
        public String label() {
            return labelFor("Cached report - not live financial truth", "Cached report may be outdated.");
        }

        @NonNull
        public String dashboardLabel() {
            return labelFor("Cached dashboard data - not live financial truth", "Cached dashboard data may be outdated.");
        }

        @NonNull
        private String labelFor(@NonNull String cachedLabel, @NonNull String staleLabel) {
            if (!cached) {
                return "Updated just now";
            }
            String label = cachedLabel;
            if (lastSyncAt > 0) {
                label += " - Last synced: " + formatTime(lastSyncAt);
            }
            if (stale) {
                label += "\n" + staleLabel;
            }
            return label;
        }
    }

    private final Context appContext;
    private final SessionManager sessionManager;
    private final ValoraLocalDatabase db;
    private final ReportRepository reportRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ReportCacheRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
        db = ValoraLocalDatabase.getInstance(appContext);
        reportRepository = new ReportRepository(appContext);
    }

    public void loadReportRoomFirst(@NonNull String reportType,
                                    @NonNull Map<String, String> filters,
                                    @NonNull RoomFirstCallback callback) {
        executor.execute(() -> {
            CachedReportEntity cached = getCachedReportEntity(reportType, filters);
            if (cached != null) {
                try {
                    postLocal(callback,
                            ReportRepository.ReportResponse.fromJson(new JSONObject(safe(cached.responseJson))),
                            new CacheInfo(true, cached.lastSyncAt));
                } catch (Exception ignored) {
                    cached = null;
                }
            }

            final boolean hasLocalData = cached != null;

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, hasLocalData);
                return;
            }

            reportRepository.fetchReport(reportType, filters, new ReportRepository.Callback() {
                @Override
                public void onSuccess(@NonNull ReportRepository.ReportResponse response) {
                    saveReportCache(reportType, filters, response.raw);
                    postRemote(callback, response, new CacheInfo(false, System.currentTimeMillis()));
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    postError(callback, statusCode, message, hasLocalData);
                }
            });
        });
    }

    public void loadJsonEndpointRoomFirst(@NonNull String reportType,
                                          @NonNull Map<String, String> filters,
                                          @NonNull String url,
                                          @NonNull Object requestTag,
                                          @NonNull RoomFirstCallback callback) {
        executor.execute(() -> {
            CachedReportEntity cached = getCachedReportEntity(reportType, filters);
            if (cached != null) {
                try {
                    postLocal(callback,
                            ReportRepository.ReportResponse.fromJson(new JSONObject(safe(cached.responseJson))),
                            new CacheInfo(true, cached.lastSyncAt));
                } catch (Exception ignored) {
                    cached = null;
                }
            }

            final boolean hasLocalData = cached != null;

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                postNoInternet(callback, hasLocalData);
                return;
            }

            fetchRawJson(url, requestTag, new RawJsonCallback() {
                @Override
                public void onSuccess(@NonNull JSONObject response) {
                    saveReportCache(reportType, filters, response);
                    postRemote(callback, ReportRepository.ReportResponse.fromJson(response), new CacheInfo(false, System.currentTimeMillis()));
                }

                @Override
                public void onError(int statusCode, @NonNull String message) {
                    postError(callback, statusCode, message, hasLocalData);
                }
            });
        });
    }

    public void saveReportCache(@NonNull String reportType,
                                @NonNull Map<String, String> filters,
                                @NonNull JSONObject responseJson) {
        executor.execute(() -> {
            if (!hasKnownShop()) return;
            long now = System.currentTimeMillis();
            CachedReportEntity entity = new CachedReportEntity();
            entity.shopId = currentShopId();
            entity.shopCode = currentShopCode();
            entity.apiBaseUrl = currentApiBaseUrl();
            entity.reportType = reportType;
            entity.filterJson = normalizedFilterJson(filters).toString();
            entity.queryHash = queryHash(entity.filterJson);
            entity.cacheKey = cacheKey(reportType, entity.queryHash);
            entity.responseJson = responseJson.toString();
            entity.title = titleForReport(reportType);
            entity.dateFrom = filters.get("start_date");
            entity.dateTo = filters.get("end_date");
            entity.lastSyncAt = now;
            entity.createdAt = now;
            db.cachedReportDao().upsert(entity);
            clearOldReportCache();
        });
    }

    public CachedReportEntity getCachedReport(@NonNull String reportType,
                                              @NonNull Map<String, String> filters) {
        return getCachedReportEntity(reportType, filters);
    }

    public void clearOldReportCache() {
        if (!hasKnownShop()) return;
        db.cachedReportDao().deleteOlderThanForShop(
                currentShopId(),
                currentShopCode(),
                currentApiBaseUrl(),
                System.currentTimeMillis() - CACHE_RETENTION_MS
        );
    }

    public void cancel() {
        reportRepository.cancel();
    }

    private void fetchRawJson(@NonNull String url, @NonNull Object requestTag, @NonNull RawJsonCallback callback) {
        final String token = sessionManager.getToken();
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                callback::onSuccess,
                error -> {
                    NetworkResponse response = error.networkResponse;
                    int status = response != null ? response.statusCode : 0;
                    String message = error.getMessage();
                    if (response != null && response.data != null) {
                        try {
                            message = new String(response.data, StandardCharsets.UTF_8);
                        } catch (Exception ignored) {
                        }
                    }
                    callback.onError(status, TextUtils.isEmpty(message) ? "Failed to fetch report" : message);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                if (!TextUtils.isEmpty(token)) {
                    h.put("Authorization", "Token " + token);
                }
                return h;
            }
        };
        req.setTag(requestTag);
        ApiClient.getInstance(appContext).add(req);
    }

    private interface RawJsonCallback {
        void onSuccess(@NonNull JSONObject response);
        void onError(int statusCode, @NonNull String message);
    }

    private CachedReportEntity getCachedReportEntity(@NonNull String reportType,
                                                     @NonNull Map<String, String> filters) {
        if (!hasKnownShop()) return null;
        String filterJson = normalizedFilterJson(filters).toString();
        return db.cachedReportDao().getByCacheKey(cacheKey(reportType, queryHash(filterJson)));
    }

    private boolean hasKnownShop() {
        return currentShopId() > 0 && !currentShopCode().isEmpty();
    }

    private int currentShopId() {
        return sessionManager.getShopId();
    }

    @NonNull
    private String currentShopCode() {
        return safe(sessionManager.getShopCode()).trim().toUpperCase(Locale.US);
    }

    @NonNull
    private String currentApiBaseUrl() {
        return safe(sessionManager.getBaseUrl());
    }

    @NonNull
    private String cacheKey(@NonNull String reportType, @NonNull String queryHash) {
        return currentApiBaseUrl() + "|" + currentShopId() + "|" + currentShopCode() + "|" + reportType + "|" + queryHash;
    }

    @NonNull
    private static JSONObject normalizedFilterJson(@NonNull Map<String, String> filters) {
        JSONObject obj = new JSONObject();
        List<String> keys = new ArrayList<>(filters.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (TextUtils.isEmpty(key)) continue;
            String value = filters.get(key);
            if (TextUtils.isEmpty(value)) continue;
            put(obj, key, value.trim());
        }
        return obj;
    }

    @NonNull
    private static String queryHash(@NonNull String normalizedFilterJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalizedFilterJson.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) {
                out.append(String.format(Locale.US, "%02x", b));
            }
            return out.toString();
        } catch (Exception ignored) {
            return String.valueOf(normalizedFilterJson.hashCode());
        }
    }

    @NonNull
    private static String titleForReport(@NonNull String reportType) {
        switch (reportType) {
            case ReportRepository.TYPE_DAILY:
                return "Daily Summary";
            case ReportRepository.TYPE_PAYMENTS:
                return "Payment Report";
            case ReportRepository.TYPE_EXPENSES:
                return "Expense Report";
            case ReportRepository.TYPE_STOCK:
                return "Stock Report";
            case ReportRepository.TYPE_LOW_STOCK:
                return "Low Stock Report";
            case ReportRepository.TYPE_SHIFTS:
                return "Shift Report";
            case ReportRepository.TYPE_ITEMS:
                return "Product / Item Report";
            default:
                return "Sales Report";
        }
    }

    @NonNull
    private static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private void postLocal(@NonNull RoomFirstCallback callback,
                           @NonNull ReportRepository.ReportResponse response,
                           @NonNull CacheInfo cacheInfo) {
        mainHandler.post(() -> callback.onLocal(response, cacheInfo));
    }

    private void postRemote(@NonNull RoomFirstCallback callback,
                            @NonNull ReportRepository.ReportResponse response,
                            @NonNull CacheInfo cacheInfo) {
        mainHandler.post(() -> callback.onRemote(response, cacheInfo));
    }

    private void postNoInternet(@NonNull RoomFirstCallback callback, boolean hasLocalData) {
        mainHandler.post(() -> callback.onNoInternet(hasLocalData));
    }

    private void postError(@NonNull RoomFirstCallback callback, int statusCode, @NonNull String message, boolean hasLocalData) {
        mainHandler.post(() -> callback.onError(statusCode, message, hasLocalData));
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void put(@NonNull JSONObject obj, @NonNull String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
