package com.colink.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read-only log of everything the user approved or denied. */
public class ApprovalHistoryActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        header.setText("Approval history");
        header.setTextSize(20);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        headerRow.addView(header, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(headerRow);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        JSONArray arr = ApprovalLog.read(this);
        if (arr.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No approvals yet.");
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(0, 24, 0, 0);
            list.addView(empty);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a", Locale.US);
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            long t = e.optLong("time");
            boolean ok = e.optBoolean("approved");
            String method = e.optString("method", "");
            String desc = e.optString("description", "");
            TextView tv = new TextView(this);
            tv.setText((ok ? "✅ " : "❌ ") + (method.isEmpty() ? "" : method + " — ") + fmt.format(new Date(t)) + "\n" + desc);
            tv.setTextSize(13);
            tv.setTextColor(ok ? Color.rgb(74, 222, 128) : Color.rgb(248, 113, 113));
            tv.setPadding(0, 14, 0, 0);
            list.addView(tv);
        }

        setContentView(root);
    }
}
