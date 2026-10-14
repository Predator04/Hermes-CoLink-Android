package com.colink.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

/** Explicitly plays/stops the call ringtone so "Silent" can actually stop it. */
public class CallRingtone {
    private static Ringtone ringtone;

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
            }
        } catch (Exception ignored) { }
    }

    public static void stop() {
        try {
            if (ringtone != null) {
                ringtone.stop();
                ringtone = null;
            }
        } catch (Exception ignored) { }
    }
}
