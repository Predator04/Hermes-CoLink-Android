package com.colink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Login → session list. */
public class MainActivity extends Activity {
    private EditText urlField, userField, passField;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private android.content.SharedPreferences prefs;

    private static class Session {
        final String id, title, model;
        final int count;
        Session(String id, String title, String model, int count) {
            this.id = id; this.title = title; this.model = model; this.count = count;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("colink", MODE_PRIVATE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        buildLogin();
    }

    private void buildLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);

        TextView title = new TextView(this);
        title.setText("CoLink");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Your Hermes agent, on your phone");
        sub.setTextColor(Color.GRAY);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        urlField = new EditText(this);
        urlField.setHint("Dashboard URL");
        urlField.setText(prefs.getString("url", "http://192.168.1.8:9119"));
        root.addView(urlField);

        userField = new EditText(this);
        userField.setHint("Username");
        userField.setText(prefs.getString("username", ""));
        root.addView(userField);

        passField = new EditText(this);
        passField.setHint("Password");
        passField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passField);

        Button connect = new Button(this);
        connect.setText("Connect");
        connect.setOnClickListener(v -> doLogin());
        root.addView(connect);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0, 24, 0, 0);
        root.addView(status);

        setContentView(root);
    }

    private boolean isOnline() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return true; }
    }

    private void doLogin() {
        if (!isOnline()) {
            status.setText("No internet connection");
            return;
        }
        String url = urlField.getText().toString().trim();
        String user = userField.getText().toString().trim();
        String pass = passField.getText().toString();
        status.setText("Connecting…");
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(url);
                if (!c.login(user, pass)) {
                    ui.post(() -> status.setText("Login failed — check URL + credentials"));
                    return;
                }
                prefs.edit().putString("url", url).putString("username", user).apply();
                startService(new Intent(this, CoLinkService.class)
                        .putExtra("url", url)
                        .putExtra("username", user)
                        .putExtra("password", pass));
                String sessions = c.getSessions();
                List<Session> list = parseSessions(sessions);
                ui.post(() -> showSessions(url, list));
            } catch (Exception e) {
                ui.post(() -> status.setText(Err.friendly(e)));
            }
        }).start();
    }

    private List<Session> parseSessions(String json) throws Exception {
        List<Session> out = new ArrayList<>();
        JSONObject obj = new JSONObject(json);
        JSONArray arr = obj.optJSONArray("sessions");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s == null) continue;
                String id = s.optString("id");
                String title = s.optString("title", "");
                if (title.isEmpty()) title = s.optString("display_name", "Untitled");
                out.add(new Session(id, title, s.optString("model", ""), s.optInt("message_count", 0)));
            }
        }
        return out;
    }

    private void showSessions(String url, List<Session> sessions) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        TextView header = new TextView(this);
        header.setText("Sessions");
        header.setTextSize(22);
        header.setTextColor(Color.WHITE);
        root.addView(header);

        if (sessions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No sessions yet.");
            empty.setTextColor(Color.GRAY);
            empty.setPadding(0, 24, 0, 0);
            root.addView(empty);
        } else {
            ListView list = new ListView(this);
            list.setPadding(0, 16, 0, 0);
            List<String> rows = new ArrayList<>();
            for (Session s : sessions) {
                String meta = (s.model.isEmpty() ? "" : s.model + " · ") + s.count + " msgs";
                rows.add(s.title + "\n" + meta);
            }
            list.setAdapter(new android.widget.ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_2, android.R.id.text1, rows));
            list.setOnItemClickListener((parent, view, pos, id) -> {
                Session s = sessions.get(pos);
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra("url", url);
                i.putExtra("session_id", s.id);
                i.putExtra("title", s.title);
                startActivity(i);
            });
            root.addView(list);
        }

        setContentView(root);
    }
}
