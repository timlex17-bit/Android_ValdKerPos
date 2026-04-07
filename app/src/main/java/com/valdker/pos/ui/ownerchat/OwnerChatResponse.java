package com.valdker.pos.ui.ownerchat;

import java.util.ArrayList;
import java.util.List;

public class OwnerChatResponse {
    public String replyText;
    public List<Link> links = new ArrayList<>();

    public static class Link {
        public String title;
        public String url;
    }
}