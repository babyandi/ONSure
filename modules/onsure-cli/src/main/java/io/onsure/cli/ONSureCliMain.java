package io.onsure.cli;

import io.onsure.platform.ONSureCli;

/** Stable module-owned entry point delegating to the compatibility CLI API in core. */
public final class ONSureCliMain {
    private ONSureCliMain() {}

    public static void main(String[] args) throws Exception {
        ONSureCli.main(args);
    }
}
