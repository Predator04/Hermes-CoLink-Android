package com.colink.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Read-only message view for one session (user/assistant bubbles). */
public class ChatActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static class Msg {
        final boolean fromUser;
        final String text;
        Msg(boolean fromUser, String text) { this.fromUser = fromUser; this.text = text; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String url = getIntent().getStringExtra("url");
        String sid = getIntent().getStringExtra("session_id");
        String title = getIntent().getStringExtra("title");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);

        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(18);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setPadding(0, 16, 0, 0);
        root.addView(list);

        setContentView(root);

        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(url);
                String json = c.get("/api/sessions/" + sid + "/messages?limit=200&order=latest");
                List<Msg> msgs = parseMessages(json);
                ui.post(() -> list.setAdapter(new MsgAdapter(msgs)));
            } catch (Exception e) {
                ui.post(() -> {
                    TextView err = new TextView(ChatActivity.this);
                    err.setText("Error: " + e.getMessage());
                    err.setTextColor(Color.LTGRAY);
                    root.addView(err);
                });
            }
        }).start();
    }

    private List<Msg> parseMessages(String json) throws Exception {
        List<Msg> out = new ArrayList<>();
        JSONObject obj = new JSONObject(json);
        JSONArray arr = obj.optJSONArray("messages");
        if (arr != null) {
            for (int i = arr.length() - 1; i >= 0; i--) { // latest-first → chronological
                JSONObject m = arr.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role", "");
                String content = m.optString("content", "");
                if (content == null || content.trim().isEmpty()) continue;
                if (role.equals("user")) out.add(new Msg(true, content));
                else if (role.equals("assistant")) out.add(new Msg(false, content));
                // skip tool/system
            }
        }
        return out;
    }

    private class MsgAdapter extends BaseAdapter {
        private final List<Msg> msgs;
        MsgAdapter(List<Msg> msgs) { this.msgs = msgs; }

        @Override public int getCount() { return msgs.size(); }
        @Override public Object getItem(int i) { return msgs.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Msg m = msgs.get(i);
            TextView tv = new TextView(ChatActivity.this);
            tv.setText(m.text);
            tv.setTextSize(15);
            tv.setTextColor(Color.WHITE);
            tv.setPadding(24, 14, 24, 14);
            tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8));

            LinearLayout wrap = new LinearLayout(ChatActivity.this);
            wrap.setGravity(m.fromUser ? Gravity.END : Gravity.START);
            wrap.setPadding(8, 6, 8, 6);

            if (m.fromUser) {
                tv.setBackgroundColor(Color.rgb(47, 100, 160));
            } else {
                tv.setBackgroundColor(Color.rgb(38, 38, 42));
            }
            wrap.addView(tv);
            return wrap;
        }
    }
}
