package com.colink.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only Kanban board (columns × task cards) from the dashboard's /api/plugins/kanban/board. */
public class KanbanActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String baseUrl;
    private LinearLayout boardRow;
    private static final String[] STATUSES = {"triage", "todo", "ready", "blocked", "scheduled", "review", "done"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        baseUrl = getIntent().getStringExtra("url");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 48, 12, 12);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        Button back = new Button(this);
        back.setText("⬅");
        back.setOnClickListener(v -> finish());
        headerRow.addView(back);
        TextView header = new TextView(this);
        header.setText("Kanban");
        header.setTextSize(20);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        headerRow.addView(header, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(this);
        add.setText("＋ New");
        add.setOnClickListener(v -> showCreateDialog());
        headerRow.addView(add);
        root.addView(headerRow);

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
        final String id = t.optString("id", "");
        final String title = t.optString("title", "Untitled");
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
        tv.setOnClickListener(v -> showMoveDialog(id, title));
        return tv;
    }

    private void showCreateDialog() {
        EditText input = new EditText(this);
        input.setHint("Task title");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("New task")
                .setView(input)
                .setPositiveButton("Create", (d, w) -> createTask(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createTask(String title) {
        if (title.isEmpty()) return;
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(baseUrl);
                JSONObject body = new JSONObject();
                body.put("title", title);
                c.post("/api/plugins/kanban/tasks", body.toString());
                ui.post(() -> { Toast.makeText(this, "Created: " + title, Toast.LENGTH_SHORT).show(); loadBoard(); });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "Failed: " + Err.friendly(e), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showMoveDialog(String id, String title) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(STATUSES, (d, which) -> moveTask(id, STATUSES[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void moveTask(String id, String status) {
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(baseUrl);
                JSONObject body = new JSONObject();
                body.put("status", status);
                c.patch("/api/plugins/kanban/tasks/" + id, body.toString());
                ui.post(() -> { Toast.makeText(this, "Moved to " + status, Toast.LENGTH_SHORT).show(); loadBoard(); });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "Failed: " + Err.friendly(e), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void addError(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(Color.LTGRAY);
        tv.setPadding(16, 16, 16, 16);
        boardRow.addView(tv);
    }
}
