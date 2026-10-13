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
        String url = getSharedPreferences("colink", MODE_PRIVATE).getString("url", "");
        new Thread(() -> registerToken(url, token)).start();
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        String title = msg.getData().getOrDefault("title", "Hermes");
        String body = msg.getData().getOrDefault("body", "Your agent needs you");
        showCall(title, body);
    }

    private void registerToken(String url, String token) {
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

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("colink_call", "Agent calls",
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }

        Notification n = new Notification.Builder(this, "colink_call")
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(true)
                .build();

        nm.notify(1, n);
    }

    private static String extractHost(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) { return null; }
    }
}
