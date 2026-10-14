package com.colink.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Appends an approve/deny entry to a local log (SharedPreferences JSON array). */
public class ApprovalLog {
    private static final String KEY = "approval_log";

    public static void add(Context c, String method, boolean approved, String description) {
        try {
            SharedPreferences prefs = c.getSharedPreferences("colink", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            JSONObject e = new JSONObject();
            e.put("time", System.currentTimeMillis());
            e.put("method", method == null ? "" : method);
            e.put("approved", approved);
            e.put("description", description == null ? "" : description);
            arr.put(e);
            // keep last 100
            while (arr.length() > 100) arr.remove(0);
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static JSONArray read(Context c) {
        try {
            return new JSONArray(c.getSharedPreferences("colink", Context.MODE_PRIVATE).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
