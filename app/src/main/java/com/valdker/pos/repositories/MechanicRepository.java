package com.valdker.pos.repositories;

import android.content.Context;

import androidx.annotation.NonNull;

import com.valdker.pos.models.MechanicRequest;
import com.valdker.pos.models.MechanicResponse;
import com.valdker.pos.network.MechanicApi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

public class MechanicRepository {
    public interface ListCallback {
        void onSuccess(@NonNull List<MechanicResponse> mechanics);
        void onError(int statusCode, @NonNull String message);
    }

    public interface ItemCallback {
        void onSuccess(@NonNull MechanicResponse mechanic);
        void onError(int statusCode, @NonNull String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(int statusCode, @NonNull String message);
    }

    private final MechanicApi api;

    public MechanicRepository(@NonNull Context context) {
        api = new MechanicApi(context);
    }

    public void fetchMechanics(@NonNull ListCallback callback) {
        api.list(new MechanicApi.StringCallback() {
            @Override
            public void onSuccess(@NonNull String response) {
                try {
                    callback.onSuccess(parseList(extractArray(response)));
                } catch (Exception e) {
                    callback.onError(0, "Parse error: " + e.getMessage());
                }
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    public void create(@NonNull MechanicRequest request, @NonNull ItemCallback callback) {
        api.create(request, new MechanicApi.ObjectCallback() {
            @Override
            public void onSuccess(@NonNull JSONObject response) {
                callback.onSuccess(MechanicResponse.fromJson(response));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    public void update(int id, @NonNull MechanicRequest request, @NonNull ItemCallback callback) {
        api.update(id, request, new MechanicApi.ObjectCallback() {
            @Override
            public void onSuccess(@NonNull JSONObject response) {
                callback.onSuccess(MechanicResponse.fromJson(response));
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    public void delete(int id, @NonNull DeleteCallback callback) {
        api.delete(id, new MechanicApi.DeleteCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(int statusCode, @NonNull String message) {
                callback.onError(statusCode, message);
            }
        });
    }

    @NonNull
    private static JSONArray extractArray(@NonNull String response) throws Exception {
        Object parsed = new JSONTokener(response).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONArray results = ((JSONObject) parsed).optJSONArray("results");
            return results != null ? results : new JSONArray();
        }
        return new JSONArray();
    }

    @NonNull
    private static List<MechanicResponse> parseList(@NonNull JSONArray array) {
        List<MechanicResponse> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) out.add(MechanicResponse.fromJson(obj));
        }
        return out;
    }
}
