package com.colink.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen "agent call" screen — fires when the agent wants you (Phase 1 skeleton). */
public class CallActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 64, 64, 64);

        TextView title = new TextView(this);
        title.setText("📞 Your agent is calling");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView msg = new TextView(this);
        String m = getIntent().getStringExtra("message");
        msg.setText(m != null ? m : "Hermes needs you.");
        msg.setTextSize(16);
        msg.setTextColor(Color.LTGRAY);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 24, 0, 48);
        root.addView(msg);

        Button accept = new Button(this);
        accept.setText("Answer");
        accept.setOnClickListener(v -> finish());
        root.addView(accept);

        Button decline = new Button(this);
        decline.setText("Decline");
        decline.setOnClickListener(v -> finish());
        root.addView(decline);

        setContentView(root);
    }
}
