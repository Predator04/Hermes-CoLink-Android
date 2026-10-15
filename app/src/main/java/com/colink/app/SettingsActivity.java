package com.colink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App settings: ringtone sound, vibration. */
public class SettingsActivity extends Activity {
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("colink", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        Button back = new Button(this);
        back.setText("⬅");
        back.setOnClickListener(v -> finish());
        headerRow.addView(back);
        TextView header = new TextView(this);
        header.setText("Settings");
        header.setTextSize(20);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        headerRow.addView(header, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(headerRow);

        CheckBox sound = new CheckBox(this);
        sound.setText("Ringtone sound");
        sound.setTextSize(16);
        sound.setTextColor(Color.WHITE);
        sound.setPadding(0, 24, 0, 0);
        sound.setChecked(prefs.getBoolean("sound", true));
        sound.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("sound", checked).apply());
        root.addView(sound);

        CheckBox vibrate = new CheckBox(this);
        vibrate.setText("Vibration");
        vibrate.setTextSize(16);
        vibrate.setTextColor(Color.WHITE);
        vibrate.setPadding(0, 12, 0, 0);
        vibrate.setChecked(prefs.getBoolean("vibrate", true));
        vibrate.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("vibrate", checked).apply());
        root.addView(vibrate);

        TextView notifyHeader = new TextView(this);
        notifyHeader.setText("Notifications");
        notifyHeader.setTextSize(14);
        notifyHeader.setTextColor(Color.GRAY);
        notifyHeader.setPadding(0, 28, 0, 0);
        root.addView(notifyHeader);

        CheckBox notifyApproval = new CheckBox(this);
        notifyApproval.setText("Approval requests");
        notifyApproval.setTextSize(16);
        notifyApproval.setTextColor(Color.WHITE);
        notifyApproval.setPadding(0, 12, 0, 0);
        notifyApproval.setChecked(prefs.getBoolean("notify_approval", true));
        notifyApproval.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("notify_approval", checked).apply());
        root.addView(notifyApproval);

        CheckBox notifyQuestion = new CheckBox(this);
        notifyQuestion.setText("Questions");
        notifyQuestion.setTextSize(16);
        notifyQuestion.setTextColor(Color.WHITE);
        notifyQuestion.setPadding(0, 12, 0, 0);
        notifyQuestion.setChecked(prefs.getBoolean("notify_question", true));
        notifyQuestion.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("notify_question", checked).apply());
        root.addView(notifyQuestion);

        Button battery = new Button(this);
        battery.setText("🔋 Disable battery optimization");
        battery.setOnClickListener(v -> {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception e2) { }
            }
        });
        root.addView(battery);

        Button history = new Button(this);
        history.setText("🗂 Approval history");
        history.setOnClickListener(v -> startActivity(new Intent(this, ApprovalHistoryActivity.class)));
        root.addView(history);

        setContentView(root);
    }
}
