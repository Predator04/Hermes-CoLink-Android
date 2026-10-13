package com.colink.app;

/** Human-friendly error messages (map raw exceptions → plain English). */
public final class Err {
    private Err() {}

    public static String friendly(Throwable t) {
        if (t == null) return "Unknown error";
        String m = t.getMessage();
        if (m == null || m.isEmpty()) m = t.getClass().getSimpleName();
        if (m.contains("401")) return "Invalid credentials or session expired — reconnect";
        if (m.contains("403")) return "Access denied";
        if (m.contains("429")) return "Too many attempts — wait a minute";
        if (m.contains("ConnectException") || m.contains("refused")
                || m.contains("Failed to connect") || m.contains("ECONN"))
            return "Can't reach the dashboard — is it on?";
        if (m.contains("UnknownHost") || m.contains("resolve"))
            return "Unknown address — check the URL";
        if (m.contains("timed out") || m.contains("timeout"))
            return "Timed out — check your connection";
        if (m.contains("SSL") || m.contains("certificate"))
            return "TLS error — check http vs https in the URL";
        return m;
    }
}
