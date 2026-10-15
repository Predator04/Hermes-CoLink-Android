package com.colink.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/** Explicitly plays/stops the call ringtone so "Silent" can actually stop it.
 *  Auto-stops after 60s so a missed call can never ring forever. */
public class CallRingtone {
    private static final long AUTO_STOP_MS = 60_000L;
    private static Ringtone ringtone;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static final Runnable stopRunnable = CallRingtone::stop;

    public static void start(Context c) {
        stop();
        android.content.SharedPreferences prefs = c.getSharedPreferences("colink", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("sound", true)) return;
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            String custom = prefs.getString("ringtone", "");
            if (!custom.isEmpty()) uri = Uri.parse(custom);
            ringtone = RingtoneManager.getRingtone(c, uri);
            if (ringtone != null) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                ringtone.setLooping(true);
                ringtone.play();
                h.postDelayed(stopRunnable, AUTO_STOP_MS); // never ring forever
            }
        } catch (Exception ignored) { }
    }

    public static void stop() {
        h.removeCallbacks(stopRunnable);
        try {
            if (ringtone != null) {
                ringtone.stop();
                ringtone = null;
            }
        } catch (Exception ignored) { }
    }
}
