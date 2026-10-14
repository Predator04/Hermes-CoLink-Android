package com.colink.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** FCM: registers the device token with the relay, turns pushes into a full-screen agent call. */
public class CoLinkFirebaseService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        String url = getSharedPreferences("colink", MODE_PRIVATE).getString("url", "http://192.168.1.8:9119");
        new Thread(() -> registerToken(url, token)).start();
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        String title = msg.getData().getOrDefault("title", "Hermes");
        String body = msg.getData().getOrDefault("body", "Your agent needs you");
        debug("onMessageReceived data=" + msg.getData());
        triggerCall(title, body);
    }

    private void triggerCall(String title, String body) {
        showCall(title, body);
    }

    public static void registerCurrent(Context ctx) {
        try {
            com.google.firebase.messaging.FirebaseMessaging fm = com.google.firebase.messaging.FirebaseMessaging.getInstance();
            // Force a fresh token — a stale token returns UNREGISTERED (404) from FCM.
            fm.deleteToken().addOnCompleteListener(task -> {
                fm.getToken().addOnSuccessListener(tok -> {
                    String url = ctx.getSharedPreferences("colink", Context.MODE_PRIVATE)
                            .getString("url", "http://192.168.1.8:9119");
                    registerToken(url, tok);
                });
            });
        } catch (Exception ignored) { }
    }

    public static void registerToken(String url, String token) {
        String host = extractHost(url);
        if (host == null || token == null || token.isEmpty()) return;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":8790/register").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            try (OutputStream os = c.getOutputStream()) {
                os.write(("{\"url\":\"" + url + "\",\"token\":\"" + token + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
        } catch (Exception ignored) { }
    }

    private void showCall(String title, String body) {
        Intent call = new Intent(this, CallActivity.class);
        call.putExtra("title", title);
        call.putExtra("body", body);
        call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPi = PendingIntent.getActivity(this, 0, call,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent approvePi = PendingIntent.getActivity(this, 1, call,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent silentIntent = new Intent(this, NotificationActionReceiver.class);
        silentIntent.setAction(NotificationActionReceiver.ACTION_SILENT);
        PendingIntent silentPi = PendingIntent.getBroadcast(this, 2, silentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent denyIntent = new Intent(this, NotificationActionReceiver.class);
        denyIntent.setAction(NotificationActionReceiver.ACTION_DENY);
        PendingIntent denyPi = PendingIntent.getBroadcast(this, 3, denyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        CallRingtone.start(this);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            boolean vibrate = getSharedPreferences("colink", MODE_PRIVATE).getBoolean("vibrate", true);
            NotificationChannel ch = new NotificationChannel("colink_ring", "Agent calls",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(vibrate);
            if (vibrate) ch.setVibrationPattern(new long[]{0, 400, 400, 400});
            nm.createNotificationChannel(ch);
        }

        Notification n = new Notification.Builder(this, "colink_ring")
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_send),
                        "Approve", approvePi).build())
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                        "Silent", silentPi).build())
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "Deny", denyPi).build())
                .build();

        nm.notify(1, n);
    }

    private static void debug(String msg) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://192.168.1.8:8790/debug").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            try (OutputStream os = c.getOutputStream()) {
                os.write(("{\"msg\":\"" + msg.replace("\"", "'") + "\"}").getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
        } catch (Exception ignored) { }
    }

    private static String extractHost(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) { return null; }
    }
}
