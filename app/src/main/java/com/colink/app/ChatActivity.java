package com.colink.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Chat screen: message history + send + live streaming. */
public class ChatActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout messageList;
    private ScrollView scroll;
    private EditText input;
    private GatewayClient gw;
    private String runtimeSessionId;
    private String baseUrl, sessionId;
    private TextView streamingBubble;
    private final StringBuilder streamingText = new StringBuilder();
    private boolean streaming = false;
    private TextToSpeech tts;
    private boolean ttsOn = false;
    private Button ttsButton;
    private boolean callMode = false;
    private Button callButton;

    private static class Msg {
        final boolean fromUser;
        final String text;
        Msg(boolean f, String t) { fromUser = f; text = t; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        baseUrl = getIntent().getStringExtra("url");
        sessionId = getIntent().getStringExtra("session_id");
        String title = getIntent().getStringExtra("title");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 48, 16, 8);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        Button back = new Button(this);
        back.setText("⬅");
        back.setOnClickListener(v -> finish());
        headerRow.addView(back);
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(18);
        header.setTextColor(Color.WHITE);
        headerRow.addView(header, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ttsButton = new Button(this);
        ttsButton.setText("🔇");
        ttsButton.setOnClickListener(v -> {
            ttsOn = !ttsOn;
            ttsButton.setText(ttsOn ? "🔊" : "🔇");
        });
        headerRow.addView(ttsButton);
        callButton = new Button(this);
        callButton.setText("📞");
        callButton.setOnClickListener(v -> toggleCallMode());
        headerRow.addView(callButton);
        root.addView(headerRow);

        scroll = new ScrollView(this);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messageList);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(this);
        input.setHint("Message…");
        input.setTextColor(Color.WHITE);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button cam = new Button(this);
        cam.setText("📷");
        cam.setOnClickListener(v -> pickPhoto());
        inputRow.addView(cam);
        Button mic = new Button(this);
        mic.setText("🎤");
        mic.setOnClickListener(v -> startVoice());
        inputRow.addView(mic);
        Button send = new Button(this);
        send.setText("Send");
        send.setOnClickListener(v -> onSend());
        inputRow.addView(send);
        root.addView(inputRow);

        setContentView(root);
        tts = new TextToSpeech(this, status -> {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) {
                    if (callMode) ui.post(() -> startVoice());
                }
                @Override public void onError(String id) {
                    if (callMode) ui.post(() -> startVoice());
                }
            });
        });
        loadHistory();
    }

    private void loadHistory() {
        addBubble("Loading…", false);
        new Thread(() -> {
            try {
                DashboardClient c = new DashboardClient(baseUrl);
                String json = c.get("/api/sessions/" + sessionId + "/messages?limit=200&order=latest");
                List<Msg> msgs = parseMessages(json);
                ui.post(() -> {
                    messageList.removeAllViews();
                    for (Msg m : msgs) addBubble(m.text, m.fromUser);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    messageList.removeAllViews();
                    addBubble(Err.friendly(e), false);
                });
            }
        }).start();
    }

    private List<Msg> parseMessages(String json) throws Exception {
        List<Msg> out = new ArrayList<>();
        JSONArray arr = new JSONObject(json).optJSONArray("messages");
        if (arr != null) {
            for (int i = arr.length() - 1; i >= 0; i--) { // latest-first → chronological
                JSONObject m = arr.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role", "");
                String content = m.optString("content", "");
                if (content == null || content.trim().isEmpty()) continue;
                if (role.equals("user")) out.add(new Msg(true, content));
                else if (role.equals("assistant")) out.add(new Msg(false, content));
            }
        }
        return out;
    }

    private TextView addBubble(String text, boolean fromUser) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(24, 14, 24, 14);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82));
        tv.setBackgroundColor(fromUser ? Color.rgb(47, 100, 160) : Color.rgb(38, 38, 42));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(fromUser ? Gravity.END : Gravity.START);
        wrap.setPadding(8, 6, 8, 6);
        wrap.addView(tv);
        messageList.addView(wrap);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        return tv;
    }

    private void onSend() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        addBubble(text, true);
        streamReply(text);
    }

    private void streamReply(String text) {
        new Thread(() -> {
            try {
                if (gw == null) {
                    gw = Gw.instance;
                    if (gw == null) {
                        gw = new GatewayClient(baseUrl);
                        gw.setRequestListener(this::onRequest);
                        gw.connectBlocking();
                        Gw.instance = gw;
                    }
                    gw.setEventListener(this::onEvent);
                }
                int waits = 0;
                while (!gw.isConnected() && waits < 5) {
                    if (waits == 0) ui.post(() -> addBubble("Reconnecting…", false));
                    Thread.sleep(3000);
                    waits++;
                }
                if (!gw.isConnected()) {
                    ui.post(() -> addBubble("Still reconnecting — try again in a moment", false));
                    return;
                }
                if (runtimeSessionId == null) {
                    runtimeSessionId = resolveSession();
                }
                JSONObject p = new JSONObject().put("session_id", runtimeSessionId).put("text", text);
                gw.requestSync("prompt.submit", p);
                // assistant text streams in via onEvent → message.delta
            } catch (Exception e) {
                ui.post(() -> {
                    if (streaming) { streamingBubble.setText(Err.friendly(e)); streaming = false; }
                    else addBubble(Err.friendly(e), false);
                });
            }
        }).start();
    }

    private void onEvent(String type, JSONObject payload) {
        if ("message.delta".equals(type)) {
            String t = payload == null ? "" : payload.optString("text", "");
            if (t.isEmpty()) return;
            ui.post(() -> {
                if (!streaming) {
                    streamingBubble = addBubble("", false);
                    streamingText.setLength(0);
                    streaming = true;
                }
                streamingText.append(t);
                streamingBubble.setText(streamingText.toString());
            });
        } else if ("message.complete".equals(type)) {
            final String spoken = streamingText.toString();
            ui.post(() -> { streaming = false; streamingBubble = null; streamingText.setLength(0); });
            if (ttsOn && tts != null && !spoken.isEmpty()) {
                tts.speak(spoken, TextToSpeech.QUEUE_ADD, null, "colink-msg");
            }
        }
    }

    private String resolveSession() throws Exception {
        try {
            return gw.requestSync("session.resume", new JSONObject().put("session_id", sessionId))
                    .optString("session_id", sessionId);
        } catch (Exception e1) {
            try {
                return gw.requestSync("session.activate", new JSONObject().put("session_id", sessionId))
                        .optString("session_id", sessionId);
            } catch (Exception e2) {
                throw new Exception("This session can't be opened — it may have been deleted");
            }
        }
    }

    private void onRequest(String id, String method, JSONObject params) {
        ui.post(() -> {
            Intent i = new Intent(this, CallActivity.class);
            i.putExtra("req_id", id);
            i.putExtra("method", method);
            i.putExtra("params", params == null ? "" : params.toString());
            startActivity(i);
        });
    }

    private void startVoice() {
        try {
            Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(i, 1);
        } catch (Exception e) {
            addBubble("Voice not available: " + e.getMessage(), false);
        }
    }

    private void pickPhoto() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Choose photo"), 2);
        } catch (Exception e) {
            addBubble("Photo picker not available", false);
        }
    }

    private Bitmap readAndDownscale(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, opts);
        in.close();
        int max = 1280;
        int scale = 1;
        while (opts.outWidth / scale > max || opts.outHeight / scale > max) scale *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        in = getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, o2);
        in.close();
        if (bmp == null) throw new Exception("Could not decode image");
        return bmp;
    }

    private void sendPhoto(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bmp = readAndDownscale(uri);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                String dataUrl = "data:image/jpeg;base64," + b64;

                DashboardClient c = new DashboardClient(baseUrl);
                JSONObject body = new JSONObject();
                body.put("data_url", dataUrl);
                body.put("filename", "photo.jpg");
                JSONObject r = new JSONObject(c.post("/api/chat/image-upload", body.toString()));
                String path = r.optString("path", "");
                if (path.isEmpty()) {
                    ui.post(() -> addBubble("Photo upload failed", false));
                    return;
                }
                ui.post(() -> addBubble("📷 Photo sent", true));
                streamReply("/image " + path);
            } catch (Exception e) {
                ui.post(() -> addBubble("Photo failed: " + Err.friendly(e), false));
            }
        }).start();
    }

    private void toggleCallMode() {
        callMode = !callMode;
        if (callMode) {
            callButton.setText("🔴 End");
            ttsOn = true;
            ttsButton.setText("🔊");
            startVoice();
        } else {
            callButton.setText("📞");
            if (tts != null) tts.stop();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(
                    android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                String spoken = r.get(0);
                if (callMode) {
                    addBubble(spoken, true);
                    streamReply(spoken);
                } else {
                    input.setText(spoken);
                    onSend();
                }
            }
        } else if (req == 1 && callMode) {
            // User cancelled / didn't speak — end the call.
            callMode = false;
            callButton.setText("📞");
            if (tts != null) tts.stop();
        } else if (req == 2 && res == RESULT_OK && data != null && data.getData() != null) {
            sendPhoto(data.getData());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gw != null) gw.close();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
