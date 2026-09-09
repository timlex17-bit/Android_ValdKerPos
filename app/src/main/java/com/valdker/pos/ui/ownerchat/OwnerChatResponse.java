package com.valdker.pos.ui.ownerchat;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OwnerChatResponse {
    public String replyText;
    public String conversationId;
    public List<Link> links = new ArrayList<>();

    @NonNull
    public static OwnerChatResponse fromJson(@NonNull JSONObject js) {
        OwnerChatResponse res = new OwnerChatResponse();
        res.replyText = firstNonEmpty(
                js.optString("reply_text", ""),
                js.optString("reply", ""),
                js.optString("answer", ""),
                js.optString("message", "")
        );
        res.conversationId = js.optString("conversation_id", "");

        JSONArray linksArray = js.optJSONArray("links");
        if (linksArray != null) {
            for (int i = 0; i < linksArray.length(); i++) {
                JSONObject l = linksArray.optJSONObject(i);
                if (l == null) continue;

                Link link = new Link();
                link.title = l.optString("title", "");
                link.url = l.optString("url", "");
                if (!TextUtils.isEmpty(link.title) && !TextUtils.isEmpty(link.url)) {
                    res.links.add(link);
                }
            }
        }

        return res;
    }

    @NonNull
    private static String firstNonEmpty(@NonNull String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    public static class Link {
        public String title;
        public String url;
    }
}
