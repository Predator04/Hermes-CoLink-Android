package com.colink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Full-screen "agent call" — fires on a server→client request (clarify/approval/sudo) or an FCM push. */
public class CallActivity extends Activity {
    private String reqId;
    private String method;
    private String params;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CallRingtone.stop(); // the call screen is showing — stop ringing
        reqId = getIntent().getStringExtra("req_id");
        method = getIntent().getStringExtra("method");
        params = getIntent().getStringExtra("params");
        final String fcmTitle = getIntent().getStringExtra("title");
        final String fcmBody = getIntent().getStringExtra("body");
        final boolean answerable = reqId != null && !reqId.isEmpty();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);

        TextView title = new TextView(this);
        title.setText((fcmTitle != null && !fcmTitle.isEmpty()) ? ("📞 " + fcmTitle) : "📞 Your agent needs you");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView what = new TextView(this);
        what.setText(answerable ? humanize(method) : "");
        what.setTextSize(15);
        what.setTextColor(Color.rgb(79, 195, 247));
        what.setGravity(Gravity.CENTER);
        what.setPadding(0, 12, 0, 0);
        root.addView(what);

        ScrollView scroll = new ScrollView(this);
        scroll.setScrollbarFadingEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.setMargins(0, 16, 0, 16);
        scroll.setLayoutParams(scrollLp);

        TextView detail = new TextView(this);
        String ptext = readableParams(params);
        detail.setText(ptext.isEmpty() ? (fcmBody == null ? "" : fcmBody) : ptext);
        detail.setTextSize(15);
        detail.setTextColor(Color.LTGRAY);
        detail.setTextIsSelectable(true);
        detail.setGravity(Gravity.START);
        detail.setPadding(8, 0, 8, 0);
        scroll.addView(detail);
        root.addView(scroll);

        Button accept = new Button(this);
        accept.setText(answerable ? "Answer" : "Open");
        accept.setOnClickListener(v -> {
            if (answerable) {
                answer(true);
            } else {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }
        });
        root.addView(accept);

        Button silent = new Button(this);
        silent.setText("🔕 Silent");
        silent.setOnClickListener(v -> {
            try {
                CallRingtone.stop();
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.cancel(1);
            } catch (Exception ignored) { }
        });
        root.addView(silent);

        Button decline = new Button(this);
        decline.setText(answerable ? "Decline" : "Dismiss");
        decline.setOnClickListener(v -> {
            if (answerable) answer(false);
            else finish();
        });
        root.addView(decline);

        setContentView(root);
    }

    private String humanize(String m) {
        if (m == null) return "";
        switch (m) {
            case "clarify": return "The agent is asking you a question";
            case "sudo": return "The agent needs permission to run a command";
            case "approval": return "The agent is asking for tool approval";
            case "secret": return "The agent needs a credential";
            case "vault": return "The agent needs a vault entry";
            default: return "Request: " + m;
        }
    }

    private String readableParams(String params) {
        if (params == null || params.isEmpty()) return "";
        try {
            JSONObject p = new JSONObject(params);
            JSONArray qs = p.optJSONArray("questions");
            if (qs != null && qs.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < qs.length(); i++) {
                    JSONObject q = qs.optJSONObject(i);
                    if (q == null) continue;
                    String t = q.optString("question", q.optString("text", ""));
                    if (!t.isEmpty()) { if (sb.length() > 0) sb.append("\n\n"); sb.append(t); }
                }
                if (sb.length() > 0) return sb.toString();
            }
            String cmd = p.optString("command", p.optString("description", ""));
            if (!cmd.isEmpty()) return cmd;
            String s = p.toString(2);
            return s;
        } catch (Exception e) {
            return params;
        }
    }

    private void answer(boolean yes) {
        GatewayClient g = Gw.instance;
        if (g != null && reqId != null) {
            try {
                JSONObject r = new JSONObject();
                r.put("value", yes ? "yes" : "no");
                r.put("approved", yes);
                g.respond(reqId, r);
            } catch (Exception ignored) { }
        }
        ApprovalLog.add(this, method, yes, readableParams(params));
        finish();
    }
}
