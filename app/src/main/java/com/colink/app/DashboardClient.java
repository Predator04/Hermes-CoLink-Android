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

    public DashboardClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // Cookie manager holds the hermes_session_at cookie across requests.
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cm);
    }

    /** POST /login with username+password; returns true if a session cookie was set. */
    public boolean login(String username, String password) throws Exception {
        HttpURLConnection c = open("POST", "/login");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        String body = "username=" + URLEncoder.encode(username, "UTF-8")
                + "&password=" + URLEncoder.encode(password, "UTF-8");
        writeBody(c, body);
        int code = c.getResponseCode();
        // Follow redirects are disabled; a 302/303 to the dashboard root means success.
        return (code == 200 || code == 302 || code == 303) && hasSessionCookie();
    }

    /** GET /api/sessions — returns the raw JSON body. */
    public String getSessions() throws Exception {
        return get("/api/sessions?limit=20&offset=0&order=recent");
    }

    /** GET /api/auth/me — identity probe (works when authenticated). */
    public String getMe() throws Exception {
        return get("/api/auth/me");
    }

    public String get(String path) throws Exception {
        HttpURLConnection c = open("GET", path);
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
