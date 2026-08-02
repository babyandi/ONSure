package io.onsure.harness;

import io.onsure.common.Sha256;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class Hashing {
    private Hashing() {}

    public static String sha256(String value) {
        return Sha256.digest(value);
    }

    public static String sha256(byte[] value) {
        return Sha256.digest(value);
    }

    public static String sha256(Path file) throws Exception {
        return Sha256.digest(file);
    }

    public static String environmentDigest(String label) {
        List<String> values = List.of(
                "label=" + label,
                "java.version=" + System.getProperty("java.version", "unknown"),
                "java.vendor=" + System.getProperty("java.vendor", "unknown"),
                "os.name=" + System.getProperty("os.name", "unknown"),
                "os.arch=" + System.getProperty("os.arch", "unknown"),
                "os.version=" + System.getProperty("os.version", "unknown"));
        return sha256(values.stream().sorted().reduce("", (a, b) -> a + "\n" + b));
    }

    public static String manifest(Path root, List<Path> files) throws Exception {
        StringBuilder value = new StringBuilder();
        for (Path file : files.stream().sorted(Comparator.comparing(Path::toString)).toList()) {
            value.append(sha256(file)).append("  ")
                    .append(root.relativize(file).toString().replace('\\', '/')).append('\n');
        }
        return value.toString();
    }
}
