package com.example.valdker.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * Centralized Volley RequestQueue singleton.
 * Use this class across all modules (POS, Settings, Categories, Units, etc.)
 * to avoid creating multiple queues and to simplify request cancellation.
 */
public final class ApiClient {

    private static volatile ApiClient instance;

    private final RequestQueue requestQueue;

    private ApiClient(@NonNull Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    /**
     * Returns the singleton instance of ApiClient.
     */
    public static ApiClient getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient(context);
                }
            }
        }
        return instance;
    }

    /**
     * Adds a request to the queue.
     * If the request has no tag, it will receive a default tag for safer cancellation.
     */
    public <T> void add(@NonNull Request<T> req) {
        if (req.getTag() == null) {
            req.setTag("ApiClient");
        }
        requestQueue.add(req);
    }

    /**
     * Cancels all pending requests that match the given tag.
     * Use this in onStop()/onDestroy() of Activities/Fragments to avoid callbacks after UI is gone.
     */
    public void cancelAll(@Nullable Object tag) {
        if (tag == null) return;
        requestQueue.cancelAll(tag);
    }

    /**
     * Optional: expose the underlying RequestQueue for advanced use cases.
     */
    @NonNull
    public RequestQueue getQueue() {
        return requestQueue;
    }

    /**
     * Optional: clears Volley cache.
     * Useful if you want to force-refresh data.
     */
    public void clearCache() {
        requestQueue.getCache().clear();
    }
}
