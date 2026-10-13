package com.colink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
        checkForUpdate();
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL("https://api.github.com/repos/Predator04/Hermes-CoLink-Android/releases/latest");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                c.setRequestProperty("User-Agent", "CoLink");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                java.io.InputStream in = c.getInputStream();
                String json = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A").next();
                in.close();
                org.json.JSONObject o = new org.json.JSONObject(json);
                String tag = o.optString("tag_name", "");
                String apkUrl = null;
                org.json.JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        org.json.JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url");
                            break;
                        }
                    }
                }
                if (!tag.isEmpty() && apkUrl != null && isNewer(tag, BuildConfig.VERSION_NAME)) {
                    java.io.File apk = downloadApk(apkUrl, tag);
                    ui.post(() -> showUpdateDialog(tag, apk));
                }
            } catch (Exception ignored) { }
        }).start();
    }

    private boolean isNewer(String tag, String current) {
        try {
            String[] a = tag.replaceFirst("^v", "").split("\\.");
            String[] b = current.split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int x = i < a.length ? Integer.parseInt(a[i].trim()) : 0;
                int y = i < b.length ? Integer.parseInt(b[i].trim()) : 0;
                if (x != y) return x > y;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private java.io.File downloadApk(String url, String tag) throws Exception {
        java.net.URL u = new java.net.URL(url);
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
        c.setRequestProperty("User-Agent", "CoLink");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        java.io.InputStream in = c.getInputStream();
        java.io.File out = new java.io.File(getCacheDir(), "colink-" + tag + ".apk");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        in.close();
        return out;
    }

    private void showUpdateDialog(String tag, java.io.File apk) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Update " + tag + " ready")
                .setMessage("Downloaded. Install now?")
                .setPositiveButton("Install", (d, w) -> installApk(apk))
                .setNegativeButton("Later", null)
                .show();
    }

    private void installApk(java.io.File apk) {
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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
        sub.setText("Your Hermes agent, on your phone\nv" + BuildConfig.VERSION_NAME);
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

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView header = new TextView(this);
        header.setText("Sessions");
        header.setTextSize(22);
        header.setTextColor(Color.WHITE);
        headerRow.addView(header, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button kanban = new Button(this);
        kanban.setText("📋 Kanban");
        kanban.setOnClickListener(v -> startActivity(new Intent(this, KanbanActivity.class).putExtra("url", url)));
        headerRow.addView(kanban);
        root.addView(headerRow);

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
