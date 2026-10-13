package com.colink.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Model picker: lists providers/models from /api/model/options and sets the main model via /api/model/set. */
public class ModelActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String baseUrl;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        baseUrl = getIntent().getStringExtra("url");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);

        TextView header = new TextView(this);
        header.setText("Model");
        header.setTextSize(20);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        loadModels();
    }

    private void loadModels() {
        addLine("Loading…", Color.GRAY);
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(baseUrl);
                String json = c.get("/api/model/options");
                ui.post(() -> { list.removeAllViews(); render(json); });
            } catch (Exception e) {
                ui.post(() -> { list.removeAllViews(); addLine(Err.friendly(e), Color.LTGRAY); });
            }
        }).start();
    }

    private void render(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String curProvider = o.optString("provider", "");
            String curModel = o.optString("model", "");
            addLine("Current: " + curProvider + " / " + curModel, Color.rgb(79, 195, 247));

            JSONArray providers = o.optJSONArray("providers");
            if (providers == null) return;
            for (int i = 0; i < providers.length(); i++) {
                JSONObject p = providers.optJSONObject(i);
                if (p == null) continue;
                String slug = p.optString("slug", "");
                JSONArray models = p.optJSONArray("models");
                if (models == null || models.length() == 0) continue;
                addLine(slug.toUpperCase(), Color.rgb(163, 163, 170));
                for (int j = 0; j < models.length(); j++) {
                    String model = models.optString(j, "");
                    if (model.isEmpty()) continue;
                    addModelRow(slug, model, model.equals(curModel));
                }
            }
        } catch (Exception e) {
            addLine("Parse error: " + e.getMessage(), Color.LTGRAY);
        }
    }

    private void addModelRow(String provider, String model, boolean current) {
        TextView tv = new TextView(this);
        tv.setText((current ? "● " : "") + model);
        tv.setTextSize(14);
        tv.setTextColor(current ? Color.rgb(79, 195, 247) : Color.WHITE);
        tv.setPadding(32, 12, 12, 12);
        tv.setOnClickListener(v -> setModel(provider, model));
        list.addView(tv);
    }

    private void setModel(String provider, String model) {
        Toast.makeText(this, "Setting " + provider + "/" + model + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(baseUrl);
                JSONObject body = new JSONObject();
                body.put("scope", "main");
                body.put("provider", provider);
                body.put("model", model);
                String resp = c.post("/api/model/set", body.toString());
                ui.post(() -> Toast.makeText(this,
                        "Model set: " + model + " (applies to new sessions)", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "Failed: " + Err.friendly(e), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void addLine(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setPadding(12, 8, 12, 8);
        list.addView(tv);
    }
}
