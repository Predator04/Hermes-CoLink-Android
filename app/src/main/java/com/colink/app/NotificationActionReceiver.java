package com.colink.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles notification action buttons: Silent (stop ringing) and Deny (dismiss). */
public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_SILENT = "com.colink.app.ACTION_SILENT";
    public static final String ACTION_DENY = "com.colink.app.ACTION_DENY";

    @Override
    public void onReceive(Context c, Intent i) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(1);
    }
}
