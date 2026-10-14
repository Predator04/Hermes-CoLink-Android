package com.colink.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Self-managed telecom connection — makes the agent "call" like a real incoming call. */
public class ColinkCallService extends ConnectionService {

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle handle, ConnectionRequest request) {
        debug("ColinkCallService.onCreateIncomingConnection called");
        android.os.Bundle extras = request.getExtras() != null ? request.getExtras() : new android.os.Bundle();
        final String title = extras.getString("title", "Hermes");
        final String body = extras.getString("body", "Your agent needs you");

        Connection conn = new Connection() {
            @Override
            public void onAnswer() {
                debug("onAnswer");
                setActive();
                openCallActivity(title, body);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                    destroy();
                }, 1500);
            }

            @Override
            public void onReject() {
                debug("onReject");
                setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                destroy();
            }

            @Override
            public void onDisconnect() {
                debug("onDisconnect");
                setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                destroy();
            }
        };
        conn.setAddress(Uri.fromParts("tel", "agent", null), TelecomManager.PRESENTATION_ALLOWED);
        conn.setCallerDisplayName(title, TelecomManager.PRESENTATION_ALLOWED);
        conn.setRingbackRequested(true);
        conn.setRinging();

        debug("connection created, state=" + conn.getState() + ", set ringing");

        // Auto-reject after 45s so a missed call never blocks the user's dialer.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (conn.getState() == Connection.STATE_RINGING || conn.getState() == Connection.STATE_NEW) {
                    conn.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                    conn.destroy();
                }
            } catch (Exception ignored) { }
        }, 45000);
        return conn;
    }

    private void openCallActivity(String title, String body) {
        try {
            Intent i = new Intent(this, CallActivity.class)
                    .putExtra("title", title)
                    .putExtra("body", body)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ignored) { }
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
}
