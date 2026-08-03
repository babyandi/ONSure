package io.onsure.localapi;

import io.onsure.platform.LocalAuthenticatedApiServer;

/** Stable module-owned entry point delegating to the compatibility Local API in core. */
public final class LocalApiMain {
    private LocalApiMain() {}

    public static void main(String[] args) throws Exception {
        LocalAuthenticatedApiServer.main(args);
    }
}
