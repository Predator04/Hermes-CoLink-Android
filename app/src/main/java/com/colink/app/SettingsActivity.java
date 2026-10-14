package com.colink.app;

import android.app.Activity;
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

        setContentView(root);
    }
}
