package io.onsure.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonSupport {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private JsonSupport() {}

    public static <T> T read(Path file, Class<T> type) throws Exception {
        return MAPPER.readValue(file.toFile(), type);
    }

    public static byte[] canonicalBytes(Object value) throws Exception {
        ObjectMapper canonical = MAPPER.copy().disable(SerializationFeature.INDENT_OUTPUT);
        return canonical.writeValueAsBytes(value);
    }

    public static void writeAtomic(Path file, Object value) throws Exception {
        Path normalized = file.toAbsolutePath().normalize();
        if (normalized.getParent() == null) throw new IllegalArgumentException("output parent missing");
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        MAPPER.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
