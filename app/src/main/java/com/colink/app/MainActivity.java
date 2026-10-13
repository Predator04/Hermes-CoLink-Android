package com.colink.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Login + session list — Phase 1 "connect" screen. */
public class MainActivity extends Activity {
    private EditText urlField, userField, passField;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);

        TextView title = new TextView(this);
        title.setText("CoLink");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Your Hermes agent, on your phone");
        sub.setTextColor(Color.GRAY);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        urlField = new EditText(this);
        urlField.setHint("Dashboard URL");
        urlField.setText("http://192.168.1.8:9119");
        root.addView(urlField);

        userField = new EditText(this);
        userField.setHint("Username");
        root.addView(userField);

        passField = new EditText(this);
        passField.setHint("Password");
        passField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passField);

        Button connect = new Button(this);
        connect.setText("Connect");
        connect.setOnClickListener(v -> doLogin());
        root.addView(connect);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextIsSelectable(true);
        status.setPadding(0, 24, 0, 0);
        root.addView(status);

        setContentView(root);
    }

    private void doLogin() {
        String url = urlField.getText().toString().trim();
        String user = userField.getText().toString().trim();
        String pass = passField.getText().toString();
        status.setText("Connecting…");
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(url);
                boolean ok = c.login(user, pass);
                if (!ok) {
                    ui.post(() -> status.setText("Login failed — check URL + credentials"));
                    return;
                }
                String me = c.getMe();
                String sessions = c.getSessions();
                ui.post(() -> status.setText("✓ Connected\n\n" + me + "\n\n" + sessions));
            } catch (Exception e) {
                ui.post(() -> status.setText("Error: " + e.getMessage()));
            }
        }).start();
    }
}
