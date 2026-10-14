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

/** Self-managed telecom connection — makes the agent "call" like a real incoming call. */
public class ColinkCallService extends ConnectionService {

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle handle, ConnectionRequest request) {
        android.os.Bundle extras = request.getExtras() != null ? request.getExtras() : new android.os.Bundle();
        final String title = extras.getString("title", "Hermes");
        final String body = extras.getString("body", "Your agent needs you");

        Connection conn = new Connection() {
            @Override
            public void onAnswer() {
                setActive();
                openCallActivity(title, body);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                    destroy();
                }, 1500);
            }

            @Override
            public void onReject() {
                setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                destroy();
            }

            @Override
            public void onDisconnect() {
                setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                destroy();
            }
        };
        conn.setAddress(Uri.fromParts("tel", "agent", null), TelecomManager.PRESENTATION_ALLOWED);
        conn.setCallerDisplayName(title, TelecomManager.PRESENTATION_ALLOWED);
        conn.setRingbackRequested(true);

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
}
