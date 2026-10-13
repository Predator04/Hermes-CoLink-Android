package com.colink.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal HTTP client for the Hermes dashboard (gated mode: cookie auth).
 * Login -> session cookie -> /api/* requests with the cookie.
 */
public class DashboardClient {
    private final String baseUrl; // e.g. http://192.168.1.8:9119

    private static final CookieManager COOKIES = new CookieManager();
    static {
        COOKIES.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(COOKIES);
    }

    public DashboardClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** POST /auth/password-login with JSON {provider, username, password}; sets session cookie. */
    public boolean login(String username, String password) throws Exception {
        HttpURLConnection c = open("POST", "/auth/password-login");
        c.setRequestProperty("Content-Type", "application/json");
        org.json.JSONObject body = new org.json.JSONObject();
        body.put("provider", "basic");
        body.put("username", username);
        body.put("password", password);
        writeBody(c, body.toString());
        int code = c.getResponseCode();
        String resp = readResponse(c);
        return (code == 200) && resp.contains("\"ok\"") && hasSessionCookie();
    }

    /** GET /api/sessions — returns the raw JSON body. */
    public String getSessions() throws Exception {
        return get("/api/sessions?limit=20&offset=0&order=recent");
    }

    /** GET /api/auth/me — identity probe (works when authenticated). */
    public String getMe() throws Exception {
        return get("/api/auth/me");
    }

    /** POST /api/auth/ws-ticket — returns the single-use ticket for a WS upgrade. */
    public String getWsTicket() throws Exception {
        HttpURLConnection c = open("POST", "/api/auth/ws-ticket");
        c.setRequestProperty("Content-Type", "application/json");
        writeBody(c, "{}");
        int code = c.getResponseCode();
        String resp = readResponse(c);
        if (code != 200) throw new Exception("ws-ticket failed: HTTP " + code);
        return new org.json.JSONObject(resp).getString("ticket");
    }

    public String get(String path) throws Exception {
        HttpURLConnection c = open("GET", path);
        return readResponse(c);
    }

    /** POST with a JSON body, returning the raw response. */
    public String post(String path, String jsonBody) throws Exception {
        HttpURLConnection c = open("POST", path);
        c.setRequestProperty("Content-Type", "application/json");
        writeBody(c, jsonBody);
        return readResponse(c);
    }

    /** PATCH with a JSON body, returning the raw response. */
    public String patch(String path, String jsonBody) throws Exception {
        HttpURLConnection c = open("PATCH", path);
        c.setRequestProperty("Content-Type", "application/json");
        writeBody(c, jsonBody);
        return readResponse(c);
    }

    private boolean hasSessionCookie() {
        CookieManager cm = (CookieManager) CookieHandler.getDefault();
        if (cm == null || cm.getCookieStore() == null) return false;
        return cm.getCookieStore().getCookies().stream()
                .anyMatch(cookie -> cookie.getName().contains("session"));
    }

    private HttpURLConnection open(String method, String path) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CoLink/0.1");
        return c;
    }

    private void writeBody(HttpURLConnection c, String body) throws Exception {
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readResponse(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
