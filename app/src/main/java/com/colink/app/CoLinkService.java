package com.colink.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Foreground service holding a persistent WS so the agent can "call" in the background. */
public class CoLinkService extends Service {
    private static final String CHANNEL_ID = "colink";
    private static final Map<String, Boolean> pendingResponses = new ConcurrentHashMap<>();
    private GatewayClient gw;
    private String baseUrl;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(1, statusNotification("Starting…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String url = intent.getStringExtra("url");
            String user = intent.getStringExtra("username");
            String pass = intent.getStringExtra("password");
            if (url == null || user == null) {
                SharedPreferences p = getSharedPreferences("colink", MODE_PRIVATE);
                url = p.getString("url", null);
                user = p.getString("username", null);
                pass = CryptoUtils.decrypt(p.getString("password", ""));
            }
            if (url != null && user != null) {
                baseUrl = url;
                connect(url, user, pass == null ? "" : pass);
            }
        }
        return START_STICKY;
    }

    /** Answer an approval/clarify request from the notification (Approve/Deny). */
    public static void respond(Context c, String requestId, boolean approved) {
        if (requestId == null || requestId.isEmpty()) return;
        GatewayClient g = Gw.instance;
        if (g != null && g.isConnected()) {
            try {
                JSONObject r = new JSONObject();
                r.put("value", approved ? "yes" : "no");
                r.put("approved", approved);
                g.respond(requestId, r);
                return;
            } catch (Exception ignored) { }
        }
        // WS not ready — queue + wake the service to reconnect, resume, then answer.
        pendingResponses.put(requestId, approved);
        ensureRunning(c);
    }

    /** Start the service (with prefs credentials) so the WS connects and can answer. */
    public static void ensureRunning(Context c) {
        try {
            Intent svc = new Intent(c, CoLinkService.class);
            c.startForegroundService(svc);
        } catch (Exception ignored) { }
    }

    private void connect(String url, String user, String pass) {
        new Thread(() -> {
            while (true) {
                try {
                    DashboardClient c = new DashboardClient(url);
                    if (!c.login(user, pass)) {
                        update("Login failed — check credentials");
                        return;
                    }
                    gw = new GatewayClient(url);
                    Gw.instance = gw;
                    gw.setRequestListener(this::onRequest);
                    final java.util.concurrent.CountDownLatch dropped = new java.util.concurrent.CountDownLatch(1);
                    gw.setDisconnectListener(dropped::countDown);
                    gw.connectBlocking();
                    attachToSession(url); // attach to your main session so background calls fire
                    update("Connected — agent can reach you");
                    dropped.await(); // hold until the WS drops
                    update("Connection lost — reconnecting…");
                } catch (Exception e) {
                    update("Reconnecting… (" + Err.friendly(e) + ")");
                }
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }).start();
    }

    private void attachToSession(String url) {
        try {
            String json = new DashboardClient(url).getSessions();
            JSONArray arr = new JSONObject(json).optJSONArray("sessions");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                String src = s.optString("source", "");
                if ("cron".equals(src) || "tool".equals(src)) continue; // skip automated sessions
                gw.requestSync("session.resume", new JSONObject().put("session_id", s.optString("id")));
                return;
            }
        } catch (Exception ignored) { }
    }

    private void onRequest(String id, String method, JSONObject params) {
        // A queued notification decision: answer it now that the request has re-arrived.
        Boolean decision = pendingResponses.remove(id);
        if (decision != null) {
            try {
                JSONObject r = new JSONObject();
                r.put("value", decision ? "yes" : "no");
                r.put("approved", decision);
                gw.respond(id, r);
            } catch (Exception ignored) { }
            return;
        }
        Intent ci = new Intent(this, CallActivity.class);
        ci.putExtra("req_id", id);
        ci.putExtra("method", method);
        ci.putExtra("params", params == null ? "" : params.toString());
        ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, ci,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Your agent needs you")
                .setContentText(humanize(method))
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(100, n);
    }

    private String humanize(String m) {
        if (m == null) return "";
        switch (m) {
            case "clarify": return "The agent is asking you a question";
            case "sudo": return "The agent needs permission";
            case "approval": return "The agent needs tool approval";
            default: return "Request: " + m;
        }
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "CoLink",
                NotificationManager.IMPORTANCE_HIGH);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification statusNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("CoLink")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void update(String text) {
        getSystemService(NotificationManager.class).notify(1, statusNotification(text));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (gw != null) gw.close();
    }
}
