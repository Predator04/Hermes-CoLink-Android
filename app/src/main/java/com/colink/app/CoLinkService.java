package com.colink.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.json.JSONObject;

/** Foreground service holding a persistent WS so the agent can "call" in the background. */
public class CoLinkService extends Service {
    private static final String CHANNEL_ID = "colink";
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
            if (url != null && user != null) {
                baseUrl = url;
                connect(url, user, pass);
            }
        }
        return START_STICKY;
    }

    private void connect(String url, String user, String pass) {
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(url);
                if (!c.login(user, pass)) {
                    update("Login failed — check credentials");
                    return;
                }
                gw = new GatewayClient(url);
                Gw.instance = gw;
                gw.setRequestListener(this::onRequest);
                gw.connectBlocking();
                update("Connected — agent can reach you");
            } catch (Exception e) {
                update("Error: " + e.getMessage());
            }
        }).start();
    }

    private void onRequest(String id, String method, JSONObject params) {
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
