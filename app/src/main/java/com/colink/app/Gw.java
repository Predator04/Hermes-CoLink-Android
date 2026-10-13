package com.colink.app;

/** Holder for the shared GatewayClient so CallActivity can respond to a request. */
public final class Gw {
    public static GatewayClient instance;
    private Gw() {}
}
