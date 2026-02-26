package com.example.valdker.ui.ownerchat;

import android.content.Context;

import com.example.valdker.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

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

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // ✅ Render production
    private static final String BASE_URL = "https://valdker.onrender.com";
    private static final String ENDPOINT = "/api/owner/chat/";

    private final OkHttpClient client = new OkHttpClient();
    private final SessionManager session;
    private final Context ctx;

    public OwnerChatRepository(Context ctx, SessionManager session) {
        this.ctx = ctx;
        this.session = session;
    }

    public void sendChat(String message, ChatCallback cb) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("message", message);

            String token = session.getToken();
            if (token == null || token.trim().isEmpty()) {
                cb.onError("Token kosong. Silakan login ulang.");
                return;
            }

            // Pastikan format header: "Token xxxxx"
            String auth = token.startsWith("Token ") ? token : ("Token " + token);

            RequestBody body = RequestBody.create(obj.toString(), JSON);

            Request req = new Request.Builder()
                    .url(BASE_URL + ENDPOINT)
                    .addHeader("Authorization", auth)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(req).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    cb.onError(e.getMessage() != null ? e.getMessage() : "Network error");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    String raw = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        cb.onError("HTTP " + response.code() + " " + raw);
                        return;
                    }

                    try {
                        JSONObject js = new JSONObject(raw);

                        OwnerChatResponse res = new OwnerChatResponse();
                        res.replyText = js.optString("reply_text", "");

                        JSONArray links = js.optJSONArray("links");
                        if (links != null) {
                            for (int i = 0; i < links.length(); i++) {
                                JSONObject l = links.optJSONObject(i);
                                if (l == null) continue;
                                OwnerChatResponse.Link link = new OwnerChatResponse.Link();
                                link.title = l.optString("title", "");
                                link.url = l.optString("url", "");
                                res.links.add(link);
                            }
                        }

                        cb.onSuccess(res);

                    } catch (Exception ex) {
                        cb.onError("Parse error: " + ex.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }
}