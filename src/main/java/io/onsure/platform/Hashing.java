package io.onsure.platform;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class Hashing {
    private Hashing() {}

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String file(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    static String tree(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .forEach(files::add);
        }
        ByteArrayOutputStream aggregate = new ByteArrayOutputStream();
        for (Path file : files) {
            aggregate.write(relative(root, file).getBytes(StandardCharsets.UTF_8));
            aggregate.write(0);
            aggregate.write(Files.readAllBytes(file));
            aggregate.write(0);
        }
        return sha256(aggregate.toByteArray());
    }

    static String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }
}
