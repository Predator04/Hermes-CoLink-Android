package com.colink.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles notification action buttons: Approve/Deny (answer the agent) and Silent (stop ringing). */
public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_APPROVE = "com.colink.app.ACTION_APPROVE";
    public static final String ACTION_SILENT = "com.colink.app.ACTION_SILENT";
    public static final String ACTION_DENY = "com.colink.app.ACTION_DENY";

    @Override
    public void onReceive(Context c, Intent i) {
        String action = i.getAction() == null ? "" : i.getAction();
        CallRingtone.stop();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(1);
        String reqId = i.getStringExtra("request_id");
        debug("receiver action=" + action + " req=" + reqId);
        if (ACTION_APPROVE.equals(action)) {
            CoLinkService.respond(c, reqId, true);
        } else if (ACTION_DENY.equals(action)) {
            CoLinkService.respond(c, reqId, false);
        }
        // SILENT: ring already stopped + notification cancelled above.
    }

    private static void debug(String msg) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL("http://192.168.1.8:8790/debug").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(("{\"msg\":\"" + msg.replace("\"", "'") + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
        } catch (Exception ignored) { }
    }
}
