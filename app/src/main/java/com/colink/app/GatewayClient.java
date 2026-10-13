package com.colink.app;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Minimal JSON-RPC WebSocket client for the Hermes dashboard gateway (tui_gateway protocol). */
public class GatewayClient {
    public interface Callback { void onResult(JSONObject result); void onError(String msg); }
    public interface EventListener { void onEvent(String type, JSONObject payload); }

    private final OkHttpClient http = new OkHttpClient();
    private final String baseUrl;
    private WebSocket ws;
    private final Map<String, Callback> pending = new HashMap<>();
    private EventListener eventListener;
    private int nextId = 0;

    public GatewayClient(String baseUrl) { this.baseUrl = baseUrl; }

    public void setEventListener(EventListener l) { this.eventListener = l; }

    /** Fetch a single-use WS ticket, connect, and block until the socket opens. */
    public void connectBlocking() throws Exception {
        String ticket = new DashboardClient(baseUrl).getWsTicket();
        String wsUrl = baseUrl.replaceFirst("^http", "ws") + "/api/ws?ticket=" + ticket;
        final CountDownLatch latch = new CountDownLatch(1);
        final Exception[] err = new Exception[1];
        Request req = new Request.Builder().url(wsUrl).build();
        ws = http.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response r) { latch.countDown(); }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                err[0] = new Exception("WS failed: " + t.getMessage());
                latch.countDown();
            }
            @Override public void onMessage(WebSocket ws, String text) { handleFrame(text); }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new Exception("WS connect timeout");
        if (err[0] != null) throw err[0];
    }

    private void handleFrame(String text) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JSONObject f = new JSONObject(line);
                if (f.has("id") && !f.isNull("id")) {
                    String id = f.optString("id");
                    Callback cb = pending.remove(id);
                    if (cb != null) {
                        if (f.has("error")) {
                            cb.onError(f.optJSONObject("error").optString("message", "rpc error"));
                        } else {
                            cb.onResult(f.optJSONObject("result"));
                        }
                    }
                } else if ("event".equals(f.optString("method"))) {
                    JSONObject p = f.optJSONObject("params");
                    if (p != null && eventListener != null) {
                        eventListener.onEvent(p.optString("type"), p.optJSONObject("payload"));
                    }
                } else if (f.has("method") && f.has("id")) {
                    // server→client request (clarify/approval/…) — this client can't answer it.
                    JSONObject reply = new JSONObject();
                    reply.put("jsonrpc", "2.0");
                    reply.put("id", f.optString("id"));
                    JSONObject err = new JSONObject();
                    err.put("code", -32601);
                    err.put("message", "not supported");
                    reply.put("error", err);
                    ws.send(reply.toString());
                }
            } catch (Exception ignored) { }
        }
    }

    private void request(String method, JSONObject params, Callback cb) {
        String id = "c" + (nextId++);
        pending.put(id, cb);
        try {
            JSONObject f = new JSONObject();
            f.put("jsonrpc", "2.0");
            f.put("id", id);
            f.put("method", method);
            f.put("params", params);
            ws.send(f.toString());
        } catch (Exception e) {
            pending.remove(id);
            cb.onError(e.getMessage());
        }
    }

    /** Blocking request — resolves when the matching response arrives. */
    public JSONObject requestSync(String method, JSONObject params) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JSONObject[] result = new JSONObject[1];
        final Exception[] err = new Exception[1];
        request(method, params, new Callback() {
            public void onResult(JSONObject r) { result[0] = r; latch.countDown(); }
            public void onError(String m) { err[0] = new Exception(m); latch.countDown(); }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) throw new Exception("request timeout: " + method);
        if (err[0] != null) throw err[0];
        return result[0];
    }

    public void close() { if (ws != null) ws.close(1000, "bye"); }
}
