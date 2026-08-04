package io.onsure.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical SHA-256 primitive shared by ONSure components. */
public final class Sha256 {
    private static final String ALGORITHM = "SHA-256";

    private Sha256() {}

    /** Returns the lowercase hexadecimal SHA-256 digest of a UTF-8 string. */
    public static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the lowercase hexadecimal SHA-256 digest of the supplied bytes. */
    public static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITHM).digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", e);
        }
    }

    /** Returns the lowercase hexadecimal SHA-256 digest of a file's exact bytes. */
    public static String digest(Path file) throws IOException {
        return digest(Files.readAllBytes(file));
    }
}
