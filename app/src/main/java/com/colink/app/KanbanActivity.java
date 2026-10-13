package com.colink.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only Kanban board (columns × task cards) from the dashboard's /api/plugins/kanban/board. */
public class KanbanActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String baseUrl;
    private LinearLayout boardRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        baseUrl = getIntent().getStringExtra("url");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 48, 12, 12);

        TextView header = new TextView(this);
        header.setText("Kanban");
        header.setTextSize(20);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        HorizontalScrollView hscroll = new HorizontalScrollView(this);
        boardRow = new LinearLayout(this);
        boardRow.setOrientation(LinearLayout.HORIZONTAL);
        hscroll.addView(boardRow);
        root.addView(hscroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        loadBoard();
    }

    private void loadBoard() {
        addError("Loading…");
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(baseUrl);
                String json = c.get("/api/plugins/kanban/board");
                ui.post(() -> { boardRow.removeAllViews(); renderBoard(json); });
            } catch (Exception e) {
                ui.post(() -> { boardRow.removeAllViews(); addError(Err.friendly(e)); });
            }
        }).start();
    }

    private void renderBoard(String json) {
        try {
            JSONArray cols = new JSONObject(json).optJSONArray("columns");
            if (cols == null) { addError("No board data"); return; }
            for (int i = 0; i < cols.length(); i++) {
                JSONObject col = cols.optJSONObject(i);
                if (col == null) continue;
                addColumn(col.optString("name"), col.optJSONArray("tasks"));
            }
        } catch (Exception e) {
            addError("Parse error: " + e.getMessage());
        }
    }

    private void addColumn(String name, JSONArray tasks) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(6, 0, 6, 0);

        TextView title = new TextView(this);
        int n = tasks == null ? 0 : tasks.length();
        title.setText(name.toUpperCase() + " (" + n + ")");
        title.setTextSize(13);
        title.setTextColor(Color.rgb(79, 195, 247));
        title.setPadding(8, 8, 8, 8);
        col.addView(title);

        ScrollView sv = new ScrollView(this);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        if (tasks != null) {
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t != null) cards.addView(makeCard(t));
            }
        }
        sv.addView(cards);
        col.addView(sv, new LinearLayout.LayoutParams(250, LinearLayout.LayoutParams.MATCH_PARENT));
        boardRow.addView(col);
    }

    private TextView makeCard(JSONObject t) {
        String title = t.optString("title", "Untitled");
        String assignee = t.optString("assignee", "");
        String summary = t.optString("latest_summary", "");
        StringBuilder sb = new StringBuilder(title);
        if (!assignee.isEmpty()) sb.append("\n@").append(assignee);
        if (!summary.isEmpty()) {
            sb.append("\n").append(summary.length() > 80 ? summary.substring(0, 80) + "…" : summary);
        }

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextSize(13);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(12, 10, 12, 10);
        tv.setBackgroundColor(Color.rgb(38, 38, 42));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void addError(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(Color.LTGRAY);
        tv.setPadding(16, 16, 16, 16);
        boardRow.addView(tv);
    }
}
