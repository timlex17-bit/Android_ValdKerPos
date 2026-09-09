package com.valdker.pos.ui.ownerchat;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.valdker.pos.R;
import com.valdker.pos.SessionManager;
import com.valdker.pos.network.ApiConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OwnerChatRepository {

    public interface ChatCallback {
        void onSuccess(OwnerChatResponse res);
        void onError(String error);
    }

    private static final String TAG = "OWNER_CHAT";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String ENDPOINT = "api/owner/chat/";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final SessionManager session;
    private final Context ctx;

    public OwnerChatRepository(Context ctx, SessionManager session) {
        this.ctx = ctx.getApplicationContext();
        this.session = session;
    }

    public void sendChat(String message, String conversationId, ChatCallback cb) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("message", message);
            if (!TextUtils.isEmpty(conversationId)) {
                obj.put("conversation_id", conversationId.trim());
            }

            String token = session.getToken();
            if (TextUtils.isEmpty(token)) {
                cb.onError(ctx.getString(R.string.owner_chat_session_expired));
                return;
            }

            String shopCode = session.getShopCode();
            if (TextUtils.isEmpty(shopCode)) {
                cb.onError(ctx.getString(R.string.owner_chat_shop_context_missing));
                return;
            }

            String auth = token.startsWith("Token ") ? token : ("Token " + token);
            RequestBody body = RequestBody.create(obj.toString(), JSON);

            Request req = new Request.Builder()
                    .url(ApiConfig.url(session, ENDPOINT))
                    .addHeader("Authorization", auth)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Shop-Code", shopCode)
                    .post(body)
                    .build();

            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Owner chat network failed", e);
                    if (e instanceof SocketTimeoutException || e instanceof UnknownHostException) {
                        cb.onError(ctx.getString(R.string.owner_chat_network_timeout));
                    } else {
                        cb.onError(ctx.getString(R.string.owner_chat_unable_contact));
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String raw = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Owner chat failed status=" + response.code() + " body=" + raw);
                        cb.onError(mapHttpError(response.code()));
                        return;
                    }

                    try {
                        cb.onSuccess(OwnerChatResponse.fromJson(new JSONObject(raw)));
                    } catch (Exception ex) {
                        Log.e(TAG, "Owner chat parse failed body=" + raw, ex);
                        cb.onError(ctx.getString(R.string.owner_chat_unable_contact));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Owner chat request failed", e);
            cb.onError(ctx.getString(R.string.owner_chat_unable_contact));
        }
    }

    private String mapHttpError(int statusCode) {
        switch (statusCode) {
            case 400:
                return ctx.getString(R.string.owner_chat_question_not_processed);
            case 401:
                return ctx.getString(R.string.owner_chat_session_expired);
            case 403:
                return ctx.getString(R.string.owner_chat_no_permission);
            case 500:
            case 502:
            case 503:
            case 504:
                return ctx.getString(R.string.owner_chat_server_error);
            default:
                return ctx.getString(R.string.owner_chat_unable_contact);
        }
    }
}
